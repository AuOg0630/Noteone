// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.feature.report

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.noteone.app.R
import com.noteone.app.core.data.model.Category
import com.noteone.app.core.design.AppColor
import com.noteone.app.core.design.AppTheme
import com.noteone.app.core.design.AppType
import com.noteone.app.core.design.Motion
import com.noteone.app.core.design.Radius
import com.noteone.app.core.design.SemanticColor
import com.noteone.app.core.design.SemanticKeys
import com.noteone.app.core.design.Space
import com.noteone.app.core.design.component.CategoryChip
import com.noteone.app.core.design.component.MoneySign
import com.noteone.app.core.design.component.MoneyText
import com.noteone.app.core.design.component.SectionHeader
import com.noteone.app.core.design.component.SelectionChip
import com.noteone.app.core.design.component.TextButtonSmall

/** 三卡与统计卡片的固定尺寸（规范 §5.3：8dp 圆角、12dp 内边距）。 */
private val StatCardRadius = 8.dp

/** 排行条形与趋势柱的尺寸（规范 §5.3）。 */
private val BarHeight = 8.dp
private val TrendChartHeight = 120.dp
private val TrendBarGap = 8.dp
private val TrendMaxBarWidth = 32.dp
private val TrendMinBarHeight = 2.dp

/** 预算进度条高 4dp（规范 §5.4）。 */
private val BudgetBarHeight = 4.dp

/**
 * 13sp 但**带等宽数字**的金额样式。
 *
 * `AppType.Caption` 是正文样式，没有 `tnum`；规范 §3.3 要求「所有金额与数字」都带
 * 等宽数字，所以这里补上。字号与字重仍然走 token，不新增设计语言。
 */
private val CaptionTabular: TextStyle = AppType.Caption.copy(fontFeatureSettings = "tnum")

/**
 * 页头：标题「汇总」24sp Medium + 右上角「导出」13sp Muted。
 *
 * 24sp 是全站唯一的大标题（规范 §5.3 末条），不要在其他页面对齐这个字号。
 */
@Composable
internal fun ReportHeader(onExport: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = stringResource(R.string.report_title), style = AppType.PageTitle, color = AppColor.Ink)
        TextButtonSmall(text = stringResource(R.string.report_export), onClick = onExport)
    }
}

/**
 * 时间范围 chip 行（规范 §5.3）：`本周 / 本月 / 近 3 月 / 今年 / 自定义`，横滑。
 *
 * 用 [SelectionChip]（墨底白字）——**不要给时间范围 chip 套语义色**，
 * 语义色是分类的专属语言。
 *
 * 选中「自定义」后 chip 文字变成 `9.01 – 9.29`。
 */
@Composable
internal fun ReportPresetRow(
    preset: ReportPreset,
    customRangeLabel: String,
    onSelect: (ReportPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.S),
    ) {
        items(ReportPreset.entries.toList(), key = { it.name }) { item ->
            SelectionChip(
                label = if (item == ReportPreset.Custom) {
                    customRangeLabel.ifEmpty { stringResource(R.string.report_range_custom) }
                } else {
                    stringResource(presetLabelRes(item))
                },
                selected = preset == item,
                onClick = { onSelect(item) },
            )
        }
    }
}

/**
 * 分类筛选行（规范 §5.3）：行首固定「全部」，多选，横滑。
 *
 * - 选中任一分类后「全部」自动取消（即 `selectedIds` 非空）
 * - 点「全部」清空所有选择
 */
@Composable
internal fun ReportCategoryFilterRow(
    categories: List<Category>,
    selectedIds: Set<Long>,
    onSelectAll: () -> Unit,
    onToggle: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.S),
    ) {
        item(key = "all") {
            SelectionChip(
                label = stringResource(R.string.report_filter_all),
                selected = selectedIds.isEmpty(),
                onClick = onSelectAll,
            )
        }
        items(categories, key = { it.id }) { category ->
            CategoryChip(
                name = category.name,
                colorKey = category.colorKey,
                selected = category.id in selectedIds,
                onClick = { onToggle(category.id) },
            )
        }
    }
}

