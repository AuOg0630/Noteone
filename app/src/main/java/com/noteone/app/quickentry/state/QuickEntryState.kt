// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.quickentry.state

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.core.content.edit
import com.noteone.app.quickentry.capture.ScreenCaptureHolder
import com.noteone.app.quickentry.capture.ScreenCaptureService

/**
 * 无障碍服务的三态（规范 §8.8 / 任务书 §9）。
 *
 * `KILLED_BY_SYSTEM` 必须与 `DISABLED` 分开——它直接命中国产 ROM
 * （HyperOS / ColorOS / EMUI / OriginOS）在后台把无障碍服务杀掉这个场景，
 * 用户看到的是「明明开着却不好使」，提示文案与处理方式完全不同。
 */
enum class A11yStatus {
    /** 服务在线。 */
    RUNNING,

    /** 从未被系统绑定过，或用户在系统设置里主动关掉了。 */
    DISABLED,

    /** 曾经成功绑定过、系统设置里也还开着，但现在服务不在了 —— 被系统杀了。 */
    KILLED_BY_SYSTEM,
}

/**
 * D 模块的少量持久状态。
 *
 * 只用 `SharedPreferences` 存**布尔标记**，不涉及任何截图数据 ——
 * 规范 §8.7 禁止的是把 Bitmap / 识别结果落盘，服务是否启用过这种开关状态不在其列。
 * 也不放 `DataStore`：那是 A 的 `SettingsRepository`，D 不越界。
 */
object QuickEntryState {

    private const val PREFS = "quickentry_state"
    private const val KEY_ENABLED_ONCE = "a11y_enabled_once"
    private const val KEY_LOST = "a11y_lost"
    private const val KEY_LOST_NOTICE_PENDING = "a11y_lost_notice_pending"

    /** 服务连上了。 */
    fun markServiceConnected(context: Context) {
        prefs(context).edit {
            putBoolean(KEY_ENABLED_ONCE, true)
            putBoolean(KEY_LOST, false)
        }
    }

    /** 服务掉了（`onUnbind` / `onDestroy`）。置一条"下次进 App 提示一次"的待办。 */
    fun markServiceLost(context: Context) {
        prefs(context).edit {
            putBoolean(KEY_LOST, true)
            putBoolean(KEY_LOST_NOTICE_PENDING, true)
        }
    }

    /**
     * 服务状态自查。
     *
     * 判定顺序（越靠前优先级越高）：
     * 1. 句柄非空 → [A11yStatus.RUNNING]
     * 2. 从未连上过 → [A11yStatus.DISABLED]（从未开启过）
     * 3. 系统设置里已经没有本服务了 → [A11yStatus.DISABLED]（用户自己关掉的）
     * 4. 其余情况 → [A11yStatus.KILLED_BY_SYSTEM]
     */
    fun checkStatus(context: Context): A11yStatus {
        if (ScreenCaptureHolder.isRunning) return A11yStatus.RUNNING
        val prefs = prefs(context)
        if (!prefs.getBoolean(KEY_ENABLED_ONCE, false)) return A11yStatus.DISABLED
        // 总开关关掉时，ENABLED_ACCESSIBILITY_SERVICES 里通常仍留着本服务的条目，
        // 只看它会把「用户自己关的」误判成「被系统杀的」，提示文案与动作全错。
        if (!isAccessibilityMasterEnabled(context)) return A11yStatus.DISABLED
        if (!isServiceEnabledInSettings(context)) return A11yStatus.DISABLED
        return A11yStatus.KILLED_BY_SYSTEM
    }

    /**
     * 「服务掉线」这条提示只弹一次。
     *
     * 读取即清除，所以调用方每进一次设置页调一次即可，不会反复骚扰；
     * 规范也明确要求**不弹 notification**。
     */
    fun consumeServiceLostNotice(context: Context): Boolean {
        val prefs = prefs(context)
        if (!prefs.getBoolean(KEY_LOST_NOTICE_PENDING, false)) return false
        prefs.edit { putBoolean(KEY_LOST_NOTICE_PENDING, false) }
        return true
    }

    /**
     * 系统设置里是否勾选着本服务。
     *
     * 用 `ComponentName.unflattenFromString` 逐项还原再比，比字符串包含判断可靠：
     * 不同 ROM 存的可能是全类名或 `.Service` 简写，也有大小写差异。
     */
    /** 「设置 → 无障碍」的总开关（关掉时所有无障碍服务都不生效）。 */
    private fun isAccessibilityMasterEnabled(context: Context): Boolean =
        Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0,
        ) == 1

    fun isServiceEnabledInSettings(context: Context): Boolean {
        val expected = ComponentName(context, ScreenCaptureService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { entry ->
            val component = ComponentName.unflattenFromString(entry) ?: return@any false
            component.packageName.equals(expected.packageName, ignoreCase = true) &&
                component.className.equals(expected.className, ignoreCase = true)
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
