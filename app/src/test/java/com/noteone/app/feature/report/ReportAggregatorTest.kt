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
import com.noteone.app.core.domain.TimeRange
import com.noteone.app.core.domain.TimeRanges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 汇总统计的口径测试（任务书 §3 要求「给定 2026-09-01 至 2026-09-02 的固定数据集」）。
 *
 * 全部用固定日期，**不取 `LocalDate.now()`**——否则测试会随运行日期变化。
 */
class ReportAggregatorTest {

    /** 固定「今天」。 */
    private val today: LocalDate = LocalDate.of(2026, 9, 2)

    private val sep1: LocalDate = LocalDate.of(2026, 9, 1)
    private val sep2: LocalDate = LocalDate.of(2026, 9, 2)

    private val 餐饮 = Category(1L, "餐饮", 0, SemanticKeys.Red, 0, true, false)
    private val 交通 = Category(2L, "交通", 0, SemanticKeys.Blue, 1, true, false)
    private val 购物 = Category(3L, "购物", 0, SemanticKeys.Yellow, 2, true, false)
    private val 学习 = Category(4L, "学习", 0, SemanticKeys.Clay, 3, true, false)
    private val 生活费 = Category(9L, "生活费", 1, SemanticKeys.Green, 0, true, false)

    private val categories: List<Category> = listOf(餐饮, 交通, 购物, 学习, 生活费)

    private fun tx(
        id: Long,
        date: LocalDate,
        cents: Long,
        direction: Int,
        categoryId: Long?,
        note: String = "",
    ): Transaction = Transaction(
        id = id,
        bookId = 1L,
        amountCents = cents,
        direction = direction,
        categoryId = categoryId,
        note = note,
        occurredAt = TimeRanges.startOfDay(date) + 12 * 60 * 60 * 1000L,
        source = 0,
        deletedAt = null,
    )

    /**
     * 9/1：餐饮 50.00、交通 25.00、购物 15.00、学习 10.00，收入生活费 1,500.00
     * 9/2：餐饮 50.00
     *
     * 合计支出 150.00、收入 1,500.00、结余 1,350.00。
     */
    private fun sample(): List<Transaction> = listOf(
        tx(1, sep1, 5000, Direction.Expense, 1L, "食堂"),
        tx(2, sep1, 2500, Direction.Expense, 2L, "地铁公交"),
        tx(3, sep1, 1500, Direction.Expense, 3L, "日用品"),
        tx(4, sep1, 1000, Direction.Expense, 4L, "打印复印"),
        tx(5, sep1, 150000, Direction.Income, 9L, "九月生活费"),
        tx(6, sep2, 5000, Direction.Expense, 1L, "食堂"),
    )

    // ------------------------------------------------------------------ 时间范围

    @Test
    fun `本月范围是 9月1日到9月2日而不是近 30 天`() {
        val range = TimeRanges.resolve(TimeRange.ThisMonth, today)

        assertEquals(TimeRanges.startOfDay(sep1), range.first)
        assertEquals(TimeRanges.endOfDay(sep2), range.last)
        assertEquals(2, TimeRanges.daysOf(range))
        assertNotEquals(TimeRanges.startOfDay(today.minusDays(29)), range.first)
    }

    // ------------------------------------------------------------------ 三卡与日均

    @Test
    fun `三卡数值`() {
        val summary = ReportAggregator.summarize(sample(), elapsedDays = 2)

        assertEquals(15000L, summary.expenseCents)
        assertEquals(150000L, summary.incomeCents)
        assertEquals(135000L, summary.balanceCents)
    }

    @Test
    fun `日均支出的分母是已过天数`() {
        val records = sample()

        assertEquals(7500L, ReportAggregator.summarize(records, elapsedDays = 2).dailyAverageCents)
        // 范围是 9/1–9/2，分母就是 2；分母换成自然天数或有记账的天数都不对
        assertEquals(5000L, ReportAggregator.summarize(records, elapsedDays = 3).dailyAverageCents)
        assertEquals(15000L, ReportAggregator.summarize(records, elapsedDays = 1).dailyAverageCents)
    }

    @Test
    fun `日均支出保留到分做四舍五入`() {
        val records = listOf(tx(1, sep1, 100, Direction.Expense, 1L))

        // 1.00 元 ÷ 3 天 = 0.333… 元 → 0.33 元
        assertEquals(33L, ReportAggregator.summarize(records, elapsedDays = 3).dailyAverageCents)
    }

