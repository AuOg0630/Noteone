// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.feature.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import com.noteone.app.core.design.AppColor
import com.noteone.app.core.design.Dimension
import kotlin.math.roundToInt

/** 尾部追加内容（分类页的「已归档」区）在 `LazyColumn` 里的 key。 */
private const val TRAILING_KEY = "reorderable_trailing"

/**
 * 可长按拖拽排序的列表（规范要求「不要引入第三方拖拽库」）。
 *
 * 交互：按住行尾的**拖拽手柄**（[row] 的 `handle` 参数必须挂到手柄视图上，
 * 长按手柄才会进入拖拽）→ 上下移动 → 松手落位。
 *
 * 做法说明（为什么这么写）：
 * - 行高固定 [rowHeight]，所以「拖动位移 ÷ 行高」四舍五入就是跨越了几格，
 *   目标下标 = 起始下标 + 格数，**不需要测量每个 item 的位置**，逻辑完全可预测。
 * - 拖拽中：被拖的行用 `graphicsLayer.translationY` 跟手 + 底色变 `CanvasWarm`
 *   （规范 §3.7 的列表按下反馈色），并 `zIndex` 提到最上层。
 * - 松手后才真正写库（`onMove`），列表拿到新顺序由 `Modifier.animateItem()`
 *   做落位动画——全程只动 `transform`，不违反「禁止动画 width/height」。
 * - 因此**不显示实时让位预览**，也不做拖拽中的自动滚动：三个管理页的列表长度
 *   上限分别是 10 / 13 / 8 行，一屏放得下，自动滚动是纯负担。
 *
 * @param keyOf 稳定且唯一的 key，同时用于 `LazyColumn` 的 item key 与拖拽判定
 * @param onMove 顺序变化回调，只在松手且下标确实变了时触发
 * @param trailingContent 追加在列表末尾的区块（不参与拖拽排序）
 */
@Composable
fun <T : Any> ReorderableList(
    items: List<T>,
    keyOf: (T) -> Long,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    rowHeight: Dp = Dimension.ListRow,
    trailingContent: (@Composable () -> Unit)? = null,
    row: @Composable (index: Int, item: T, isDragging: Boolean, handle: Modifier) -> Unit,
) {
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }

    var draggingKey by remember { mutableStateOf<Long?>(null) }
    var startIndex by remember { mutableIntStateOf(-1) }
    var targetIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    val resetDrag: () -> Unit = {
        draggingKey = null
        startIndex = -1
        targetIndex = -1
        dragOffset = 0f
    }

    LazyColumn(modifier = modifier) {
        itemsIndexed(items, key = { _, item -> keyOf(item) }) { index, item ->
            val isDragging = draggingKey == keyOf(item)
            val handle: Modifier = if (!enabled) {
                Modifier
            } else {
                Modifier.pointerInput(keyOf(item), index, items.size) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            draggingKey = keyOf(item)
                            startIndex = index
                            targetIndex = index
                            dragOffset = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffset += dragAmount.y
                            val steps = (dragOffset / rowHeightPx).roundToInt()
                            targetIndex = (startIndex + steps).coerceIn(0, items.lastIndex)
                        },
                        onDragEnd = {
                            val from = startIndex
                            val to = targetIndex
                            resetDrag()
                            if (from in items.indices && to in items.indices && from != to) {
                                onMove(from, to)
                            }
                        },
                        onDragCancel = { resetDrag() },
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .animateItem()
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
                    .background(if (isDragging) AppColor.CanvasWarm else AppColor.Canvas),
            ) {
                row(index, item, isDragging, handle)
            }
        }

        if (trailingContent != null) {
            item(key = TRAILING_KEY) { trailingContent() }
        }
    }
}
