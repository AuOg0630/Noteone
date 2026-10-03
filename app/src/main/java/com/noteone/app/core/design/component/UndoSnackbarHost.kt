// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.design.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.noteone.app.R
import com.noteone.app.core.design.AppTheme
import com.noteone.app.core.design.AppType
import com.noteone.app.core.design.Dimension
import com.noteone.app.core.design.Motion
import com.noteone.app.core.design.Radius
import com.noteone.app.core.design.Space
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Snackbar 底色：`#111111` 90% 不透明。 */
private val SnackbarBackground = Color(0xE6111111)

/**
 * 撤销 Snackbar 宿主（规范 §3.7 / §5.1）。
 *
 * 深色条（`#111111` 90%）+ 13sp 白字 + 右侧「撤销」文字按钮 13sp Medium。
 * 进入 `translateY(16dp) → 0` 200ms，退出淡出 200ms。
 *
 * 位置由调用方决定：记账页放在页面**顶部**（规范 §5.1），账单页删除后同样放在顶部。
 *
 * 用法：
 * ```
 * val snackbarHostState = remember { SnackbarHostState() }
 * // 页面顶部
 * UndoSnackbarHost(state = snackbarHostState)
 * // 记账成功后
 * scope.launch { snackbarHostState.showUndoSnackbar("已记 ¥14.00 · 餐饮") { repo.softDelete(id) } }
 * ```
 */
@Composable
fun UndoSnackbarHost(
    state: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val current = state.currentSnackbarData
    // 退出动画期间仍需要内容，所以记住最后一次的数据
    var lastData by remember { mutableStateOf<SnackbarData?>(null) }
    if (current != null) lastData = current

    val visible = current != null
    val progress = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        progress.animateTo(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(durationMillis = Motion.Normal, easing = Motion.Ease),
        )
    }

    val data = lastData ?: return
    val value = progress.value
    if (value <= 0.001f) return

    val offsetPx = with(LocalDensity.current) { Motion.SnackbarEnterOffset.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimension.PageHPadding)
            .graphicsLayer {
                alpha = value
                translationY = offsetPx * (1f - value)
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.Button))
                .background(SnackbarBackground)
                .padding(horizontal = Space.M, vertical = Space.M),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = data.visuals.message,
                style = AppType.Caption,
                color = Color.White,
            )
            val actionLabel = data.visuals.actionLabel ?: stringResource(R.string.common_undo)
            Box(
                modifier = Modifier
                    // 13sp 文字 + 8dp 上下内边距只有约 35dp，低于规范 §13 的 48dp 可点区下限
                    .minimumInteractiveComponentSize()
                    .clickable { data.performAction() }
                    .padding(start = Space.L, top = Space.S, bottom = Space.S),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = actionLabel,
                    style = AppType.Caption.copy(fontWeight = FontWeight.Medium),
                    color = Color.White,
                )
            }
        }
    }
}

/**
 * 弹出一条「可撤销」提示。
 *
 * 与 `SnackbarHostState.showSnackbar()` 的区别：**停留 2.5 秒后自动消失**
 * （规范 §5.1 要求的撤销窗口），并且**重复调用只重置计时**——连点「完成」时
 * 不会出现两条叠在一起，也不会因为排队而看不到最新一条。
 *
 * @param holdMillis 撤销窗口，默认 2500ms
 * @param actionLabel 按钮文字；传 `null`（默认）时 [UndoSnackbarHost] 会渲染
 *                    `common_undo`（「撤销」），所以调用方通常不用自己传
 * @param onUndo 用户点了「撤销」时回调
 * @return `true` 表示用户点了撤销
 */
suspend fun SnackbarHostState.showUndoSnackbar(
    message: String,
    holdMillis: Long = Motion.SnackbarDurationMs,
    actionLabel: String? = null,
    onUndo: () -> Unit,
): Boolean = coroutineScope {
    val autoDismiss = launch {
        delay(holdMillis)
        currentSnackbarData?.dismiss()
    }
    try {
        val result = showSnackbar(
            message = message,
            actionLabel = actionLabel,
            // Indefinite 的 snackbar 必须有「动作」或「关闭」之一，否则 M3 会抛异常。
            // 这里用 null 的 actionLabel 配合 withDismissAction，UI 上的按钮仍由
            // UndoSnackbarHost 自己画，withDismissAction 只用来满足这个前置条件。
            withDismissAction = actionLabel == null,
            duration = SnackbarDuration.Indefinite,
        )
        if (result == SnackbarResult.ActionPerformed) {
            onUndo()
            true
        } else {
            false
        }
    } finally {
        autoDismiss.cancel()
    }
}

@Preview(name = "UndoSnackbarHost / 已记一笔", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun UndoSnackbarPreview() {
    AppTheme {
        val state = remember { SnackbarHostState() }
        LaunchedEffect(Unit) {
            state.showSnackbar("已记 ¥14.00 · 餐饮", actionLabel = "撤销")
        }
        Box(modifier = Modifier.fillMaxWidth().padding(Space.L)) {
            UndoSnackbarHost(state = state)
        }
    }
}
