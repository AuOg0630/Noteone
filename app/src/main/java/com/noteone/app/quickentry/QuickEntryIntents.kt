// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.quickentry

/**
 * D 模块的 intent 约定。`res/xml/shortcuts.xml` 与 `AndroidManifest.xml` 里的
 * action 字符串必须与这里逐字一致，否则快捷方式会打不开。
 */
object QuickEntryIntents {

    /** 桌面快捷方式「记一笔」：走无障碍框选识别。 */
    const val ACTION_QUICK_RECORD = "com.noteone.app.action.QUICK_RECORD"

    /** 桌面快捷方式「从图片记一笔」：走 Photo Picker，零权限。 */
    const val ACTION_QUICK_RECORD_IMAGE = "com.noteone.app.action.QUICK_RECORD_IMAGE"

    /** 显式要求「从图片记一笔」的 extra（设置页入口也用它）。 */
    const val EXTRA_FROM_IMAGE = "from_image"
}
