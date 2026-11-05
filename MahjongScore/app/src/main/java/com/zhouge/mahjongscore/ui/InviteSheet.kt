package com.zhouge.mahjongscore.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhouge.mahjongscore.net.GameClient

/** 下载链接（与更新通道同一台服务器、同一个 APK） */
private const val APK_URL = GameClient.HTTP_BASE + "/app/latest.apk"

private fun inviteText(room: String): String =
    "【麻将计分器】我在房间 $room 开了一桌，来跟我打两把！\n" +
        "下载 App：$APK_URL\n" +
        "安装后输入房间号 $room 加入。"

private fun appText(): String =
    "推荐一个纯积分麻将计分器：只记账不管钱，朋友局免算分。\n下载：$APK_URL"

private fun copyText(ctx: Context, text: String, toast: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("mahjong", text))
    Toast.makeText(ctx, toast, Toast.LENGTH_SHORT).show()
}

private fun shareText(ctx: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    ctx.startActivity(Intent.createChooser(send, "邀请好友"))
}

/**
 * 邀请好友弹层：房间号 + 复制/分享邀请文案 + 分享下载链接。
 * 在房与不在房共用；不在房时引导先进房。
 */
@Composable
fun InviteSheet(inRoom: Boolean, room: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    BSheet(onDismiss = onDismiss) {
        // 标题
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(14.dp)
                    .background(B.P1, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(8.dp))
            Text("邀请好友", fontSize = 14.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            BChip("✕") { onDismiss() }
        }
        Spacer(Modifier.height(12.dp))

        if (inRoom && room.isNotEmpty()) {
            // 房间号大字
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(B.White, RoundedCornerShape(14.dp))
                    .border(2.5.dp, B.Ink, RoundedCornerShape(14.dp))
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("房间号", fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = B.Ink2)
                    Spacer(Modifier.height(2.dp))
                    BNum(room, size = 30.sp, letterSpacing = 4.sp)
                    Spacer(Modifier.height(2.dp))
                    Text("牌友装好 App 后输入房号加入", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = B.Ink2)
                }
            }
            Spacer(Modifier.height(10.dp))

            BButton("复制邀请文案", style = BtnStyle.FILL, modifier = Modifier.fillMaxWidth()) {
                copyText(ctx, inviteText(room), "邀请文案已复制，去粘贴给朋友吧")
                onDismiss()
            }
            Spacer(Modifier.height(8.dp))
            BButton("分享给朋友", modifier = Modifier.fillMaxWidth()) {
                shareText(ctx, inviteText(room))
                onDismiss()
            }
        } else {
            Text(
                "还没进房间。先创建或加入一桌，再邀请牌友同桌；\n也可以直接把 App 推荐给朋友。",
                fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = B.Ink2, lineHeight = 16.sp,
            )
            Spacer(Modifier.height(10.dp))
        }

        BButton(
            "分享 App 下载链接",
            style = if (inRoom && room.isNotEmpty()) BtnStyle.NORMAL else BtnStyle.FILL,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        ) {
            shareText(ctx, appText())
            onDismiss()
        }
    }
}
