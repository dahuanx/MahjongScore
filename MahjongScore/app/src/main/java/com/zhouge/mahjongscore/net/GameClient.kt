package com.zhouge.mahjongscore.net

import com.zhouge.mahjongscore.Identity

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

@Serializable
data class PlayerDto(
    val id: String,
    val name: String,
    val seat: Int,
    val host: Boolean = false,
    val online: Boolean = false,
    val wind: String = "",                 // 吊风结果：东风/南风/西风/北风
)

@Serializable
data class RoundDto(
    val seq: Long,
    val winner: String,
    val kind: String,
    val points: Long,                       // 每家应付
    val payers: List<String> = emptyList(),
    val loser: String? = null,              // 旧数据兼容
    val ts: Long,
)

@Serializable
data class S2C(
    val type: String,
    val room: String? = null,
    val you: String? = null,
    val host: String? = null,
    val roomStart: Long = 0,
    val closed: Boolean = false,
    val players: List<PlayerDto> = emptyList(),
    val rounds: List<RoundDto> = emptyList(),
    val balances: Map<String, Long>? = null,
    val scores: Map<String, Long>? = null,
    val msg: String? = null,
)

enum class Screen { LOBBY, GAME, HISTORY, HISTORY_DETAIL, STATS, SETTINGS, DOC, RULES }
enum class Conn { DOWN, CONNECTING, UP }

data class GameUi(
    val screen: Screen = Screen.LOBBY,
    val conn: Conn = Conn.DOWN,
    val room: String = "",
    val myId: String = "",
    val myName: String = "",
    val hostId: String = "",
    val players: List<PlayerDto> = emptyList(),
    /** id → 昵称 的历史缓存，离场的人也在里面，流水还原名字用 */
    val names: Map<String, String> = emptyMap(),
    val rounds: List<RoundDto> = emptyList(),
    val balances: Map<String, Long> = emptyMap(),
    val toast: String? = null,
    val reconnecting: Boolean = false,
    val lastRoom: String = "",
    val history: List<com.zhouge.mahjongscore.SessionRecord> = emptyList(),
    val historyDetailId: String? = null,
    /** 文档页标识：terms / privacy / about */
    val docKey: String? = null,
    /** 房间生效的分值表（胡型 → 每家应付） */
    val scores: Map<String, Long> = com.zhouge.mahjongscore.KIND_POINTS,
)

object GameClient {
    const val SERVER_URL = "ws://182.92.191.33:9100/ws"
    /** 同一台服务器的 HTTP 基址，更新检查用 */
    const val HTTP_BASE = "http://182.92.191.33:9100"

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var ws: WebSocket? = null
    private var pendingJoin: ((S2C) -> Unit)? = null
    private var pendingRoom = ""
    private var wantConnected = false

    // 历史记录：由 MainActivity 注入 Context
    var appContext: android.content.Context? = null
    private var currentSession: com.zhouge.mahjongscore.SessionRecord? = null

    // 昵称缓存：只增不减。离场的人不在 players 里，但流水还得显示名字
    private val nameCache = LinkedHashMap<String, String>()
    private fun cacheNames(list: List<PlayerDto>) {
        list.forEach { nameCache[it.id] = it.name }
    }

    val ui = kotlinx.coroutines.flow.MutableStateFlow(GameUi())

    fun updateName(name: String) {
        ui.value = ui.value.copy(myName = name)
    }

    fun hostGame(name: String) {
        ui.value = ui.value.copy(myName = name, reconnecting = false)
        connectAndJoin(room = "", onJoined = { s ->
            ui.value = ui.value.copy(screen = Screen.GAME, room = s.room ?: "")
        })
    }

    fun joinRoom(room: String, name: String) {
        ui.value = ui.value.copy(myName = name, reconnecting = false)
        connectAndJoin(room = room, onJoined = { s ->
            ui.value = ui.value.copy(screen = Screen.GAME, room = s.room ?: room)
        })
    }

