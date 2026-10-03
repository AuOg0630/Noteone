// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.feature.onboarding

import android.content.Context
import androidx.core.content.edit

/**
 * 「首次启动权限引导」是否已经走完。
 *
 * 只存一个布尔标记，用 `SharedPreferences` 而不是 A 的 `SettingsRepository`（DataStore）：
 * 那是记账偏好，这里是纯 UI 的一次性开关，混进去只会让备份 / 恢复多带一个
 * 不该被备份的字段（恢复备份后不该重新弹引导）。
 *
 * 与 `QuickEntryState` 同一套做法，不越界改 `core/`。
 */
object OnboardingState {

    private const val PREFS = "onboarding_state"
    private const val KEY_FINISHED = "finished"

    /** 用户是否已经点过引导页的「开始记账」。 */
    fun isFinished(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FINISHED, false)

    /** 引导页点「开始记账」后落标记，之后启动直接进主界面。 */
    fun markFinished(context: Context) {
        prefs(context).edit { putBoolean(KEY_FINISHED, true) }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
