// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.feature.ledger

import com.noteone.app.core.common.DateFormats
import com.noteone.app.core.data.model.Direction
import com.noteone.app.core.data.model.Transaction
import com.noteone.app.core.domain.TimeRanges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * 账单页的纯函数测试：分组、小计、筛选、搜索、月份区间。
 *
 * 这些函数不碰数据库也不碰 Compose，所以能直接断言——「业务计算放纯函数」就是为了这个。
 */
class LedgerGroupingTest {

    private val today: LocalDate = LocalDate.now()
    private val yesterday: LocalDate = today.minusDays(1)

    private fun tx(
        id: Long,
        date: LocalDate,
        cents: Long,
        direction: Int,
        note: String = "",
        categoryId: Long? = 1L,
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

    /** 已按 `occurredAt DESC` 排好，与 DAO 的返回顺序一致。 */
    private fun sample(): List<Transaction> = listOf(
        tx(1, today, 8600, Direction.Expense, "日常采购", 3L),
        tx(2, today, 150000, Direction.Income, "九月生活费", 9L),
        tx(3, today, 1400, Direction.Expense, "食堂", 1L),
        tx(4, yesterday, 400, Direction.Expense, "", 2L),
    )

    @Test
    fun `按自然日分组并保持倒序`() {
        val groups = groupByDay(sample(), LedgerFilter.All, "")

        assertEquals(2, groups.size)
        assertEquals(
            DateFormats.monthDayWithYearIfNeeded(TimeRanges.startOfDay(today)),
            groups[0].dateLabel,
        )
        assertEquals(
            DateFormats.monthDayWithYearIfNeeded(TimeRanges.startOfDay(yesterday)),
            groups[1].dateLabel,
        )
        assertEquals(3, groups[0].items.size)
        assertEquals(1, groups[1].items.size)
    }

    @Test
    fun `组头小计分别算支出与收入`() {
        val groups = groupByDay(sample(), LedgerFilter.All, "")

        // 今日：14.00 + 86.00 支出；1500.00 收入
        assertEquals(10000L, groups[0].expenseCents)
        assertEquals(150000L, groups[0].incomeCents)
        assertEquals(400L, groups[1].expenseCents)
        assertEquals(0L, groups[1].incomeCents)
    }

    @Test
    fun `筛支出时收入记不进来`() {
        val groups = groupByDay(sample(), LedgerFilter.Expense, "")

        assertEquals(2, groups.size)
        assertEquals(10000L, groups[0].expenseCents)
        assertEquals(0L, groups[0].incomeCents)
        assertTrue(groups.all { group -> group.items.all { it.direction == Direction.Expense } })
    }

    @Test
    fun `筛收入时只剩收入记录`() {
        val groups = groupByDay(sample(), LedgerFilter.Income, "")

        assertEquals(1, groups.size)
        assertEquals(150000L, groups[0].incomeCents)
        assertEquals(1, groups[0].items.size)
    }

    @Test
    fun `关键词搜备注`() {
        val groups = groupByDay(sample(), LedgerFilter.All, "食堂")

        assertEquals(1, groups.size)
        assertEquals(1, groups[0].items.size)
        assertEquals(1400L, groups[0].items[0].amountCents)
    }

    @Test
    fun `金额区间搜金额`() {
        // 10-50 元 → 命中 14.00
        val groups = groupByDay(sample(), LedgerFilter.All, "10-50")

        assertEquals(1, groups.size)
        assertEquals(1400L, groups[0].items.single().amountCents)
    }

    @Test
    fun `关键词搜不到就是空列表`() {
        assertTrue(groupByDay(sample(), LedgerFilter.All, "星巴克").isEmpty())
    }

    @Test
    fun `金额区间解析`() {
        assertEquals(1000L..5000L, parseAmountRange("10-50"))
        assertEquals(1000L..5000L, parseAmountRange("50-10"))
        assertEquals(1000L..5000L, parseAmountRange("10 ~ 50"))
        assertEquals(1000L..5000L, parseAmountRange("10至50"))
        assertEquals(1050L..5000L, parseAmountRange("10.5-50"))
        assertNull(parseAmountRange("咖啡"))
        assertNull(parseAmountRange("50"))
        assertNull(parseAmountRange("-50"))
        assertNull(parseAmountRange("10-"))
    }

    @Test
    fun `月份区间取整月而不是到今天`() {
        val range = fullMonthRange(YearMonth.of(2026, 2))

        assertEquals(TimeRanges.startOfDay(LocalDate.of(2026, 2, 1)), range.first)
        assertEquals(TimeRanges.endOfDay(LocalDate.of(2026, 2, 28)), range.last)
    }

    @Test
    fun `闰年二月有 29 天`() {
        val range = fullMonthRange(YearMonth.of(2028, 2))

        assertEquals(TimeRanges.endOfDay(LocalDate.of(2028, 2, 29)), range.last)
    }
}