    /** 记一局：赢家 + 胡型；付款方由服务端按胡型判定（点炮单付，其余三家付） */
    fun play(winner: String, kind: String) {
        val msg = buildJsonObject {
            put("type", "play")
            put("winner", winner)
            put("kind", kind)
        }
        ws?.send(msg.toString())
    }

    fun undo(seq: Long) {
        val msg = buildJsonObject {
            put("type", "undo")
            put("seq", seq)
        }
        ws?.send(msg.toString())
    }

    /** 吊风：服务端随机分配东南西北，全房同步 */
    fun drawWind() {
        ws?.send("""{"type":"wind"}""")
    }

    fun endGame() {
        val msg = buildJsonObject { put("type", "end") }
        ws?.send(msg.toString())
    }

    /** 房主保存计分规则：空 map 表示恢复默认 */
    fun sendSetScores(scores: Map<String, Long>) {
        val msg = buildJsonObject {
            put("type", "setScores")
            put("scores", buildJsonObject { scores.forEach { (k, v) -> put(k, v) } })
        }
        ws?.send(msg.toString())
    }

    fun leave() {
        wantConnected = false
        ws?.close(1000, "leave")
        ws = null
        // 离开不结束对局：session 保持"进行中"，同一房间回来继续累计
        currentSession?.let { s ->
            s.lastActivityTs = System.currentTimeMillis() / 1000
            appContext?.let { com.zhouge.mahjongscore.HistoryStore.save(it, s) }
        }
        val lastRoom = ui.value.room.ifEmpty { ui.value.lastRoom }
        ui.value = ui.value.copy(
            screen = Screen.LOBBY, conn = Conn.DOWN, room = "",
            players = emptyList(), rounds = emptyList(), balances = emptyMap(),
            reconnecting = false, lastRoom = lastRoom,
        )
    }

    // ---------- 历史对局导航 ----------

    fun openHistory() {
        val ctx = appContext
        val list = if (ctx != null) com.zhouge.mahjongscore.HistoryStore.loadAll(ctx) else emptyList()
        ui.value = ui.value.copy(screen = Screen.HISTORY, history = list, historyDetailId = null)
    }

    fun openStats() {
        val ctx = appContext
        val list = if (ctx != null) com.zhouge.mahjongscore.HistoryStore.loadAll(ctx) else emptyList()
        ui.value = ui.value.copy(screen = Screen.STATS, history = list, historyDetailId = null)
    }

    fun openSettings() {
        ui.value = ui.value.copy(screen = Screen.SETTINGS, historyDetailId = null)
    }

    /** 打开内嵌文档页（用户协议 / 隐私政策 / 关于我们） */
    fun openDoc(key: String) {
        ui.value = ui.value.copy(screen = Screen.DOC, docKey = key)
    }

    /** 打开计分规则设置页 */
    fun openRules() {
        ui.value = ui.value.copy(screen = Screen.RULES, docKey = null)
    }

    /** 规则页返回：回设置页 */
    fun rulesBack() {
        ui.value = ui.value.copy(screen = Screen.SETTINGS, docKey = null)
    }

    /** 文档页返回：回到设置页 */
    fun docBack() {
        ui.value = ui.value.copy(screen = Screen.SETTINGS, docKey = null)
    }

    /** 清除缓存后调用：丢弃内存中的当前会话引用 */
    fun resetCurrentSession() {
        currentSession = null
    }

    fun openHistoryDetail(id: String) {
        ui.value = ui.value.copy(screen = Screen.HISTORY_DETAIL, historyDetailId = id)
    }

    fun historyBack() {
        ui.value = ui.value.copy(screen = Screen.HISTORY, historyDetailId = null)
    }

    /** 历史、统计、设置这些二级页面统一回大厅（左上角返回和系统返回键走同一条路） */
    fun backToLobby() {
        ui.value = ui.value.copy(screen = Screen.LOBBY, historyDetailId = null)
    }

    /** 底部 Tab「对局」：仅在房间连接还在时切回对局屏（不断线、不离开房间） */
    fun gotoGame() {
        val u = ui.value
        if (u.room.isNotEmpty() && u.screen != Screen.GAME) {
            ui.value = u.copy(screen = Screen.GAME, historyDetailId = null)
        }
    }

