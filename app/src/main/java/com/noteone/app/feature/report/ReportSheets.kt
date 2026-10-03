// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

// `AppBottomSheet` 的签名里有 M3 的 `SheetState`（实验 API），所以调用方也要 opt-in。
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.noteone.app.feature.report

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.noteone.app.R
import com.noteone.app.core.common.DateFormats
import com.noteone.app.core.design.AppColor
import com.noteone.app.core.design.AppTheme
import com.noteone.app.core.design.AppType
import com.noteone.app.core.design.Dimension
import com.noteone.app.core.design.Radius
import com.noteone.app.core.design.Space
import com.noteone.app.core.design.component.AppBottomSheet
import com.noteone.app.core.design.component.PrimaryButton
import com.noteone.app.core.design.component.SecondaryButton
import java.time.LocalDate
import java.time.YearMonth

/** 允许指定的最早日期。 */
private const val FIRST_YEAR = 2000

/** 选择器三列的高度与单格高度。 */
private val PickerHeight = 200.dp
private val PickerItemHeight = 40.dp

/** 正在编辑范围的哪一端。 */
private enum class RangeEnd { Start, End }

/**
 * 自定义时间范围弹层（规范 §5.3、任务书 §2.2）。
 *
 * 结构：`起始日` / `结束日` 两个可点的输入格 + 一个年 / 月 / 日三列选择器 + 确定 / 取消。
 *
 * 两个刻意的决定：
 *
 * 1. **自绘日期选择器，不用 Material3 `DatePicker`**。M3 的选择器自带一整套 Material 视觉语言
 *    （圆形选中态、大标题、模式切换），要覆盖到符合本项目规范需要重写几十个颜色槽；
 *    规范 §2.1 又明确要求「不使用 M3 默认配色 / 默认圆角 / 默认高程」。自绘版还顺手把未来日期排除了。
 *    （B 在账单页编辑面板里同样是自绘；那份在 `feature.ledger` 包里，不跨包引用。）
 * 2. **只有一个弹层**：切换「编辑起始日 / 编辑结束日」用格子的选中态，
 *    而不是在弹层上再叠一层日期选择器——两层 `ModalBottomSheet` 叠加的观感没有验证过，
 *    没必要冒这个风险。
 *
 * 日期上限是**今天**：未来还没有记录，允许选只会得到一个空范围。
 */
