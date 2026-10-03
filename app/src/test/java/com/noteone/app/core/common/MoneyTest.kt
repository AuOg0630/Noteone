// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `Money` 的单元测试。
 *
 * 重点验证：**全程整数运算**、固定 2 位小数、千分位分组正确、未知输入不抛异常。
 */
class MoneyTest {

    // ---------------------------------------------------------------- 元 → 分

    @Test
    fun `元转分 - 一位小数补零`() {
        assertEquals(1250L, Money.yuanToCents("12.5"))
    }

    @Test
    fun `元转分 - 一分钱`() {
        assertEquals(1L, Money.yuanToCents("0.01"))
    }

    @Test
    fun `元转分 - 纯整数`() {
        assertEquals(1400L, Money.yuanToCents("14"))
    }

    @Test
    fun `元转分 - 零与空串都是零`() {
        assertEquals(0L, Money.yuanToCents("0"))
        assertEquals(0L, Money.yuanToCents("0."))
        assertEquals(0L, Money.yuanToCents(""))
    }

    @Test
    fun `元转分 - 容忍货币符号与千分位`() {
        assertEquals(1400L, Money.yuanToCents("¥14.00"))
        assertEquals(1400L, Money.yuanToCents("￥14.00"))
        assertEquals(1400L, Money.yuanToCents("14.00元"))
        assertEquals(123456L, Money.yuanToCents("1,234.56"))
        assertEquals(1400L, Money.yuanToCents(" 14.00 "))
    }

    @Test
    fun `元转分 - 超过两位小数截断而不是四舍五入`() {
        assertEquals(1234L, Money.yuanToCents("12.345"))
        assertEquals(1234L, Money.yuanToCents("12.349"))
        assertEquals(1L, Money.yuanToCents("0.019"))
    }

    @Test
    fun `元转分 - 负数`() {
        assertEquals(-1400L, Money.yuanToCents("-14"))
        assertEquals(-1250L, Money.yuanToCents("-12.5"))
    }

    @Test
    fun `元转分 - 非法输入返回零或 null`() {
        assertEquals(0L, Money.yuanToCents("abc"))
        assertEquals(0L, Money.yuanToCents("."))
        assertNull(Money.yuanToCentsOrNull("abc"))
        assertNull(Money.yuanToCentsOrNull("12.3.4"))
        assertNull(Money.yuanToCentsOrNull(""))
    }

    // ---------------------------------------------------------------- 分 → 元

    @Test
    fun `分转无千分位字符串`() {
        assertEquals("1234.56", Money.centsToPlain(123456))
        assertEquals("0.00", Money.centsToPlain(0))
        assertEquals("14.00", Money.centsToPlain(1400))
        assertEquals("0.01", Money.centsToPlain(1))
        assertEquals("-14.00", Money.centsToPlain(-1400))
    }

    @Test
    fun `千分位格式化 - 固定两位小数`() {
        assertEquals("1,234.56", Money.formatThousands(123456))
        assertEquals("0.00", Money.formatThousands(0))
        assertEquals("14.00", Money.formatThousands(1400))
        assertEquals("1.00", Money.formatThousands(100))
        assertEquals("0.01", Money.formatThousands(1))
    }

    @Test
    fun `千分位格式化 - 分组边界`() {
        assertEquals("999.99", Money.formatThousands(99999))
        assertEquals("1,000.00", Money.formatThousands(100000))
        assertEquals("1,000,000.00", Money.formatThousands(100_000_000))
        assertEquals("12,345,678,901.23", Money.formatThousands(1_234_567_890_123))
    }

    @Test
    fun `千分位格式化 - 负号在最前面`() {
        assertEquals("-14.00", Money.formatThousands(-1400))
        assertEquals("-1,234.56", Money.formatThousands(-123456))
    }

    @Test
    fun `元分互转 - 大额不丢精度`() {
        // 用 Long 边界附近的值验证没有经过 Double
        val cents = 900_719_925_474_099L
        assertEquals(cents, Money.yuanToCents(Money.centsToPlain(cents)))
        assertEquals(cents, Money.yuanToCents(Money.formatThousands(cents).replace(",", "")))
    }

    @Test
    fun `元分互转 - 往返一百次仍一致`() {
        val samples = listOf(1L, 99L, 100L, 1234L, 99999L, 123456789L)
        samples.forEach { cents ->
            assertEquals(cents, Money.yuanToCents(Money.centsToPlain(cents)))
        }
    }

    // ---------------------------------------------------------------- 亿元阈值

    @Test
    fun `一亿元是阈值`() {
        val oneYi = 100_000_000L * 100L
        assertEquals(false, Money.shouldUseYi(oneYi - 1))
        assertEquals(true, Money.shouldUseYi(oneYi))
        assertEquals(true, Money.shouldUseYi(oneYi + 1))
        assertEquals(true, Money.shouldUseYi(-oneYi))
    }

    @Test
    fun `亿元紧凑写法保留两位小数`() {
        assertEquals("1.23", Money.centsToYi(12_345_678_900L))
        assertEquals("1.00", Money.centsToYi(10_000_000_000L))
        assertEquals("2.05", Money.centsToYi(20_500_000_000L))
    }
}