/**
 * 三卡：支出 / 收入 / 结余（规范 §5.3）。
 *
 * 结余为负时数字用砖红深色 `#9F2F2D`，为正用 `Ink`。**不做周期对比**（用户已明确否定）。
 */
@Composable
internal fun ReportSummaryCards(summary: ReportSummary, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.M),
    ) {
        StatCard(
            label = stringResource(R.string.common_expense),
            cents = summary.expenseCents,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = stringResource(R.string.common_income),
            cents = summary.incomeCents,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = stringResource(R.string.report_card_balance),
            cents = summary.balanceCents,
            valueColor = if (summary.balanceCents < 0) {
                SemanticColor.of(SemanticKeys.Red).fg
            } else {
                AppColor.Ink
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    cents: Long,
    modifier: Modifier = Modifier,
    valueColor: Color = AppColor.Ink,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(StatCardRadius))
            .background(AppColor.Surface)
            .padding(Space.M),
    ) {
        Text(text = label, style = AppType.Label, color = AppColor.Muted)
        Box(modifier = Modifier.height(Space.XS))
        // 卡片宽度只有 1/3 屏，大额必须压成「¥1.23 亿」，否则会截断
        MoneyText(cents = cents, style = AppType.AmountStat, color = valueColor, compact = true)
    }
}

/**
 * 日均支出一行小字：13sp `Muted`。
 *
 * 口径 = 范围内总支出 ÷ 范围内**已过天数（含今天）**，在 `ReportAggregator.summarize` 里算好。
 */
@Composable
internal fun ReportDailyAverageRow(cents: Long, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = stringResource(R.string.report_daily_average), style = AppType.Caption, color = AppColor.Muted)
        Box(modifier = Modifier.width(Space.XS))
        MoneyText(cents = cents, style = CaptionTabular, color = AppColor.Muted)
    }
}

/**
 * 本月预算进度（规范 §5.4）。
 *
 * 三行：`本月预算 / 已用 68%` → 4dp 进度条 → `剩余 ¥420.00`。
 *
 * 为什么分成三行而不是挤成一行：右侧那串在 13sp 下最长会到
 * 「本月预算　已用 128% · 超出 ¥12,345.67」，328dp 的可用宽度放不下。
 *
 * - 进度条底色 `Line`，进度色 `Ink`；**超支时进度色变砖红深色**，条子画满不溢出
 * - 口径固定**本自然月**，所以标签写死「本月预算」，不跟着上方的时间范围变
 */
@Composable
internal fun BudgetProgressRow(progress: BudgetProgress, modifier: Modifier = Modifier) {
    val over = progress.over
    val accent = if (over) SemanticColor.of(SemanticKeys.Red).fg else AppColor.Ink

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.report_budget_title),
                style = AppType.Caption,
                color = AppColor.Muted,
            )
            Text(
                text = stringResource(R.string.report_budget_percent, progress.percent),
                style = AppType.Caption,
                color = if (over) accent else AppColor.Muted,
            )
        }
        Box(modifier = Modifier.height(Space.S))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BudgetBarHeight)
                .clip(RoundedCornerShape(Radius.Bar))
                .background(AppColor.Line),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.ratio)
                    .height(BudgetBarHeight)
                    .background(accent),
            )
        }
        Box(modifier = Modifier.height(Space.S))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(
                    if (over) R.string.report_budget_over else R.string.report_budget_remaining,
                ),
                style = AppType.Caption,
                color = AppColor.Muted,
            )
            Box(modifier = Modifier.width(Space.XS))
            MoneyText(
                cents = progress.differenceCents,
                style = CaptionTabular,
                sign = MoneySign.None,
                color = if (over) accent else AppColor.Muted,
            )
        }
    }
}

/**
 * 分类排行（规范 §5.3）。**不套卡片**，直接列表。
 *
 * 条形宽度按**最大值**归一（首条满宽），占比按**总量**算——两个分母不一样，别搞混。
 */
@Composable
internal fun CategoryRankingSection(slices: List<CategorySlice>, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    // 数据变化时重新绘制一遍（400ms，规范 §3.7 的「条形图绘制」）
    LaunchedEffect(slices) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = Motion.Chart, easing = Motion.Ease))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(text = stringResource(R.string.report_ranking_title))
        slices.forEachIndexed { index, slice ->
            if (index > 0) Box(modifier = Modifier.height(Space.M))
            RankingRow(slice = slice, progress = progress)
        }
    }
}

