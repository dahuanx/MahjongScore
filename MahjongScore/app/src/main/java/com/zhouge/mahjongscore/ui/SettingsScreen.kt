package com.zhouge.mahjongscore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhouge.mahjongscore.HistoryStore
import com.zhouge.mahjongscore.UpdateManager
import com.zhouge.mahjongscore.net.Conn
import com.zhouge.mahjongscore.net.GameClient

private data class SettingItem(
    val title: String,
    val subtitle: String = "",
    /** 右侧小字标签，空字符串表示不显示 */
    val tag: String = "暂不开发",
    /** 为 null 表示不可点（占位项） */
    val action: (() -> Unit)? = null,
    /** 图标块底色（玩家色轮换） */
    val color: Color,
)

@Composable
fun SettingsScreen(cacheSizeText: String, onCleared: () -> Unit) {
    var showClearDialog by remember { mutableStateOf(false) }
    var showInvite by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val appVer = remember(ctx) { UpdateManager.versionName(ctx) }
    val ui by GameClient.ui.collectAsState()

    val groups = listOf(
        "账号" to listOf(
            SettingItem("我的昵称", "在大厅的「我的身份」里修改", tag = "大厅修改", color = B.P1),
        ),
        "对局规则" to listOf(
            SettingItem(
                "计分规则",
                "平胡${ui.scores["pinghu"] ?: 5} · 点炮${ui.scores["dianpao"] ?: 20} · 自摸${ui.scores["zimo"] ?: 10} · 搂宝${ui.scores["loubao"] ?: 20} · 宝中宝${ui.scores["baozhongbao"] ?: 40} · 点击修改",
                tag = "", color = B.P4,
                action = { GameClient.openRules() },
            ),
            SettingItem(
                "邀请好友",
                "复制房间号或分享链接",
                tag = "", color = B.P2,
                action = { showInvite = true },
            ),
        ),
        "应用" to listOf(
            SettingItem(
                "检查更新",
                "当前 v$appVer · 每天首次打开自动检查",
                tag = "", color = B.P1,
                action = { UpdateManager.check(ctx, manual = true) },
            ),
            SettingItem(
                "清除缓存",
                "本地对局数据 $cacheSizeText · 清除后战绩全丢",
                tag = "", color = B.P3,
                action = { showClearDialog = true },
            ),
        ),
        "关于" to listOf(
            SettingItem("用户协议", "使用条款与免责说明", tag = "", color = B.P2, action = { GameClient.openDoc("terms") }),
            SettingItem("隐私政策", "我们收集什么、怎么用", tag = "", color = B.P2, action = { GameClient.openDoc("privacy") }),
            SettingItem("关于我们", "开发者与版本信息", tag = "", color = B.P4, action = { GameClient.openDoc("about") }),
        ),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BChip("返回") { GameClient.backToLobby() }
                Spacer(Modifier.weight(1f))
                BChip("设置", dark = true)
            }
            Spacer(Modifier.height(12.dp))
        }
        groups.forEach { (label, items) ->
            item(key = "label-$label") {
                Text(
                    label,
                    fontSize = 9.5.sp, fontWeight = FontWeight.Black, letterSpacing = 2.5.sp,
                    color = B.Ink2,
                    modifier = Modifier.padding(start = 2.dp, bottom = 7.dp),
                )
            }
            items(items.size, key = { "$label-${items[it].title}" }) { i ->
                val item = items[i]
                val enabled = item.action != null
                Sticker(
                    bg = if (enabled) B.White else B.White.copy(alpha = 0.55f),
                    radius = 14.dp,
                    shadow = if (enabled) 2.dp else 0.dp,
                    stroke = 2.5.dp,
                    enabled = enabled,
                    onClick = item.action,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .background(item.color, RoundedCornerShape(9.dp))
                                .border(2.5.dp, B.Ink, RoundedCornerShape(9.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(item.title.removePrefix("我的").take(1), fontSize = 13.sp, fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, fontSize = 12.5.sp, fontWeight = FontWeight.Black)
                            if (item.subtitle.isNotEmpty()) {
                                Text(
                                    item.subtitle,
                                    fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = B.Ink2,
                                    lineHeight = 14.sp,
                                )
                            }
                        }
                        if (item.tag.isNotEmpty()) {
                            Text(
                                item.tag,
                                fontSize = 9.sp, fontWeight = FontWeight.Black, color = B.Ink2,
                                modifier = Modifier
                                    .alpha(0.75f)
                                    .background(Color.Transparent)
                                    .border(2.dp, B.Ink.copy(alpha = 0.24f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        } else if (enabled) {
                            Text("›", fontSize = 15.sp, fontWeight = FontWeight.Black, color = B.Ink2)
                        }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                "麻将计分器 v$appVer · 纯积分记账",
                fontSize = 10.sp, fontWeight = FontWeight.Black, color = B.Ink2,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showClearDialog) {
        BDialog(
            title = "清除全部数据？",
            text = "本机全部历史对局与战绩统计（$cacheSizeText）都会被删掉，\n不可恢复。",
            color = B.P3,
            okText = "确认清除",
            onOk = {
                GameClient.appContext?.let { HistoryStore.clearAll(it) }
                onCleared()
                showClearDialog = false
            },
            onDismiss = { showClearDialog = false },
        )
    }

    if (showInvite) {
        InviteSheet(
            inRoom = ui.conn == Conn.UP && ui.room.isNotEmpty(),
            room = ui.room,
            onDismiss = { showInvite = false },
        )
    }
}
