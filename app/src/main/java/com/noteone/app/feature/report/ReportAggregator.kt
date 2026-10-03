// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.feature.report

import com.noteone.app.core.data.model.Category
import com.noteone.app.core.data.model.Direction
import com.noteone.app.core.data.model.Transaction
import com.noteone.app.core.design.SemanticKeys
import com.noteone.app.core.domain.TimeRanges
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 统计聚合的纯函数（任务书 §3）。
 *
 * 这一层**不碰数据库、不碰 Compose、不碰 `LocalDate.now()`**——
 * 时间与天数由调用方传进来，所以单测可以直接给定固定日期断言。
 *
 * 口径（照抄规范 §9.2，不要自行发挥）：
 *
 * | 指标 | 口径 |
 * |---|---|
 * | 支出 | 范围内 `direction = 0` 的 `amountCents` 求和 |
 * | 收入 | 范围内 `direction = 1` 的 `amountCents` 求和 |
 * | 结余 | 收入 − 支出 |
 * | 日均支出 | 总支出 ÷ 范围内已过天数（含今天） |
 * | 分类占比 | 该分类 ÷ 总支出，整数百分比 |
 * | 条形长度 | 该分类 ÷ **最大值**（首条满宽），不是 ÷ 总量 |
 */

/** 趋势图的聚合粒度：范围 ≤ 31 天按日，> 31 天按月（规范 §5.3）。 */
enum class Granularity { DAILY, MONTHLY }

/** 三卡 + 日均支出。 */
data class ReportSummary(
    val expenseCents: Long = 0L,
    val incomeCents: Long = 0L,
    /** 范围内总支出 ÷ 已过天数（含今天），保留到分 */
    val dailyAverageCents: Long = 0L,
) {
    /** 结余 = 收入 − 支出，可能为负。 */
    val balanceCents: Long get() = incomeCents - expenseCents
}

/**
 * 分类排行的一行。
 *
 * `name` 为空串有两种含义，由 [categoryId] 区分：
 * - [ReportAggregator.MERGED_CATEGORY_ID]：排行第 9 名之后合并出来的「其他」桶
 * - `null`：记录没有分类（`categoryId = null`）
 *
 * 这两种情况下**显示名由 UI 用字符串资源补**——聚合层不产出用户可见文案。
 */
data class CategorySlice(
    val categoryId: Long?,
    val name: String,
    val colorKey: String,
    val amountCents: Long,
    /** 该分类 ÷ 总支出，四舍五入的整数百分比 */
    val sharePercent: Int,
    /** 该分类 ÷ 最大值，用于条形长度（首条满宽） */
    val barFraction: Float,
)

/** 趋势图的一根柱。`startMs` 是桶的起点（按日 = 当日 00:00，按月 = 当月 1 日 00:00）。 */
data class TrendPoint(val startMs: Long, val amountCents: Long)

/**
 * 本月预算进度（规范 §5.4）。
 *
 * 口径固定为**本自然月**：预算是「本月支出上限」，拿它去比「本周」或「今年」没有意义。
 * 所以汇总页上方无论选了什么时间范围，这一行都标着「本月预算」。
 *
 * 只在预算开启且限额 > 0 时存在，由 [ReportAggregator.budget] 判空。
 */
data class BudgetProgress(
    val limitCents: Long,
    val spentCents: Long,
) {
    /** 是否超支。 */
    val over: Boolean get() = spentCents > limitCents

    /** 进度条填充比例，**已 clamp 到 0..1**（超支时条子画满，不溢出）。 */
    val ratio: Float get() = (spentCents.toFloat() / limitCents.toFloat()).coerceIn(0f, 1f)

    /** 已用百分比，四舍五入的整数。**刻意不 clamp** —— 超支要能显示 128%。 */
    val percent: Int get() = ((spentCents * 100 + limitCents / 2) / limitCents).toInt()

    /** 剩余额度；超支时是超出金额。符号由 UI 补，这里恒为非负。 */
    val differenceCents: Long get() = if (over) spentCents - limitCents else limitCents - spentCents
}

object ReportAggregator {

    /** 排行默认最多显示 8 条，其余合并进「其他」（规范 §5.3）。 */
    const val DEFAULT_RANKING_LIMIT = 8

    /** 合并桶的伪 id。真实分类 id 是 Room 自增的正数，不会撞。 */
    const val MERGED_CATEGORY_ID = -1L

    /** 超过这个天数就按月聚合。 */
    const val MONTHLY_THRESHOLD_DAYS = 31

    /** 该范围该按日还是按月画柱。 */
    fun granularityFor(range: LongRange, zone: ZoneId = ZoneId.systemDefault()): Granularity =
        if (TimeRanges.daysOf(range, zone) > MONTHLY_THRESHOLD_DAYS) {
            Granularity.MONTHLY
        } else {
            Granularity.DAILY
        }

    /**
     * 三卡 + 日均支出。
     *
     * @param elapsedDays 范围内已过天数（含今天），传 `TimeRanges.elapsedDaysOf()` 的结果。
     *                    分母**不是**自然天数、也不是有记账的天数。
     */
    fun summarize(transactions: List<Transaction>, elapsedDays: Int = 1): ReportSummary {
        var expense = 0L
        var income = 0L
        transactions.forEach { record ->
            if (record.direction == Direction.Income) income += record.amountCents else expense += record.amountCents
        }
        val days = elapsedDays.coerceAtLeast(1)
        // 整数运算完成「保留到分」的四舍五入：全程不碰 Double
        val dailyAverage = (expense + days / 2) / days
        return ReportSummary(
            expenseCents = expense,
            incomeCents = income,
            dailyAverageCents = dailyAverage,
        )
    }