@Composable
private fun RankingRow(
    slice: CategorySlice,
    progress: Animatable<Float, AnimationVector1D>,
) {
    val label = when {
        slice.categoryId == ReportAggregator.MERGED_CATEGORY_ID -> stringResource(R.string.report_ranking_other)
        slice.name.isEmpty() -> stringResource(R.string.report_uncategorized)
        else -> slice.name
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, style = AppType.Body, color = AppColor.Ink)
            Spacer(modifier = Modifier.weight(1f))
            MoneyText(cents = slice.amountCents, style = AppType.AmountRow, color = AppColor.Ink)
            Box(modifier = Modifier.width(Space.S))
            Text(
                text = stringResource(R.string.report_share_percent, slice.sharePercent),
                style = AppType.Caption,
                color = AppColor.Muted,
            )
        }
        Box(modifier = Modifier.height(Space.S))
        RankingBar(
            fraction = slice.barFraction,
            // 条形用该分类语义色的**深色字色**（如餐饮 `#9F2F2D`），不用浅底色
            color = SemanticColor.of(slice.colorKey).fg,
            progress = progress,
        )
    }
}

/**
 * 排行条形：高 8dp、4dp 圆角。
 *
 * 宽度用 `drawBehind` 画——只在绘制阶段读动画值，既不触发重组，
 * 也不会像 `Modifier.width` 那样做布局动画（规范红线：禁止动画 width / height）。
 */
@Composable
private fun RankingBar(
    fraction: Float,
    color: Color,
    progress: Animatable<Float, AnimationVector1D>,
    modifier: Modifier = Modifier,
) {
    val radius = with(LocalDensity.current) { Radius.Bar.toPx() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(BarHeight)
            .drawBehind {
                val barWidth = size.width * fraction.coerceIn(0f, 1f) * progress.value
                if (barWidth <= 0f) return@drawBehind
                val corner = minOf(radius, barWidth / 2f, size.height / 2f)
                drawRoundRect(
                    color = color,
                    topLeft = Offset.Zero,
                    size = Size(barWidth, size.height),
                    cornerRadius = CornerRadius(corner, corner),
                )
            },
    )
}

/**
 * 趋势柱状图（规范 §5.3）。**自绘 Compose `Canvas`，不引任何图表库。**
 *
 * - 范围 ≤ 31 天按日一根柱，> 31 天按月一根柱（粒度由 `ReportAggregator.granularityFor` 决定）
 * - 柱色 `Ink`、4dp 圆角、间距 8dp（柱太多时自动收窄，保证柱宽 ≥ 1dp）
 * - **无坐标轴、无网格线**，只在底部两端标注起止日期
 * - 周期未结束不补零到周期末尾：逐桶铺满 `range` 自然就只画到今天
 */
@Composable
internal fun TrendSection(
    points: List<TrendPoint>,
    startLabel: String,
    endLabel: String,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(points) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = Motion.Chart, easing = Motion.Ease))
    }
    val density = LocalDensity.current
    val radius = with(density) { Radius.Bar.toPx() }
    val gapPx = with(density) { TrendBarGap.toPx() }
    val maxBarPx = with(density) { TrendMaxBarWidth.toPx() }
    val minBarPx = with(density) { TrendMinBarHeight.toPx() }

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(text = stringResource(R.string.report_trend_title))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(TrendChartHeight),
        ) {
            if (points.isEmpty()) return@Canvas
            val maxCents = points.maxOf { it.amountCents }
            if (maxCents <= 0L) return@Canvas

            val count = points.size
            val spacing = if (count > 1) {
                minOf(gapPx, (size.width - count) / (count - 1)).coerceAtLeast(0f)
            } else {
                0f
            }
            val barWidth = minOf(
                ((size.width - spacing * (count - 1)) / count).coerceAtLeast(1f),
                maxBarPx,
            )
            val contentWidth = barWidth * count + spacing * (count - 1)
            val startX = (size.width - contentWidth) / 2f
            val scale = progress.value

            points.forEachIndexed { index, point ->
                if (point.amountCents <= 0L) return@forEachIndexed
                val ratio = point.amountCents.toFloat() / maxCents.toFloat()
                val barHeight = maxOf(size.height * ratio * scale, minBarPx * scale)
                    .coerceAtMost(size.height)
                if (barHeight <= 0f) return@forEachIndexed
                val corner = minOf(radius, barWidth / 2f, barHeight / 2f)
                drawRoundRect(
                    color = AppColor.Ink,
                    topLeft = Offset(startX + index * (barWidth + spacing), size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(corner, corner),
                )
            }
        }
        Box(modifier = Modifier.height(Space.S))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = startLabel, style = AppType.Caption, color = AppColor.Muted)
            Text(text = endLabel, style = AppType.Caption, color = AppColor.Muted)
        }
    }
}

