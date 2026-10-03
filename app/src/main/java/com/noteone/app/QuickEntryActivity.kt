// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.noteone.app.quickentry.QuickEntryIntents
import com.noteone.app.quickentry.capture.QuickEntrySession
import com.noteone.app.quickentry.di.QuickEntryRepositories
import com.noteone.app.quickentry.imports.ImageImport
import com.noteone.app.quickentry.ocr.OcrClient
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 磁贴 / 桌面快捷方式 / 相册分享的**透明入口**（规范 §8.1）。
 *
 * 它自己不渲染任何东西——每一次会话的框选层、结果面板、引导页都由
 * [QuickEntrySession] 用 `WindowManager` 加在窗口上。这样四种来路
 * （磁贴、桌面快捷方式、Photo Picker、`ACTION_SEND`）能共用同一套状态机。
 *
 * 生命周期上刻意**没有**用任务书里的 `noHistory`：`noHistory` 会让 Activity 一被切到
 * 后台就 finish，而「从图片记一笔」必须先启动系统 Photo Picker（本 Activity 会被 stop），
 * 结果回调就永远回不来了。改成 `excludeFromRecents + taskAffinity="" + singleTask`
 * 加显式 `finish()`，用户可见行为一致（不进最近任务、不留返回栈），但回调不会丢。
 */
@AndroidEntryPoint
class QuickEntryActivity : ComponentActivity() {

    @Inject
    lateinit var repositories: QuickEntryRepositories

    @Inject
    lateinit var ocr: OcrClient

    private var activeSession: QuickEntrySession? = null
    private var decodeJob: Job? = null

    private val pickImage: ActivityResultLauncher<PickVisualMediaRequest> =
        registerForActivityResult(ImageImport.pickVisualMediaContract()) { uri ->
            if (uri == null) {
                // 用户取消了选图：整条会话到此为止
                finishIdle()
            } else {
                loadStaticImage(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 悬浮窗拿到焦点时返回键由面板自己的 OnKeyListener 接；这里的回调是兜底，
        // 保证任何情况下返回键都能干净地结束会话。
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val current = activeSession
                    if (current == null) finish() else current.close()
                }
            },
        )

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val running = activeSession
        if (running != null) {
            // 连点磁贴：正在会话中，只提示节流，绝不重开会话（否则会叠出两层悬浮窗）
            running.notifyThrottled()
            return
        }
        handleIntent(intent)
    }

    override fun onDestroy() {
        decodeJob?.cancel()
        activeSession?.close()
        activeSession = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------ 入口分发

    private fun handleIntent(intent: Intent?) {
        val action = intent?.action
        when {
            action == Intent.ACTION_SEND -> handleSharedImage(intent)

            action == QuickEntryIntents.ACTION_QUICK_RECORD_IMAGE ||
                intent?.getBooleanExtra(QuickEntryIntents.EXTRA_FROM_IMAGE, false) == true ->
                requestPickImage()

            else -> session().startLiveCapture()
        }
    }

    private fun requestPickImage() {
        pickImage.launch(ImageImport.pickVisualMediaRequest())
    }

    /** 相册「分享」到本应用：只有 URI，零权限读取。 */
    private fun handleSharedImage(intent: Intent) {
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        if (uri == null) {
            finishIdle()
            return
        }
        loadStaticImage(uri)
    }

    private fun loadStaticImage(uri: Uri) {
        decodeJob = lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { ImageImport.decode(this@QuickEntryActivity, uri) }
            if (bitmap == null) {
                session().showNotice(R.string.quick_guide_image_title, R.string.quick_error_no_image)
                return@launch
            }
            session().startStaticImage(bitmap)
        }
    }

    private fun session(): QuickEntrySession =
        activeSession ?: QuickEntrySession(
            activity = this,
            repositories = repositories,
            ocr = ocr,
            onRequestPickImage = ::requestPickImage,
        ).also { activeSession = it }

    private fun finishIdle() {
        if (!isFinishing) finish()
    }

    companion object {

        /** 构造「记一笔」的启动 intent（磁贴与桌面快捷方式共用同一个 action）。 */
        fun intentForLiveCapture(context: Context): Intent =
            Intent(context, QuickEntryActivity::class.java)
                .setAction(QuickEntryIntents.ACTION_QUICK_RECORD)

        /** 构造「从图片记一笔」的启动 intent。 */
        fun intentForPickImage(context: Context): Intent =
            Intent(context, QuickEntryActivity::class.java)
                .setAction(QuickEntryIntents.ACTION_QUICK_RECORD_IMAGE)
                .putExtra(QuickEntryIntents.EXTRA_FROM_IMAGE, true)
    }
}
