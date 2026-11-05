package com.zhouge.mahjongscore

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class SessionPlayer(
    val id: String,
    val name: String,
    val bot: Boolean = false,
)

@Serializable
data class SessionRound(
    val seq: Long,
    val winner: String,
    val kind: String,
    val points: Long,                 // 每家应付
    val payers: List<String> = emptyList(),
    val loser: String? = null,        // 旧数据兼容
    val ts: Long,
)

@Serializable
data class SessionRecord(
    val id: String,
    val room: String,
    var startTs: Long,
    var endTs: Long = 0,          // 点"结束"时写入；离开不写
    var lastActivityTs: Long = 0, // 最近一次有动静的时间，算时长用
    val myId: String,
    val players: MutableList<SessionPlayer> = mutableListOf(),
    val rounds: MutableList<SessionRound> = mutableListOf(),
    val balances: MutableMap<String, Long> = mutableMapOf(),
) {
    fun myNet(): Long = balances[myId] ?: 0L

    /**
     * 合并玩家名单：只增不减。
     * 服务端下发的 players 只有在场的人，离场的人会消失；若直接覆盖，
     * 历史流水里已离场的人就会解析不出名字。这里保留离开过的牌友。
     */
    fun mergePlayers(incoming: List<SessionPlayer>) {
        incoming.forEach { np ->
            val i = players.indexOfFirst { it.id == np.id }
            if (i >= 0) players[i] = np else players.add(np)
        }
    }

    /** 名字查询：先查名单，再按 id 兜底 */
    fun nameOf(id: String): String =
        players.find { it.id == id }?.name
            ?: if (id.startsWith("bot-")) "机器人" else "已离场"

    /** 已结束 = 房主点过结束，或开局超过 24 小时 */
    fun isEnded(nowTs: Long = System.currentTimeMillis() / 1000): Boolean =
        endTs > 0 || (startTs > 0 && nowTs - startTs >= 24 * 3600)

    fun effectiveEndTs(): Long =
        when {
            endTs > 0 -> endTs
            lastActivityTs > startTs -> lastActivityTs
            else -> startTs
        }

    fun durationText(): String {
        val sec = (effectiveEndTs() - startTs).coerceAtLeast(0)
        return when {
            sec < 60 -> "${sec}秒"
            sec < 3600 -> "${sec / 60}分钟"
            else -> "${sec / 3600}小时${(sec % 3600) / 60}分"
        }
    }

    fun timeRangeText(): String {
        val fmt = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
        return "${fmt.format(java.util.Date(startTs * 1000))} ~ ${
            if (endTs > 0) fmt.format(java.util.Date(endTs * 1000)) else if (isEnded()) "已超时" else "…"
        }"
    }

    fun statusText(): String = if (isEnded()) "已结束" else "进行中"

    /** 各方输赢一行文本 */
    fun balancesLine(): String =
        players.joinToString("  ") { p ->
            val b = balances[p.id] ?: 0L
            "${p.name} ${if (b > 0) "+$b" else "$b"}"
        }
}

object HistoryStore {
    private val json = Json { ignoreUnknownKeys = true }

    private fun dir(ctx: Context): File = File(ctx.filesDir, "history").apply { mkdirs() }

    fun save(ctx: Context, s: SessionRecord) {
        runCatching {
            File(dir(ctx), "${s.id}.json").writeText(json.encodeToString(s))
        }
    }

    fun loadAll(ctx: Context): List<SessionRecord> =
        dir(ctx).listFiles()
            ?.filter { it.extension == "json" }
            ?.mapNotNull { f -> runCatching { json.decodeFromString<SessionRecord>(f.readText()) }.getOrNull() }
            ?.sortedByDescending { it.startTs }
            ?: emptyList()

    fun delete(ctx: Context, id: String) {
        File(dir(ctx), "$id.json").delete()
    }

    /** 本地历史数据总大小（设置页"清除缓存"显示用） */
    fun sizeText(ctx: Context): String {
        var bytes = 0L
        dir(ctx).listFiles()?.forEach { bytes += it.length() }
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(java.util.Locale.CHINA, "%.1f KB", bytes / 1024.0)
            else -> String.format(java.util.Locale.CHINA, "%.1f MB", bytes / 1024.0 / 1024)
        }
    }

    /** 清空全部对局数据（设置页二次确认后调用） */
    fun clearAll(ctx: Context) {
        dir(ctx).listFiles()?.forEach { it.delete() }
    }
}
