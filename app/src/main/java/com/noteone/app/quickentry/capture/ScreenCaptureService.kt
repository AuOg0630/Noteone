// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.quickentry.capture

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.Display
import com.noteone.app.quickentry.state.QuickEntryState

/**
 * 无障碍服务，**只用来做一件事：`takeScreenshot()`**。
 *
 * 依据 `docs/开发规范.md` §2.3 / §8.8：
 * - 不在 `onAccessibilityEvent` 里做任何事（不读节点、不模拟点击），只留服务存活标记；
 * - 不订阅文本变化事件（见 `res/xml/accessibility_service_config.xml`）；
 * - `canTakeScreenshot="true"` 是硬要求，缺失时截图直接抛 `SecurityException`。
 *
 * 之所以走无障碍而不是 MediaProjection：Android 14 起 `targetSdk >= 34` 的应用
 * **每个采集会话都必须重新获得用户同意**，与"无感"需求直接冲突（规范 §8.2）。
 */
class ScreenCaptureService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        ScreenCaptureHolder.service = this
        QuickEntryState.markServiceConnected(this)
    }

    /** 刻意什么都不做：本服务不消费事件，只是为了让 `takeScreenshot()` 可用而存在。 */
    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) = Unit

    /**
     * 刻意什么都不做。
     *
     * 规范 §8.8 写的是「`onUnbind` / `onInterrupt` 时置空并标记」，但 `onInterrupt()`
     * 只表示"系统要求打断当前的反馈"，服务本身仍然连着、`takeScreenshot()` 仍然可用。
     * 在这里把 [ScreenCaptureHolder] 置空会让紧随其后的截图平白失败，
     * 所以只在 [onUnbind] / [onDestroy] 里清理并标记。
     */
    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        detach()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        detach()
        super.onDestroy()
    }

    private fun detach() {
        if (ScreenCaptureHolder.service === this) {
            ScreenCaptureHolder.service = null
            QuickEntryState.markServiceLost(this)
        }
    }

    /**
     * 截当前屏幕。
     *
     * 回调在主线程，且**一定**按顺序完成释放：
     * `wrapHardwareBuffer` → `copy(ARGB_8888)` → `hwBitmap.recycle()` + `hwBuffer.close()`。
     * HardwareBuffer 不在 Java 堆上、GC 不收，漏了 `close()` 会持续泄漏图形缓冲。
     *
     * 注意（与任务书的差异）：`AccessibilityService.ScreenshotResult` **没有 `code` 字段**，
     * `ScreenshotResult.SUCCESS` 这个常量也不存在。错误码走 `TakeScreenshotCallback.onFailure(int)`，
     * 常量定义在 `AccessibilityService` 上。任务书 §4.2 的示例代码在这两点上是错的。
     */
    internal fun captureScreen(onResult: (CaptureOutcome) -> Unit) {
        val callback = object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                // 全屏 ARGB_8888 的一次拷贝是十几 MB 的活儿，放在 [captureExecutor] 上做：
                // 主线程不再被蹭掉一帧，也避免给这个「因为无障碍常开而永不退出」的进程
                // 在主线程堆上反复顶出高位水位线。
                val soft = copyToSoftwareBitmap(screenshot)
                // 回调仍切回主线程：调用方要改 View / 弹悬浮窗
                mainExecutor.execute {
                    if (soft == null) {
                        onResult(CaptureOutcome.Failure(ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR))
                    } else {
                        onResult(CaptureOutcome.Success(soft))
                    }
                }
            }

            override fun onFailure(errorCode: Int) {
                onResult(CaptureOutcome.Failure(errorCode))
            }
        }

        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, captureExecutor, callback)
        } catch (error: Exception) {
            // canTakeScreenshot 缺失 / 服务被回收 / 显示不存在时这里会抛，按失败处理而不是崩溃
            onResult(CaptureOutcome.Failure(ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR))
        }
    }

    /**
     * Hardware Bitmap 不可直接读像素，必须转软件 Bitmap。
     *
     * 无论成功与否都要把 HardwareBuffer 还回去 —— 它不在 Java 堆上、GC 不收，
     * 漏掉 `close()` 会持续泄漏图形缓冲。
     */
    private fun copyToSoftwareBitmap(screenshot: ScreenshotResult): Bitmap? {
        val hwBuffer = screenshot.hardwareBuffer
        var hwBitmap: Bitmap? = null
        return try {
            hwBitmap = Bitmap.wrapHardwareBuffer(hwBuffer, screenshot.colorSpace)
            hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
        } catch (error: Exception) {
            null
        } finally {
            hwBitmap?.recycle()
            runCatching { hwBuffer.close() }
        }
    }

    private companion object {
        /**
         * 截图的单线程后台执行器。
         *
         * 用 `by lazy`：只有真的截过图才创建这条线程，没触发过采集的用户不会多一条常驻线程。
         */
        private val captureExecutor: java.util.concurrent.Executor by lazy {
            java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "remember-money-capture")
            }
        }
    }
}

