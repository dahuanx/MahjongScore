package com.zhouge.mahjongscore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhouge.mahjongscore.KIND_POINTS
import com.zhouge.mahjongscore.KINDS_LOSER
import com.zhouge.mahjongscore.KINDS_SELF
import com.zhouge.mahjongscore.gainOf
import com.zhouge.mahjongscore.kindName
import com.zhouge.mahjongscore.net.Conn
import com.zhouge.mahjongscore.net.GameClient
import com.zhouge.mahjongscore.net.GameUi
import com.zhouge.mahjongscore.net.PlayerDto
import com.zhouge.mahjongscore.net.RoundDto
import com.zhouge.mahjongscore.payersOf
import com.zhouge.mahjongscore.roundPayText
import com.zhouge.mahjongscore.roundTitle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 待记分的目标：点了谁（谁胡了） */
private data class ScoreTarget(val winnerId: String, val winnerName: String, val bySelf: Boolean)

@Composable
fun GameScreen() {
    val ui by GameClient.ui.collectAsState()
    var showEndDialog by remember { mutableStateOf(false) }
    var showWindDialog by remember { mutableStateOf(false) }
    var showInvite by remember { mutableStateOf(false) }
    var undoTarget by remember { mutableStateOf<RoundDto?>(null) }
    var scoreTarget by remember { mutableStateOf<ScoreTarget?>(null) }

    val canPlay = ui.conn == Conn.UP && ui.players.size >= 4
    val sortedRounds = ui.rounds.sortedByDescending { it.seq }
    val scroll = rememberScrollState()

    // 新记一局后自动滚回顶部（流水最新在最上面）
    LaunchedEffect(ui.rounds.size) {
        if (ui.rounds.isNotEmpty()) scroll.animateScrollTo(0)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ---- 可滚动内容 ----
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp),
        ) {
            // 顶栏芯片
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BChip(
                    text = when {
                        ui.reconnecting -> "重连中 ${ui.room}"
                        ui.conn == Conn.UP -> "房间 ${ui.room}"
                        else -> "连接中…"
                    },
                    live = ui.conn == Conn.UP,
                )
                Spacer(Modifier.width(8.dp))
                BChip("第 ${ui.rounds.size + 1} 局", dark = true)
                Spacer(Modifier.width(6.dp))
                if (ui.hostId == ui.myId) {
                    BChip("规则") { GameClient.openRules() }
                    Spacer(Modifier.width(6.dp))
                }
                BChip("邀请", enabled = ui.conn == Conn.UP) { showInvite = true }
                Spacer(Modifier.weight(1f))
                BChip("离房", enabled = ui.conn != Conn.CONNECTING) { GameClient.leave() }
            }

            if (ui.reconnecting) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(B.Warn, RoundedCornerShape(12.dp))
                        .border(2.5.dp, B.Ink, RoundedCornerShape(12.dp))
                        .padding(horizontal = 11.dp, vertical = 8.dp)
                ) {
                    Text(
                        "连接已断开，3 秒后自动重连，已记流水不会丢失",
                        fontSize = 10.5.sp, fontWeight = FontWeight.Black,
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            // ---- 我的主卡 ----
            val me = ui.players.find { it.id == ui.myId }
            if (me != null) {
                MeCard(ui, me, canPlay) { scoreTarget = ScoreTarget(me.id, me.name, bySelf = true) }
                Spacer(Modifier.height(12.dp))
            }

            // ---- 三家小卡 ----
            val others = ui.players.filter { it.id != ui.myId }
            if (others.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    others.take(3).forEachIndexed { i, p ->
                        MiniCard(
                            ui = ui, p = p, rotation = listOf(1.4f, -1.8f, 1f)[i % 3],
                            enabled = canPlay,
                            modifier = Modifier.weight(1f),
                        ) {
                            scoreTarget = ScoreTarget(p.id, p.name, bySelf = false)
                        }
                    }
                }
            }

            // ---- 操作提示 ----
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (canPlay) "我胡牌点自己 · 我点炮点赢家" else "坐满4人才能开局（当前 ${ui.players.size}/4）",
                    fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = B.Ink2,
                )
            }

            Spacer(Modifier.height(8.dp))
            SectionHeader("对局流水", ui.rounds.size)
            Spacer(Modifier.height(9.dp))

            // ---- 票券流水 ----
            if (sortedRounds.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(B.White.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                        .border(2.5.dp, B.Ink.copy(alpha = 0.34f), RoundedCornerShape(18.dp))
                        .padding(vertical = 24.dp, horizontal = 14.dp)
                ) {
                    Text(
                        "还没有记分。我胡牌点自己，我点炮点赢家。",
                        fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                        color = B.Ink2, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            sortedRounds.forEach { r ->
                TicketRow(
                    r = r,
                    ui = ui,
                    canUndo = ui.hostId == ui.myId && r.seq == (ui.rounds.maxOfOrNull { it.seq } ?: 0),
                    onUndo = { undoTarget = r },
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(10.dp))
        }

        // ---- 底部操作 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BButton("吊风", Modifier.weight(1f), enabled = canPlay) { showWindDialog = true }
            BButton("离开", Modifier.weight(1f), enabled = ui.conn != Conn.CONNECTING) { GameClient.leave() }
            if (ui.hostId == ui.myId) {
                BButton("结束", Modifier.weight(1f), style = BtnStyle.DARK, enabled = ui.conn == Conn.UP) {
                    showEndDialog = true
                }
            }
        }
    }

    // ---- 弹层 ----
    scoreTarget?.let { t ->
        ScoreSheet(
            ui = ui,
            target = t,
            onDismiss = { scoreTarget = null },
            onConfirm = { kind ->
                GameClient.play(t.winnerId, kind)
                scoreTarget = null
            },
        )
    }

    if (showEndDialog) {
        BDialog(
            title = "结束对局？",
            text = "结束后无法再进入这个房间。\n中途离开请点「离开」。",
            color = B.P4,
            okText = "确认结束",
            onOk = { showEndDialog = false; GameClient.endGame() },
            onDismiss = { showEndDialog = false },
        )
    }

    if (showInvite) {
        InviteSheet(
            inRoom = ui.conn == Conn.UP && ui.room.isNotEmpty(),
            room = ui.room,
            onDismiss = { showInvite = false },
        )
    }

    if (showWindDialog) {
        BDialog(
            title = "重新吊风？",
            text = "将重新洗东南西北，四方风位可能全部变化。\n已记分数不受影响。",
            color = B.P2,
            okText = "吊风",
            onOk = { showWindDialog = false; GameClient.drawWind() },
            onDismiss = { showWindDialog = false },
        )
    }

    undoTarget?.let { r ->
        BDialog(
            title = "撤销第 ${r.seq} 局",
            text = "将删除这一局流水并重算四方分数。\n此操作不可恢复。",
            color = B.P3,
            okText = "确认撤销",
            onOk = { GameClient.undo(r.seq); undoTarget = null },
            onDismiss = { undoTarget = null },
        )
    }
}