    @Test
    fun `分母为 0 时按 1 天算不炸`() {
        val summary = ReportAggregator.summarize(sample(), elapsedDays = 0)

        assertEquals(15000L, summary.dailyAverageCents)
    }

    // ------------------------------------------------------------------ 分类排行

    @Test
    fun `排行按金额降序且占比按总量条形按最大值`() {
        val slices = ReportAggregator.byCategory(sample(), categories)

        assertEquals(4, slices.size)
        assertEquals(listOf("餐饮", "交通", "购物", "学习"), slices.map { it.name })

        // 金额
        assertEquals(listOf(10000L, 2500L, 1500L, 1000L), slices.map { it.amountCents })
        // 占比 ÷ 总量 15000（整数百分比）
        assertEquals(listOf(67, 17, 10, 7), slices.map { it.sharePercent })
        // 条形 ÷ 最大值 10000 —— 首条满宽，这不是「占比」
        assertEquals(1f, slices[0].barFraction, 0.0001f)
        assertEquals(0.25f, slices[1].barFraction, 0.0001f)
        assertEquals(0.15f, slices[2].barFraction, 0.0001f)
        assertEquals(0.10f, slices[3].barFraction, 0.0001f)
    }

    @Test
    fun `排行只统计支出收入不参与`() {
        val slices = ReportAggregator.byCategory(sample(), categories)

        assertTrue(slices.none { it.name == "生活费" })
        assertEquals(15000L, slices.sumOf { it.amountCents })
    }

    @Test
    fun `只有收入时没有排行`() {
        val onlyIncome = listOf(tx(1, sep1, 150000, Direction.Income, 9L))

        assertTrue(ReportAggregator.byCategory(onlyIncome, categories).isEmpty())
    }

    @Test
    fun `超过 8 条时其余合并进其他`() {
        // 10 个支出分类，金额 1000 / 900 / … / 100
        val manyCategories = (1..10).map { index ->
            Category(index.toLong(), "分类$index", 0, SemanticKeys.Ink, index, true, false)
        }
        val records = manyCategories.mapIndexed { index, category ->
            tx(index + 1L, sep1, (1000 - index * 100).toLong(), Direction.Expense, category.id)
        }

        val slices = ReportAggregator.byCategory(records, manyCategories)

        assertEquals(9, slices.size)
        assertEquals(ReportAggregator.MERGED_CATEGORY_ID, slices.last().categoryId)
        // 第 9、10 名：200 + 100
        assertEquals(300L, slices.last().amountCents)
        assertTrue(slices.last().name.isEmpty())
        assertEquals(1f, slices.first().barFraction, 0.0001f)
    }

    @Test
    fun `未分类的记录也能进排行`() {
        val records = listOf(
            tx(1, sep1, 5000, Direction.Expense, null),
            tx(2, sep1, 5000, Direction.Expense, 1L),
        )

        val slices = ReportAggregator.byCategory(records, categories)

        assertEquals(2, slices.size)
        // 名字交给 UI 用字符串资源补，聚合层不产出用户可见文案
        assertTrue(slices.any { it.categoryId == null && it.name.isEmpty() })
    }

    // ------------------------------------------------------------------ 趋势

    @Test
    fun `本年本月趋势只有已过的两根柱`() {
        val range = TimeRanges.resolve(TimeRange.ThisMonth, today)

        val points = ReportAggregator.trend(sample(), range, Granularity.DAILY)

        assertEquals(2, points.size)
        assertEquals(TimeRanges.startOfDay(sep1), points[0].startMs)
        assertEquals(10000L, points[0].amountCents)
        assertEquals(TimeRanges.startOfDay(sep2), points[1].startMs)
        assertEquals(5000L, points[1].amountCents)
        // 不补零到 9 月 30 日
        assertNotEquals(30, points.size)
    }

    @Test
    fun `按日粒度时没有记录的那天是 0`() {
        val range = TimeRanges.resolve(TimeRange.Custom(sep1, LocalDate.of(2026, 9, 4)), today)

        val points = ReportAggregator.trend(sample(), range, Granularity.DAILY)

        assertEquals(4, points.size)
        assertEquals(listOf(10000L, 5000L, 0L, 0L), points.map { it.amountCents })
    }