/**
 * 把服务实例暴露给同进程使用。
 *
 * `takeScreenshot()` 只能由服务实例发起，而框选层、面板都在 Activity 侧，
 * 所以需要一个进程内句柄。**只在 `onServiceConnected` 赋值、`onUnbind`/`onDestroy` 置空。**
 */
object ScreenCaptureHolder {

    @Volatile
    var service: ScreenCaptureService? = null

    val isRunning: Boolean get() = service != null
}

/** 截图结果。区分「服务不在」「被节流」「安全窗口」等，调用方各自给不同文案。 */
sealed interface CaptureOutcome {

    /** 已经转成软件 Bitmap，HardwareBuffer 与硬件 Bitmap 都已释放。 */
    data class Success(val bitmap: Bitmap) : CaptureOutcome

    /** `AccessibilityService.ERROR_TAKE_SCREENSHOT_*` 错误码。 */
    data class Failure(val code: Int) : CaptureOutcome

    /** 服务未连接 / 已被系统回收。 */
    data object ServiceUnavailable : CaptureOutcome

    /** 本地节流拦截：距上次截图不足 [ScreenCapturer.MIN_INTERVAL_MS]。 */
    data object Throttled : CaptureOutcome
}

/**
 * 截图的唯一入口。
 *
 * 除了系统自己对无障碍截图设的最小调用间隔，**客户端也拦一道**（规范 §8.2）：
 * 完全依赖系统返回错误码的话，用户在防抖到期前连点磁贴会拿到难看的失败提示。
 */
object ScreenCapturer {

    /** 两次识别之间至少间隔 1 秒。 */
    const val MIN_INTERVAL_MS = 1_000L

    @Volatile
    private var lastCaptureAtMs = 0L

    fun capture(onResult: (CaptureOutcome) -> Unit) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastCaptureAtMs < MIN_INTERVAL_MS) {
            onResult(CaptureOutcome.Throttled)
            return
        }
        val service = ScreenCaptureHolder.service
        if (service == null) {
            onResult(CaptureOutcome.ServiceUnavailable)
            return
        }
        lastCaptureAtMs = now
        service.captureScreen(onResult)
    }

    /** 距可以再次截图还剩多少毫秒。框选层用它决定什么时候把提示药丸恢复成默认文案。 */
    fun throttleRemainingMs(): Long {
        val elapsed = SystemClock.elapsedRealtime() - lastCaptureAtMs
        return (MIN_INTERVAL_MS - elapsed).coerceAtLeast(0L)
    }

    /** 服务掉线后重置节流窗口，避免"刚掉线又被节流"的叠加提示。 */
    fun resetThrottle() {
        lastCaptureAtMs = 0L
    }
}

/** 便捷判断：无障碍服务是否在线。 */
val Context.isScreenCaptureReady: Boolean get() = ScreenCaptureHolder.isRunning
