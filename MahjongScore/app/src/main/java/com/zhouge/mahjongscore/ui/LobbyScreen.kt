package com.zhouge.mahjongscore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhouge.mahjongscore.net.GameClient

@Composable
fun LobbyScreen(
    savedName: String,
    playerId: String,
    lastRoom: String,
    rejoinValid: Boolean,
    connecting: Boolean,
    inRoom: Boolean,
    onHost: (String) -> Unit,
    onJoin: (String, String) -> Unit,
    onRejoin: (String, String) -> Unit,
    onHistory: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
) {
    var name by remember { mutableStateOf(savedName) }
    var room by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    val appVer = remember(ctx) { com.zhouge.mahjongscore.UpdateManager.versionName(ctx) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        // ---- Hero ----
        Column(Modifier.padding(top = 14.dp, bottom = 14.dp)) {
            Box(
                Modifier
                    .background(B.P3, RoundedCornerShape(50))
                    .border(2.5.dp, B.Ink, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
                    .rotate(-2f)
            ) {
                Text("联机版 · V$appVer", fontSize = 9.5.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = B.White)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("麻将", fontSize = 40.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, lineHeight = 42.sp)
                    Text(
                        "计分器",
                        fontSize = 40.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, lineHeight = 42.sp,
                        color = B.P3,
                    )
                }
                Spacer(Modifier.weight(1f))
                HeroArt()
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "4 PLAYERS · NO MONEY",
                fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.5.sp,
                color = B.Ink2, fontFamily = FontFamily.Monospace,
            )
        }

        // ---- 正在对局提示（Tab 切过来时） ----
        if (inRoom) {
            Sticker(
                bg = B.P1, radius = 20.dp, shadow = 4.dp, rotation = -0.6f,
                modifier = Modifier.fillMaxWidth(),
                onClick = { GameClient.gotoGame() },
            ) {
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("对局进行中", fontSize = 12.5.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.weight(1f))
                    Text("回到桌面 →", fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // ---- 我的身份 ----
        Sticker(bg = B.White, radius = 20.dp, shadow = 4.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(13.dp)) {
                Text("我的身份", fontSize = 9.5.sp, fontWeight = FontWeight.Black, letterSpacing = 2.5.sp, color = B.Ink2)
                Spacer(Modifier.height(8.dp))
                BField(
                    value = name,
                    onValueChange = { name = it.take(8) },
                    placeholder = "昵称",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "识别号 $playerId · 本机生成，战绩靠它认人",
                    fontSize = 10.sp, fontWeight = FontWeight.Bold, color = B.Ink2,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        // ---- 创建房间 ----
        BButton(
            "创建房间 · 随机新号",
            modifier = Modifier.fillMaxWidth(),
            style = BtnStyle.FILL,
            enabled = !connecting && !inRoom,
            height = 52.dp, fontSize = 15.sp,
            onClick = { onHost(name.ifBlank { "玩家" }) },
        )
        Spacer(Modifier.height(12.dp))

        // ---- 回到上次的房间 ----
        if (lastRoom.isNotEmpty() && rejoinValid) {
            Sticker(
                bg = Color(0xFFFFF0F5), radius = 20.dp, shadow = 4.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(13.dp)) {
                    Text("回到上次的房间", fontSize = 9.5.sp, fontWeight = FontWeight.Black, letterSpacing = 2.5.sp, color = B.P3)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BNum(lastRoom, 22.sp, letterSpacing = 2.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "24h 内有效",
                            fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = B.Ink2, lineHeight = 14.sp,
                        )
                        Spacer(Modifier.weight(1f))
                        BButton(
                            "进入",
                            enabled = !connecting,
                            style = BtnStyle.FILL, height = 40.dp, fontSize = 12.sp,
                            modifier = Modifier.width(74.dp),
                            onClick = { onRejoin(lastRoom, name.ifBlank { "玩家" }) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // ---- 加入朋友的房 ----
        Sticker(bg = B.White, radius = 20.dp, shadow = 4.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(13.dp)) {
                Text("加入朋友的房", fontSize = 9.5.sp, fontWeight = FontWeight.Black, letterSpacing = 2.5.sp, color = B.Ink2)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BField(
                        value = room,
                        onValueChange = { room = it.filter { c -> c.isDigit() }.take(5) },
                        placeholder = "5 位房号",
                        mono = true,
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    BButton(
                        "加入",
                        enabled = !connecting && !inRoom && room.length == 5,
                        style = BtnStyle.FILL,
                        modifier = Modifier.width(86.dp),
                        onClick = { onJoin(room, name.ifBlank { "玩家" }) },
                    )
                }
            }
        }

        if (connecting) {
            Spacer(Modifier.height(16.dp))
            Text(
                "连接服务器中…",
                fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = B.Ink2,
                modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "纯积分记账 · 不涉及任何资金 · v$appVer",
            fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = B.Ink2,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** 头图「实时计分」小票插画（静态装饰，数字与名字均为示例） */
@Composable
private fun HeroArt() {
    Box(Modifier.width(178.dp).height(150.dp)) {
        // 实时计分小票：压底层
        Sticker(
            bg = B.White, radius = 12.dp, shadow = 5.dp, rotation = -5f,
            modifier = Modifier.align(Alignment.CenterStart).width(132.dp),
        ) {
            Column(Modifier.padding(start = 12.dp, end = 10.dp, top = 9.dp, bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .background(B.Live, RoundedCornerShape(50))
                            .border(1.8.dp, B.Ink, RoundedCornerShape(50))
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "LIVE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp,
                        color = B.Ink,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text("实时计分", fontSize = 11.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(3.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.5.dp)
                        .background(B.Ink.copy(alpha = 0.24f), RoundedCornerShape(1.dp))
                )
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "+20",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                        color = B.Win,
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "小洲",
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, color = B.Ink2,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text("-10", fontFamily = FontFamily.Monospace, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = B.Lose)
                Spacer(Modifier.height(3.dp))
                Text("-10", fontFamily = FontFamily.Monospace, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = B.Lose)
            }
        }
        // 红中牌：右下压在小票前面
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .offset(y = 12.dp)
                .rotate(10f)
                .size(62.dp, 94.dp)
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .offset(4.dp, 4.dp)
                    .background(B.Ink, RoundedCornerShape(12.dp))
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(B.P1, RoundedCornerShape(12.dp))
                    .border(2.6.dp, B.Ink, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("中", fontSize = 30.sp, fontWeight = FontWeight.Black, color = B.Win)
            }
        }
    }
}
