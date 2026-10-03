// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.design.component

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noteone.app.R
import com.noteone.app.core.design.AppColor
import com.noteone.app.core.design.AppTheme
import com.noteone.app.core.design.AppType
import com.noteone.app.core.design.Motion
import com.noteone.app.core.design.Dimension
import com.noteone.app.core.design.Radius
import com.noteone.app.core.design.Space
import com.noteone.app.core.design.icon.AppIcons
import kotlinx.coroutines.delay

/** 按键字号 24sp Regular（规范 §6.1）。只属于键盘，不进 [AppType]。 */
private val KeyDigitStyle = TextStyle(
    fontSize = 24.sp,
    fontWeight = FontWeight.Normal,
    lineHeight = 28.sp,
)

/** 长按后启动连续删除的等待时间（规范 §6.1）。 */
private const val BACKSPACE_LONG_PRESS_DELAY_MS = 200L

/** 连续删除的触发间隔。 */
private const val BACKSPACE_REPEAT_INTERVAL_MS = 80L

/** 键盘键值常量，供调用方按需引用。 */
object AmountKeypadKeys {
    const val Dot = "."
    const val DoubleZero = "00"
}

/**
 * 自绘九宫格数字键盘。**首页记账卡片与悬浮识别面板共用，不要各写一套。**
 *
 * 规格：`docs/开发规范.md` §6.1。4 列网格、行高 56dp、1dp `#EAEAEA` 网格线、
 * 数字键 24sp `#111111`、按下态底色 `#EAEAEA`、「完成」键 `#111111` 底白字 6dp 圆角高 48dp。
 *
 * 组件**自身不持有金额状态**，只往外抛事件；状态由调用方持有，
 * 输入变换请调用 `AmountInputRules.accept()`。
 *
 * @param onDigit 数字键回调，取值 `"0"`–`"9"`、`"00"`、`"."`
 * @param onBackspace 退格；长按会以 80ms 间隔连续回调
 * @param onDone 点「完成」
 * @param doneEnabled 为 false 时「完成」变禁用态（`#EAEAEA` 底 + `#A8A6A1` 字）且不可点
 */
@Composable
fun AmountKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onDone: () -> Unit,
    doneEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AppColor.CanvasWarm)
            .border(Dimension.Divider, AppColor.Line),
    ) {
        Row(modifier = Modifier.height(Dimension.KeypadRow)) {
            DigitKey("1", Modifier.weight(1f), onDigit)
            GridVLine()
            DigitKey("2", Modifier.weight(1f), onDigit)
            GridVLine()
            DigitKey("3", Modifier.weight(1f), onDigit)
            GridVLine()
            BackspaceKey(Modifier.weight(1f), onBackspace)
        }

        GridHLine()

        Row(modifier = Modifier.height(Dimension.KeypadRow * 3 + Dimension.Divider * 2)) {
            Column(modifier = Modifier.weight(3f)) {
                Row(modifier = Modifier.weight(1f)) {
                    DigitKey("4", Modifier.weight(1f), onDigit)
                    GridVLine()
                    DigitKey("5", Modifier.weight(1f), onDigit)
                    GridVLine()
                    DigitKey("6", Modifier.weight(1f), onDigit)
                }
                GridHLine()
                Row(modifier = Modifier.weight(1f)) {
                    DigitKey("7", Modifier.weight(1f), onDigit)
                    GridVLine()
                    DigitKey("8", Modifier.weight(1f), onDigit)
                    GridVLine()
                    DigitKey("9", Modifier.weight(1f), onDigit)
                }
                GridHLine()
                Row(modifier = Modifier.weight(1f)) {
                    DigitKey(AmountKeypadKeys.Dot, Modifier.weight(1f), onDigit)
                    GridVLine()
                    DigitKey("0", Modifier.weight(1f), onDigit)
                    GridVLine()
                    DigitKey("00", Modifier.weight(1f), onDigit)
                }
            }

            GridVLine()

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                DoneKey(enabled = doneEnabled, onDone = onDone)
            }
        }
    }
}

@Composable
private fun DigitKey(
    label: String,
    modifier: Modifier = Modifier,
    onDigit: (String) -> Unit,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(if (pressed) AppColor.Line else AppColor.Canvas)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onDigit(label)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = KeyDigitStyle, color = AppColor.Ink)
    }
}

@Composable
private fun BackspaceKey(
    modifier: Modifier = Modifier,
    onBackspace: () -> Unit,
) {
    val view = LocalView.current
    var pressed by remember { mutableStateOf(false) }

    // 长按连续删除：按下 200ms 后启动，之后每 80ms 一次
    LaunchedEffect(pressed) {
        if (!pressed) return@LaunchedEffect
        delay(BACKSPACE_LONG_PRESS_DELAY_MS)
        while (true) {
            onBackspace()
            delay(BACKSPACE_REPEAT_INTERVAL_MS)
        }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(if (pressed) AppColor.Line else AppColor.Canvas)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        pressed = true
                        // 按下即删一次；继续按住则由上面的循环接管
                        onBackspace()
                        tryAwaitRelease()
                        pressed = false
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = AppIcons.Backspace),
            contentDescription = stringResource(R.string.common_backspace),
            tint = AppColor.Ink,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun DoneKey(
    enabled: Boolean,
    onDone: () -> Unit,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // 按下态用 scale(0.98)：禁用态本身就是灰底，用底色变化容易与之混淆
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.98f else 1f,
        // 规范 §3.7：按下反馈 150ms + 全站统一曲线。漏了 animationSpec 会走默认 spring()
        animationSpec = tween(durationMillis = Motion.Fast, easing = Motion.Ease),
        label = "doneScale",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.S)
            .height(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(Radius.Button))
            .background(if (enabled) AppColor.Ink else AppColor.Line)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onDone()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.common_done),
            style = AppType.Button,
            color = if (enabled) AppColor.Canvas else AppColor.Faint,
        )
    }
}

@Composable
private fun GridVLine() {
    Box(
        modifier = Modifier
            .width(Dimension.Divider)
            .fillMaxHeight()
            .background(AppColor.Line),
    )
}

@Composable
private fun GridHLine() {
    Box(
        modifier = Modifier
            .height(Dimension.Divider)
            .fillMaxWidth()
            .background(AppColor.Line),
    )
}

@Preview(name = "AmountKeypad / 可提交", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun AmountKeypadPreview() {
    AppTheme {
        AmountKeypad(
            onDigit = {},
            onBackspace = {},
            onDone = {},
            doneEnabled = true,
        )
    }
}

@Preview(name = "AmountKeypad / 禁用完成", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun AmountKeypadDisabledPreview() {
    AppTheme {
        AmountKeypad(
            onDigit = {},
            onBackspace = {},
            onDone = {},
            doneEnabled = false,
        )
    }
}
