package com.zhouge.mahjongscore.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * B 糖纸波普 · 设计系统
 * 依据《设计规范-B方案.html》：所有元素都是贴在纸上的贴纸——
 * 粗黑描边 + 无模糊硬阴影 + 按下塌陷。px 与 dp 1:1 对应（390dp 基准）。
 */
object B {
    // 纸与墨
    val Paper = Color(0xFFFFF7EC)
    val Paper2 = Color(0xFFFFFCF5)
    val White = Color(0xFFFFFFFF)
    val Ink = Color(0xFF191322)
    val Ink2 = Color(0xFF5B5170)
    val Ink3 = Color(0xFF9A90AC)

    // 四家玩家色（一人一色，跟人不跟位）
    val P1 = Color(0xFFFFD23F) // 我 · 柠檬
    val P2 = Color(0xFF3D7BFF) // 2 号位 · 天蓝
    val P3 = Color(0xFFFF3D7F) // 3 号位 · 玫红
    val P4 = Color(0xFF00C9A7) // 4 号位 · 薄荷

    // 语义色
    val Win = Color(0xFFE01B4C)   // 涨 · 红
    val Lose = Color(0xFF0E8C6A)  // 跌 · 绿
    val WinBg = Color(0xFFFFE8EF)
    val Warn = Color(0xFFFFE8A3)
    val Live = Color(0xFF00C853)
}

/** 玩家色跟人走（按 id 稳定散列），「我」固定柠檬 */
fun playerColor(id: String, myId: String): Color =
    if (id == myId) B.P1
    else when (kotlin.math.abs(id.hashCode()) % 3) {
        0 -> B.P2
        1 -> B.P3
        else -> B.P4
    }

/** 带符号分数文本（等宽字体配套） */
fun signed(v: Long): String = if (v > 0) "+$v" else "$v"

/** 分数语义色：涨红跌绿，零平墨灰 */
fun scoreColor(v: Long): Color = when {
    v > 0 -> B.Win
    v < 0 -> B.Lose
    else -> B.Ink
}

/** 页面纸底 + 波点纹理（16dp 网格，5% 墨点） */
fun Modifier.dotGrid(): Modifier = drawBehind {
    val step = 16.dp.toPx()
    val r = 1.2.dp.toPx()
    val c = B.Ink.copy(alpha = 0.05f)
    var y = step / 2
    while (y < size.height) {
        var x = step / 2
        while (x < size.width) {
            drawCircle(c, r, Offset(x, y))
            x += step
        }
        y += step
    }
}

/**
 * 贴纸容器：硬阴影（外层偏移实色 Box）+ 描边 + 按下塌陷。
 * 阴影永远向右下，光从左上来。
 */
@Composable
fun Sticker(
    bg: Color,
    modifier: Modifier = Modifier,
    radius: Dp = 18.dp,
    shadow: Dp = 4.dp,
    stroke: Dp = 3.dp,
    rotation: Float = 0f,
    shadowColor: Color = B.Ink,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressable = onClick != null && enabled
    val off by animateDpAsState(if (pressed && pressable) (shadow - 1.dp).coerceAtLeast(0.dp) else 0.dp)
    val sh by animateDpAsState(if (pressed && pressable) 1.dp else shadow)
    val shape = RoundedCornerShape(radius)

    Box(modifier = modifier.rotate(rotation)) {
        // 阴影层
        if (shadow > 0.dp && (enabled || onClick == null)) {
            Box(
                Modifier
                    .matchParentSize()
                    .offset(sh, sh)
                    .alpha(if (enabled || onClick == null) 1f else 0f)
                    .background(shadowColor, shape)
            )
        }
        // 主体层
        Box(
            Modifier
                .offset(off, off)
                .alpha(if (enabled || onClick == null) 1f else 0.42f)
                .background(bg, shape)
                .border(stroke, B.Ink, shape)
                .then(
                    if (pressable) Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick!!,
                    ) else Modifier
                )
                .then(if (!enabled && onClick != null) Modifier.alpha(0.42f) else Modifier)
        ) { content() }
    }
}

/** 胶囊标签：导航主力。dark 款无阴影不可点。 */
@Composable
fun BChip(
    text: String,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    live: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Sticker(
        bg = if (dark) B.Ink else B.White,
        radius = 999.dp,
        shadow = if (dark) 0.dp else 2.dp,
        stroke = if (dark) 3.dp else 3.dp,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (live) {
                Box(Modifier.size(8.dp).background(B.Live, RoundedCornerShape(50)).border(2.dp, B.Ink, RoundedCornerShape(50)))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
                color = if (dark) B.White else B.Ink,
            )
        }
    }
}

enum class BtnStyle { NORMAL, FILL, DARK }