    @Test
    fun `超过 31 天自动按月聚合`() {
        val aug = TimeRanges.resolve(TimeRange.Custom(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)), today)
        val julAug = TimeRanges.resolve(TimeRange.Custom(LocalDate.of(2026, 7, 31), LocalDate.of(2026, 8, 31)), today)
        val thisYear = TimeRanges.resolve(TimeRange.ThisYear, today)

        assertEquals(31, TimeRanges.daysOf(aug))
        assertEquals(Granularity.DAILY, ReportAggregator.granularityFor(aug))
        assertEquals(32, TimeRanges.daysOf(julAug))
        assertEquals(Granularity.MONTHLY, ReportAggregator.granularityFor(julAug))
        assertEquals(Granularity.MONTHLY, ReportAggregator.granularityFor(thisYear))
    }

    @Test
    fun `按月聚合只统计支出`() {
        val range = TimeRanges.resolve(TimeRange.ThisYear, today)

        val points = ReportAggregator.trend(sample(), range, Granularity.MONTHLY)

        // 1 月到 9 月，共 9 根
        assertEquals(9, points.size)
        assertEquals(TimeRanges.startOfDay(LocalDate.of(2026, 1, 1)), points.first().startMs)
        assertEquals(TimeRanges.startOfDay(LocalDate.of(2026, 9, 1)), points.last().startMs)
        // 1 月没有记录
        assertEquals(0L, points.first().amountCents)
        // 9 月：50.00 + 50.00（收入 1,500.00 不计入）
        assertEquals(15000L, points.last().amountCents)
    }

    @Test
    fun `空数据集不炸`() {
        val range = TimeRanges.resolve(TimeRange.ThisMonth, today)

        val summary = ReportAggregator.summarize(emptyList(), elapsedDays = 2)
        assertEquals(0L, summary.expenseCents)
        assertEquals(0L, summary.incomeCents)
        assertEquals(0L, summary.balanceCents)
        assertEquals(0L, summary.dailyAverageCents)
        assertTrue(ReportAggregator.byCategory(emptyList(), categories).isEmpty())
        // 桶还是要铺满（2 天 2 根零柱），这样图表的时间轴不会变形
        assertEquals(2, ReportAggregator.trend(emptyList(), range, Granularity.DAILY).size)
    }

    // ------------------------------------------------------------------ 本月预算

    @Test
    fun `预算没开或限额为 0 时不产出这一行`() {
        assertNull(ReportAggregator.budget(enabled = false, limitCents = 60000L, spentCents = 15000L))
        assertNull(ReportAggregator.budget(enabled = true, limitCents = 0L, spentCents = 15000L))
        assertNull(ReportAggregator.budget(enabled = true, limitCents = -1L, spentCents = 0L))
    }

    @Test
    fun `预算未超支时给出已用百分比与剩余额度`() {
        // 已用 150.00 / 上限 600.00 = 25%
        val progress = ReportAggregator.budget(enabled = true, limitCents = 60000L, spentCents = 15000L)!!

        assertNotEquals(true, progress.over)
        assertEquals(25, progress.percent)
        assertEquals(0.25f, progress.ratio, 0.0001f)
        assertEquals(45000L, progress.differenceCents)
    }

    @Test
    fun `预算超支时百分比不封顶且给出超出金额`() {
        // 已用 768.00 / 上限 600.00 = 128%，条子画满但不溢出
        val progress = ReportAggregator.budget(enabled = true, limitCents = 60000L, spentCents = 76800L)!!

        assertTrue(progress.over)
        assertEquals(128, progress.percent)
        assertEquals(1f, progress.ratio, 0.0001f)
        assertEquals(16800L, progress.differenceCents)
    }

    @Test
    fun `预算刚用满不算超支`() {
        val progress = ReportAggregator.budget(enabled = true, limitCents = 60000L, spentCents = 60000L)!!

        assertNotEquals(true, progress.over)
        assertEquals(100, progress.percent)
        assertEquals(0L, progress.differenceCents)
    }

    @Test
    fun `百分比四舍五入到整数`() {
        // 1.00 / 3.00 = 33.33% → 33；1.50 / 3.00 = 50%
        assertEquals(33, ReportAggregator.budget(true, 300L, 100L)!!.percent)
        assertEquals(50, ReportAggregator.budget(true, 300L, 150L)!!.percent)
    }
}
