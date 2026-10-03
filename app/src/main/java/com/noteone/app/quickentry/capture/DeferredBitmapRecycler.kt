// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.quickentry.capture

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 进程级「延后回收」通道（规范 §8.7）。
 *
 * 为什么不能挂在会话的 scope 上：`QuickEntrySession.close()` 会 `sessionScope.cancel()`，
 * 而 `close()` 本身又必须把在途裁片处理掉 —— 两者叠起来只有一种结果：延时回收被取消，
 * 只剩「立刻 recycle()」。可 ML Kit 的原生任务**不随协程取消而停止**
 * （`TextRecognizer.process()` 返回的是 Task，包它的 `suspendCancellableCoroutine`
 * 也没有 `invokeOnCancellation`），于是原生侧就会读到已经还给系统的像素缓冲区，
 * 表现为 native 崩溃或内存损坏 —— 而这恰恰发生在「冷启动识别（模型加载约 3s）期间
 * 用户按返回键」这条最日常的路径上。
 *
 * 所以「延后」这件事必须由一个与任何会话都无关的通道来做。
 * 任何退出路径（识别超时、返回键、关面板、Activity 销毁）都只经过这里。
 */
internal object DeferredBitmapRecycler {

    /** 与 `QuickEntrySession` 原本的兜底延迟同一量级：原生任务通常几秒内结束。 */
    const val DELAY_MS = 10_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 延迟 [delayMs] 再回收。重复调用同一个 Bitmap 是安全的（`isRecycled` 兜底）。 */
    fun recycleLater(bitmap: Bitmap, delayMs: Long = DELAY_MS) {
        scope.launch {
            delay(delayMs)
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }
}
