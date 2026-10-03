// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.quickentry.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.noteone.app.R
import com.noteone.app.QuickEntryActivity
import com.noteone.app.core.design.component.formatMoneyText

/** 磁贴要展示的临时状态。 */
sealed interface TileStatus {

    /** 空闲：会话结束但没记成（识别失败 / 用户关掉面板）时用来复位。 */
    data object Idle : TileStatus

    /** 会话进行中：`STATE_ACTIVE` + 「识别中…」。 */
    data object Recognizing : TileStatus

    /** 记完了：`STATE_ACTIVE` + 「已记 ¥14.00」，1.5 秒后恢复。 */
    data class Saved(val cents: Long) : TileStatus
}

/**
 * 快捷设置磁贴（规范 §8.1）。
 *
 * - `label` = 「记一笔」；图标是单色矢量（见 `res/drawable/ic_tile_record.xml`），
 *   系统按状态着色；
 * - 空闲 `STATE_INACTIVE`；会话中 `STATE_ACTIVE`；成功后 `subtitle` 显示「已记 ¥14.00」
 *   并在 1.5 秒后恢复；
 * - 点一下启动 [QuickEntryActivity]，用 `startActivityAndCollapse` 收起状态栏面板。
 *
 * 磁贴的 `subtitle` / 状态只能由**本服务自己的实例**修改，而 `startActivityAndCollapse`
 * 之后系统会立刻解绑服务。所以"已记 ¥14.00"这条反馈走 [QuickEntryTileBus]：
 * 会话先写在内存里，再用 `requestListeningState()` 让系统重新绑定本服务，
 * `onStartListening()` 里把待办取出来刷一次。
 */
class QuickEntryTileService : TileService() {

    private val handler = Handler(Looper.getMainLooper())

    /** 1.5 秒后把磁贴恢复成空闲态。 */
    private val resetRunnable = Runnable { applyIdle() }

    override fun onStartListening() {
        super.onStartListening()
        QuickEntryTileBus.attach(this)
        val pending = QuickEntryTileBus.consume()
        if (pending == null) applyIdle() else applyStatus(pending)
    }

    override fun onStopListening() {
        handler.removeCallbacks(resetRunnable)
        QuickEntryTileBus.detach(this)
        super.onStopListening()
    }

    override fun onDestroy() {
        handler.removeCallbacks(resetRunnable)
        QuickEntryTileBus.detach(this)
        super.onDestroy()
    }

    /**
     * 点磁贴：启动透明入口并收起状态栏面板。
     *
     * `startActivityAndCollapse(Intent)` 在 targetSdk >= 34 时会被平台直接拒绝
     * （`UnsupportedOperationException`），只有 `PendingIntent` 重载能在 API 34+ 用；
     * 而那个重载在 API 30–33 的平台上根本不存在。所以按系统版本二选一是**唯一**可行写法，
     * lint 的 `StartActivityAndCollapseDeprecated` 只看到了"用了废弃方法"这一面，
     * 这里显式豁免并说明原因（不关全局规则）。
     */
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        handler.removeCallbacks(resetRunnable)
        applyIdle()

        val intent = QuickEntryActivity.intentForLiveCapture(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    REQUEST_CODE_LAUNCH,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    /** 供 [QuickEntryTileBus] 在服务已绑定时直接刷新（省掉一次 requestListeningState）。 */
    internal fun applyStatus(status: TileStatus) {
        val tile = qsTile ?: return
        handler.removeCallbacks(resetRunnable)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_record)
        tile.label = getString(R.string.quick_tile_label)
        tile.state = Tile.STATE_ACTIVE
        when (status) {
            TileStatus.Idle -> {
                applyIdle()
                return
            }

            TileStatus.Recognizing -> {
                tile.subtitle = getString(R.string.quick_tile_subtitle_recognizing)
            }

            is TileStatus.Saved -> {
                tile.subtitle = getString(
                    R.string.quick_tile_subtitle_saved,
                    formatMoneyText(status.cents),
                )
                handler.postDelayed(resetRunnable, SAVED_SUBTITLE_MS)
            }
        }
        tile.updateTile()
    }

    private fun applyIdle() {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_record)
        tile.label = getString(R.string.quick_tile_label)
        tile.state = Tile.STATE_INACTIVE
        tile.subtitle = null
        tile.updateTile()
    }

    private companion object {
        const val REQUEST_CODE_LAUNCH = 1001

        /** 「已记 ¥14.00」显示多久（规范 §8.6）。 */
        const val SAVED_SUBTITLE_MS = 1_500L
    }
}

/**
 * 磁贴状态总线。
 *
 * 为什么需要它：会话跑在 `QuickEntryActivity` 里，而磁贴状态只有 `TileService` 实例能改。
 * 两边通过这个进程内单例传递，服务没绑定时用 `requestListeningState()` 唤起。
 */
object QuickEntryTileBus {

    @Volatile
    private var service: QuickEntryTileService? = null

    @Volatile
    private var pending: TileStatus? = null

    /** 会话开始识别。 */
    fun showRecognizing(context: Context) {
        publish(context, TileStatus.Recognizing)
    }

    /** 记账成功，磁贴上显示「已记 ¥xx」。 */
    fun showSaved(context: Context, cents: Long) {
        publish(context, TileStatus.Saved(cents))
    }

    /**
     * 会话结束但没记成：把磁贴从「识别中…」拉回空闲。
     *
     * 少了这一步，任何一次识别失败之后磁贴都会永久停在 `STATE_ACTIVE`，
     * 每次下拉状态栏还会被重新绑定并把旧状态再播一遍。
     */
    fun showIdle(context: Context) {
        publish(context, TileStatus.Idle)
    }

    internal fun attach(target: QuickEntryTileService) {
        service = target
    }

    internal fun detach(target: QuickEntryTileService) {
        if (service === target) service = null
    }

    /** 取出待展示状态并清空，只在 `onStartListening` 里调用。 */
    internal fun consume(): TileStatus? {
        val value = pending
        pending = null
        return value
    }

    private fun publish(context: Context, status: TileStatus) {
        val attached = service
        if (attached != null) {
            // 服务正在监听：直接改磁贴，并把待办清掉。
            // 不清的话，下一次 onStartListening()（每次下拉状态栏都会触发）
            // 会把这条早就过期的状态再播一遍。
            pending = null
            attached.applyStatus(status)
            return
        }
        pending = status
        // 服务没在监听：请系统重新绑定一次，onStartListening() 会把 pending 刷上去。
        // 这是唯一能从应用侧主动更新磁贴的合法手段，失败也不影响记账主流程。
        runCatching {
            TileService.requestListeningState(
                context.applicationContext,
                ComponentName(context.applicationContext, QuickEntryTileService::class.java),
            )
        }
    }
}
