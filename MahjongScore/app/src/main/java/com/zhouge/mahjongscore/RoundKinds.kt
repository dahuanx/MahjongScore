package com.zhouge.mahjongscore

/** 胡型 → 每家应付分（与服务端 scoreTable 保持一致） */
val KIND_POINTS: Map<String, Long> = linkedMapOf(
    "pinghu" to 5L,
    "dianpao" to 20L,
    "zimo" to 10L,
    "loubao" to 20L,
    "baozhongbao" to 40L,
)

/** 输家可选的胡型（含点炮） */
val KINDS_ALL: List<String> = KIND_POINTS.keys.toList()

/** 输家可选：只允许点炮，避免多个输家各点一次造成重复记分 */
val KINDS_LOSER: List<String> = listOf("dianpao")

/** 赢家自己发起时可选（点炮必须由输家操作，故不含） */
val KINDS_SELF: List<String> = KINDS_ALL.filter { it != "dianpao" }

fun kindName(kind: String): String = when (kind) {
    "pinghu" -> "平胡"
    "dianpao" -> "点炮"
    "zimo" -> "自摸"
    "loubao" -> "搂宝"
    "baozhongbao" -> "宝中宝"
    else -> kind
}

/** 兼容旧数据：老流水只有 loser，没有 payers */
fun payersOf(payers: List<String>, loser: String?): List<String> =
    if (payers.isNotEmpty()) payers else listOfNotNull(loser)

/** 本局赢家实收（每家分值 × 付款人数） */
fun gainOf(points: Long, payerCount: Int): Long = points * payerCount.coerceAtLeast(1)

/** 流水主行：点炮显示「某某 给 某某 点炮」，其余显示「某某 自摸」 */
fun roundTitle(seq: Long, kind: String, winnerName: String, payerNames: List<String>): String =
    if (kind == "dianpao" && payerNames.isNotEmpty())
        "第 $seq 局 · ${payerNames.joinToString("、")} 给 $winnerName 点炮"
    else
        "第 $seq 局 · $winnerName ${kindName(kind)}"

/** 流水副行：金额口径 */
fun roundPayText(kind: String, points: Long, payerCount: Int): String =
    if (kind == "dianpao" || payerCount == 1) "$points 分"
    else "${payerCount} 家各付 $points 分"
