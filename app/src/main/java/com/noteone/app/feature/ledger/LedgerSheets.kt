// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

// `AppBottomSheet` 的签名里有 M3 的 `SheetState`（实验 API），所以调用方也要 opt-in。
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.noteone.app.feature.ledger

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.noteone.app.R
import com.noteone.app.core.common.DateFormats
import com.noteone.app.core.common.Money
import com.noteone.app.core.data.model.Category
import com.noteone.app.core.data.model.CategoryScope
import com.noteone.app.core.data.model.Direction
import com.noteone.app.core.data.model.Transaction
import com.noteone.app.core.data.seed.BuiltInData
import com.noteone.app.core.design.AppColor
import com.noteone.app.core.design.AppType
import com.noteone.app.core.design.Dimension
import com.noteone.app.core.design.Radius
import com.noteone.app.core.design.SemanticColor
import com.noteone.app.core.design.SemanticKeys
import com.noteone.app.core.design.Space
import com.noteone.app.core.design.component.AppBottomSheet
import com.noteone.app.core.design.component.PrimaryButton
import com.noteone.app.core.design.component.SecondaryButton
import com.noteone.app.core.design.icon.AppIcons
import com.noteone.app.core.domain.AmountInputRules
import com.noteone.app.core.domain.TimeRanges
import com.noteone.app.feature.record.CategoryPickerSheet
import com.noteone.app.feature.record.RecordForm
import java.time.LocalDate
import java.time.YearMonth

/** 记账卡片的内边距，与 `RecordForm` 保持一致。 */
private val CardPadding = 20.dp

/** 日期选择器三列的高度。 */
private val PickerHeight = 200.dp

/** 单列里每格的高度。 */
private val PickerItemHeight = 40.dp

/** 允许补录的最早年份。 */
private const val FIRST_YEAR = 2000

/**
 * 账单编辑面板（规范 §6.2）。
 *
 * **复用 [RecordForm]**：方向 / 金额 / 分类 / 备注 / 键盘与首页记账卡片是同一份实现，
 * 额外多一行「日期时间」，底部并排「删除」「保存」。
 *
 * 日期只改「哪一天」，时间部分沿用原记录，所以行上显示 `2026-09-29 12:30`。
 */
