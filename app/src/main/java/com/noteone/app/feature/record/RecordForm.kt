// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.feature.record

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.noteone.app.R
import com.noteone.app.core.data.model.Category
import com.noteone.app.core.data.model.CategoryScope
import com.noteone.app.core.design.AppColor
import com.noteone.app.core.design.AppTheme
import com.noteone.app.core.design.AppType
import com.noteone.app.core.design.Dimension
import com.noteone.app.core.design.Motion
import com.noteone.app.core.design.Radius
import com.noteone.app.core.design.SemanticKeys
import com.noteone.app.core.design.Space
import com.noteone.app.core.design.component.ActionChip
import com.noteone.app.core.design.component.AmountKeypad
import com.noteone.app.core.design.component.CategoryChip
import com.noteone.app.core.design.component.DirectionToggle
import com.noteone.app.core.design.component.rememberAlignedLazyListState
import com.noteone.app.core.design.icon.AppIcons
import com.noteone.app.core.domain.AmountInputRules

/** 记账卡片的内边距 20dp（规范 §3.4：记账卡片专用值，不属于间距刻度）。 */
private val CardPadding = 20.dp

/**
 * 整数位超过这个位数就降到 [AppType.AmountL]（40sp）。
 *
 * 规范 §5.1 写的阈值是 9 位，但输入规则本身把整数位封在 9 位内（
 * `AmountInputRules.MAX_INT_DIGITS`），那条规则永远不会触发。
 * 而 56sp 下一个数字宽约 0.55em ≈ 31dp，卡片可用宽度只有屏宽 − 16×2 − 20×2
 * ≈ 288dp，算上逗号后 **7 位整数就会溢出**（`9,999,999.99` ≈ 324dp）。
 * 所以这里按「装得下」取 6 位为阈值：6 位时最宽 `999,999.99` ≈ 277dp，仍然放得下。
 */
private const val XL_INT_DIGITS = 6

/**
 * 记账卡片。**首页与账单编辑面板共用同一份实现**（规范 §5.1 / §6.2）。
 *
 * 首页和编辑面板视觉必须完全一致，所以这里没有「首页模式 / 编辑模式」两套代码，
 * 只有一根可选的 [extraRow] 插槽——编辑面板用它塞「日期时间」行，首页不传。
 *
 * 组件**自身不持有任何状态**，金额串、备注、方向、选中分类全部由调用方持有。
 *
 * @param amountText 原始输入串（无千分位），必须经 `AmountInputRules` 变换
 * @param pulse 每次成功入账 +1；用于触发金额区的 `scale 1.0 → 0.98 → 1.0` 微动效
 * @param extraRow 插在分类行与备注行之间的可选额外行（编辑面板的「日期时间」）
 * @param fillHeight 卡片是否撑满调用方给的高度（**只有首页传 true**）。
 *   首页固定区之外已经没有任何滚动内容，卡片不撑满就会在键盘下面空出小半屏；
 *   撑满后多出来的高度全部给**金额区**（`weight(1f)`，数字垂直居中），
 *   方向 / 分类 / 备注 / 键盘仍然定高，于是键盘自然贴到底栏上方——也是拇指最好按的位置。
 *   编辑面板是 wrap-content 的弹层，必须保持 `false`。
 */
