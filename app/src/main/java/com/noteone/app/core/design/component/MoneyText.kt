// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.design.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnitType
import com.noteone.app.core.common.Money
import com.noteone.app.core.design.AppColor
import com.noteone.app.core.design.AppTheme
import com.noteone.app.core.design.AppType
import com.noteone.app.core.design.Space

/** 金额符号策略。 */
enum class MoneySign {
    /** 按正负自动：负数加 `-`，正数与零不加 */
    Auto,

    /** 不带任何符号（负数也按绝对值显示） */
    None,

    /** 强制 `+` */
    Plus,

    /** 强制 `-` */
    Minus,
}

/**
 * 全项目**唯一**的金额显示组件。
 *
 * 职责：分↔元、千分位、固定 2 位小数、tabular figures、符号前缀、超长自动降字号。
 * **禁止在别处写 `Text("¥" + amount)`。**
 *
 * - `¥1,234.56` / `¥0.00` / `-¥14.00`（[MoneySign.Auto]）
 * - `+¥1,500.00`（[MoneySign.Plus]）
 * - 整数位超过 9 位时，若当前样式比 [AppType.AmountL] 更大，自动降为 [AppType.AmountL]
 *
 * @param cents 金额，单位「分」，可正可负
 * @param style 文字样式，默认列表金额样式
 * @param sign 符号策略
 * @param color 覆盖文字颜色（例如输入态金额为 0 时传 [AppColor.Faint]）；不传则跟随内容色
 * @param compact 是否把超过 1 亿元的金额压缩为「亿」单位（如 `¥1.23 亿`）。
 *                列表内必须保持 `false` 显示完整数字，仅在统计卡片这类空间紧张处使用。
 */
@Composable
fun MoneyText(
    cents: Long,
    style: TextStyle = AppType.AmountRow,
    sign: MoneySign = MoneySign.Auto,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    compact: Boolean = false,
) {
    val effectiveStyle = resolveStyle(cents, style)
    Text(
        text = format(cents, sign, compact),
        modifier = modifier,
        style = effectiveStyle,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
    )
}

/**
 * 与 [MoneyText] 完全同源的字符串结果，供非 Compose 场景（CSV、通知文案、磁贴 subtitle）使用。
 * 保证 UI 与导出文案里的金额写法永远一致。
 */
fun formatMoneyText(
    cents: Long,
    sign: MoneySign = MoneySign.Auto,
    compact: Boolean = false,
): String = format(cents, sign, compact)

private fun format(cents: Long, sign: MoneySign, compact: Boolean): String {
    val useYi = compact && Money.shouldUseYi(cents)
    val body = if (useYi) {
        "¥" + Money.centsToYi(cents) + " 亿"
    } else {
        "¥" + Money.formatThousands(absOrZero(cents))
    }
    val prefix = when (sign) {
        MoneySign.Auto -> if (cents < 0) "-" else ""
        MoneySign.None -> ""
        MoneySign.Plus -> "+"
        MoneySign.Minus -> "-"
    }
    return prefix + body
}

private fun absOrZero(cents: Long): Long = if (cents < 0) -cents else cents

private fun resolveStyle(cents: Long, style: TextStyle): TextStyle {
    val intDigits = integerDigitCount(cents)
    if (intDigits <= MAX_INT_DIGITS_FOR_XL) return style
    // 只有比 AmountL 更大的样式才降级，避免列表里的 15sp 金额被放大到 40sp。
    // 这里只比同一单位（sp）下的数值：字号未指定时（Unspecified）不降级。
    val fontSize = style.fontSize
    val shouldDownsize = fontSize.type == TextUnitType.Sp &&
        fontSize.value > AppType.AmountL.fontSize.value
    return if (shouldDownsize) AppType.AmountL else style
}

private fun integerDigitCount(cents: Long): Int {
    val yuan = absOrZero(cents) / 100L
    return if (yuan == 0L) 1 else yuan.toString().length
}

private const val MAX_INT_DIGITS_FOR_XL = 9

// ------------------------------------------------------------------ Preview

@Preview(name = "MoneyText / 金额写法", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun MoneyTextPreview() {
    AppTheme {
        Column(modifier = Modifier.padding(Space.L)) {
            MoneyText(cents = 1400, style = AppType.AmountXL)
            MoneyText(cents = 123456, style = AppType.AmountRow)
            MoneyText(cents = 0, style = AppType.AmountXL, color = AppColor.Faint)
            MoneyText(cents = -1400, style = AppType.AmountRow)
            MoneyText(cents = 150000, style = AppType.AmountRow, sign = MoneySign.Plus)
            MoneyText(cents = 1234567890123, style = AppType.AmountXL)
            MoneyText(cents = 12345678900, style = AppType.AmountStat, compact = true)
        }
    }
}