@Composable
internal fun TransactionEditSheet(
    transaction: Transaction,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit,
    onDelete: (Transaction) -> Unit,
) {
    var amountText by remember(transaction.id) {
        mutableStateOf(Money.centsToPlain(transaction.amountCents))
    }
    var note by remember(transaction.id) { mutableStateOf(transaction.note) }
    var isIncome by remember(transaction.id) {
        mutableStateOf(transaction.direction == Direction.Income)
    }
    var categoryId by remember(transaction.id) { mutableStateOf(transaction.categoryId) }
    var occurredAt by remember(transaction.id) { mutableLongStateOf(transaction.occurredAt) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }

    val direction = if (isIncome) Direction.Income else Direction.Expense
    val availableCategories = categories.filter { CategoryScope.matches(it.direction, direction) }
    val doneEnabled = AmountInputRules.isDoneEnabled(amountText)

    fun edited(): Transaction = transaction.copy(
        amountCents = AmountInputRules.toCents(amountText),
        direction = direction,
        categoryId = categoryId,
        note = note.trim(),
        occurredAt = occurredAt,
    )

    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.ledger_edit_title),
                style = AppType.SectionTitle,
                color = AppColor.Ink,
            )
            Box(modifier = Modifier.height(Space.M))
            RecordForm(
                isIncome = isIncome,
                onDirectionChange = { isIncome = it },
                amountText = amountText,
                onDigit = { amountText = AmountInputRules.accept(amountText, it) },
                onBackspace = { amountText = AmountInputRules.backspace(amountText) },
                onDone = { if (doneEnabled) onSave(edited()) },
                doneEnabled = doneEnabled,
                categories = availableCategories,
                selectedCategoryId = categoryId,
                onCategorySelect = { categoryId = it.id },
                onOpenCategoryPicker = { showCategoryPicker = true },
                note = note,
                onNoteChange = { if (it.length <= BuiltInData.NOTE_MAX_LENGTH) note = it },
                extraRow = {
                    EditDateTimeRow(
                        occurredAt = occurredAt,
                        onClick = { showDatePicker = true },
                    )
                },
            )
            Box(modifier = Modifier.height(Space.L))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.M)) {
                SecondaryButton(
                    text = stringResource(R.string.ledger_delete),
                    onClick = { onDelete(transaction) },
                    modifier = Modifier.weight(1f),
                    contentColor = SemanticColor.of(SemanticKeys.Red).fg,
                )
                PrimaryButton(
                    text = stringResource(R.string.ledger_save),
                    onClick = { if (doneEnabled) onSave(edited()) },
                    enabled = doneEnabled,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (showDatePicker) {
        DatePickerSheet(
            initialMillis = occurredAt,
            onConfirm = { picked ->
                occurredAt = picked
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }

    if (showCategoryPicker) {
        CategoryPickerSheet(
            categories = availableCategories,
            selectedCategoryId = categoryId,
            onSelect = { category ->
                categoryId = category.id
                showCategoryPicker = false
            },
            onManage = { showCategoryPicker = false },
            onDismiss = { showCategoryPicker = false },
            showManageEntry = false,
        )
    }
}

/** 编辑面板独占的一行：`日期时间` + 值，点击弹日期选择器。 */
@Composable
private fun EditDateTimeRow(
    occurredAt: Long,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimension.TouchMin)
            .padding(horizontal = CardPadding)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.ledger_edit_datetime),
            style = AppType.Caption,
            color = AppColor.Muted,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = DateFormats.fullDateTime(occurredAt),
            style = AppType.Caption,
            color = AppColor.Muted,
        )
    }
}

/**
 * 日期选择器：年 / 月 / 日三列，**只能选到今天为止**（规范 §5.2「允许改到过去任意日期」）。
 *
 * 刻意自绘而不引 Material3 的 `DatePicker`：M3 的选择器自带 Material 视觉语言
 * （圆形选中态、大标题、模式切换），要覆盖到符合本项目规范需要重写几十个颜色槽，
 * 而规范 §2.1 要求「视觉全量自绘」。自绘版还能顺手把「未来日期」直接排除。
 *
 * 只改日期，**时间部分沿用原记录**（行上显示的 `12:30` 不会变）。
 */
