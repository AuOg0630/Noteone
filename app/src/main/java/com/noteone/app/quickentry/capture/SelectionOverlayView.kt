// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.quickentry.capture

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import androidx.annotation.StringRes
import com.noteone.app.R

/**
 * 框选悬浮层（规范 §8.4；v1.4 改交互）。
 *
 * ## 交互
 *
 * **手指按下处 = 一个顶点，抬起处 = 对角顶点**，两点撑出选区，松手立即识别。
 * 不再是「先给一个框、用户再去拖它」——那种做法要先对准框、再拖到目标，两步都别扭；
 * 现在是一笔划完，和「小爱识屏」的手感一致。
 *
 * - 手指几乎没动 → 视为点按 → 取消整条会话（保留旧行为，给用户一条退路；系统返回键同样可取消）；
 * - 拖出来了但小于最小尺寸 → 只提示「范围太小」，不清空会话，让用户重新框。
 *
 * ## 其它要点
 *
 * 1. **框选发生在截图之前**，所以这一层的底下就是真实屏幕画面——选区天然透出，不做图像处理。
 * 2. 遮罩用 `Path.FillType.EVEN_ODD` 画「全屏矩形 − 选区矩形」的差集。
 *    **不能用 `PorterDuff.Mode.CLEAR`**：那会连同悬浮层自身一起挖穿。
 * 3. 用系统 `View` + `Canvas` 而不是 Compose：它是加在 WindowManager 上的独立悬浮窗
 *    （不是页面），自绘最省事，也不会被 Composition 的生命周期牵着走。
 *
 * ## 坐标约定
 *
 * 本视图给出的是**视图坐标**。窗口被系统压在状态栏/导航栏之间时，视图坐标 ≠ 屏幕坐标，
 * 而截图位图是从屏幕 (0,0) 开始的。会话层用 `getLocationOnScreen` 做换算
 * （见 `QuickEntrySession.overlayLocation`），这里只管自己坐标系内的框选。
 */
@SuppressLint("ViewConstructor")
class SelectionOverlayView(context: Context) : View(context) {

    companion object {
        /** 选区最小尺寸（规范 §8.4）。 */
        private const val MIN_WIDTH_DP = 80f
        private const val MIN_HEIGHT_DP = 40f

        /** 四角标记视觉尺寸。 */
        private const val HANDLE_SIZE_DP = 12f

        /** 提示药丸高度与左右内边距。 */
        private const val PILL_HEIGHT_DP = 24f
        private const val PILL_PADDING_DP = 16f

        /** 提示药丸文字 11sp（规范 §8.4）。 */
        private const val PILL_TEXT_SP = 11f
    }

    /** 提示药丸的文案。 */
    enum class Hint(@StringRes val textRes: Int) {
        /** 还没动过：告诉用户怎么框。 */
        Idle(R.string.quick_hint_press_to_select),

        /** 框得太小，没达到最小尺寸。 */
        TooSmall(R.string.quick_hint_too_small),

        /** 正在识别。 */
        Recognizing(R.string.quick_hint_recognizing),

        /** 距上次识别不足 1 秒，被节流。 */
        TooFast(R.string.quick_hint_too_fast),
    }

    /** 松手且选区足够大时回调，参数是**视图坐标系**下的选区。 */
    var onSettle: ((Rect) -> Unit)? = null

    /** 点按（手指基本没动）应该取消整条会话。 */
    var onCancel: (() -> Unit)? = null

    private val density = resources.displayMetrics.density

