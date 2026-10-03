// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.design.icon

import androidx.annotation.DrawableRes
import com.noteone.app.R

/**
 * 全项目图标集。
 *
 * 图标库统一为 **Phosphor Icons v2.1.1**（`regular` = 线性 / `fill` = 实心），
 * 由 `tools/gen_icons.py` 从官方 SVG 自动转成 VectorDrawable，描边即 1.5dp。
 * **禁止混用第二套图标库**（Lucide / Feather / Heroicons / Material Icons 默认集都不允许）。
 *
 * 颜色不写死在 xml 里，一律由 `Icon(tint = ...)` 决定。
 */
object AppIcons {

    // 底栏：未选中用线性，选中用实心（同一套库内切换）
    @DrawableRes val RecordLine = R.drawable.ic_nav_record_line
    @DrawableRes val RecordFill = R.drawable.ic_nav_record_fill
    @DrawableRes val LedgerLine = R.drawable.ic_nav_ledger_line
    @DrawableRes val LedgerFill = R.drawable.ic_nav_ledger_fill
    @DrawableRes val ReportLine = R.drawable.ic_nav_report_line
    @DrawableRes val ReportFill = R.drawable.ic_nav_report_fill
    @DrawableRes val SettingsLine = R.drawable.ic_nav_settings_line
    @DrawableRes val SettingsFill = R.drawable.ic_nav_settings_fill

    // 通用
    @DrawableRes val CaretDown = R.drawable.ic_caret_down
    @DrawableRes val CaretRight = R.drawable.ic_caret_right
    @DrawableRes val ArrowLeft = R.drawable.ic_arrow_left
    @DrawableRes val ArrowRight = R.drawable.ic_arrow_right
    @DrawableRes val Backspace = R.drawable.ic_backspace
    @DrawableRes val Search = R.drawable.ic_search
    @DrawableRes val Keyboard = R.drawable.ic_keyboard
    @DrawableRes val Sliders = R.drawable.ic_sliders
    @DrawableRes val Plus = R.drawable.ic_plus
    @DrawableRes val Trash = R.drawable.ic_trash
    @DrawableRes val Check = R.drawable.ic_check
    @DrawableRes val Close = R.drawable.ic_close
    @DrawableRes val Clock = R.drawable.ic_clock
    @DrawableRes val Pencil = R.drawable.ic_pencil

    // 空状态插画（墨线线稿 + offset 语义 pastel 几何形）
    @DrawableRes val IllEmptyLedger = R.drawable.ill_empty_ledger
    @DrawableRes val IllEmptyReport = R.drawable.ill_empty_report
}
