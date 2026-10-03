// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.quickentry.panel

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.noteone.app.R
import com.noteone.app.core.data.model.Category
import com.noteone.app.core.data.model.QuickAction
import com.noteone.app.core.design.AppColor
import com.noteone.app.core.design.AppTheme
import com.noteone.app.core.design.AppType
import com.noteone.app.core.design.Dimension
import com.noteone.app.core.design.Radius
import com.noteone.app.core.design.SemanticColor
import com.noteone.app.core.design.SemanticKeys
import com.noteone.app.core.design.Space
import com.noteone.app.core.design.component.AmountKeypad
import com.noteone.app.core.design.component.DirectionToggle
import com.noteone.app.core.design.component.PrimaryButton
import com.noteone.app.core.design.component.QuickActionChip
import com.noteone.app.core.design.component.SelectionChip
import com.noteone.app.core.design.component.formatMoneyText
import com.noteone.app.core.design.icon.AppIcons
import com.noteone.app.core.domain.AmountInputRules
import com.noteone.app.quickentry.ocr.AmountCandidate

/** 面板内边距 20dp（与记账卡片一致，规范 §7.1）。 */
private val PanelPadding = 20.dp

/**
 * 金额降字号阈值，与 B 的记账卡片保持一致（见 `RecordForm.kt` 的说明：
 * 56sp 下 7 位整数加逗号就会溢出，所以按"装得下"取 6 位）。
 */
private const val XL_INT_DIGITS = 6

/** 面板里的一行提示。降级说明与保存失败共用它。 */
data class PanelMessage(
    val text: String,
    /** true 表示是错误（用砖红深色），false 表示只是说明（Muted）。 */
    val isError: Boolean = false,
)

/**
 * 悬浮结果面板的全部渲染状态。
 *
 * 与 B 的 `RecordUiState` 一样是**不可变快照**：会话层持有 `MutableState`，
 * 状态一变 Compose 自动重组，组件本身不持有任何状态。
 */
data class QuickEntryPanelState(
    val isIncome: Boolean = false,
    /** 原始输入串（无千分位），交给 `AmountInputRules` 变换。 */
    val amountText: String = "",
    val note: String = "",
    /** 排序后的候选，最多 3 个。只有 1 个时不显示 chip 行。 */
    val candidates: List<AmountCandidate> = emptyList(),
    /** 当前预填的候选下标，`-1` 表示用户自己敲的、不是候选。 */
    val selectedCandidateIndex: Int = -1,
    val quickActions: List<QuickAction> = emptyList(),
    /** 当前方向可用的分类，用于取快捷按钮的颜色。 */
    val categoryById: Map<Long, Category> = emptyMap(),
    val selectedCategoryId: Long? = null,
    val keypadExpanded: Boolean = false,
    val message: PanelMessage? = null,
    /** 仅降级面板有：48dp 缩略图（内存 Bitmap，面板关闭立即回收）。 */
    val thumbnail: ImageBitmap? = null,
    val saving: Boolean = false,
) {
    val doneEnabled: Boolean get() = !saving && AmountInputRules.isDoneEnabled(amountText)
}

/**
 * 悬浮结果面板（规范 §8.6）。
 *
 * 规格要点：
 * - 卡片宽 = 屏宽 − 32dp（宽度由窗口 LayoutParams 决定，这里只管内容），
 *   `#FFFFFF` 底、12dp 圆角、1dp `#EAEAEA` 边框、20dp 内边距；
 * - **全项目唯一允许有阴影的地方**：`0 2px 8px rgba(0,0,0,0.04)`；
 * - 方向 / 金额 / 候选 chip / 分类快捷按钮 / 备注 / 「完成」依次排下来；
 * - 点面板以外区域**不关闭**：那由窗口的 `FLAG_NOT_TOUCH_MODAL` 保证，
 *   组件侧不做任何"点外部关闭"的处理。
 *
 * 与任务书的两处实现差异（都在交付说明里写了理由）：
 * 1. 键盘嵌在卡片**底部**（占据「完成」按钮的位置），而不是紧跟金额下方 ——
 *    键盘 224dp 高，插在金额下面会把候选、快捷按钮、备注全部挤出屏幕，
 *    放在底部后"面板高度增长 → 位置重新计算"这条要求的语义不变；
 * 2. 键盘展开时不再渲染全宽「完成」按钮：键盘自带的「完成」键就是提交，
 *    两个"完成"并排会让用户不知道点哪个。
 */
