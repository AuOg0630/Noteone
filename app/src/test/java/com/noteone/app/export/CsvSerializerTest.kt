// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.export

import com.noteone.app.core.data.model.Category
import com.noteone.app.core.data.model.Direction
import com.noteone.app.core.data.model.Transaction
import com.noteone.app.core.data.model.TransactionSource
import com.noteone.app.core.design.SemanticKeys
import com.noteone.app.core.domain.TimeRanges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * CSV 序列化测试。
 *
 * 重点是四条最容易做错的规格：BOM、字段转义、金额为纯数字、`\r\n` 行分隔。
 */
class CsvSerializerTest {

    private val categories: Map<Long, Category> = mapOf(
        1L to Category(1L, "餐饮", 0, SemanticKeys.Red, 0, true, false),
        2L to Category(2L, "交通", 0, SemanticKeys.Blue, 1, true, false),
    )

    private val bookNames: Map<Long, String> = mapOf(1L to "我的账本")

    private fun at(day: Int, hour: Int, minute: Int): Long =
        TimeRanges.startOfDay(LocalDate.of(2026, 9, day)) + hour * 3_600_000L + minute * 60_000L

    private fun tx(
        id: Long,
        occurredAt: Long,
        cents: Long,
        direction: Int = Direction.Expense,
        categoryId: Long? = 1L,
        note: String = "",
        source: Int = TransactionSource.Manual,
    ): Transaction = Transaction(
        id = id,
        bookId = 1L,
        amountCents = cents,
        direction = direction,
        categoryId = categoryId,
        note = note,
        occurredAt = occurredAt,
        source = source,
        deletedAt = null,
    )

    @Test
    fun `文件以 BOM 和表头开头`() {
        val csv = CsvSerializer.serialize(listOf(tx(1, at(29, 12, 30), 1400, note = "食堂")), categories, bookNames)

        assertTrue(csv.startsWith("\uFEFF" + CsvSerializer.HEADER + "\r\n"))
        assertEquals("日期,时间,类型,分类,金额,备注,来源,账本", CsvSerializer.HEADER)
    }

    @Test
    fun `一行的字段与顺序`() {
        val row = CsvSerializer.rowOf(tx(1, at(29, 12, 30), 1400, note = "食堂"), "餐饮", "我的账本")

        assertEquals("2026-09-29,12:30,支出,餐饮,14.00,食堂,手动,我的账本", row)
    }

    @Test
    fun `金额是纯数字不带符号也不带千分位`() {
        val row = CsvSerializer.rowOf(tx(1, at(29, 8, 10), 150000), "餐饮", "我的账本")

        assertTrue(row.contains(",1500.00,"))
        assertFalse(row.contains("¥"))
        assertFalse(row.contains("1,500.00"))
    }

    @Test
    fun `含逗号与引号的字段被包裹并转义`() {
        val row = CsvSerializer.rowOf(tx(1, at(29, 12, 30), 1400, note = "食堂,二楼\"加饭\""), "餐饮", "我的账本")

        assertTrue(row.endsWith(",\"食堂,二楼\"\"加饭\"\"\",手动,我的账本"))
    }

    @Test
    fun `未分类的记录分类列留空`() {
        val row = CsvSerializer.rowOf(tx(1, at(29, 12, 30), 1400, categoryId = null), "", "我的账本")

        assertEquals("2026-09-29,12:30,支出,,14.00,,手动,我的账本", row)
    }

    @Test
    fun `收入与来源列的中文映射`() {
        val income = CsvSerializer.rowOf(
            tx(1, at(29, 8, 10), 150000, Direction.Income, 2L, source = TransactionSource.TileOcr),
            "交通",
            "我的账本",
        )
        val quick = CsvSerializer.rowOf(
            tx(2, at(29, 8, 11), 400, source = TransactionSource.QuickAction),
            "交通",
            "我的账本",
        )

        assertTrue(income.startsWith("2026-09-29,08:10,收入,交通,1500.00,"))
        assertTrue(income.endsWith(",磁贴识别,我的账本"))
        assertTrue(quick.endsWith(",快捷按钮,我的账本"))
    }

    @Test
    fun `行分隔统一是 CRLF`() {
        val csv = CsvSerializer.serialize(
            listOf(tx(1, at(29, 12, 30), 1400, note = "食堂"), tx(2, at(29, 8, 10), 400, categoryId = 2L)),
            categories,
            bookNames,
        )

        // 表头 1 行 + 记录 2 行 = 3 个行尾
        assertEquals(3, Regex("\r\n").findAll(csv).count())
        // 没有落单的 \n
        assertEquals(0, Regex("[^\r]\n").findAll(csv).count())
    }

    @Test
    fun `默认文件名带时间戳`() {
        val fileName = defaultCsvFileName(TimeRanges.startOfDay(LocalDate.of(2026, 9, 29)) + 21 * 3_600_000L + 30 * 60_000L)

        assertEquals("记账_20260929_2130.csv", fileName)
    }

    @Test
    fun `空数据也要有表头`() {
        val csv = CsvSerializer.serialize(emptyList(), categories, bookNames)

        assertEquals("\uFEFF" + CsvSerializer.HEADER + "\r\n", csv)
    }
}