    private val minWidthPx = MIN_WIDTH_DP * density
    private val minHeightPx = MIN_HEIGHT_DP * density
    private val handleSizePx = HANDLE_SIZE_DP * density
    private val pillHeightPx = PILL_HEIGHT_DP * density
    private val pillPaddingPx = PILL_PADDING_DP * density
    private val gapPx = 12f * density
    private val touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    private val selection = RectF()

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x99000000.toInt() // #000000 @ 60%
    }
    private val maskPath = Path()

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = 0xFFFFFFFF.toInt()
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFFFFFF.toInt()
    }

    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xE6FFFFFF.toInt() // #FFFFFF @ 90%
    }

    private val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF111111.toInt()
        // 11sp：`scaledDensity` 已废弃，用 applyDimension 换算才带上用户的字体缩放
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            PILL_TEXT_SP,
            resources.displayMetrics,
        )
        typeface = Typeface.DEFAULT
        textAlign = Paint.Align.CENTER
    }

    private var hint = Hint.Idle
    private var dragging = false
    private var anchorX = 0f
    private var anchorY = 0f

    /**
     * 状态栏 / 挖孔摄像头的安全上边距（px）。
     *
     * 遮罩本身要盖满全屏（包括状态栏那一条），但**提示药丸不能被摄像头挡住**。
     * 由 [onApplyWindowInsets] 刷新，拿不到时保持 0（此时贴底显示，同样不会碰到摄像头）。
     */
    private var safeTopInsetPx = 0f

    /** 当前选区（拷贝，视图坐标），供截图裁剪使用。 */
    fun currentSelection(): Rect = Rect(
        selection.left.toInt().coerceAtLeast(0),
        selection.top.toInt().coerceAtLeast(0),
        selection.right.toInt().coerceAtMost(width),
        selection.bottom.toInt().coerceAtMost(height),
    )

    fun showHint(value: Hint) {
        if (hint == value) return
        hint = value
        invalidate()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val safe = insets.getInsets(
            WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout(),
        )
        if (safe.top.toFloat() != safeTopInsetPx) {
            safeTopInsetPx = safe.top.toFloat()
            invalidate()
        }
        return insets
    }

    // ------------------------------------------------------------------ 绘制

    override fun onDraw(canvas: Canvas) {
        // 差集：外框 CW + 内框 CCW，EVEN_ODD 下相减。
        // 还没框时 selection 是空矩形，差集就是整块屏幕 —— 正是「蒙一层半透明黑」。
        maskPath.reset()
        maskPath.fillType = Path.FillType.EVEN_ODD
        maskPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        maskPath.addRect(selection, Path.Direction.CCW)
        canvas.drawPath(maskPath, scrimPaint)

        if (selection.width() > 0f && selection.height() > 0f) {
            canvas.drawRect(selection, borderPaint)
            val radius = 2f * density
            forEachCorner { x, y ->
                canvas.drawRoundRect(
                    x - handleSizePx / 2f,
                    y - handleSizePx / 2f,
                    x + handleSizePx / 2f,
                    y + handleSizePx / 2f,
                    radius,
                    radius,
                    handlePaint,
                )
            }
        }

        // 拖动中不画药丸：它正好压在用户正在框的内容上
        if (!dragging) drawHintPill(canvas)
    }

    private inline fun forEachCorner(action: (Float, Float) -> Unit) {
        action(selection.left, selection.top)
        action(selection.right, selection.top)
        action(selection.left, selection.bottom)
        action(selection.right, selection.bottom)
    }

    private fun drawHintPill(canvas: Canvas) {
        val text = context.getString(hint.textRes)
        val textWidth = pillTextPaint.measureText(text)
        val pillWidth = textWidth + pillPaddingPx * 2f
        val radius = pillHeightPx / 2f

        // 药丸的可用纵向范围：上不压状态栏/摄像头，下不压手势条
        val minTop = safeTopInsetPx + gapPx
        val maxTop = (height - gapPx - pillHeightPx).coerceAtLeast(minTop)

        val top = if (selection.height() <= 0f) {
            // 还没开始框选：**贴底居中**。
            // 不能贴在顶上——遮罩是盖满全屏的（含状态栏与挖孔），贴顶就会和摄像头叠在一起。
            maxTop
        } else {
            val below = selection.bottom + gapPx
            val above = selection.top - gapPx - pillHeightPx
            when {
                below + pillHeightPx <= height - gapPx -> below
                above >= minTop -> above
                else -> maxTop
            }.coerceIn(minTop, maxTop)
        }

        var centerX = if (selection.width() > 0f) selection.centerX() else width / 2f
        centerX = centerX.coerceIn(pillWidth / 2f, width - pillWidth / 2f)

        canvas.drawRoundRect(
            centerX - pillWidth / 2f,
            top,
            centerX + pillWidth / 2f,
            top + pillHeightPx,
            radius,
            radius,
            pillPaint,
        )
        // 垂直居中：基线 = 中心 + 半字高 - descent 修正，用 fontMetrics 算更准
        val metrics = pillTextPaint.fontMetrics
        val baseline = top + pillHeightPx / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(text, centerX, baseline, pillTextPaint)
    }

    // ------------------------------------------------------------------ 交互

    /**
     * 一笔框选：按下是起点，移动时以起点与当前点为对角撑出矩形，抬起即完成。
     *
     * 全程只在自己的坐标系里算，越界一律 clamp 到视图内。
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount > 1) return true

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                anchorX = event.x.coerceIn(0f, width.toFloat())
                anchorY = event.y.coerceIn(0f, height.toFloat())
                selection.set(anchorX, anchorY, anchorX, anchorY)
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return true
                val x = event.x.coerceIn(0f, width.toFloat())
                val y = event.y.coerceIn(0f, height.toFloat())
                selection.set(
                    minOf(anchorX, x),
                    minOf(anchorY, y),
                    maxOf(anchorX, x),
                    maxOf(anchorY, y),
                )
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!dragging) return true
                dragging = false
                when {
                    isBigEnough() -> {
                        invalidate()
                        onSettle?.invoke(currentSelection())
                    }

                    isTap() -> {
                        // 手指基本没动 = 点按 → 取消（走 performClick，无障碍服务也能触发同一个动作）
                        clearSelection()
                        performClick()
                    }

                    else -> {
                        // 拖出来了但太小：不清空会话，提示后让用户重新框
                        clearSelection()
                        showHint(Hint.TooSmall)
                    }
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                clearSelection()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun isBigEnough(): Boolean =
        selection.width() >= minWidthPx && selection.height() >= minHeightPx

    /** 位移小于系统触摸阈值的算点按。 */
    private fun isTap(): Boolean =
        selection.width() < touchSlopPx && selection.height() < touchSlopPx

    private fun clearSelection() {
        selection.setEmpty()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        dragging = false
        onSettle = null
        onCancel = null
        super.onDetachedFromWindow()
    }

    /**
     * 本视图唯一的"点击"语义就是点按屏幕 → 取消整条会话。
     *
     * 手指点按走 [onTouchEvent] 的 `ACTION_UP` 分支调到它；无障碍服务（TalkBack）
     * 通过 `performClick` 触发时落到同一个动作，两条路径行为一致。
     */
    override fun performClick(): Boolean {
        super.performClick()
        onCancel?.invoke()
        return true
    }
}