    fun deleteSession(id: String) {
        appContext?.let { com.zhouge.mahjongscore.HistoryStore.delete(it, id) }
        val list = appContext?.let { com.zhouge.mahjongscore.HistoryStore.loadAll(it) } ?: emptyList()
        ui.value = ui.value.copy(history = list, screen = Screen.HISTORY, historyDetailId = null)
    }

    private fun connectAndJoin(room: String, onJoined: (S2C) -> Unit) {
        pendingJoin = onJoined
        pendingRoom = room
        wantConnected = true
        openSocket()
    }

    private fun openSocket() {
        val st = ui.value
        ui.value = st.copy(conn = Conn.CONNECTING)
        val myId = st.myId
        val myName = st.myName
        val req = Request.Builder().url(SERVER_URL).build()
        ws = http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val join = buildJsonObject {
                    put("type", "join")
                    if (pendingRoom.isNotEmpty()) put("room", pendingRoom)
                    put("player_id", myId)
                    put("name", myName)
                    // 建新房时带上房主的分值偏好
                    if (pendingRoom.isEmpty()) {
                        appContext?.let { ctx ->
                            Identity.loadScores(ctx)?.let { pref ->
                                put("scores", buildJsonObject { pref.forEach { (k, v) -> put(k, v) } })
                            }
                        }
                    }
                }
                webSocket.send(join.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val m = try { json.decodeFromString(S2C.serializer(), text) } catch (e: Exception) { return }
                onMessage(m)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!wantConnected) return
                val cur = ui.value
                if (cur.screen == Screen.GAME) {
                    ui.value = cur.copy(conn = Conn.DOWN, reconnecting = true, toast = "连接断开，正在重连…")
                    scheduleReconnect()
                } else {
                    ui.value = cur.copy(conn = Conn.DOWN, toast = "无法连接服务器")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (wantConnected && ui.value.screen == Screen.GAME) {
                    scheduleReconnect()
                }
            }
        })
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var retryCount = 0

    private fun scheduleReconnect() {
        if (!wantConnected) return
        retryCount++
        mainHandler.postDelayed({
            if (wantConnected) openSocket()
        }, 3000)
    }

    private fun onMessage(m: S2C) {
        val cur = ui.value
        when (m.type) {
            "state" -> {
                val first = cur.screen != Screen.GAME
                val room = m.room ?: cur.room
                if (room.isNotEmpty() && room != cur.room) nameCache.clear()
                cacheNames(m.players)

                // 房主点过"结束"：服务端广播 closed 状态 → 收尾历史、回大厅
                if (m.closed) {
                    currentSession?.let { s ->
                        if (s.room == room) {
                            s.endTs = System.currentTimeMillis() / 1000
                            s.mergePlayers(m.players.map { p -> com.zhouge.mahjongscore.SessionPlayer(p.id, p.name, p.id.startsWith("bot-")) })
                            m.balances?.let { b -> s.balances.clear(); s.balances.putAll(b) }
                            appContext?.let { com.zhouge.mahjongscore.HistoryStore.save(it, s) }
                        }
                    }
                    currentSession = null
                    appContext?.let { com.zhouge.mahjongscore.Identity.clearLastRoom(it) }
                    ui.value = cur.copy(
                        screen = Screen.LOBBY, conn = Conn.DOWN, room = "",
                        players = emptyList(), rounds = emptyList(), balances = emptyMap(),
                        reconnecting = false, lastRoom = "",
                        toast = "对局已结束，本房间无法再进入",
                    )
                    wantConnected = false
                    ws?.close(1000, "closed")
                    ws = null
                    return
                }

                // 历史记录按房间合并：同一房间只有一条（离开几次都算同一桌）
                val ctx = appContext
                if (ctx != null && room.isNotEmpty()) {
                    val existing = currentSession
                    if (existing == null || existing.room != room) {
                        // 先找该房间未结束的旧记录续用（App 重启后内存丢失的场景）
                        val resumed = com.zhouge.mahjongscore.HistoryStore.loadAll(ctx)
                            .firstOrNull { it.room == room && !it.isEnded() }
                        currentSession = resumed ?: com.zhouge.mahjongscore.SessionRecord(
                            id = "r$room",
                            room = room,
                            startTs = if (m.roomStart > 0) m.roomStart else System.currentTimeMillis() / 1000,
                            myId = m.you ?: cur.myId,
                        )
                    }
                    currentSession?.let { s ->
                        if (s.startTs == 0L && m.roomStart > 0) s.startTs = m.roomStart
                        s.lastActivityTs = System.currentTimeMillis() / 1000
                        s.mergePlayers(m.players.map { p -> com.zhouge.mahjongscore.SessionPlayer(p.id, p.name, p.id.startsWith("bot-")) })
                        m.balances?.let { b -> s.balances.clear(); s.balances.putAll(b) }
                        s.rounds.clear(); s.rounds.addAll(m.rounds.map { com.zhouge.mahjongscore.SessionRound(it.seq, it.winner, it.kind, it.points, it.payers, it.loser, it.ts) })
                        com.zhouge.mahjongscore.HistoryStore.save(ctx, s)
                    }
                }

                ui.value = cur.copy(
                    screen = Screen.GAME,
                    conn = Conn.UP,
                    reconnecting = false,
                    room = room,
                    myId = m.you ?: cur.myId,
                    hostId = m.host ?: "",
                    players = m.players,
                    names = HashMap(nameCache),
                    rounds = m.rounds,
                    balances = m.balances ?: cur.balances,
                    scores = m.scores ?: cur.scores,
                    toast = if (cur.reconnecting) "已重新连接" else cur.toast,
                    lastRoom = room,
                )
                if (room.isNotEmpty()) {
                    appContext?.let {
                        com.zhouge.mahjongscore.Identity.saveLastRoom(
                            it, room,
                            if (m.roomStart > 0) m.roomStart else System.currentTimeMillis() / 1000,
                        )
                    }
                }
                if (first) pendingJoin?.invoke(m)
                pendingJoin = null
            }
            "round" -> {
                if (m.balances != null && m.rounds.isNotEmpty()) {
                    currentSession?.let { s ->
                        m.rounds.forEach { r -> s.rounds.add(com.zhouge.mahjongscore.SessionRound(r.seq, r.winner, r.kind, r.points, r.payers, r.loser, r.ts)) }
                        s.balances.clear(); s.balances.putAll(m.balances)
                        appContext?.let { com.zhouge.mahjongscore.HistoryStore.save(it, s) }
                    }
                    ui.value = cur.copy(rounds = cur.rounds + m.rounds, balances = m.balances)
                }
            }
            "scores" -> {
                // 房主改了计分规则：全房同步生效
                m.scores?.let { newScores ->
                    ui.value = cur.copy(scores = newScores, toast = "计分规则已更新")
                }
            }
            "undid" -> {
                currentSession?.let { s ->
                    s.rounds.clear(); s.rounds.addAll(m.rounds.map { com.zhouge.mahjongscore.SessionRound(it.seq, it.winner, it.kind, it.points, it.payers, it.loser, it.ts) })
                    m.balances?.let { b -> s.balances.clear(); s.balances.putAll(b) }
                    appContext?.let { com.zhouge.mahjongscore.HistoryStore.save(it, s) }
                }
                ui.value = cur.copy(rounds = m.rounds, balances = m.balances ?: cur.balances)
            }
            "peers" -> {
                cacheNames(m.players)
                currentSession?.let { s ->
                    s.mergePlayers(m.players.map { p -> com.zhouge.mahjongscore.SessionPlayer(p.id, p.name, p.id.startsWith("bot-")) })
                }
                ui.value = cur.copy(players = m.players, names = HashMap(nameCache))
            }
            "error" -> {
                ui.value = cur.copy(toast = m.msg ?: "操作失败")
            }
        }
    }

    fun clearToast() {
        ui.value = ui.value.copy(toast = null)
    }
}
