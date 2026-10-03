// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.quickentry.capture

import android.accessibilityservice.AccessibilityService
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.noteone.app.R
import com.noteone.app.core.common.Money
import com.noteone.app.core.data.model.Category
import com.noteone.app.core.data.model.CategoryScope
import com.noteone.app.core.data.model.Direction
import com.noteone.app.core.data.model.QuickAction
import com.noteone.app.core.data.model.TransactionDraft
import com.noteone.app.core.data.model.TransactionSource
import com.noteone.app.core.data.seed.BuiltInData
import com.noteone.app.core.design.AppTheme
import com.noteone.app.core.design.Motion
import com.noteone.app.core.domain.AmountInputRules
import com.noteone.app.quickentry.di.QuickEntryRepositories
import com.noteone.app.quickentry.ocr.AmountCandidate
import com.noteone.app.quickentry.ocr.AmountParser
import com.noteone.app.quickentry.ocr.OcrClient
import com.noteone.app.quickentry.ocr.OcrLine
import com.noteone.app.quickentry.panel.PanelMessage
import com.noteone.app.quickentry.panel.QuickEntryGuide
import com.noteone.app.quickentry.panel.QuickEntryPanel
import com.noteone.app.quickentry.panel.QuickEntryPanelState
import com.noteone.app.quickentry.state.A11yStatus
import com.noteone.app.quickentry.state.QuickEntryState
import com.noteone.app.quickentry.tile.QuickEntryTileBus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min

/**
 * 一次「框选 → 识别 → 记账」会话。状态机见规范 §8.3。
 *
 * ```
 * CHECK_PERMISSION → CROPPING → CAPTURE → RECOGNIZING → RESULT_PANEL → SAVED / DISCARDED
 * ```
 *
 * 设计要点：
 * - **所有悬浮内容都由本类用 `WindowManager` 管理**，Activity 是透明的空壳。
 *   实拍与静态图两条路径因此复用同一套窗口与状态逻辑（规范 §8.8 要求复用率接近 100%）。
 * - 框选发生在**截图之前**，所以选框底下就是真实屏幕，选区天然透出，不做任何图像处理。
 * - 所有 Bitmap / HardwareBuffer 的释放都在 [recognize] 与 [close] 里成对完成，
 *   任何一步都不写磁盘（规范 §8.7）。
 */
