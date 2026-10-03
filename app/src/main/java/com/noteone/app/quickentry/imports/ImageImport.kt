// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.quickentry.imports

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import kotlin.math.max

/**
 * 回退路径 B 的图片解码（规范 §8.8）。
 *
 * 三个入口（相册分享 / Photo Picker / 桌面快捷方式）最终都落在这里，
 * 之后与实拍路径**共用同一套框选 + OCR + 面板逻辑**。
 *
 * 只用 `ContentResolver` + `BitmapFactory`，**不申请任何存储权限**；
 * 解出来的 Bitmap 只活在内存里，由 `QuickEntrySession` 负责回收，不落盘。
 */
object ImageImport {

    /** 解码后的像素上限，防止一张超广角全景图把内存打满。 */
    private const val MAX_PIXELS = 12_000_000

    /** Photo Picker 的请求（系统相册选择器，零权限）。 */
    fun pickVisualMediaRequest(): PickVisualMediaRequest =
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    /** Photo Picker 用的契约，Activity 侧 `registerForActivityResult` 直接传它。 */
    fun pickVisualMediaContract() = ActivityResultContracts.PickVisualMedia()

    /**
     * 从 `content://` URI 解码一张位图。解码不出来返回 `null`（调用方提示"没有读取到图片"）。
     *
     * 分两次解码：先只读尺寸算采样率，再按采样率真正解码。直接解原图在
     * 4800×3600 这种截图上很容易 OOM。
     */
    fun decode(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        }.getOrNull()
    }

    /** 采样率必须是 2 的幂。 */
    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        while (width / sample * (height / sample) > MAX_PIXELS) {
            sample *= 2
        }
        return max(1, sample)
    }
}