@Composable
fun QuickEntryPanel(
    state: QuickEntryPanelState,
    onDirectionChange: (Boolean) -> Unit,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onToggleKeypad: () -> Unit,
    onCandidateSelect: (Int) -> Unit,
    onQuickActionSelect: (QuickAction) -> Unit,
    onNoteChange: (String) -> Unit,
    onNoteFocusChange: (Boolean) -> Unit,
    onDragBy: (Float, Float) -> Unit,
    onClose: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(Radius.Card)
    val shadowColor = Color(0x0A000000) // rgba(0,0,0,0.04)

    Column(
        modifier = modifier
            .fillMaxWidth()
            // 全项目唯一允许的阴影
            .shadow(
                elevation = 4.dp,
                shape = cardShape,
                clip = true,
                ambientColor = shadowColor,
                spotColor = shadowColor,
            )
            .background(AppColor.SurfaceCard)
            .border(Dimension.Divider, AppColor.Line, cardShape)
            .padding(PanelPadding),
    ) {
        // ① 方向 + 拖动把手 + （降级时的缩略图）+ 关闭
        Row(verticalAlignment = Alignment.CenterVertically) {
            DirectionToggle(isIncome = state.isIncome, onChange = onDirectionChange)
            Spacer(modifier = Modifier.width(Space.M))
            Spacer(modifier = Modifier.weight(1f))
            PanelDragHandle(onDragBy = onDragBy)
            Spacer(modifier = Modifier.width(Space.S))
            state.thumbnail?.let { thumbnail ->
                Image(
                    bitmap = thumbnail,
                    contentDescription = stringResource(R.string.quick_panel_thumbnail),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(Dimension.TouchMin)
                        .clip(RoundedCornerShape(Radius.Input)),
                )
                Spacer(modifier = Modifier.width(Space.S))
            }
            CloseButton(onClose = onClose)
        }

        // ② 降级 / 失败提示行
        state.message?.let { message ->
            Spacer(modifier = Modifier.height(Space.S))
            Text(
                text = message.text,
                style = AppType.Caption,
                color = if (message.isError) SemanticColor.of(SemanticKeys.Red).fg else AppColor.Muted,
            )
        }

        // ③ 金额（点击展开 / 收起键盘）
        Spacer(modifier = Modifier.height(Space.S))
        AmountRow(amountText = state.amountText, onClick = onToggleKeypad)

        // ④ 候选 chip 行：只在多候选时出现
        if (state.candidates.size > 1) {
            Spacer(modifier = Modifier.height(Space.M))
            CandidateRow(
                candidates = state.candidates,
                selectedIndex = state.selectedCandidateIndex,
                onSelect = onCandidateSelect,
            )
        }

        // ⑤ 分类快捷按钮横滑行
        if (state.quickActions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Space.M))
            QuickActionRow(
                quickActions = state.quickActions,
                categoryById = state.categoryById,
                selectedCategoryId = state.selectedCategoryId,
                onSelect = onQuickActionSelect,
            )
        }

        // ⑥ 备注
        Spacer(modifier = Modifier.height(Space.S))
        NoteRow(
            note = state.note,
            onNoteChange = onNoteChange,
            onFocusChange = onNoteFocusChange,
        )

        // ⑦ 键盘（展开时）或「完成」按钮
        Spacer(modifier = Modifier.height(Space.M))
        if (state.keypadExpanded) {
            AmountKeypad(
                onDigit = onDigit,
                onBackspace = onBackspace,
                onDone = onSubmit,
                doneEnabled = state.doneEnabled,
            )
        } else {
            PrimaryButton(
                text = stringResource(R.string.common_done),
                onClick = onSubmit,
                enabled = state.doneEnabled,
            )
        }
    }
}

/**
 * 面板拖动把手。
 *
 * 面板经常正好压在用户想核对的金额上；不能拖的话只能关掉重开。
 * 用把手而不是整卡可拖，是为了不影响金额 / 分类 chip / 备注的点击。
 */
@Composable
private fun PanelDragHandle(onDragBy: (Float, Float) -> Unit) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDragBy(dragAmount.x, dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_grip),
            contentDescription = stringResource(R.string.quick_panel_drag_handle),
            tint = AppColor.Faint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun CloseButton(onClose: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = AppIcons.Close),
            contentDescription = stringResource(R.string.quick_panel_close),
            tint = AppColor.Muted,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 金额：56sp tabular 右对齐；未输入显示 `0.00` 且用 `Faint`。输入区不带 `¥`（规范 §9.3）。 */
