package com.zhouge.mahjongscore.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zhouge.mahjongscore.UpdateManager
import com.zhouge.mahjongscore.UpdatePhase

/**
 * 全局更新弹窗。挂在根 Composable 上，任何页面都能弹。
 * 自动检查（manual=false）无更新或失败时不显示，只有手动检查才给反馈。
 */
@Composable
fun UpdateDialog(ctx: Context) {
    val st by UpdateManager.state.collectAsState()
    val cur = remember(ctx) { UpdateManager.versionName(ctx) }

    when (st.phase) {
        UpdatePhase.IDLE -> Unit

        UpdatePhase.CHECKING -> {
            if (st.manual) {
                AlertDialog(
                    onDismissRequest = { },
                    title = { Text("检查更新") },
                    text = { Text("正在连接服务器…") },
                    confirmButton = { },
                )
            }
        }

        UpdatePhase.LATEST -> {
            AlertDialog(
                onDismissRequest = { UpdateManager.dismiss() },
                title = { Text("已是最新版本") },
                text = { Text("当前版本 v$cur，没有可用的更新。") },
                confirmButton = {
                    TextButton(onClick = { UpdateManager.dismiss() }) { Text("知道了") }
                },
            )
        }

        UpdatePhase.AVAILABLE -> {
            val info = st.info
            AlertDialog(
                onDismissRequest = { UpdateManager.dismiss() },
                title = { Text("发现新版本 v${info?.version ?: ""}") },
                text = {
                    Column {
                        Text(
                            "当前 v$cur  →  最新 v${info?.version ?: ""}" +
                                if (info != null && info.size > 0) "（${UpdateManager.sizeText(info.size)}）" else "",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (!info?.notes.isNullOrBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                info?.notes.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "下载完成后会打开系统安装界面，点「安装」即完成更新。对局数据不受影响。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        st.info?.let { UpdateManager.download(ctx, it) }
                    }) { Text("立即更新") }
                },
                dismissButton = {
                    TextButton(onClick = { UpdateManager.dismiss() }) { Text("稍后") }
                },
            )
        }

        UpdatePhase.DOWNLOADING -> {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("正在下载 v${st.info?.version ?: ""}") },
                text = {
                    Column {
                        LinearProgressIndicator(
                            progress = { st.progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("${st.progress}%", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "下载完会自动打开安装界面，请稍候。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = { },
            )
        }

        UpdatePhase.NEED_PERMISSION -> {
            AlertDialog(
                onDismissRequest = { UpdateManager.dismiss() },
                title = { Text("还差一步：允许安装") },
                text = {
                    Text(
                        "安装包已经下载好了，不用重新下载。\n\n" +
                            "系统要求先允许本应用「安装未知应用」。打开开关后返回这里，会自动继续安装。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    TextButton(onClick = { UpdateManager.openInstallSettings(ctx) }) { Text("去设置") }
                },
                dismissButton = {
                    TextButton(onClick = { UpdateManager.resumeInstall(ctx) }) { Text("已允许，继续") }
                },
            )
        }

        UpdatePhase.ERROR -> {
            AlertDialog(
                onDismissRequest = { UpdateManager.dismiss() },
                title = { Text("更新失败") },
                text = { Text(st.message.ifBlank { "未知错误" }) },
                confirmButton = {
                    TextButton(onClick = { UpdateManager.dismiss() }) { Text("知道了") }
                },
            )
        }
    }
}
