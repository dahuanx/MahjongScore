package com.zhouge.mahjongscore

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.zhouge.mahjongscore.HistoryStore
import com.zhouge.mahjongscore.net.Conn
import com.zhouge.mahjongscore.net.GameClient
import com.zhouge.mahjongscore.net.Screen
import com.zhouge.mahjongscore.ui.B
import com.zhouge.mahjongscore.ui.BChip
import com.zhouge.mahjongscore.ui.LobbyScreen
import com.zhouge.mahjongscore.ui.dotGrid
import com.zhouge.mahjongscore.ui.GameScreen
import com.zhouge.mahjongscore.ui.HistoryDetailScreen
import com.zhouge.mahjongscore.ui.HistoryListScreen
import com.zhouge.mahjongscore.ui.DocScreen
import com.zhouge.mahjongscore.ui.RulesScreen
import com.zhouge.mahjongscore.ui.SettingsScreen
import com.zhouge.mahjongscore.ui.StatsScreen
import com.zhouge.mahjongscore.ui.UpdateDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val id = Identity.ensureId(this)
        val savedName = Identity.loadName(this)
        GameClient.appContext = applicationContext
        GameClient.ui.value = GameClient.ui.value.copy(myId = id, myName = savedName, lastRoom = Identity.loadLastRoom(this))

        // 每天第一次打开自动查一次更新（有新版才弹窗）
        UpdateManager.dailyCheck(this)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val ui by GameClient.ui.collectAsState()

                    // toast 提示（连接状态、错误消息）
                    ui.toast?.let { msg ->
                        LaunchedEffect(msg) {
                            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                            delay(1200)
                            GameClient.clearToast()
                        }
                    }

                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(B.Paper)
                            .dotGrid()
                    ) {
                        Column(Modifier.fillMaxSize()) {
                            // 页面主体
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                when (ui.screen) {
                                    Screen.LOBBY -> LobbyScreen(
                                        savedName = ui.myName,
                                        playerId = ui.myId,
                                        lastRoom = ui.lastRoom,
                                        rejoinValid = Identity.isRejoinValid(this@MainActivity),
                                        connecting = ui.conn == Conn.CONNECTING,
                                        inRoom = ui.conn == Conn.UP,
                                        onHost = { name ->
                                            Identity.saveName(this@MainActivity, name)
                                            GameClient.hostGame(name)
                                        },
                                        onJoin = { room, name ->
                                            Identity.saveName(this@MainActivity, name)
                                            GameClient.joinRoom(room, name)
                                        },
                                        onRejoin = { room, name ->
                                            Identity.saveName(this@MainActivity, name)
                                            GameClient.joinRoom(room, name)
                                        },
                                        onHistory = { GameClient.openHistory() },
                                        onStats = { GameClient.openStats() },
                                        onSettings = { GameClient.openSettings() }
                                    )
                                    Screen.GAME -> GameScreen()
                                    Screen.HISTORY -> HistoryListScreen(sessions = ui.history)
                                    Screen.HISTORY_DETAIL -> {
                                        val detail = ui.history.firstOrNull { it.id == ui.historyDetailId }
                                        if (detail != null) HistoryDetailScreen(detail)
                                        else HistoryListScreen(sessions = ui.history)
                                    }
                                    Screen.STATS -> StatsScreen(sessions = ui.history, myId = ui.myId)
                                    Screen.SETTINGS -> SettingsScreen(
                                        cacheSizeText = HistoryStore.sizeText(this@MainActivity),
                                        onCleared = {
                                            Toast.makeText(this@MainActivity, "已清除全部对局数据", Toast.LENGTH_SHORT).show()
                                            GameClient.resetCurrentSession()
                                            GameClient.ui.value = GameClient.ui.value.copy(history = emptyList())
                                        }
                                    )
                                    Screen.DOC -> DocScreen(
                                        docKey = ui.docKey ?: "terms",
                                        appVer = UpdateManager.versionName(this@MainActivity),
                                    )
                                    Screen.RULES -> RulesScreen(appVer = UpdateManager.versionName(this@MainActivity))
                                }
                            }

                            // 底部 Tab：大厅 / 历史 / 战绩 / 设置（对局中隐藏，专注牌桌）
                            if (ui.screen != Screen.GAME) {
                                BTabBar(
                                    current = ui.screen,
                                    onLobby = { GameClient.backToLobby() },
                                    onHistory = { GameClient.openHistory() },
                                    onStats = { GameClient.openStats() },
                                    onSettings = { GameClient.openSettings() },
                                )
                            }
                        }

                        // 更新弹窗：盖在任何页面之上
                        UpdateDialog(this@MainActivity)
                    }
                }
            }
        }
    }

    /**
     * 从「安装未知应用」设置页回来时，如果已经授权就自动续装，不用重新下载。
     */
    override fun onResume() {
        super.onResume()
        UpdateManager.onResume(this)
    }

    /**
     * 系统返回键：与各页左上角的「← 返回」走同一条导航路径。
     * 只有大厅才真的退出 App。
     */
    @Deprecated("Moved to back pressed callback")
    override fun onBackPressed() {
        when (GameClient.ui.value.screen) {
            com.zhouge.mahjongscore.net.Screen.GAME -> GameClient.leave()
            com.zhouge.mahjongscore.net.Screen.HISTORY_DETAIL -> GameClient.historyBack()
            com.zhouge.mahjongscore.net.Screen.HISTORY,
            com.zhouge.mahjongscore.net.Screen.STATS,
            com.zhouge.mahjongscore.net.Screen.SETTINGS -> GameClient.backToLobby()
            com.zhouge.mahjongscore.net.Screen.DOC -> GameClient.docBack()
            com.zhouge.mahjongscore.net.Screen.RULES -> GameClient.rulesBack()
            com.zhouge.mahjongscore.net.Screen.LOBBY -> super.onBackPressed()
        }
    }
}

/** 底部导航：大厅 / 历史 / 战绩 / 设置。对局中不显示。 */
@Composable
private fun BTabBar(
    current: Screen,
    onLobby: () -> Unit,
    onHistory: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(B.Paper)
            .border(3.dp, B.Ink, RoundedCornerShape(0.dp))
            .height(64.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabItem("大厅", current == Screen.LOBBY, Modifier.weight(1f), onLobby)
        TabItem("历史", current == Screen.HISTORY || current == Screen.HISTORY_DETAIL, Modifier.weight(1f), onHistory)
        TabItem("战绩", current == Screen.STATS, Modifier.weight(1f), onStats)
        TabItem("设置", current == Screen.SETTINGS || current == Screen.DOC || current == Screen.RULES, Modifier.weight(1f), onSettings)
    }
}

@Composable
private fun TabItem(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (selected) {
            BChip(label, dark = false, onClick = onClick)
        } else {
            BChip(label, dark = true, onClick = onClick)
        }
    }
}