@Composable
fun RecordForm(
    isIncome: Boolean,
    onDirectionChange: (Boolean) -> Unit,
    amountText: String,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onDone: () -> Unit,
    doneEnabled: Boolean,
    categories: List<Category>,
    selectedCategoryId: Long?,
    onCategorySelect: (Category) -> Unit,
    onOpenCategoryPicker: () -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    pulse: Int = 0,
    extraRow: (@Composable () -> Unit)? = null,
    fillHeight: Boolean = false,
) {
    val cardShape = RoundedCornerShape(Radius.Card)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier)
            .clip(cardShape)
            .background(AppColor.SurfaceCard)
            .border(Dimension.Divider, AppColor.Line, cardShape),
    ) {
        // ① 方向切换
        Box(
            modifier = Modifier.padding(
                start = CardPadding,
                end = CardPadding,
                top = CardPadding,
                bottom = Space.M,
            ),
        ) {
            DirectionToggle(isIncome = isIncome, onChange = onDirectionChange)
        }

        // ② 金额。首页撑满时由它吃掉全部富余高度，数字在带内垂直居中
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fillHeight) Modifier.weight(1f) else Modifier)
                .padding(horizontal = CardPadding, vertical = Space.S),
            contentAlignment = Alignment.CenterEnd,
        ) {
            AmountDisplay(amountText = amountText, pulse = pulse)
        }

        // ③ 分类行
        CategoryChipRow(
            categories = categories,
            selectedCategoryId = selectedCategoryId,
            onCategorySelect = onCategorySelect,
            onOpenCategoryPicker = onOpenCategoryPicker,
        )

        // ④ 可选额外行（编辑面板的「日期时间」）
        extraRow?.invoke()

        // ⑤ 备注行。下方 1dp 分隔线由键盘自带的顶部边框承担
        //   （规范 §5.1：备注行「仅下方 1dp #EAEAEA（与键盘区隔开）」），
        //   这里再画一条会出现 2dp 粗的线。
        NoteRow(note = note, onNoteChange = onNoteChange)

        // ⑥ 自绘键盘（自带 1dp 边框，禁止自己重写）
        AmountKeypad(
            onDigit = onDigit,
            onBackspace = onBackspace,
            onDone = onDone,
            doneEnabled = doneEnabled,
        )
    }
}

/**
 * 金额显示。右对齐、56sp tabular（整数位过多时自动降到 40sp）。
 *
 * - 未输入时显示 `0.00` 且颜色 `Faint`；输入中 `Ink`
 * - **整数位逐位滚动切换 300ms，小数位直接替换**（规范 §3.7）
 * - 千分位由 `AmountInputRules.toDisplay()` 加，输入区不显示 `¥` 前缀（规范 §9.3）
 */
@Composable
private fun AmountDisplay(
    amountText: String,
    pulse: Int,
) {
    val display = if (amountText.isEmpty()) "0.00" else AmountInputRules.toDisplay(amountText)
    val color = if (amountText.isEmpty()) AppColor.Faint else AppColor.Ink

    // 记账成功的微动效：scale 1.0 → 0.98 → 1.0，150ms
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pulse) {
        if (pulse <= 0) return@LaunchedEffect
        scale.animateTo(0.98f, tween(Motion.Fast, easing = Motion.Ease))
        scale.animateTo(1f, tween(Motion.Fast, easing = Motion.Ease))
    }

    val dotIndex = display.indexOf('.')
    val intPart = if (dotIndex >= 0) display.substring(0, dotIndex) else display
    val decimalPart = if (dotIndex >= 0) display.substring(dotIndex) else ""
    val style = if (intPart.count { it.isDigit() } > XL_INT_DIGITS) {
        AppType.AmountL
    } else {
        AppType.AmountXL
    }

    Row(
        modifier = Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        intPart.forEach { char ->
            if (char.isDigit()) {
                RollingDigitSlot(digit = char, style = style, color = color)
            } else {
                TextSlot(text = char.toString(), style = style, color = color)
            }
        }
        if (decimalPart.isNotEmpty()) {
            // 小数位直接替换，不做滚动（规范 §3.7）
            TextSlot(text = decimalPart, style = style, color = color)
        }
    }
}

/**
 * 单个整数位：在自己那一格里上下滚动。
 *
 * 格子高度固定为一行行高并 `clipToBounds`，所以滚动时不会溢出到相邻行。
 * 数字是 tabular 的，位数不变时格子宽度完全一致，不会有横向抖动。
 */