class QuickEntrySession(
    private val activity: ComponentActivity,
    private val repositories: QuickEntryRepositories,
    private val ocr: OcrClient,
    /** 无障碍不可用时，引导页上「从图片识别」要调的回调。 */
    private val onRequestPickImage: () -> Unit,
) {

    // ------------------------------------------------------------------ 窗口与图形

    private val windowManager: WindowManager get() = activity.windowManager
    private val density: Float get() = activity.resources.displayMetrics.density

    private var selectionView: SelectionOverlayView? = null
    private var imageView: ImageView? = null
    private var panelView: ComposeView? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var panelEnterAnimator: ValueAnimator? = null
    private var panelExitAnimator: ValueAnimator? = null
    private var panelDismissing = false
    private var guideView: ComposeView? = null

    /**
     * 引导页是否挂在 Activity 自己的 content view 上（而不是 WindowManager 上）。
     *
     * 没有悬浮窗权限时**只能**这样挂：往 WindowManager 加 TYPE_APPLICATION_OVERLAY
     * 会被框架直接拒绝（BadTokenException: permission denied for window type 2038）。
     * 而「没有悬浮窗权限」恰恰是引导页最主要的触发条件（首次点磁贴）。
     */
    private var guideInActivity = false

    /** 静态图路径的原图（同时显示在 [imageView] 里）。实拍路径为 null。 */
    private var staticSource: Bitmap? = null

    /** 降级面板右上角的 48dp 缩略图。两份引用分别给 Compose 与回收逻辑。 */
    private var thumbnailBitmap: Bitmap? = null
    private var thumbnailImage: ImageBitmap? = null

    /** 已裁出、正在识别的裁片。会话结束时兜底回收。 */
    private var inFlightCrop: Bitmap? = null

    /**
     * 超时后 ML Kit 仍在读的裁片。
     *
     * `withTimeout` 只结束**我方**的等待，ML Kit 的原生任务还在跑；这时立刻 `recycle()`
     * 会让原生侧读到已释放的内存。所以超时的裁片先挂在这里，10 秒后（或会话结束时）再回收。
     */
    // 延后回收统一走进程级的 DeferredBitmapRecycler（见该文件 KDoc），
    // 这里不再自己维护列表：sessionScope 在 close() 里会被取消，挂不住延时任务。

    private var selectionRect = Rect()

    /**
     * 框选层的屏幕位置。
     *
     * 框选层给的是**视图坐标**，而截图位图与面板窗口用的都是**屏幕坐标**；
     * 悬浮窗被系统压在状态栏下面时两者并不相等，中间差的正是这个偏移。
     * 在 [beginCapture] 里（悬浮层被隐藏之前）读一次，之后所有换算都基于它。
     */
    private var overlayLocation = Point(0, 0)

    /** 面板锚点，**视图坐标系**（用时经 [overlayLocation] 换算到屏幕坐标）。 */
    private var panelAnchorTop = 0
    private var panelAnchorBottom = 0

    /** 用户手动拖过面板之后就尊重他摆的位置，不再自动重排。 */
    private var panelUserMoved = false

    // ------------------------------------------------------------------ 状态

    private val panelState = mutableStateOf(QuickEntryPanelState())
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var inputs = SessionInputs()
    private var collectJob: Job? = null
    private var workJob: Job? = null
    private var closed = false
    private var panelWindowShown = false

    /** 本次会话是否已经记账成功。决定结束会话时要不要把磁贴从「识别中…」拉回空闲。 */
    private var saved = false

    init {
        observeInputs()
    }

    // ------------------------------------------------------------------ 入口

    /** 实拍路径：快捷设置磁贴 / 桌面快捷方式。 */
    fun startLiveCapture() {
        if (closed) return
        if (!Settings.canDrawOverlays(activity)) {
            showOverlayGuide()
            return
        }
        if (!ScreenCaptureHolder.isRunning) {
            showA11yGuide()
            return
        }
        showCroppingView(null)
    }

    /** 静态图路径：相册分享 / Photo Picker。 */
    fun startStaticImage(source: Bitmap) {
        if (closed) {
            source.recycle()
            return
        }
        if (!Settings.canDrawOverlays(activity)) {
            source.recycle()
            showOverlayGuide()
            return
        }
        staticSource = source
        showCroppingView(source)
    }

    /** 连点磁贴 / 重复下发 intent：不重开会话，只提示一下节流。 */
    fun notifyThrottled() {
        if (closed) return
        selectionView?.let(::showThrottledHint)
    }

    // ------------------------------------------------------------------ CROPPING

    private fun showCroppingView(image: Bitmap?) {
        if (image != null) {
            val view = ImageView(activity).apply {
                setBackgroundColor(Color.BLACK)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageBitmap(image)
            }
            imageView = view
            windowManager.addView(view, fullScreenParams())
        }

        val overlay = SelectionOverlayView(activity).apply {
            onSettle = { rect ->
                selectionRect = rect
                panelAnchorTop = rect.top
                panelAnchorBottom = rect.bottom
                beginCapture()
            }
            onCancel = { close() }
        }
        selectionView = overlay
        windowManager.addView(overlay, fullScreenParams())
    }

    private fun beginCapture() {
        val overlay = selectionView ?: return
        overlay.showHint(SelectionOverlayView.Hint.Recognizing)
        // 实拍路径马上要把悬浮层设成 GONE，GONE 之后 getLocationOnScreen 就不可靠了，
        // 所以在这里先把它的屏幕位置记下来（下面所有坐标换算都依赖它）。
        overlayLocation = viewLocationOnScreen(overlay)

        val source = staticSource
        if (source != null) {
            // 静态图路径：不需要截图，也没有"遮住自己"的问题，选框继续留着给用户看
            onCaptured(source)
            return
        }

        // 实拍路径：**先把悬浮层藏起来**，否则截到的是遮罩自己（规范 §8.4）
        overlay.visibility = View.GONE
        overlay.post {
            if (closed) return@post
            ScreenCapturer.capture(::handleCaptureOutcome)
        }
    }

    private fun handleCaptureOutcome(outcome: CaptureOutcome) {
        if (closed) {
            // 会话已经结束，这张整屏截图没人接手了：必须就地回收。
            // 它是回调参数、不在任何字段里，close() 的 recycleAll() 看不见它。
            (outcome as? CaptureOutcome.Success)?.bitmap?.recycle()
            return
        }
        when (outcome) {
            is CaptureOutcome.Success -> onCaptured(outcome.bitmap)

            CaptureOutcome.Throttled -> selectionView?.let(::showThrottledHint)

            CaptureOutcome.ServiceUnavailable -> onServiceLost()

            is CaptureOutcome.Failure -> when (outcome.code) {
                AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT ->
                    selectionView?.let(::showThrottledHint)

                AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW -> openPanel(
                    candidates = emptyList(),
                    useThumbnail = false,
                    message = activity.getString(R.string.quick_panel_secure_window),
                    isError = true,
                )

                AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> onServiceLost()

                else -> openPanel(
                    candidates = emptyList(),
                    useThumbnail = false,
                    message = activity.getString(R.string.quick_panel_capture_failed),
                    isError = true,
                )
            }
        }
    }

    /**
     * 截图成功：按选区裁剪，然后立刻把整屏位图回收掉（规范 §8.7 第 3 步）。
     *
     * `Bitmap.createBitmap` 在"裁的就是整张图"时会**直接返回源对象**，所以必须用引用比较
     * 判断一次：否则要么把源图当独立裁片回收掉，要么在回收源图后继续拿它去识别。
     */
    private fun onCaptured(source: Bitmap) {
        val display = if (staticSource != null) {
            imageDisplayRect(source)
        } else {
            screenRect(source)
        }

        // 选区是「悬浮层视图坐标」，位图是「屏幕坐标」，中间差一个悬浮窗自身的屏幕位置。
        // 少这一步，裁剪结果会整体上移一条状态栏的高度（悬浮窗被系统压在状态栏下面时）。
        val crop = cropSelection(source, selectionInScreen(), display)
        if (crop == null) {
            if (staticSource == null) source.recycle()
            openPanel(
                candidates = emptyList(),
                useThumbnail = false,
                message = activity.getString(R.string.quick_panel_capture_failed),
                isError = true,
            )
            return
        }

        val owned = if (crop === source) source.copy(Bitmap.Config.ARGB_8888, false) else crop
        if (staticSource == null) {
            // 实拍路径：整屏位图已经没有用了，立刻回收
            source.recycle()
        }
        recognize(owned)
    }

    private fun cropSelection(source: Bitmap, selection: Rect, display: Rect): Bitmap? {
        if (display.width() <= 0 || display.height() <= 0) return null
        val scaleX = source.width.toFloat() / display.width()
        val scaleY = source.height.toFloat() / display.height()

        val left = ((selection.left - display.left) * scaleX).toInt().coerceIn(0, source.width - 1)
        val top = ((selection.top - display.top) * scaleY).toInt().coerceIn(0, source.height - 1)
        val right = ((selection.right - display.left) * scaleX).toInt().coerceIn(left + 1, source.width)
        val bottom = ((selection.bottom - display.top) * scaleY).toInt().coerceIn(top + 1, source.height)
        return runCatching {
            Bitmap.createBitmap(source, left, top, right - left, bottom - top)
        }.getOrNull()
    }

    /** 原图按 `FIT_CENTER` 铺在 [imageView] 上时实际占据的**屏幕**矩形。 */
    private fun imageDisplayRect(source: Bitmap): Rect {
        val view = imageView ?: return Rect(0, 0, source.width, source.height)
        if (view.width <= 0 || view.height <= 0) return Rect(0, 0, source.width, source.height)
        val scale = min(
            view.width.toFloat() / source.width,
            view.height.toFloat() / source.height,
        )
        val width = (source.width * scale).toInt()
        val height = (source.height * scale).toInt()
        // 同样要换算到屏幕坐标：选区那边已经加过 overlayLocation，两边必须同一个坐标系
        val location = viewLocationOnScreen(view)
        val left = (view.width - width) / 2 + location.x
        val top = (view.height - height) / 2 + location.y
        return Rect(left, top, left + width, top + height)
    }

    // ------------------------------------------------------------------ RECOGNIZING

    private fun recognize(crop: Bitmap) {
        inFlightCrop = crop
        QuickEntryTileBus.showRecognizing(activity)

        workJob = sessionScope.launch {
            val height = crop.height
            // 显式重抛 CancellationException：withTimeoutOrNull 靠它结束等待，
            // 被 runCatching 吞掉的话超时就不生效了
            // 超时由 OcrClient 决定：识别器冷启动（模型要重新加载）时需要更宽松的上限，
            // 否则长期没用之后第一次记账会被 3s 超时误判成「未识别到金额」。
            val outcome: Result<List<OcrLine>>? = withTimeoutOrNull(ocr.suggestedTimeoutMs) {
                try {
                    Result.success(ocr.recognize(crop))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Result.failure(error)
                }
            }
            if (closed) {
                recycleLater(crop)
                return@launch
            }

            val lines = outcome?.getOrNull()
            val candidates = lines?.let {
                AmountParser.extract(it, height, inputs.preferCurrencySymbol)
            }.orEmpty()

            // 缩略图必须在裁片回收**之前**生成（它要读像素）
            val hasThumbnail = candidates.isEmpty() && lines != null && makeThumbnail(crop)

            // 规范 §8.7 第 4 步：ML Kit 返回后立即回收裁片；超时的裁片延后回收
            if (outcome == null) recycleLater(crop) else crop.recycle()
            inFlightCrop = null

            if (candidates.isEmpty()) {
                val messageRes = if (lines == null) {
                    R.string.quick_panel_ocr_failed
                } else {
                    R.string.quick_panel_not_recognized
                }
                openPanel(
                    candidates = emptyList(),
                    useThumbnail = hasThumbnail,
                    message = activity.getString(messageRes),
                    isError = true,
                )
            } else {
                openPanel(candidates = candidates, useThumbnail = false, message = null)
            }
        }
    }

    /**
     * 降级面板要用的 48dp 缩略图：**独立的、缩小后的小 Bitmap**，面板关闭时回收。
     * 只在「识别到了文字但没识别出金额」时才做，所以拿到的裁片一定还没被回收。
     *
     * @return 是否成功做出缩略图
     */
    private fun makeThumbnail(crop: Bitmap): Boolean {
        if (crop.isRecycled) return false
        val scale = THUMBNAIL_PX.toFloat() / maxOf(crop.width, crop.height)
        val width = (crop.width * scale).toInt().coerceAtLeast(1)
        val height = (crop.height * scale).toInt().coerceAtLeast(1)
        val scaled = runCatching { crop.scale(width, height) }.getOrNull() ?: return false
        thumbnailBitmap = scaled
        thumbnailImage = scaled.asImageBitmap()
        return true
    }

    // ------------------------------------------------------------------ RESULT_PANEL

    private fun openPanel(
        candidates: List<AmountCandidate>,
        useThumbnail: Boolean,
        message: String?,
        isError: Boolean = false,
    ) {
        val top = candidates.firstOrNull()
        val hasCandidates = top != null

        panelState.value = QuickEntryPanelState(
            // 规范 §7.2 / §8.6 明确「默认支出」；用户点了收入类快捷按钮才会切过去
            isIncome = false,
            amountText = top?.let { AmountInputRules.normalize(Money.centsToPlain(it.cents)) }.orEmpty(),
            note = "",
            candidates = candidates,
            selectedCandidateIndex = if (hasCandidates) 0 else -1,
            quickActions = inputs.quickActions,
            categoryById = inputs.categoryById,
            selectedCategoryId = resolveCategory(Direction.Expense),
            // 没识别到金额时自动展开键盘（规范 §7.3）
            keypadExpanded = !hasCandidates,
            message = message?.let { PanelMessage(text = it, isError = isError) },
            thumbnail = if (useThumbnail) thumbnailImage else null,
            saving = false,
        )
        showPanelWindow()
    }

    private fun showPanelWindow() {
        if (panelView != null) {
            repositionPanel()
            return
        }

        val screen = screenSize()
        val params = WindowManager.LayoutParams(
            screen.x - dp(32),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // 不用 FLAG_NOT_FOCUSABLE：备注行要弹系统键盘。
            // 用 FLAG_NOT_TOUCH_MODAL ⇒ 点面板以外的区域落到下面的 App 上，且**不关闭面板**
            // （规范 §8.6：防误触丢数据，只能点关闭图标或返回键）。
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            // 锚点是框选层的视图坐标，窗口几何要的是屏幕坐标（差一个 overlayLocation）
            y = anchorOnScreenY(panelAnchorBottom) + dp(12)
            // 面板位置由我们自己算，不让系统的 IME 逻辑插手（见 movePanelToTop）
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            // 刘海区域也要能放（面板可能被拖到顶部）
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        val view = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            isFocusable = true
            isFocusableInTouchMode = true
            // 悬浮窗不在 Activity 的视图树里，BackHandler 收不到返回键，只能自己接
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    close()
                    true
                } else {
                    false
                }
            }
            setContent {
                AppTheme {
                    QuickEntryPanel(
                        state = panelState.value,
                        onDirectionChange = ::onDirectionChange,
                        onDigit = ::onDigit,
                        onBackspace = ::onBackspace,
                        onToggleKeypad = ::onToggleKeypad,
                        onCandidateSelect = ::onCandidateSelect,
                        onQuickActionSelect = ::onQuickActionSelect,
                        onNoteChange = ::onNoteChange,
                        onNoteFocusChange = { focused -> if (focused) movePanelToTop() },
                        onDragBy = ::dragPanelBy,
                        onClose = { close() },
                        onSubmit = ::submit,
                    )
                }
            }
        }
        view.alpha = 0f
        view.translationY = dpFloat(12)

        panelView = view
        panelParams = params
        panelWindowShown = true
        windowManager.addView(view, params)

        view.doOnLayout {
            repositionPanel()
            if (view.alpha == 0f) animatePanelIn(view)
        }
    }

    /** 优先放选区下方 12dp；下方不够放上方；都不够就垂直居中（规范 §8.6）。 */
    private fun repositionPanel() {
        val view = panelView ?: return
        val params = panelParams ?: return
        val height = view.height
        if (height <= 0) return

        val screenHeight = screenSize().y

        // 用户自己拖过面板：位置以他摆的为准，只保证不跑出屏幕
        if (panelUserMoved) {
            val clamped = params.y.coerceIn(0, (screenHeight - height).coerceAtLeast(0))
            if (clamped != params.y) {
                params.y = clamped
                runCatching { windowManager.updateViewLayout(view, params) }
            }
            return
        }

        val gap = dp(12)
        val below = anchorOnScreenY(panelAnchorBottom) + gap
        val above = anchorOnScreenY(panelAnchorTop) - gap - height
        val target = when {
            below + height <= screenHeight -> below
            above >= dp(8) -> above
            else -> ((screenHeight - height) / 2).coerceAtLeast(dp(8))
        }
        if (params.y != target) {
            params.y = target
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }

    /** 框选层锚点（视图坐标）→ 屏幕坐标 Y。 */
    private fun anchorOnScreenY(viewY: Int): Int = viewY + overlayLocation.y

    /**
     * 拖动面板：按住面板顶部的把手，窗口跟着手指走。
     *
     * 面板经常正好压在用户想看的金额上，不给拖的话只能先关掉再重开。
     */
    private fun dragPanelBy(dx: Float, dy: Float) {
        val view = panelView ?: return
        val params = panelParams ?: return
        panelUserMoved = true
        val screen = screenSize()
        val maxX = (screen.x - view.width).coerceAtLeast(0)
        val maxY = (screen.y - view.height).coerceAtLeast(0)
        val nextX = (params.x + dx.toInt()).coerceIn(0, maxX)
        val nextY = (params.y + dy.toInt()).coerceIn(0, maxY)
        if (nextX == params.x && nextY == params.y) return
        params.x = nextX
        params.y = nextY
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    /** 备注行拿到焦点时把面板移到顶部，避免被系统键盘盖住。 */
    private fun movePanelToTop() {
        val view = panelView ?: return
        val params = panelParams ?: return
        val target = statusBarHeight() + dp(24)
        if (params.y == target) return
        params.y = target
        windowManager.updateViewLayout(view, params)
    }

    private fun animatePanelIn(view: View) {
        panelEnterAnimator?.cancel()
        panelEnterAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = Motion.Normal.toLong()
            interpolator = PathInterpolator(0.16f, 1f, 0.3f, 1f)
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                view.alpha = progress
                view.translationY = dpFloat(12) * (1f - progress)
            }
            start()
        }
    }

    /** 面板收起 200ms，动画结束后再移除视图。 */
    private fun dismissPanel(onDismissed: () -> Unit) {
        val view = panelView
        if (view == null) {
            onDismissed()
            return
        }
        // 刻意**不**在这里把 panelView 置空：动画还要跑 200ms，
        // 期间如果会话被结束（返回键 / onDestroy），close() 必须还能把这个窗口摘掉。
        panelDismissing = true
        panelExitAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = PANEL_EXIT_MS
            interpolator = PathInterpolator(0.16f, 1f, 0.3f, 1f)
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                view.alpha = 1f - progress
                view.translationY = dpFloat(12) * progress
            }
            addListener(object : AnimatorListenerAdapter() {
                private var handled = false

                override fun onAnimationEnd(animation: Animator) {
                    if (handled) return
                    handled = true
                    panelExitAnimator = null
                    panelDismissing = false
                    if (panelView === view) {
                        panelView = null
                        panelWindowShown = false
                    }
                    removeView(view)
                    onDismissed()
                }
            })
            start()
        }
    }

    // ------------------------------------------------------------------ 面板交互

    private fun onDirectionChange(isIncome: Boolean) {
        val direction = if (isIncome) Direction.Income else Direction.Expense
        panelState.value = panelState.value.copy(
            isIncome = isIncome,
            selectedCategoryId = resolveCategory(direction),
        )
    }

    private fun onDigit(key: String) {
        panelState.value = panelState.value.copy(
            amountText = AmountInputRules.accept(panelState.value.amountText, key),
            selectedCandidateIndex = -1,
        )
    }

    private fun onBackspace() {
        panelState.value = panelState.value.copy(
            amountText = AmountInputRules.backspace(panelState.value.amountText),
            selectedCandidateIndex = -1,
        )
    }

    private fun onToggleKeypad() {
        panelState.value = panelState.value.copy(keypadExpanded = !panelState.value.keypadExpanded)
    }

    private fun onCandidateSelect(index: Int) {
        val candidate = panelState.value.candidates.getOrNull(index) ?: return
        panelState.value = panelState.value.copy(
            amountText = AmountInputRules.normalize(Money.centsToPlain(candidate.cents)),
            selectedCandidateIndex = index,
        )
    }

    /** 快捷按钮点击 = 选中该分类；**收入类按钮顺带把方向切成收入**（规范 §8.6）。 */
    private fun onQuickActionSelect(action: QuickAction) {
        panelState.value = panelState.value.copy(
            isIncome = action.direction == Direction.Income,
            selectedCategoryId = action.categoryId,
        )
        sessionScope.launch { repositories.settings.setLastUsedCategoryId(action.categoryId) }
    }

    private fun onNoteChange(value: String) {
        if (value.length > BuiltInData.NOTE_MAX_LENGTH) return
        panelState.value = panelState.value.copy(note = value)
    }

    // ------------------------------------------------------------------ SAVING

    private fun submit() {
        val state = panelState.value
        if (state.saving) return
        val cents = AmountInputRules.toCents(state.amountText)
        if (cents <= 0L || inputs.bookId <= 0L) return

        panelState.value = state.copy(saving = true, message = null)
        sessionScope.launch {
            try {
                repositories.transactions.insert(
                    TransactionDraft(
                        bookId = inputs.bookId,
                        amountCents = cents,
                        direction = if (state.isIncome) Direction.Income else Direction.Expense,
                        categoryId = state.selectedCategoryId,
                        note = state.note.trim(),
                        occurredAt = System.currentTimeMillis(),
                        source = TransactionSource.TileOcr,
                        recognizedText = recognizedTextOf(state.candidates),
                    ),
                )
                state.selectedCategoryId?.let { id ->
                    repositories.settings.setLastUsedCategoryId(id)
                }
                onSaved(cents)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // 写入失败：保留输入内容，只在面板里给一行提示（规范 §11）
                panelState.value = panelState.value.copy(
                    saving = false,
                    message = PanelMessage(activity.getString(R.string.quick_save_failed), isError = true),
                )
            }
        }
    }

    /** OCR 原始文本行，只存文本不存图像（`transactions.recognizedText`）。 */
    private fun recognizedTextOf(candidates: List<AmountCandidate>): String? =
        candidates.takeIf { it.isNotEmpty() }?.joinToString(separator = " ") { it.raw }

    private fun onSaved(cents: Long) {
        saved = true
        panelView?.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        // 不弹系统 Toast（会在别人的 App 上留痕），反馈走磁贴 subtitle
        QuickEntryTileBus.showSaved(activity, cents)
        dismissPanel { close() }
    }

    // ------------------------------------------------------------------ 引导页

    private fun showOverlayGuide() {
        showGuide(
            titleRes = R.string.quick_guide_overlay_title,
            bodyRes = R.string.quick_guide_overlay_body,
            primaryLabelRes = R.string.quick_guide_overlay_action,
            onPrimary = {
                startSystemActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        "package:${activity.packageName}".toUri(),
                    ),
                )
                close()
            },
        )
    }

    private fun showA11yGuide() {
        val killed = QuickEntryState.checkStatus(activity) == A11yStatus.KILLED_BY_SYSTEM
        showGuide(
            titleRes = if (killed) R.string.quick_guide_killed_title else R.string.quick_guide_a11y_title,
            bodyRes = if (killed) R.string.quick_guide_killed_body else R.string.quick_guide_a11y_body,
            primaryLabelRes = if (killed) {
                R.string.quick_guide_killed_action
            } else {
                R.string.quick_guide_a11y_action
            },
            onPrimary = {
                startSystemActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                close()
            },
            // 规范 §8.8：无障碍不可用时自动降级到路径 B，保证功能可用
            secondaryLabelRes = R.string.quick_guide_a11y_fallback,
            onSecondary = {
                removeGuide()
                onRequestPickImage()
            },
        )
    }

    private fun onServiceLost() {
        ScreenCapturer.resetThrottle()
        removeView(selectionView)
        selectionView = null
        showA11yGuide()
    }

    /** 一次性提示（例如「从图片记一笔」时那张图读不出来）。只有一个「关闭」按钮。 */
    fun showNotice(titleRes: Int, bodyRes: Int) {
        if (closed) return
        showGuide(
            titleRes = titleRes,
            bodyRes = bodyRes,
            primaryLabelRes = R.string.quick_guide_close,
            onPrimary = { close() },
        )
    }

    private fun showGuide(
        titleRes: Int,
        bodyRes: Int,
        primaryLabelRes: Int,
        onPrimary: () -> Unit,
        secondaryLabelRes: Int? = null,
        onSecondary: (() -> Unit)? = null,
    ) {
        if (guideView != null) return
        val view = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setContent {
                AppTheme {
                    QuickEntryGuide(
                        title = activity.getString(titleRes),
                        body = activity.getString(bodyRes),
                        primaryLabel = activity.getString(primaryLabelRes),
                        onPrimary = onPrimary,
                        onDismiss = { close() },
                        secondaryLabel = secondaryLabelRes?.let { activity.getString(it) },
                        onSecondary = onSecondary,
                    )
                }
            }
        }
        guideView = view
        if (Settings.canDrawOverlays(activity)) {
            windowManager.addView(view, fullScreenParams(focusable = true))
        } else {
            // 这里就是「没有悬浮窗权限」的场景，绝不能再加悬浮窗类型。
            // 改挂 Activity 自己的 content view：Activity 本来就是透明空壳，视觉一致，
            // 而且不需要任何权限。没有这条兜底，首次点磁贴 = 必崩。
            guideInActivity = true
            activity.setContentView(view)
        }
    }

    /** 引导页可能在 WindowManager 上，也可能挂在 Activity 上，两种都要能摘掉。 */
    private fun removeGuide() {
        val view = guideView ?: return
        guideView = null
        if (guideInActivity) {
            guideInActivity = false
            (view.parent as? ViewGroup)?.removeView(view)
        } else {
            removeView(view)
        }
    }

    private fun startSystemActivity(intent: Intent) {
        runCatching { activity.startActivity(intent) }
    }

    // ------------------------------------------------------------------ 节流提示

    private fun showThrottledHint(overlay: SelectionOverlayView) {
        // 面板已经弹出来时不要再把框选层拉回可见：它是全屏遮罩，会盖住面板，
        // 而且点面板外会走 onCancel 直接 close() 丢掉正在编辑的数据。
        if (panelWindowShown) return
        overlay.visibility = View.VISIBLE
        overlay.showHint(SelectionOverlayView.Hint.TooFast)
        val delayMs = ScreenCapturer.throttleRemainingMs().coerceAtLeast(MIN_THROTTLE_HINT_MS)
        overlay.postDelayed(
            {
                if (!closed) overlay.showHint(SelectionOverlayView.Hint.Idle)
            },
            delayMs,
        )
    }

    // ------------------------------------------------------------------ 输入流

    private data class CategoryPart(
        val active: List<Category>,
        val byId: Map<Long, Category>,
    )

    private data class SettingsPart(
        val lastCategoryId: Long,
        val preferCurrencySymbol: Boolean,
    )

    private fun observeInputs() {
        collectJob = activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    repositories.books.observeCurrent(),
                    combine(
                        repositories.categories.observeActive(),
                        repositories.categories.observeAll(),
                    ) { active, all -> CategoryPart(active, all.associateBy { it.id }) },
                    repositories.quickActions.observeActive(),
                    combine(
                        repositories.settings.lastUsedCategoryId,
                        repositories.settings.preferCurrencySymbol,
                    ) { last, prefer -> SettingsPart(last, prefer) },
                ) { book, categoryPart, actions, settingsPart ->
                    SessionInputs(
                        bookId = book.id,
                        categories = categoryPart.active,
                        categoryById = categoryPart.byId,
                        quickActions = actions,
                        lastCategoryId = settingsPart.lastCategoryId,
                        preferCurrencySymbol = settingsPart.preferCurrencySymbol,
                    )
                }.collectLatest { value ->
                    inputs = value
                    if (panelWindowShown && panelView != null) {
                        panelState.value = panelState.value.copy(
                            quickActions = value.quickActions,
                            categoryById = value.categoryById,
                        )
                    }
                }
            }
        }
    }

    /** 当前方向下的默认选中分类：上次用过的（仍在该方向可用分类里）→ 该方向第一个。 */
    private fun resolveCategory(direction: Int): Long? {
        val candidates = inputs.categories.filter { CategoryScope.matches(it.direction, direction) }
        val last = inputs.categoryById[inputs.lastCategoryId]
        if (last != null && candidates.any { it.id == last.id }) return last.id
        return candidates.firstOrNull()?.id
    }

    // ------------------------------------------------------------------ 生命周期

    /** 整条会话销毁：移除全部窗口 + 回收全部 Bitmap。任何退出路径都必须走到这里。 */
    fun close() {
        if (closed) return
        closed = true
        workJob?.cancel()
        collectJob?.cancel()
        // 退场动画可能正在跑：先取消，再把窗口摘掉（否则动画结束后窗口才被移除，
        // 期间面板还挂在屏幕上，而下面 recycleAll() 已经把它的缩略图回收了）
        panelEnterAnimator?.cancel()
        panelEnterAnimator = null
        panelExitAnimator?.cancel()
        panelExitAnimator = null
        panelDismissing = false
        removeView(panelView)
        panelView = null
        panelWindowShown = false
        // 没记成功就结束（识别失败 / 用户关掉面板）：磁贴不能一直停在「识别中…」。
        // 记成功了则保留 showSaved() 写下的「已记 ¥xx」，由它自己的 1.5s 计时复位。
        if (!saved) QuickEntryTileBus.showIdle(activity)
        removeGuide()
        removeView(selectionView)
        selectionView = null
        removeView(imageView)
        imageView = null
        recycleAll()
        sessionScope.cancel()
        if (!activity.isFinishing) activity.finish()
    }

    private fun recycleLater(bitmap: Bitmap) = DeferredBitmapRecycler.recycleLater(bitmap)

    private fun recycleAll() {
        // 在途裁片可能还被 ML Kit 的原生任务读着（协程取消停不下它），
        // 必须走进程级延后回收 —— 这里立刻 recycle() 就是 native 崩溃。
        inFlightCrop?.let(DeferredBitmapRecycler::recycleLater)
        inFlightCrop = null
        // 下面这些只服务 UI（缩略图）或由自己解码（静态图原图），窗口已经摘掉，可以直接回收
        thumbnailBitmap?.let { if (!it.isRecycled) it.recycle() }
        thumbnailBitmap = null
        thumbnailImage = null
        staticSource?.let { if (!it.isRecycled) it.recycle() }
        staticSource = null
    }

    // ------------------------------------------------------------------ 工具

    private fun fullScreenParams(focusable: Boolean = false): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        if (!focusable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        val display = screenSize()
        return WindowManager.LayoutParams(
            display.x,
            display.y,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // 必须显式给尺寸 + TOP|START 对齐屏幕原点。只写 FLAG_LAYOUT_IN_SCREEN 时，
            // 系统仍会把悬浮窗压在状态栏与导航栏之间（验收截图 12 里状态栏那一条没有被
            // 遮罩盖住就是证据）。窗口被压进去的后果不只是遮罩少一圈：框选层的坐标原点
            // 随之下移，而截图位图是从屏幕 (0,0) 开始的，裁剪就会整体上移一条状态栏。
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            // 刘海 / 挖孔区域也要能盖住、能框到
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    /**
     * 屏幕真实尺寸（含状态栏与导航栏）。
     *
     * **不能用 currentWindowMetrics.bounds**：那是「本窗口」的尺寸，QuickEntryActivity 没有
     * edge-to-edge，拿到的就是被系统栏压过的区域，会把这个 bug 原样复制一遍。
     */
    private fun screenSize(): Point {
        @Suppress("DEPRECATION")
        val real = Point().also { windowManager.defaultDisplay.getRealSize(it) }
        if (real.x > 0 && real.y > 0) return real
        val bounds = windowManager.currentWindowMetrics.bounds
        return Point(bounds.width(), bounds.height())
    }

    /** 截图位图覆盖的区域：整块屏幕，起点就是 (0,0)。 */
    private fun screenRect(source: Bitmap): Rect = Rect(0, 0, source.width, source.height)

    /** 选区从「框选层视图坐标」换算到「屏幕坐标」。 */
    private fun selectionInScreen(): Rect = Rect(
        selectionRect.left + overlayLocation.x,
        selectionRect.top + overlayLocation.y,
        selectionRect.right + overlayLocation.x,
        selectionRect.bottom + overlayLocation.y,
    )

    /** 视图在屏幕上的位置；拿不到时退回 (0,0)。 */
    private fun viewLocationOnScreen(view: View): Point {
        val location = IntArray(2)
        runCatching { view.getLocationOnScreen(location) }
        return Point(location[0], location[1])
    }

    private fun statusBarHeight(): Int =
        windowManager.currentWindowMetrics.windowInsets
            .getInsets(WindowInsets.Type.statusBars())
            .top

    private fun removeView(view: View?) {
        if (view == null) return
        runCatching { windowManager.removeViewImmediate(view) }
    }

    private fun dp(value: Int): Int = (value * density + 0.5f).toInt()

    private fun dpFloat(value: Int): Float = value * density

    /** 一次会话的全部外部输入，来自 core 的 Repository。 */
    private data class SessionInputs(
        val bookId: Long = 0L,
        val categories: List<Category> = emptyList(),
        val categoryById: Map<Long, Category> = emptyMap(),
        val quickActions: List<QuickAction> = emptyList(),
        val lastCategoryId: Long = 0L,
        val preferCurrencySymbol: Boolean = true,
    )

    private companion object {
        // 识别整段超时不再放在这里：热态 3000ms / 冷态 8000ms 由 OcrClient 自己给
        // （见 MlKitOcrClient.suggestedTimeoutMs），会话只负责取用。

        /** 面板收起动画（规范 §8.6）。 */
        const val PANEL_EXIT_MS = 200L

        /** 缩略图边长：48dp × 3 倍密度，够看清又不占内存。 */
        const val THUMBNAIL_PX = 144

        /** 节流提示至少显示这么久，避免一闪而过。 */
        const val MIN_THROTTLE_HINT_MS = 600L

    }
}
