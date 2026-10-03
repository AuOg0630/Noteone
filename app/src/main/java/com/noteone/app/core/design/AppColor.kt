// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.design

import androidx.compose.ui.graphics.Color

/**
 * 全站唯一的中性色板（11 个值，仅浅色模式）。
 *
 * 依据 `docs/开发规范.md` §3.2。**任何界面实现不得引入本文件之外的色值**，
 * 也不得使用 Material 3 的默认配色。
 */
object AppColor {
    /** 页面背景 */
    val Canvas = Color(0xFFFFFFFF)

    /** 弹层 / 键盘背景 */
    val CanvasWarm = Color(0xFFF7F6F3)

    /** 内部区块（概览条、统计小卡） */
    val Surface = Color(0xFFF9F9F8)

    /** 卡片本体 */
    val SurfaceCard = Color(0xFFFFFFFF)

    /** 唯一分割线颜色 */
    val Line = Color(0xFFEAEAEA)

    /** 主文字、主按钮底 */
    val Ink = Color(0xFF111111)

    /** 大段文字（正文替代纯黑） */
    val InkSecondary = Color(0xFF2F3437)

    /** 次要文字 */
    val Muted = Color(0xFF787774)

    /** 占位符、禁用态 */
    val Faint = Color(0xFFA8A6A1)

    /** 主按钮按下态 */
    val Pressed = Color(0xFF333333)

    /** 遮罩（黑色 25%） */
    val Scrim = Color(0x40000000)
}
