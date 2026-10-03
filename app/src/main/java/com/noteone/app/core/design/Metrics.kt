// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.unit.dp

/**
 * 间距刻度。**只允许 4 / 8 / 12 / 16 / 24 / 32 这 6 个值**（依据 `docs/开发规范.md` §3.4）。
 */
object Space {
    val XS = 4.dp
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val XL = 24.dp
    val XXL = 32.dp
}

/**
 * 圆角。**只用 12 / 16 / 6 / 4 / 9999 五档**。
 * 卡片与主按钮禁止用 [Pill]（9999）——那是标签胶囊与状态徽标专用的。
 */
object Radius {
    val Card = 12.dp
    val Sheet = 16.dp
    val Button = 6.dp
    val Input = 4.dp
    val Pill = 9999.dp

    /** 统计条形 / 进度条 / 柱状图的圆角 */
    val Bar = 4.dp
}

/**
 * 固定尺寸（这些值来自规范 §4.2 / §5.1 / §6.1，不属于「间距刻度」）。
 */
object Dimension {
    /** 最小可点击区域 */
    val TouchMin = 48.dp

    /** 唯一分割线线宽 */
    val Divider = 1.dp

    /** 记账页固定头部 */
    val HeaderRow = 44.dp

    /** 账单页条目 */
    val ListRow = 56.dp

    /** 底栏 */
    val BottomBar = 56.dp

    /** 底栏选中指示线：2dp 高 × 16dp 宽 */
    val IndicatorHeight = 2.dp
    val IndicatorWidth = 16.dp

    /** 自绘键盘行高 */
    val KeypadRow = 56.dp

    /** chip（分类 / 快捷用途 / 筛选 / 时间范围） */
    val Chip = 32.dp
    val ChipHPadding = 12.dp

    /** 分类色点直径 */
    val ColorDot = 8.dp

    /** 底部弹层拖拽指示条 */
    val SheetHandleWidth = 32.dp
    val SheetHandleHeight = 4.dp

    /** 页面左右边距 */
    val PageHPadding = 16.dp

    /** 状态栏下留白 */
    val ContentTop = 24.dp

    /** 底栏上留白 */
    val ContentBottom = 16.dp
}

/**
 * 动效参数。统一曲线 `CubicBezierEasing(0.16f, 1f, 0.3f, 1f)`。
 * 时长单位毫秒，直接传给 `tween(durationMillis = Motion.Normal)`。
 *
 * **只允许动画 transform 与 opacity**。除页面切换外，位移不超过 16dp。
 * 页面切换是唯一的例外：它必须整屏横向平移（见下方与 `docs/开发规范.md` §3.7）。
 * 禁止弹跳、旋转、加载转圈。
 */
object Motion {
    val Ease = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** 按下反馈、金额微动效 */
    const val Fast = 150

    /** Tab / 页面切换、列表进入、Snackbar */
    const val Normal = 250

    /** 底部弹层上滑、金额整数位滚动 */
    const val Slow = 300

    /** 图表绘制 */
    const val Chart = 400

    /** 列表逐项延迟 */
    const val StaggerStep = 60

    /*
     * 页面切换**没有**固定位移常量：转场是「横向左右相接」，位移量由
     * `slideInHorizontally` / `slideOutHorizontally` 的整屏宽度 `it` 直接给出。
     * 两侧页面必须共用同一时长与同一条曲线，接缝才不会错开、露出底色。
     * 见 `navigation/AppNavHost`。
     */

    /** 列表项进入位移 */
    val ListEnterOffset = 8.dp

    /** Snackbar 进入位移 */
    val SnackbarEnterOffset = 16.dp

    /** Snackbar 停留时长 */
    const val SnackbarDurationMs = 2500L
}
