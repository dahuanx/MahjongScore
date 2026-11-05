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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhouge.mahjongscore.SessionRecord
import com.zhouge.mahjongscore.net.GameClient

data class StatsRow(
    val id: String,
    val name: String,
    val sessions: Int,
    val wins: Int,
    val losses: Int,
    val totalScore: Long,
) {
    fun winRate(): String =
        if (sessions == 0) "—" else String.format("%.0f%%", wins * 100.0 / sessions)

    fun scoreText(): String = if (totalScore > 0) "+$totalScore" else "$totalScore"
}

private fun endedSessions(sessions: List<SessionRecord>) = sessions.filter { it.isEnded() }

@Composable
fun StatsScreen(sessions: List<SessionRecord>, myId: String) {
    val ended = remember(sessions) { endedSessions(sessions) }

    // 我的战绩
    val myRow = remember(ended, myId) {
        val mine = ended.filter { s -> s.players.any { it.id == myId } }
        val wins = mine.count { it.myNet() > 0 }
        val losses = mine.count { it.myNet() < 0 }
        StatsRow(myId, "我", mine.size, wins, losses, mine.sumOf { it.myNet() })
    }

    // 牌友战绩（同桌过的真人牌友；正式版已移除机器人，老记录里的 bot 不计入）
    val buddyRows = remember(ended, myId) {
        val map = linkedMapOf<String, MutableList<SessionRecord>>() // playerId -> sessions
        ended.forEach { s ->
            s.players.filter { it.id != myId && !it.id.startsWith("bot-") }.forEach { p ->
                map.getOrPut(p.id) { mutableListOf() }.add(s)
            }
        }
        map.map { (pid, list) ->
            val name = list.firstNotNullOfOrNull { s -> s.players.find { it.id == pid }?.name } ?: pid
            val wins = list.count { (it.balances[pid] ?: 0L) > 0 }
            val losses = list.count { (it.balances[pid] ?: 0L) < 0 }
            StatsRow(pid, name, list.size, wins, losses, list.sumOf { it.balances[pid] ?: 0L })
        }.sortedByDescending { it.sessions }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        item {
            // 顶栏
            Row(verticalAlignment = Alignment.CenterVertically) {
                BChip("战绩统计", dark = true)
                Spacer(Modifier.weight(1f))
                BChip("返回") { GameClient.backToLobby() }
            }
            Spacer(Modifier.height(12.dp))

            // 我的大卡
            Sticker(
                bg = B.P1, radius = 24.dp, shadow = 6.dp, rotation = -0.8f,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box {
                    // 右上角装饰圆
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 28.dp, y = (-28).dp)
                            .size(96.dp)
                            .background(B.P3.copy(alpha = 0.28f), RoundedCornerShape(50))
                            .border(3.dp, B.Ink, RoundedCornerShape(50))
                    )
                    Column(Modifier.padding(16.dp)) {
                        Text("我的累计净胜分", fontSize = 9.5.sp, fontWeight = FontWeight.Black, letterSpacing = 2.5.sp)
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            BNum(myRow.scoreText(), 54.sp, letterSpacing = (-3).sp, color = scoreColor(myRow.totalScore))
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "分 / ${myRow.sessions} 场",
                                fontSize = 12.sp, fontWeight = FontWeight.Black,
                            )
                        }
                        Spacer(Modifier.height(13.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatCell("总场", "${myRow.sessions}", Modifier.weight(1f))
                            StatCell("胜场", "${myRow.wins}", Modifier.weight(1f))
                            StatCell("负场", "${myRow.losses}", Modifier.weight(1f))
                            StatCell("胜率", myRow.winRate(), Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(13.dp))
            SectionHeader("牌友", buddyRows.size)
            Spacer(Modifier.height(9.dp))
            if (buddyRows.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(B.White.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                        .border(2.5.dp, B.Ink.copy(alpha = 0.34f), RoundedCornerShape(18.dp))
                        .padding(vertical = 24.dp, horizontal = 14.dp)
                ) {
                    Text(
                        "还没有同桌记录。\n打完一局完整的牌，这里会自动长出来。",
                        fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = B.Ink2,
                        lineHeight = 19.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
        items(buddyRows, key = { it.id }) { row ->
            Sticker(
                bg = B.White, radius = 14.dp, shadow = 3.dp, stroke = 2.5.dp,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 头像块：名字末字 + 玩家色（跟人走）
                    Box(
                        Modifier
                            .size(32.dp)
                            .background(playerColor(row.id, myId), RoundedCornerShape(10.dp))
                            .border(2.5.dp, B.Ink, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(shortName(row.name), fontSize = 13.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(row.name, fontSize = 12.5.sp, fontWeight = FontWeight.Black)
                        Text(
                            "${row.sessions} 场 · ${row.wins}胜${row.losses}负 · ${row.winRate()}",
                            fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = B.Ink2,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .background(playerColor(row.id, myId), RoundedCornerShape(8.dp))
                            .border(2.5.dp, B.Ink, RoundedCornerShape(8.dp))
                            .padding(horizontal = 7.dp, vertical = 1.dp)
                    ) {
                        BNum(row.scoreText(), 15.sp, color = B.Ink)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Sticker(
        bg = B.White, radius = 11.dp, shadow = 2.dp, stroke = 2.5.dp,
        modifier = modifier,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BNum(value, 16.sp)
            Text(label, fontSize = 8.5.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp, color = B.Ink2)
        }
    }
}
