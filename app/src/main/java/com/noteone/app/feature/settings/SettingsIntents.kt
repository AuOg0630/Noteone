// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.net.toUri
import com.noteone.app.QuickEntryActivity

/**
 * 设置页要跳转的几个系统页面。
 *
 * 全部是「跳过去让用户自己改」的场景：无障碍、悬浮窗这类特殊权限**没有运行时申请接口**，
 * 只能引导到系统设置（规范 §8.8）。把这些 Intent 的构造集中在一处，
 * 页面里就只剩一句 `startActivity(...)`。
 */
object SettingsIntents {

    /** 无障碍服务设置。 */
    fun accessibility(context: Context): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    /** 本应用的悬浮窗权限设置。 */
    fun overlay(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:${context.packageName}".toUri(),
        )

    /** 本应用的「应用信息」页：自启动白名单与电池优化都在它的下一层。 */
    fun appDetails(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )

    /** 「从图片识别」：零权限入口，直接走 D 的透明 Activity（D 交付说明 §4.1）。 */
    fun pickImage(context: Context): Intent =
        QuickEntryActivity.intentForPickImage(context)
}