    /**
     * 分类排行，**只统计支出**（收入不参与排行）。按金额降序，超过 [limit] 条的合并进「其他」。
     *
     * @param categories **要传含归档的全量分类**，否则已归档分类的历史记录会丢掉名字与色点。
     */
    fun byCategory(
        transactions: List<Transaction>,
        categories: List<Category>,
        limit: Int = DEFAULT_RANKING_LIMIT,
    ): List<CategorySlice> {
        val expenseRecords = transactions.filter { it.direction == Direction.Expense }
        val total = expenseRecords.sumOf { it.amountCents }
        if (total <= 0L) return emptyList()

        val categoryById = categories.associateBy { it.id }
        val ranked = expenseRecords
            .groupBy { it.categoryId }
            .map { (categoryId, records) ->
                val category = categoryId?.let(categoryById::get)
                CategorySlice(
                    categoryId = categoryId,
                    name = category?.name.orEmpty(),
                    colorKey = category?.colorKey ?: SemanticKeys.Ink,
                    amountCents = records.sumOf { it.amountCents },
                    sharePercent = 0,
                    barFraction = 0f,
                )
            }
            .sortedWith(compareByDescending<CategorySlice> { it.amountCents }.thenBy { it.name })

        val displayed = if (limit <= 0 || ranked.size <= limit) ranked else mergeBeyondLimit(ranked, limit)
        val max = displayed.maxOf { it.amountCents }

        return displayed.map { slice ->
            slice.copy(
                sharePercent = percentOf(slice.amountCents, total),
                barFraction = if (max <= 0L) 0f else slice.amountCents.toFloat() / max.toFloat(),
            )
        }
    }

    /**
     * 趋势柱：**按 [range] 逐桶铺满**，没有记录的桶出 0 值。
     *
     * 「周期未结束不补零到周期末尾」是免费得到的——`TimeRanges.resolve()` 的 `last`
     * 就是今天最后一刻，逐桶铺开自然只有已过的那些天。
     *
     * 与排行一致，趋势图统计的是**支出**。
     */
    fun trend(
        transactions: List<Transaction>,
        range: LongRange,
        granularity: Granularity,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<TrendPoint> {
        val expenseRecords = transactions.filter { it.direction == Direction.Expense }
        val startDate = TimeRanges.toLocalDate(range.first, zone)
        val endDate = TimeRanges.toLocalDate(range.last, zone)
        if (endDate.isBefore(startDate)) return emptyList()

        return when (granularity) {
            Granularity.DAILY -> {
                val buckets = LinkedHashMap<LocalDate, Long>()
                var date = startDate
                while (!date.isAfter(endDate)) {
                    buckets[date] = 0L
                    date = date.plusDays(1)
                }
                expenseRecords.forEach { record ->
                    val key = TimeRanges.toLocalDate(record.occurredAt, zone)
                    if (buckets.containsKey(key)) buckets[key] = buckets.getValue(key) + record.amountCents
                }
                buckets.map { (date, cents) -> TrendPoint(TimeRanges.startOfDay(date, zone), cents) }
            }

            Granularity.MONTHLY -> {
                val buckets = LinkedHashMap<YearMonth, Long>()
                val lastMonth = YearMonth.from(endDate)
                var month = YearMonth.from(startDate)
                while (!month.isAfter(lastMonth)) {
                    buckets[month] = 0L
                    month = month.plusMonths(1)
                }
                expenseRecords.forEach { record ->
                    val key = YearMonth.from(TimeRanges.toLocalDate(record.occurredAt, zone))
                    if (buckets.containsKey(key)) buckets[key] = buckets.getValue(key) + record.amountCents
                }
                buckets.map { (month, cents) -> TrendPoint(TimeRanges.startOfDay(month.atDay(1), zone), cents) }
            }
        }
    }

    /**
     * 组装本月预算进度。**预算没开、或限额为 0 时返回 `null`**，
     * 调用方据此整块不渲染——不要渲染一条 0% 的空条子。
     *
     * @param spentCents 本自然月 1 日至今的**支出**合计
     */
    fun budget(enabled: Boolean, limitCents: Long, spentCents: Long): BudgetProgress? {
        if (!enabled || limitCents <= 0L) return null
        return BudgetProgress(limitCents = limitCents, spentCents = spentCents.coerceAtLeast(0L))
    }

    /** `amount / total` 的整数百分比（四舍五入）。 */
    private fun percentOf(amountCents: Long, totalCents: Long): Int {
        if (totalCents <= 0L) return 0
        return ((amountCents * 100 + totalCents / 2) / totalCents).toInt()
    }

    private fun mergeBeyondLimit(ranked: List<CategorySlice>, limit: Int): List<CategorySlice> {
        val head = ranked.take(limit)
        val tail = ranked.drop(limit)
        val merged = CategorySlice(
            categoryId = MERGED_CATEGORY_ID,
            name = "",
            colorKey = SemanticKeys.Ink,
            amountCents = tail.sumOf { it.amountCents },
            sharePercent = 0,
            barFraction = 0f,
        )
        return head + merged
    }
}