@Composable
internal fun DatePickerSheet(
    initialMillis: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialDate = TimeRanges.toLocalDate(initialMillis)
    val today = LocalDate.now()
    val timeOfDayOffset = initialMillis - TimeRanges.startOfDay(initialDate)

    var year by remember { mutableIntStateOf(initialDate.year.coerceIn(FIRST_YEAR, today.year)) }
    var month by remember { mutableIntStateOf(initialDate.monthValue) }
    var day by remember { mutableIntStateOf(initialDate.dayOfMonth) }

    val monthUpper = if (year == today.year) today.monthValue else 12
    val safeMonth = month.coerceIn(1, monthUpper)
    val isCurrentMonth = year == today.year && safeMonth == today.monthValue
    val dayUpper = if (isCurrentMonth) {
        minOf(YearMonth.of(year, safeMonth).lengthOfMonth(), today.dayOfMonth)
    } else {
        YearMonth.of(year, safeMonth).lengthOfMonth()
    }
    val safeDay = day.coerceIn(1, dayUpper)

    AppBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.ledger_date_picker_title),
            style = AppType.SectionTitle,
            color = AppColor.Ink,
        )
        Box(modifier = Modifier.height(Space.M))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.S),
        ) {
            PickerColumn(
                header = stringResource(R.string.ledger_date_year),
                values = (FIRST_YEAR..today.year).toList(),
                selected = year,
                onSelect = { year = it },
                modifier = Modifier.weight(1.4f),
            )
            PickerColumn(
                header = stringResource(R.string.ledger_date_month),
                values = (1..12).toList(),
                selected = safeMonth,
                onSelect = { month = it },
                modifier = Modifier.weight(1f),
            )
            PickerColumn(
                header = stringResource(R.string.ledger_date_day),
                values = (1..dayUpper).toList(),
                selected = safeDay,
                onSelect = { day = it },
                modifier = Modifier.weight(1f),
            )
        }
        Box(modifier = Modifier.height(Space.L))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.M)) {
            SecondaryButton(
                text = stringResource(R.string.ledger_cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = stringResource(R.string.ledger_date_picker_confirm),
                onClick = {
                    val picked = LocalDate.of(year, safeMonth, safeDay)
                    onConfirm(TimeRanges.startOfDay(picked) + timeOfDayOffset)
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 一列可滚的数字（年 / 月 / 日）。高频区间，点一下就选中。 */
@Composable
private fun PickerColumn(
    header: String,
    values: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = values.indexOf(selected).coerceAtLeast(0),
    )
    Column(modifier = modifier) {
        Text(
            text = header,
            style = AppType.Label,
            color = AppColor.Muted,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(modifier = Modifier.height(Space.S))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(PickerHeight),
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(items = values, key = { it }) { value ->
                val current = value == selected
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PickerItemHeight)
                        .clickable { onSelect(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = value.toString(),
                        style = AppType.Body.copy(
                            fontWeight = if (current) FontWeight.Medium else FontWeight.Normal,
                        ),
                        color = if (current) AppColor.Ink else AppColor.Muted,
                    )
                }
            }
        }
    }
}

/**
 * 年月选择器（账单页点标题打开）。
 *
 * 范围限制到**当前月为止**：未来月份没有数据，让用户翻过去只会看到空列表。
 */
@Composable
internal fun MonthPickerSheet(
    current: YearMonth,
    onSelect: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = YearMonth.now()
    var year by remember { mutableIntStateOf(current.year.coerceIn(FIRST_YEAR, today.year)) }

    AppBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.ledger_month_picker_title),
            style = AppType.SectionTitle,
            color = AppColor.Ink,
        )
        Box(modifier = Modifier.height(Space.M))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            IconAction(
                iconRes = AppIcons.ArrowLeft,
                contentDescription = stringResource(R.string.ledger_prev_year),
                enabled = year > FIRST_YEAR,
                onClick = { year -= 1 },
            )
            Text(
                text = stringResource(R.string.ledger_year_format, year),
                style = AppType.SectionTitle,
                color = AppColor.Ink,
                modifier = Modifier.padding(horizontal = Space.M),
            )
            IconAction(
                iconRes = AppIcons.ArrowRight,
                contentDescription = stringResource(R.string.ledger_next_year),
                enabled = year < today.year,
                onClick = { year += 1 },
            )
        }
        Box(modifier = Modifier.height(Space.L))
        val monthUpper = if (year == today.year) today.monthValue else 12
        (1..12).chunked(4).forEach { rowMonths ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Space.S),
                horizontalArrangement = Arrangement.spacedBy(Space.S),
            ) {
                rowMonths.forEach { value ->
                    MonthCell(
                        month = value,
                        enabled = value <= monthUpper,
                        selected = year == current.year && value == current.monthValue,
                        onClick = { onSelect(YearMonth.of(year, value)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(4 - rowMonths.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MonthCell(
    month: Int,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radius.Input)
    val background = when {
        selected -> AppColor.Ink
        else -> AppColor.Canvas
    }
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(background)
            .then(
                if (selected || !enabled) Modifier else Modifier.border(Dimension.Divider, AppColor.Line, shape),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.ledger_month_format, month),
            style = AppType.Label,
            color = when {
                selected -> AppColor.Canvas
                enabled -> AppColor.Muted
                else -> AppColor.Faint
            },
        )
    }
}

/** 20dp 图标 + 48dp 热区，`enabled = false` 时降为 `Faint`。 */
@Composable
private fun IconAction(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = if (enabled) AppColor.Muted else AppColor.Faint,
            modifier = Modifier.size(20.dp),
        )
    }
}