@Composable
private fun AmountRow(amountText: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val display = if (amountText.isEmpty()) "0.00" else AmountInputRules.toDisplay(amountText)
    val intPart = display.substringBefore('.')
    val style = if (intPart.count { it.isDigit() } > XL_INT_DIGITS) {
        AppType.AmountL
    } else {
        AppType.AmountXL
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimension.TouchMin)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            text = display,
            style = style,
            color = if (amountText.isEmpty()) AppColor.Faint else AppColor.Ink,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * 候选 chip 行。**不自动选择**——排序第 1 个只是预填值，用户点其他 chip 可替换。
 *
 * 用 A 的 `SelectionChip`（选中墨底白字），不自己写一套。
 */
@Composable
private fun CandidateRow(
    candidates: List<AmountCandidate>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
        candidates.forEachIndexed { index, candidate ->
            SelectionChip(
                label = formatMoneyText(candidate.cents),
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
            )
        }
    }
}

/** 分类快捷按钮横滑行。数据源与首页同源（`QuickActionRepository.observeActive()`），最多 8 个。 */
@Composable
private fun QuickActionRow(
    quickActions: List<QuickAction>,
    categoryById: Map<Long, Category>,
    selectedCategoryId: Long?,
    onSelect: (QuickAction) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
        items(items = quickActions, key = { it.id }) { action ->
            QuickActionChip(
                label = action.label,
                colorKey = categoryById[action.categoryId]?.colorKey ?: SemanticKeys.Ink,
                selected = action.categoryId == selectedCategoryId,
                onClick = { onSelect(action) },
            )
        }
    }
}

/**
 * 备注行。单行输入，占位符「备注（选填）」。
 *
 * 与首页记账卡片同一套交互：**默认不弹系统键盘**，行尾一个键盘图标显式切换，
 * 避免"点一下备注行整个面板跳一下"的抖动。
 */
@Composable
private fun NoteRow(
    note: String,
    onNoteChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimension.TouchMin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = note,
            onValueChange = onNoteChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged {
                    if (focused != it.isFocused) {
                        focused = it.isFocused
                        onFocusChange(it.isFocused)
                    }
                },
            textStyle = AppType.Body.copy(color = AppColor.Ink),
            singleLine = true,
            cursorBrush = SolidColor(AppColor.Ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (note.isEmpty()) {
                        Text(
                            text = stringResource(R.string.quick_panel_note_hint),
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
                contentDescription = stringResource(R.string.quick_panel_note_toggle_ime),
                tint = AppColor.Muted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------- Preview

private fun previewCategories(): Map<Long, Category> = listOf(
    Category(1, "餐饮", 0, SemanticKeys.Red, 0, true, false),
    Category(2, "生活费", 1, SemanticKeys.Green, 0, true, false),
).associateBy { it.id }

@Preview(name = "悬浮面板 / 多候选", showBackground = true, backgroundColor = 0xFF9E9E9E)
@Composable
private fun QuickEntryPanelPreview() {
    AppTheme {
        Box(modifier = Modifier.padding(Space.L)) {
            QuickEntryPanel(
                state = QuickEntryPanelState(
                    amountText = "14",
                    candidates = listOf(
                        AmountCandidate(1400, "¥14.00", true, true, 0, 400),
                        AmountCandidate(200, "2.00", false, true, 1, 460),
                    ),
                    selectedCandidateIndex = 0,
                    quickActions = listOf(
                        QuickAction(1, "午餐", 1, 0, 0, false),
                        QuickAction(2, "地铁公交", 1, 0, 1, false),
                    ),
                    categoryById = previewCategories(),
                    selectedCategoryId = 1L,
                ),
                onDirectionChange = {},
                onDigit = {},
                onBackspace = {},
                onToggleKeypad = {},
                onCandidateSelect = {},
                onQuickActionSelect = {},
                onNoteChange = {},
                onNoteFocusChange = {},
                onDragBy = { _, _ -> },
                onClose = {},
                onSubmit = {},
            )
        }
    }
}

@Preview(name = "悬浮面板 / 降级展开键盘", showBackground = true, backgroundColor = 0xFF9E9E9E)
@Composable
private fun QuickEntryPanelDegradedPreview() {
    AppTheme {
        Box(modifier = Modifier.padding(Space.L)) {
            QuickEntryPanel(
                state = QuickEntryPanelState(
                    keypadExpanded = true,
                    message = PanelMessage("未识别到金额，请手动输入"),
                    quickActions = listOf(QuickAction(1, "午餐", 1, 0, 0, false)),
                    categoryById = previewCategories(),
                    selectedCategoryId = 1L,
                ),
                onDirectionChange = {},
                onDigit = {},
                onBackspace = {},
                onToggleKeypad = {},
                onCandidateSelect = {},
                onQuickActionSelect = {},
                onNoteChange = {},
                onNoteFocusChange = {},
                onDragBy = { _, _ -> },
                onClose = {},
                onSubmit = {},
            )
        }
    }
}