@Composable
private fun RollingDigitSlot(
    digit: Char,
    style: TextStyle,
    color: Color,
) {
    val slotHeight = with(LocalDensity.current) { style.lineHeight.toDp() }

    Box(
        modifier = Modifier
            .height(slotHeight)
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = digit,
            transitionSpec = {
                val forward = targetState > initialState
                val enter = slideInVertically(
                    animationSpec = tween(Motion.Slow, easing = Motion.Ease),
                    initialOffsetY = { full -> if (forward) full else -full },
                ) + fadeIn(animationSpec = tween(Motion.Slow, easing = Motion.Ease))
                val exit = slideOutVertically(
                    animationSpec = tween(Motion.Slow, easing = Motion.Ease),
                    targetOffsetY = { full -> if (forward) -full else full },
                ) + fadeOut(animationSpec = tween(Motion.Slow, easing = Motion.Ease))
                enter togetherWith exit
            },
            label = "rollingDigit",
        ) { value ->
            Text(
                text = value.toString(),
                style = style,
                color = color,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** 千分位逗号与小数点：固定一行高，不参与滚动。 */
@Composable
private fun TextSlot(text: String, style: TextStyle, color: Color) {
    val slotHeight = with(LocalDensity.current) { style.lineHeight.toDp() }
    Box(
        modifier = Modifier
            .height(slotHeight)
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** 分类 chip 行：横滑 + 行尾「分类 ▾」。 */
@Composable
private fun CategoryChipRow(
    categories: List<Category>,
    selectedCategoryId: Long?,
    onCategorySelect: (Category) -> Unit,
    onOpenCategoryPicker: () -> Unit,
) {
    LazyRow(
        // 行尾有固定动作 chip，数据又是异步到达的 —— 必须用对齐版 state，
        // 否则一进页面就滚到最右端（原因见 rememberAlignedLazyListState 的注释）。
        state = rememberAlignedLazyListState(dataSize = categories.size),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CardPadding),
        horizontalArrangement = Arrangement.spacedBy(Space.S),
    ) {
        items(items = categories, key = { it.id }) { category ->
            CategoryChip(
                name = category.name,
                colorKey = category.colorKey,
                selected = category.id == selectedCategoryId,
                onClick = { onCategorySelect(category) },
            )
        }
        item(key = "category_picker") {
            ActionChip(
                label = stringResource(R.string.record_category_picker),
                iconRes = AppIcons.CaretDown,
                onClick = onOpenCategoryPicker,
            )
        }
    }
}

/**
 * 备注行。单行，15sp，占位符「备注（选填）」。
 *
 * **默认不弹系统键盘**（避免抖动）：行尾一个 20dp 的键盘图标，点它才把焦点与
 * 系统键盘切过来；再点一次收起。备注硬限 100 字由 ViewModel 拒绝。
 */
@Composable
private fun NoteRow(
    note: String,
    onNoteChange: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimension.TouchMin)
            .padding(start = CardPadding, end = Space.M),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = note,
            onValueChange = onNoteChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused },
            textStyle = AppType.Body.copy(color = AppColor.Ink),
            singleLine = true,
            cursorBrush = SolidColor(AppColor.Ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (note.isEmpty()) {
                        Text(
                            text = stringResource(R.string.record_note_hint),
                            style = AppType.Body,
                            color = AppColor.Faint,
                        )
                    }
                    innerTextField()
                }
            },
        )
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clickable {
                    if (focused) {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    } else {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = AppIcons.Keyboard),
                contentDescription = stringResource(R.string.record_note_toggle_ime),
                tint = AppColor.Muted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------- Preview

private fun previewCategories(): List<Category> = listOf(
    Category(1, "餐饮", CategoryScope.ExpenseOnly, SemanticKeys.Red, 0, true, false),
    Category(2, "交通", CategoryScope.ExpenseOnly, SemanticKeys.Blue, 1, true, false),
    Category(3, "购物", CategoryScope.ExpenseOnly, SemanticKeys.Yellow, 2, true, false),
    Category(4, "居住", CategoryScope.ExpenseOnly, SemanticKeys.Green, 3, true, false),
)

@Preview(name = "RecordForm / 输入中", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun RecordFormPreview() {
    AppTheme {
        Box(modifier = Modifier.padding(Space.L)) {
            RecordForm(
                isIncome = false,
                onDirectionChange = {},
                amountText = "12.5",
                onDigit = {},
                onBackspace = {},
                onDone = {},
                doneEnabled = true,
                categories = previewCategories(),
                selectedCategoryId = 1L,
                onCategorySelect = {},
                onOpenCategoryPicker = {},
                note = "食堂",
                onNoteChange = {},
            )
        }
    }
}

@Preview(name = "RecordForm / 空金额", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun RecordFormEmptyPreview() {
    AppTheme {
        Box(modifier = Modifier.padding(Space.L)) {
            RecordForm(
                isIncome = true,
                onDirectionChange = {},
                amountText = "",
                onDigit = {},
                onBackspace = {},
                onDone = {},
                doneEnabled = false,
                categories = emptyList(),
                selectedCategoryId = null,
                onCategorySelect = {},
                onOpenCategoryPicker = {},
                note = "",
                onNoteChange = {},
            )
        }
    }
}