// ---------------- 我的主卡 ----------------

@Composable
private fun MeCard(ui: GameUi, me: PlayerDto, enabled: Boolean, onClick: () -> Unit) {
    val bal = ui.balances[me.id] ?: 0L

    // 排名与分差
    val ranked = ui.players.map { it.id to (ui.balances[it.id] ?: 0L) }.sortedByDescending { it.second }
    val myRank = ranked.indexOfFirst { it.first == me.id }.coerceAtLeast(0)
    val leaderBal = ranked.firstOrNull()?.second ?: 0L
    val gap = leaderBal - bal

    Sticker(
        bg = B.P1,
        radius = 26.dp,
        shadow = 6.dp,
        rotation = -1.1f,
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 15.dp)) {
            // 右上角五角星贴纸：绝对定位，溢出卡片边缘
            Text(
                "★",
                fontSize = 44.sp,
                color = B.Paper,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 14.dp, y = (-18).dp)
                    .rotate(12f),
            )
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WindBadge(me.wind)
                    Spacer(Modifier.width(7.dp))
                    Text(me.name, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .background(B.White, RoundedCornerShape(50))
                            .border(2.5.dp, B.Ink, RoundedCornerShape(50))
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    ) {
                        Text("我胡点这", fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    BNum(signed(bal), 56.sp, letterSpacing = (-3).sp, color = scoreColor(bal))
                    Spacer(Modifier.width(4.dp))
                    Text("分", fontSize = 13.sp, fontWeight = FontWeight.Black, color = B.Ink.copy(alpha = 0.66f))
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier
                            .background(B.Ink, RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            if (myRank == 0) "暂列第一" else "暂列第${"一二三四".getOrElse(myRank) { '?' }}",
                            fontSize = 10.5.sp, fontWeight = FontWeight.Black,
                            color = B.P1,
                        )
                    }
                    if (gap > 0) {
                        Text(
                            if (myRank == 0) "领先 $gap 分" else "落后 $gap 分",
                            fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = B.Ink2,
                        )
                    }
                }
            }
        }
    }
}