/** 按钮：默认白底、强调柠檬底、危险墨底。按下塌陷。 */
@Composable
fun BButton(
    text: String,
    modifier: Modifier = Modifier,
    style: BtnStyle = BtnStyle.NORMAL,
    enabled: Boolean = true,
    height: Dp = 46.dp,
    fontSize: TextUnit = 13.sp,
    onClick: () -> Unit,
) {
    val bg = when (style) {
        BtnStyle.NORMAL -> B.White
        BtnStyle.FILL -> B.P1
        BtnStyle.DARK -> B.Ink
    }
    Sticker(
        bg = bg,
        radius = 14.dp,
        shadow = 4.dp,
        shadowColor = if (style == BtnStyle.DARK) B.P3 else B.Ink,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier.height(height),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text,
                fontSize = fontSize,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.6.sp,
                color = if (style == BtnStyle.DARK) B.White else B.Ink,
            )
        }
    }
}

/** 分区标题：标题 + 墨线 + 计数 */
@Composable
fun SectionHeader(title: String, count: Int? = null) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .weight(1f)
                .height(3.dp)
                .background(B.Ink.copy(alpha = 0.16f), RoundedCornerShape(2.dp))
        )
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .background(B.Ink, RoundedCornerShape(50))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text("$count", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = B.White)
            }
        }
    }
}

/** 等宽数字文本 */
@Composable
fun BNum(
    text: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = B.Ink,
    letterSpacing: TextUnit = 0.sp,
    weight: FontWeight = FontWeight.ExtraBold,
) {
    Text(
        text,
        modifier = modifier,
        fontFamily = FontFamily.Monospace,
        fontSize = size,
        fontWeight = weight,
        letterSpacing = letterSpacing,
        color = color,
    )
}

/** 输入框：2.5dp 描边 + 纸底，聚焦转白 */
@Composable
fun BField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .background(B.Paper2, shape)
            .border(2.5.dp, B.Ink, shape)
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = TextStyle(
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.SansSerif,
                fontSize = if (mono) 19.sp else 14.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = if (mono) 5.sp else 0.sp,
                color = B.Ink,
            ),
            cursorBrush = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(B.Ink, B.Ink)),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            color = B.Ink3,
                            fontSize = if (mono) 19.sp else 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = if (mono) 5.sp else 2.sp,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

/** 底部弹层：纸底 + 顶角 26dp + 抓手 */
@Composable
fun BSheet(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // 遮罩
        Box(
            Modifier
                .fillMaxSize()
                .background(B.Ink.copy(alpha = 0.42f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() }
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(B.Paper, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .border(3.dp, B.Ink, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // 抓手
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(44.dp)
                    .height(5.dp)
                    .background(B.Ink.copy(alpha = 0.24f), RoundedCornerShape(3.dp))
            )
            Spacer(Modifier.height(12.dp))
            content()
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 居中确认弹窗：贴纸卡 + 图标块 + 双按钮 */
@Composable
fun BDialog(
    title: String,
    text: String,
    color: Color = B.P1,
    okText: String = "确认",
    cancelText: String = "再想想",
    onOk: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(B.Ink.copy(alpha = 0.42f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() }
        )
        Sticker(
            bg = B.Paper,
            radius = 22.dp,
            shadow = 8.dp,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 40.dp)
                .fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(46.dp)
                        .background(color, RoundedCornerShape(14.dp))
                        .border(3.dp, B.Ink, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("!", fontSize = 22.sp, fontWeight = FontWeight.Black, color = B.Ink)
                }
                Spacer(Modifier.height(10.dp))
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Spacer(Modifier.height(7.dp))
                Text(
                    text,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = B.Ink2,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(15.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BButton(cancelText, Modifier.weight(1f), height = 42.dp, fontSize = 12.5.sp, onClick = onDismiss)
                    BButton(okText, Modifier.weight(1f), style = BtnStyle.DARK, height = 42.dp, fontSize = 12.5.sp, onClick = onOk)
                }
            }
        }
    }
}

/** 风位角标：墨底白字 */
@Composable
fun WindBadge(wind: String, small: Boolean = false) {
    if (wind.isEmpty()) return
    val side = if (small) 16.dp else 22.dp
    Box(
        Modifier
            .size(side)
            .background(B.Ink, RoundedCornerShape(if (small) 5.dp else 7.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            wind.removeSuffix("风"),
            fontSize = if (small) 9.sp else 11.sp,
            fontWeight = FontWeight.Black,
            color = B.White,
        )
    }
}

/**
 * 名字缩略字：取最后一个字，用于头像方块。
 * 用代理对判断，避免把 emoji 截成半个字符；空名回退 "?"。
 */
fun shortName(name: String): String {
    val s = name.trim()
    if (s.isEmpty()) return "?"
    val end = s.length
    val start = if (end >= 2 && s[end - 1].isLowSurrogate() && s[end - 2].isHighSurrogate()) end - 2 else end - 1
    return s.substring(start, end)
}
