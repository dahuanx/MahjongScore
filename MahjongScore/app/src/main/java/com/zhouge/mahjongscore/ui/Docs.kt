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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhouge.mahjongscore.net.GameClient

/** 文档小节：标题 + 正文（\n 分条） */
private data class DocSection(val title: String, val body: String)

private const val UPDATED = "最后更新：2026-11-05"

/** 用户协议 */
private val DOC_TERMS = listOf(
    DocSection(
        "一、软件性质",
        "麻将计分器是一款纯积分记账工具，用于亲友娱乐对局的计分记录。\n积分仅为记分符号，不与任何真实货币、虚拟币挂钩，本应用不提供充值、提现或任何资金往来功能。",
    ),
    DocSection(
        "二、使用规范",
        "· 本应用仅限亲友间娱乐使用，严禁用于赌博或其他违法活动。\n· 使用者应遵守所在地法律法规，因违规使用产生的一切后果由使用者本人承担。",
    ),
    DocSection(
        "三、账号与身份",
        "· 无需注册，首次使用自动生成匿名设备 ID。\n· 昵称由您自行填写，请遵守公序良俗，不得使用侵犯他人权益的内容。",
    ),
    DocSection(
        "四、服务可用性",
        "· 联机对局依赖开发者自架服务器提供，将尽力保障稳定运行。\n· 因网络故障、服务器维护等原因可能造成服务中断或数据异常，请以实际牌局为准及时核对积分。",
    ),
    DocSection(
        "五、知识产权",
        "本应用的界面设计与程序代码归开发者所有，未经许可不得复制、反编译或用于商业用途。",
    ),
    DocSection(
        "六、协议变更",
        "计分规则、功能与协议内容可能随版本更新调整，以最新版本内展示为准。",
    ),
)

/** 隐私政策 */
private val DOC_PRIVACY = listOf(
    DocSection(
        "一、我们收集的信息",
        "· 您自填的昵称。\n· 对局记录：房间号、局数、每局计分流水、参与人昵称与匿名 ID。\n· 首次使用时自动生成的匿名设备 ID（仅用于区分同桌玩家，不含设备真实标识）。",
    ),
    DocSection(
        "二、我们不收集的信息",
        "不收集真实姓名、手机号、地理位置、通讯录、相册等个人信息；不含广告、推送与第三方统计 SDK。",
    ),
    DocSection(
        "三、数据存储",
        "· 历史对局与战绩保存在您手机本地。\n· 联机对局进行中，房间信息与计分流水暂存于开发者自架服务器，仅用于同桌玩家的实时同步显示。",
    ),
    DocSection(
        "四、数据用途",
        "收集的信息仅用于计分、战绩统计与同桌同步功能，不作任何其他用途，不对外提供、不用于商业分析。",
    ),
    DocSection(
        "五、数据删除",
        "· 设置页「清除缓存」可一键删除本机全部对局数据。\n· 服务器上的房间数据在对局结束后不再保留。",
    ),
    DocSection(
        "六、政策更新",
        "本政策如有重大变化，将随版本更新发布并在此页注明。",
    ),
)

/** 关于我们（appVer 动态注入） */
private fun DOC_ABOUT(appVer: String) = listOf(
    DocSection(
        "一、我们是谁",
        "麻将计分器由洲哥个人开发维护，出生地是黑龙江的牌桌——为朋友局免去带纸笔、算错分的麻烦而生。",
    ),
    DocSection(
        "二、产品理念",
        "· 只记账，不管钱：纯积分记录，娱乐不涉赌。\n· 简单直接：开房、计分、看战绩，三步之内。\n· 数据自主：对局数据存本机，随时可清。",
    ),
    DocSection(
        "三、当前版本",
        "v$appVer · 纯积分记账\n可在「设置 → 检查更新」获取最新版本，或每天首次打开自动检查。",
    ),
    DocSection(
        "四、意见反馈",
        "遇到问题或有改进建议，请联系邀请您使用本应用的朋友，由其转达开发者。\n您的每条反馈都会被认真对待。",
    ),
    DocSection(
        "五、技术致谢",
        "本应用基于 Jetpack Compose 构建，服务端运行于阿里云 ECS。\n感谢每一位同桌牌友。",
    ),
)

private fun docSections(key: String, appVer: String): List<DocSection> = when (key) {
    "privacy" -> DOC_PRIVACY
    "about" -> DOC_ABOUT(appVer)
    else -> DOC_TERMS
}

private fun docTitle(key: String): String = when (key) {
    "privacy" -> "隐私政策"
    "about" -> "关于我们"
    else -> "用户协议"
}

/**
 * 内嵌文档页：用户协议 / 隐私政策 / 关于我们。
 * 左上角「返回」与系统返回键同路径（docBack → 设置页）。
 */
@Composable
fun DocScreen(docKey: String, appVer: String) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BChip("返回") { GameClient.docBack() }
                Spacer(Modifier.weight(1f))
                BChip(docTitle(docKey), dark = true)
            }
            Spacer(Modifier.height(14.dp))
        }

        val sections = docSections(docKey, appVer)
        sections.forEach { sec ->
            item(key = sec.title) {
                // 小节标题贴纸条
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 7.dp)
                ) {
                    Row(
                        Modifier
                            .background(B.White, RoundedCornerShape(8.dp))
                            .border(2.5.dp, B.Ink, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .width(4.dp)
                                .height(12.dp)
                                .background(B.P1, RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(sec.title, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                }
                // 正文
                Column(Modifier.padding(start = 3.dp, bottom = 13.dp)) {
                    sec.body.split("\n").forEach { line ->
                        if (line.isBlank()) return@forEach
                        Text(
                            line,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = B.Ink2,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(6.dp))
            Text(
                "麻将计分器 · $UPDATED",
                fontSize = 9.5.sp, fontWeight = FontWeight.Black, color = B.Ink2,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