@Composable
internal fun CustomRangeSheet(
    initialStart: LocalDate,
    initialEnd: LocalDate,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = LocalDate.now()
    var start by remember { mutableStateOf(normalize(initialStart, today)) }
    var end by remember { mutableStateOf(normalize(initialEnd, today)) }
    var active by remember { mutableStateOf(RangeEnd.Start) }

    AppBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.report_custom_title),
            style = AppType.SectionTitle,
            color = AppColor.Ink,
        )
        Box(modifier = Modifier.height(Space.M))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.S),
        ) {
            RangeField(
                label = stringResource(R.string.report_custom_start),
                date = start,
                selected = active == RangeEnd.Start,
                onClick = { active = RangeEnd.Start },
                modifier = Modifier.weight(1f),
            )
            RangeField(
                label = stringResource(R.string.report_custom_end),
                date = end,
                selected = active == RangeEnd.End,
                onClick = { active = RangeEnd.End },
                modifier = Modifier.weight(1f),
            )
        }
        Box(modifier = Modifier.height(Space.L))
        DateColumns(
            date = if (active == RangeEnd.Start) start else end,
            onChange = { picked ->
                if (active == RangeEnd.Start) start = picked else end = picked
            },
        )
        Box(modifier = Modifier.height(Space.L))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.M)) {
            SecondaryButton(
                text = stringResource(R.string.report_custom_cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = stringResource(R.string.report_custom_confirm),
                // 两端顺序这里换一次（chip 文案立刻正确），TimeRanges.resolve() 里还会再归一化一次
                onClick = { onConfirm(minOf(start, end), maxOf(start, end)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 起始日 / 结束日输入格。选中态用 1dp 墨色边框，未选中用 1dp 分割线色。 */
@Composable
private fun RangeField(
    label: String,
    date: LocalDate,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radius.Input)
    Column(
        modifier = modifier
            .clip(shape)
            .background(AppColor.Canvas)
            .border(Dimension.Divider, if (selected) AppColor.Ink else AppColor.Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.M, vertical = Space.S),
    ) {
        Text(text = label, style = AppType.Label, color = AppColor.Muted)
        Box(modifier = Modifier.height(Space.XS))
        Text(text = DateFormats.isoDate(date), style = AppType.Body, color = AppColor.Ink)
    }
}

/**
 * 年 / 月 / 日三列。列里**不出现未来日期**（月列只到当月，日列只到当天），
 * 所以点任何一格都落在合法范围内，不需要额外的校验提示。
 */
@Composable
private fun DateColumns(date: LocalDate, onChange: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val shown = normalize(date, today)
    val monthUpper = if (shown.year == today.year) today.monthValue else 12
    val dayUpper = if (shown.year == today.year && shown.monthValue == today.monthValue) {
        today.dayOfMonth
    } else {
        shown.lengthOfMonth()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.S),
    ) {
        PickerColumn(
            header = stringResource(R.string.report_date_year),
            values = (FIRST_YEAR..today.year).toList(),
            selected = shown.year,
            onSelect = { year ->
                onChange(normalize(safeDate(year, shown.monthValue, shown.dayOfMonth), today))
            },
            modifier = Modifier.weight(1.4f),
        )
        PickerColumn(
            header = stringResource(R.string.report_date_month),
            values = (1..monthUpper).toList(),
            selected = shown.monthValue,
            onSelect = { month ->
                onChange(normalize(safeDate(shown.year, month, shown.dayOfMonth), today))
            },
            modifier = Modifier.weight(1f),
        )
        PickerColumn(
            header = stringResource(R.string.report_date_day),
            values = (1..dayUpper).toList(),
            selected = shown.dayOfMonth,
            onSelect = { day ->
                onChange(normalize(LocalDate.of(shown.year, shown.monthValue, day), today))
            },
            modifier = Modifier.weight(1f),
        )
    }
}

/** 一列可滚的数字（年 / 月 / 日）。点一下就选中。 */
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

/** 构造一个日期，日期超出该月天数时夹到月末（避免 `LocalDate.of` 抛异常，例如 2 月 29 日）。 */
private fun safeDate(year: Int, month: Int, day: Int): LocalDate {
    val length = YearMonth.of(year, month).lengthOfMonth()
    return LocalDate.of(year, month, day.coerceIn(1, length))
}

/** 把日期夹进 `[2000-01-01, today]`，并保证月 / 日仍然合法。 */
private fun normalize(date: LocalDate, today: LocalDate): LocalDate {
    val year = date.year.coerceIn(FIRST_YEAR, today.year)
    val month = date.monthValue.coerceIn(1, if (year == today.year) today.monthValue else 12)
    val upperDay = if (year == today.year && month == today.monthValue) {
        today.dayOfMonth
    } else {
        YearMonth.of(year, month).lengthOfMonth()
    }
    return LocalDate.of(year, month, date.dayOfMonth.coerceIn(1, upperDay))
}

@Preview(name = "汇总 / 自定义时间范围", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun CustomRangeSheetPreview() {
    AppTheme {
        AppBottomSheet(onDismissRequest = {}) {
            Text(
                text = "自定义时间范围",
                style = AppType.SectionTitle,
                color = AppColor.Ink,
            )
            Box(modifier = Modifier.height(Space.M))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.S),
            ) {
                RangeField("起始日", LocalDate.of(2026, 9, 1), true, {}, Modifier.weight(1f))
                RangeField("结束日", LocalDate.of(2026, 9, 29), false, {}, Modifier.weight(1f))
            }
        }
    }
}
