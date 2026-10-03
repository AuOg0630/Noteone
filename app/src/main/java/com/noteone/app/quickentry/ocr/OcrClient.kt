// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.quickentry.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 文本识别入口。抽出接口是为了让会话层可以单测 / 替换，也方便以后换模型。
 *
 * 实现必须**端侧、不联网**（规范 §2.1），且不得对入参 Bitmap 做任何持久化。
 */
interface OcrClient {

    /**
     * 本次识别建议使用的超时。
     *
     * 识别器**冷启动**（中文模型要从 APK 里重新加载，真机实测约 3s）比热态慢一个数量级，
     * 两种情况共用一个 3s 会把冷启动直接判成「未识别到金额」。实现自己知道当前冷不冷。
     */
    val suggestedTimeoutMs: Long

    /**
     * 识别一张位图，按阅读顺序返回文本行。
     *
     * 失败直接抛异常。
     * **实现不得回收入参 Bitmap**——它的释放顺序由 `QuickEntrySession` 统一负责。
     */
    suspend fun recognize(bitmap: Bitmap): List<OcrLine>
}

/**
 * ML Kit Text Recognition v2 中文包（`com.google.mlkit:text-recognition-chinese`）。
 *
 * 端侧模型随 APK 一起分发，不下载、不联网。
 *
 * ## 识别器的生命周期（本文件的关键设计）
 *
 * 「进程退出时由系统回收」这条前提在本项目**不成立**：为了保证 `takeScreenshot()`
 * 随时可用，无障碍服务必须常开，系统因此不会回收本进程。识别器只要创建过，
 * 中文模型就会一直驻留内存 —— 用户一天只点一次磁贴，它也要占一整天。
 *
 * 所以这里做**空闲释放**：识别完成后再等 [IDLE_RELEASE_MS] 没有新任务，就 `close()`
 * 掉识别器、把模型还给系统；连续记几笔的这段时间里保持热态，不会反复付冷启动代价。
 * 冷启动那一次的识别用 [COLD_TIMEOUT_MS]（比规范 §8.3 的 3s 长），
 * 否则模型还没加载完就被超时判定成「未识别到金额」——这正是长期未使用后第一次记账的路径。
 */
@Singleton
class MlKitOcrClient @Inject constructor() : OcrClient {

    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var recognizer: TextRecognizer? = null
    private var inFlight = 0
    private var idleRelease: Job? = null

    /** 识别器是否处于冷态（模型还没加载，或者已经被空闲释放）。 */
    @Volatile
    private var cold = true

    override val suggestedTimeoutMs: Long
        get() = if (cold) COLD_TIMEOUT_MS else WARM_TIMEOUT_MS

    override suspend fun recognize(bitmap: Bitmap): List<OcrLine> {
        val client = acquire()
        try {
            return suspendCancellableCoroutine { continuation ->
                client.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { text ->
                        // 走到这里说明模型已经加载完，后面几次都是热态
                        cold = false
                        // 超时已经被 withTimeout 取消时不要再 resume，否则会抛 IllegalStateException
                        if (continuation.isActive) continuation.resume(text.toOcrLines())
                    }
                    .addOnFailureListener { error ->
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
            }
        } finally {
            release()
        }
    }

    /** 取识别器，并把空闲释放的倒计时取消掉。 */
    private fun acquire(): TextRecognizer = synchronized(lock) {
        inFlight++
        idleRelease?.cancel()
        idleRelease = null
        recognizer ?: TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            .also { recognizer = it }
    }

    /** 一次识别结束：回到空闲就重新开始倒计时。 */
    private fun release() {
        synchronized(lock) {
            inFlight--
            if (inFlight > 0) return
            idleRelease?.cancel()
            idleRelease = scope.launch {
                delay(IDLE_RELEASE_MS)
                val target = synchronized(lock) {
                    val current = recognizer
                    if (inFlight > 0 || current == null) {
                        null
                    } else {
                        recognizer = null
                        cold = true
                        current
                    }
                }
                target?.let { runCatching { it.close() } }
            }
        }
    }

    private fun Text.toOcrLines(): List<OcrLine> =
        textBlocks
            .flatMap { block -> block.lines }
            .map { line ->
                OcrLine(
                    text = line.text,
                    centerY = line.boundingBox?.centerY() ?: 0,
                )
            }

    private companion object {
        /** 热态超时（规范 §8.3 的 3000ms）。 */
        const val WARM_TIMEOUT_MS = 3_000L

        /** 冷态超时：要把模型加载时间（真机实测约 3s）也放进来。 */
        const val COLD_TIMEOUT_MS = 8_000L

        /** 空闲多久释放识别器。连续记账的间隔远小于它，不会触发。 */
        const val IDLE_RELEASE_MS = 120_000L
    }
}
