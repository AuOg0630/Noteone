// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.design

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * 全站字号 / 字重 / 行高 / 字距。
 *
 * 依据 `docs/开发规范.md` §3.3。**字重只有 400 与 500 两档，禁止 600/700/Bold。**
 *
 * 字体族：不内嵌任何字体文件，中文走系统默认（`FontFamily.Default`）。
 * 所有金额与其他数字一律带 `fontFeatureSettings = "tnum"`（等宽数字，保证纵向对齐）。
 * 若目标设备的默认字体不支持 tnum 特性，`settings()` 会静默忽略该特性，
 * 此时由调用方改用 [TabularFallback]（`FontFamily.Monospace`）作为兜底。
 */
object AppType {

    /** 等宽数字特性开关，所有金额样式都带上它。 */
    private const val TABULAR_FEATURES = "tnum"

    /** tnum 不生效时的兜底字体族。 */
    val TabularFallback: FontFamily = FontFamily.Monospace

    /** 金额输入（首页 / 悬浮面板）：56sp Medium，行高 1.1，字距 -0.02em */
    val AmountXL = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 56.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 61.6.sp,
        letterSpacing = (-0.02).em,
        fontFeatureSettings = TABULAR_FEATURES,
    )

    /** 整数位超过 9 位时的降级字号：40sp Medium */
    val AmountL = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 40.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 44.sp,
        letterSpacing = (-0.02).em,
        fontFeatureSettings = TABULAR_FEATURES,
    )

    /** 列表项金额：15sp Medium，行高 1.4，字距 -0.01em */
    val AmountRow = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 21.sp,
        letterSpacing = (-0.01).em,
        fontFeatureSettings = TABULAR_FEATURES,
    )

    /** 统计数值（三卡、概览条）：17sp Medium tabular */
    val AmountStat = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 17.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 22.1.sp,
        letterSpacing = (-0.01).em,
        fontFeatureSettings = TABULAR_FEATURES,
    )

    /** 页面标题：24sp Medium，行高 1.2，字距 -0.02em */
    val PageTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 24.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 28.8.sp,
        letterSpacing = (-0.02).em,
    )

    /** 分组标题 / 卡片标题：17sp Medium，行高 1.3，字距 -0.01em */
    val SectionTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 17.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 22.1.sp,
        letterSpacing = (-0.01).em,
    )

    /** 正文 / 列表项文字：15sp Regular，行高 1.5 */
    val Body = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 22.5.sp,
    )

    /** 次要文字（日期、备注、占位符）：13sp Regular，行高 1.5 */
    val Caption = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 19.5.sp,
    )

    /** 标签 / 胶囊 / 底栏文字：11sp Regular，行高 1.3，字距 0.05em */
    val Label = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 14.3.sp,
        letterSpacing = 0.05.em,
    )

    /** 按钮文字：15sp Medium，行高 1.2 */
    val Button = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 18.sp,
    )

    /** 英文装饰性标题（仅设置页「关于」等极少数场景）：衬线 + 紧字距。中文场景禁止使用。 */
    val SerifDisplay = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 24.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 28.8.sp,
        letterSpacing = (-0.03).em,
    )
}
