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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhouge.mahjongscore.SessionRecord
import com.zhouge.mahjongscore.net.GameClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ==================== 历史列表 ====================

@Composable
fun HistoryListScreen(sessions: List<SessionRecord>) {
    var deleteTarget by remember { mutableStateOf<SessionRecord?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BChip("返回") { GameClient.backToLobby() }
                Spacer(Modifier.weight(1f))
                BChip("历史对局", dark = true)
            }
            Spacer(Modifier.height(12.dp))
            if (sessions.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(B.White.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                        .border(2.5.dp, B.Ink.copy(alpha = 0.34f), RoundedCornerShape(18.dp))
                        .padding(vertical = 24.dp, horizontal = 14.dp)
                ) {
                    Text(
                        "还没有对局记录。\n打完一桌（离房结算）后，这里就会出现。",
                        fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = B.Ink2,
                        lineHeight = 19.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
        items(sessions, key = { it.id }) { s ->
            SessionCard(s) { deleteTarget = s }
            Spacer(Modifier.height(10.dp))
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    deleteTarget?.let { s ->
        BDialog(
            title = "删除这条记录？",
            text = "房间 ${s.room} 的全部流水将被删除。\n此操作不可恢复。",
            color = B.P3,
            okText = "删除",
            onOk = { GameClient.deleteSession(s.id); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun SessionCard(s: SessionRecord, onDelete: () -> Unit) {
    val dateText = remember(s.startTs) {
        SimpleDateFormat("M月d日", Locale.CHINA).format(Date(s.startTs * 1000))
    }
    val startHM = remember(s.startTs) {
        SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(s.startTs * 1000))
    }
    val myId = s.myId
    Sticker(
        bg = B.White, radius = 20.dp, shadow = 4.dp,
        modifier = Modifier.fillMaxWidth(),
        onClick = { GameClient.openHistoryDetail(s.id) },
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 12.dp)) {
            // 第一行：日期大字 + 房间小字 + 状态
            Row(verticalAlignment = Alignment.Bottom) {
                Text(dateText, fontSize = 21.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.3).sp)
                Spacer(Modifier.width(7.dp))
                Text(
                    "房间 ${s.room}",
                    fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = B.Ink2,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .background(if (s.isEnded()) B.P4 else B.P1, RoundedCornerShape(6.dp))
                        .border(2.dp, B.Ink, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(s.statusText(), fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(
                "$startHM 开始 · 时长 ${s.durationText()} · ${s.rounds.size} 局",
                fontSize = 10.sp, fontWeight = FontWeight.Bold, color = B.Ink2,
                fontFamily = FontFamily.Monospace,
            )
            // 各方输赢小片
            if (s.players.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    s.players.take(4).forEach { p ->
                        val bal = s.balances[p.id] ?: 0L
                        Box(
                            Modifier
                                .background(playerColor(p.id, myId), RoundedCornerShape(7.dp))
                                .border(2.dp, B.Ink, RoundedCornerShape(7.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row {
                                Text(p.name, fontSize = 9.5.sp, fontWeight = FontWeight.Black)
                                Spacer(Modifier.width(4.dp))
                                BNum(signed(bal), 10.sp)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(9.dp))
            // 底行：我的净分 + 删除
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(B.Ink.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BNum(
                    "我的净分 ${signed(s.myNet())}", 15.sp,
                    color = scoreColor(s.myNet()),
                )
                Spacer(Modifier.weight(1f))
                BChip("删除") { onDelete() }
            }
        }
    }
}

// ==================== 历史详情 ====================

@Composable
fun HistoryDetailScreen(session: SessionRecord) {
    val nameOf: (String) -> String = remember(session) {
        { id -> session.nameOf(id) }
    }
    val myId = session.myId
    val sorted = session.rounds.sortedByDescending { it.seq }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BChip("返回") { GameClient.historyBack() }
                Spacer(Modifier.weight(1f))
                BChip("房间 ${session.room}", dark = true)
            }
            Spacer(Modifier.height(12.dp))

            // 结算总卡
            Sticker(
                bg = Color(0xFFEAF6FF), radius = 20.dp, shadow = 4.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "${session.timeRangeText()} · 时长 ${session.durationText()} · 共 ${session.rounds.size} 局",
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, color = B.Ink2,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.height(10.dp))
                    session.players.forEachIndexed { i, p ->
                        val bal = session.balances[p.id] ?: 0L
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .background(playerColor(p.id, myId), RoundedCornerShape(3.dp))
                                    .border(2.dp, B.Ink, RoundedCornerShape(3.dp))
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                "${p.name}${if (p.id == myId) "（我）" else ""}${if (p.bot) "（机器人）" else ""}",
                                fontSize = 12.sp, fontWeight = FontWeight.Black,
                            )
                            Spacer(Modifier.weight(1f))
                            BNum(signed(bal), 18.sp, color = scoreColor(bal))
                        }
                        if (i < session.players.lastIndex) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(B.Ink.copy(alpha = 0.16f))
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            SectionHeader("逐局流水", session.rounds.size)
            Spacer(Modifier.height(9.dp))
            if (sorted.isEmpty()) {
                Text("该局未记分。", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = B.Ink2)
            }
        }
        items(sorted, key = { it.seq }) { r ->
            val dto = com.zhouge.mahjongscore.net.RoundDto(
                seq = r.seq, winner = r.winner, kind = r.kind, points = r.points,
                payers = r.payers, loser = r.loser, ts = r.ts,
            )
            TicketRow(
                r = dto,
                nameOf = nameOf,
                winnerColor = playerColor(r.winner, myId),
                showUndo = false,
                onUndo = { },
            )
            Spacer(Modifier.height(8.dp))
        }
        item {
            Spacer(Modifier.height(8.dp))
            BButton("返回列表", Modifier.fillMaxWidth(), onClick = { GameClient.historyBack() })
            Spacer(Modifier.height(16.dp))
        }
    }
}