// ---------------- 三家小卡 ----------------

@Composable
private fun MiniCard(
    ui: GameUi,
    p: PlayerDto,
    rotation: Float,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bal = ui.balances[p.id] ?: 0L
    val offline = !p.online
    Sticker(
        bg = playerColor(p.id, ui.myId),
        radius = 18.dp,
        shadow = 4.dp,
        rotation = rotation,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier.alpha(if (offline) 0.48f else 1f),
    ) {
        Column(Modifier.padding(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    p.name,
                    fontSize = 11.5.sp, fontWeight = FontWeight.Black,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (offline) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "离线",
                        fontSize = 7.5.sp, fontWeight = FontWeight.Black,
                        color = B.Ink,
                        modifier = Modifier
                            .background(B.White.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                            .border(1.5.dp, B.Ink, RoundedCornerShape(4.dp))
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                    )
                }
                if (p.wind.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    WindBadge(p.wind, small = true)
                }
            }
            Spacer(Modifier.height(5.dp))
            BNum(signed(bal), 24.sp, letterSpacing = (-1.5).sp)
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(B.White.copy(alpha = 0.78f), RoundedCornerShape(6.dp))
                    .border(2.dp, B.Ink, RoundedCornerShape(6.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text("点炮给他", fontSize = 8.5.sp, fontWeight = FontWeight.Black, maxLines = 1, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

// ---------------- 票券 ----------------

/**
 * 一局流水的票券。编号块底色 = 赢家色，一眼看出谁胡的。
 * 对局屏与历史详情共用。
 */
@Composable
fun TicketRow(
    r: RoundDto,
    nameOf: (String) -> String,
    winnerColor: Color,
    showUndo: Boolean,
    onUndo: () -> Unit,
) {
    val time = remember(r.ts) { SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(r.ts * 1000)) }
    val payers = payersOf(r.payers, r.loser)
    val gain = gainOf(r.points, payers.size)
    Sticker(
        bg = B.White,
        radius = 14.dp,
        shadow = 3.dp,
        stroke = 2.5.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(26.dp)
                    .background(winnerColor, RoundedCornerShape(8.dp))
                    .border(2.dp, B.Ink, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                BNum("${r.seq}", 10.sp)
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    roundTitle(r.seq, r.kind, nameOf(r.winner), payers.map { nameOf(it) }),
                    fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                )
                Text(
                    "${roundPayText(r.kind, r.points, payers.size)} · $time",
                    fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = B.Ink2,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .background(B.WinBg, RoundedCornerShape(8.dp))
                    .border(2.dp, B.Ink, RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                BNum("+$gain", 15.sp, color = B.Win)
            }
            if (showUndo) {
                Spacer(Modifier.width(6.dp))
                BChip("撤销") { onUndo() }
            }
        }
    }
}

@Composable
private fun TicketRow(r: RoundDto, ui: GameUi, canUndo: Boolean, onUndo: () -> Unit) {
    val nameOf = remember(ui.players, ui.names) {
        { id: String ->
            ui.players.find { it.id == id }?.name
                ?: ui.names[id]
                ?: if (id.startsWith("bot-")) "机器人" else "已离场"
        }
    }
    TicketRow(r, nameOf, playerColor(r.winner, ui.myId), canUndo, onUndo)
}

// ---------------- 记分底部弹层 ----------------

/**
 * 记分面板。
 * - 点自己的卡：平胡/自摸/搂宝/宝中宝，其余三家自动付
 * - 点别人的卡：只有「点炮」，由操作者一家付
 */
@Composable
private fun ScoreSheet(
    ui: GameUi,
    target: ScoreTarget,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val kinds = if (target.bySelf) KINDS_SELF else KINDS_LOSER
    var kind by remember(target) { mutableStateOf(kinds.first()) }
    val pts = ui.scores[kind] ?: (KIND_POINTS[kind] ?: 0L)
    val others = ui.players.filter { it.id != target.winnerId }
    val winnerColor = playerColor(target.winnerId, ui.myId)
    val myName = ui.myName.ifEmpty { "我" }

    BSheet(onDismiss = onDismiss) {
        // 头部
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(winnerColor, RoundedCornerShape(12.dp))
                    .border(3.dp, B.Ink, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(shortName(target.winnerName), fontSize = 16.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (target.bySelf) "我胡了" else "给 ${target.winnerName} 点炮",
                    fontSize = 17.sp, fontWeight = FontWeight.Black,
                )
                Text(
                    if (target.bySelf) "记在自己名下，其余三家自动付" else "只有你一家付分，另外两家不扣",
                    fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = B.Ink2,
                )
            }
            BChip("✕") { onDismiss() }
        }
        Spacer(Modifier.height(14.dp))

        // 胡型按钮：2 列网格（单选项时 1 列全宽）
        if (kinds.size == 1) {
            val only = kinds.first()
            KindCell(
                k = only, points = ui.scores[only] ?: (KIND_POINTS[only] ?: 0L), selected = true, color = winnerColor,
                modifier = Modifier.fillMaxWidth(), onClick = { },
            )
        } else {
            kinds.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    row.forEach { k ->
                        KindCell(
                            k = k, points = ui.scores[k] ?: (KIND_POINTS[k] ?: 0L), selected = kind == k, color = winnerColor,
                            modifier = Modifier.weight(1f),
                        ) { kind = k }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(9.dp))
            }
        }
        Spacer(Modifier.height(4.dp))

        // 结算预览
        Box(
            Modifier
                .fillMaxWidth()
                .background(B.Paper2, RoundedCornerShape(14.dp))
                .border(2.5.dp, B.Ink.copy(alpha = 0.34f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column {
                Text("本局结算", fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 2.5.sp, color = B.Ink2)
                Spacer(Modifier.height(7.dp))
                if (target.bySelf) {
                    PreviewRow(myName + "（我）", "+${pts * others.size}", winnerColor, positive = true)
                    others.forEach { o ->
                        PreviewRow(o.name, "-$pts", playerColor(o.id, ui.myId), positive = false)
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "其余三家各付 $pts 分，你共收 ${pts * others.size} 分。",
                        fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = B.Ink2,
                    )
                } else {
                    PreviewRow("$myName（我）", "-$pts", playerColor(ui.myId, ui.myId), positive = false)
                    PreviewRow(target.winnerName, "+$pts", winnerColor, positive = true)
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "只有你一家付分，另外两家不扣分。",
                        fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = B.Ink2,
                    )
                }
            }
        }
        Spacer(Modifier.height(13.dp))

        BButton("确认记分", Modifier.fillMaxWidth(), style = BtnStyle.DARK, height = 50.dp, fontSize = 15.sp) {
            onConfirm(kind)
        }
    }
}

@Composable
private fun PreviewRow(who: String, value: String, dotColor: Color, positive: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(dotColor, RoundedCornerShape(3.dp))
                .border(2.dp, B.Ink, RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.width(6.dp))
        Text(who, fontSize = 11.5.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.weight(1f))
        BNum(value, 17.sp, color = if (positive) B.Win else B.Lose)
    }
}

@Composable
private fun KindCell(
    k: String,
    points: Long,
    selected: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val desc = if (k == "dianpao") "仅你一家付" else "三家各付"
    Sticker(
        bg = if (selected) color else B.White,
        radius = 14.dp,
        shadow = 3.dp,
        onClick = onClick,
        modifier = modifier,
    ) {
        Box(Modifier.padding(horizontal = 11.dp, vertical = 10.dp)) {
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .background(B.Ink, RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✓", fontSize = 10.sp, fontWeight = FontWeight.Black, color = B.White)
                }
            }
            Column {
                Text(kindName(k), fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    BNum("$points", 16.sp, letterSpacing = (-0.5).sp)
                    Spacer(Modifier.width(1.dp))
                    Text("分", fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
                Text(desc, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = B.Ink2)
            }
        }
    }
}