@StringRes
private fun presetLabelRes(preset: ReportPreset): Int = when (preset) {
    ReportPreset.ThisWeek -> R.string.report_range_week
    ReportPreset.ThisMonth -> R.string.report_range_month
    ReportPreset.Last3Months -> R.string.report_range_last3
    ReportPreset.ThisYear -> R.string.report_range_year
    ReportPreset.Custom -> R.string.report_range_custom
}

// ---------------------------------------------------------------------- Preview

private fun previewCategories(): List<Category> = listOf(
    Category(1L, "餐饮", 0, SemanticKeys.Red, 0, true, false),
    Category(2L, "交通", 0, SemanticKeys.Blue, 1, true, false),
    Category(3L, "购物", 0, SemanticKeys.Yellow, 2, true, false),
)

private fun previewSlices(): List<CategorySlice> = listOf(
    CategorySlice(1L, "餐饮", SemanticKeys.Red, 10000, 67, 1f),
    CategorySlice(2L, "交通", SemanticKeys.Blue, 2500, 17, 0.25f),
    CategorySlice(3L, "购物", SemanticKeys.Yellow, 1500, 10, 0.15f),
    CategorySlice(ReportAggregator.MERGED_CATEGORY_ID, "", SemanticKeys.Ink, 1000, 7, 0.1f),
)

@Preview(name = "汇总 / 筛选行与三卡", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun ReportFilterPreview() {
    AppTheme {
        Column(modifier = Modifier.padding(Space.L)) {
            ReportHeader(onExport = {})
            Box(modifier = Modifier.height(Space.L))
            ReportPresetRow(preset = ReportPreset.ThisMonth, customRangeLabel = "", onSelect = {})
            Box(modifier = Modifier.height(Space.M))
            ReportCategoryFilterRow(
                categories = previewCategories(),
                selectedIds = setOf(1L, 3L),
                onSelectAll = {},
                onToggle = {},
            )
            Box(modifier = Modifier.height(Space.L))
            ReportSummaryCards(
                summary = ReportSummary(expenseCents = 15000, incomeCents = 150000, dailyAverageCents = 7500),
            )
            Box(modifier = Modifier.height(Space.M))
            ReportDailyAverageRow(cents = 7500)
        }
    }
}

@Preview(name = "汇总 / 排行与趋势", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun ReportChartPreview() {
    AppTheme {
        Column(modifier = Modifier.padding(Space.L)) {
            ReportSummaryCards(summary = ReportSummary(expenseCents = 15000, incomeCents = 0, dailyAverageCents = 7500))
            Box(modifier = Modifier.height(Space.XXL))
            CategoryRankingSection(slices = previewSlices())
            Box(modifier = Modifier.height(Space.XXL))
            TrendSection(
                points = listOf(
                    TrendPoint(0L, 0L),
                    TrendPoint(1L, 1200L),
                    TrendPoint(2L, 8000L),
                    TrendPoint(3L, 3400L),
                    TrendPoint(4L, 0L),
                    TrendPoint(5L, 5600L),
                    TrendPoint(6L, 2100L),
                    TrendPoint(7L, 9800L),
                ),
                startLabel = "9.01",
                endLabel = "9.29",
            )
        }
    }
}

@Preview(name = "汇总 / 排行空值", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun ReportRankingEmptyPreview() {
    AppTheme {
        Box(modifier = Modifier.padding(Space.L)) {
            CategoryRankingSection(slices = emptyList())
        }
    }
}
