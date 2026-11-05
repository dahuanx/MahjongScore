package com.zhouge.mahjongscore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhouge.mahjongscore.Identity
import com.zhouge.mahjongscore.KIND_POINTS
import com.zhouge.mahjongscore.kindName
import com.zhouge.mahjongscore.net.Conn
import com.zhouge.mahjongscore.net.GameClient

/**
 * 计分规则设置页：
 * · 在房且是房主 → 可编辑，保存后全房生效
 * · 在房非房主 → 只读展示当前房规则
 * · 不在房 → 编辑本地偏好，创建房间时带出去生效
 */
@Composable
fun RulesScreen(appVer: String) {
    val ctx = LocalContext.current
    val ui by GameClient.ui.collectAsState()
    val inRoom = ui.conn == Conn.UP && ui.room.isNotEmpty()
    val isHost = inRoom && ui.hostId == ui.myId
    val editable = !inRoom || isHost

    // 输入框状态：以当前生效表初始化
    var fields by remember(ui.scores, inRoom) {
        mutableStateOf(KIND_POINTS.keys.associateWith { kind ->
            (ui.scores[kind] ?: KIND_POINTS[kind]!!).toString()
        })
    }
    var errText by remember { mutableStateOf("") }

    // 校验：全为 1..999 的数字
    fun parsed(): Map<String, Long>? {
        val out = mutableMapOf<String, Long>()
        fields.forEach { (kind, text) ->
            val v = text.toLongOrNull() ?: return null
            if (v < 1 || v > 999) return null
            out[kind] = v
        }
        return out
    }

    LazyColumnCompat(
        modifier = Modifier.fillMaxSize(),
    ) {
        // —— 顶栏 ——
        Row(verticalAlignment = Alignment.CenterVertically) {
            BChip("返回") { GameClient.rulesBack() }
            Spacer(Modifier.weight(1f))
            BChip("计分规则", dark = true)
        }
        Spacer(Modifier.height(12.dp))

        // 状态提示条
        Text(
            when {
                inRoom && isHost -> "你正在房间中且是房主，保存后全桌立即生效"
                inRoom -> "当前房的规则由房主设定，你只能查看"
                else -> "保存为我的默认规则，创建房间时自动生效；进房后房主也可在房内修改"
            },
            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = B.Ink2,
            modifier = Modifier.padding(start = 2.dp, bottom = 10.dp),
        )

            // 五种胡型逐行
            KIND_POINTS.keys.forEach { kind ->
                val points = ui.scores[kind] ?: KIND_POINTS[kind]!!
                Sticker(
                    bg = B.White, radius = 14.dp, shadow = 2.dp, stroke = 2.5.dp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(kindName(kind), fontSize = 13.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.weight(1f))
                        if (editable) {
                            BField(
                                value = fields[kind] ?: "",
                                onValueChange = { t -> fields = fields + (kind to t.filter { it.isDigit() }.take(3)); errText = "" },
                                placeholder = points.toString(),
                                modifier = Modifier.width(110.dp),
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("分", fontSize = 11.sp, fontWeight = FontWeight.Black)
                        } else {
                            Text("$points 分", fontSize = 14.sp, fontWeight = FontWeight.Black, color = B.Win)
                        }
                    }
                }
            }

            if (errText.isNotEmpty()) {
                Text(
                    errText,
                    fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = B.Win,
                    modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
                )
            }

            if (editable) {
                BButton(
                    "保存规则",
                    style = BtnStyle.FILL,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val values = parsed()
                    if (values == null) {
                        errText = "分值必须是 1 ~ 999 的整数"
                    } else {
                        // 本地偏好始终保存
                        Identity.saveScores(ctx, values)
                        android.widget.Toast.makeText(ctx, "计分规则已保存", android.widget.Toast.LENGTH_SHORT).show()
                        if (inRoom && isHost) {
                            GameClient.sendSetScores(values)
                            GameClient.gotoGame() // 从对局进来的，保存后回牌桌
                        } else {
                            GameClient.rulesBack()
                        }
                    }
                }
                if (inRoom && isHost) {
                    Spacer(Modifier.height(8.dp))
                    BButton("恢复默认（5 / 20 / 10 / 20 / 40）", modifier = Modifier.fillMaxWidth()) {
                        Identity.saveScores(ctx, KIND_POINTS)
                        GameClient.sendSetScores(emptyMap())
                        android.widget.Toast.makeText(ctx, "已恢复默认分值", android.widget.Toast.LENGTH_SHORT).show()
                        GameClient.rulesBack()
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "规则说明：点炮由输家点击、仅点炮方单付；其余胡型由赢家点击、另三家各付。",
                fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = B.Ink2,
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            )
    }
}

/** 简易竖向滚动容器（避免直接引 LazyColumn 的 key 语义） */
@Composable
private fun LazyColumnCompat(modifier: Modifier, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        content()
    }
}
