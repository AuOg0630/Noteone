// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自绘键盘输入规则的单元测试。
 *
 * 覆盖规范 §6.1 的全部规则：9 位整数上限、2 位小数上限、小数点不重复、
 * 「00」在 0 时不响应、前导 0 自动替换。
 */
class AmountInputRulesTest {

    // ---------------------------------------------------------------- 前导零

    @Test
    fun `空串按数字直接进入`() {
        assertEquals("5", AmountInputRules.accept("", "5"))
    }

    @Test
    fun `前导零被替换而不是叠加`() {
        assertEquals("5", AmountInputRules.accept("0", "5"))
        assertEquals("0", AmountInputRules.accept("0", "0"))
    }

    @Test
    fun `零之后先打小数点再打数字`() {
        assertEquals("0.", AmountInputRules.accept("0", "."))
        assertEquals("0.5", AmountInputRules.accept("0.", "5"))
    }

    // ---------------------------------------------------------------- 小数点

    @Test
    fun `空串按小数点补出 0`() {
        assertEquals("0.", AmountInputRules.accept("", "."))
    }

    @Test
    fun `已有小数点时再按小数点不响应`() {
        assertEquals("12.5", AmountInputRules.accept("12.5", "."))
        assertEquals("12.", AmountInputRules.accept("12.", "."))
    }

    // ---------------------------------------------------------------- 位数上限

    @Test
    fun `整数位上限是 9 位`() {
        assertEquals("12345678", AmountInputRules.accept("1234567", "8"))
        assertEquals("123456789", AmountInputRules.accept("12345678", "9"))
        // 第 10 位不再响应
        assertEquals("123456789", AmountInputRules.accept("123456789", "0"))
    }

    @Test
    fun `小数位上限是 2 位`() {
        assertEquals("12.34", AmountInputRules.accept("12.3", "4"))
        // 第 3 位小数不再响应
        assertEquals("12.34", AmountInputRules.accept("12.34", "5"))
    }

    // ---------------------------------------------------------------- 双零键

    @Test
    fun `金额为零时双零键不响应`() {
        assertEquals("", AmountInputRules.accept("", "00"))
        assertEquals("0", AmountInputRules.accept("0", "00"))
        assertEquals("0.", AmountInputRules.accept("0.", "00"))
    }

    @Test
    fun `双零键等价于连按两次零`() {
        assertEquals("100", AmountInputRules.accept("1", "00"))
        assertEquals("1,000", AmountInputRules.toDisplay(AmountInputRules.accept("10", "00")))
        assertEquals("12.00", AmountInputRules.accept("12.", "00"))
    }

    @Test
    fun `双零键在只剩一位小数位时只补一个零`() {
        assertEquals("1.20", AmountInputRules.accept("1.2", "00"))
        assertEquals("1.00", AmountInputRules.accept("1.0", "00"))
    }

    @Test
    fun `双零键不会越过整数位上限`() {
        assertEquals("123456789", AmountInputRules.accept("123456789", "00"))
        // 7 位时补两个零 → 9 位，刚好卡在上限
        assertEquals("123456700", AmountInputRules.accept("1234567", "00"))
        // 8 位时只补得进一个零
        assertEquals("123456780", AmountInputRules.accept("12345678", "00"))
    }

    // ---------------------------------------------------------------- 退格

    @Test
    fun `退格逐位删除`() {
        assertEquals("12.3", AmountInputRules.backspace("12.34"))
        assertEquals("1.", AmountInputRules.backspace("1.0"))
        assertEquals("", AmountInputRules.backspace("1"))
        assertEquals("", AmountInputRules.backspace(""))
    }

    // ---------------------------------------------------------------- 千分位显示

    @Test
    fun `显示串带千分位`() {
        assertEquals("1,234", AmountInputRules.toDisplay("1234"))
        assertEquals("1,234,567", AmountInputRules.toDisplay("1234567"))
        assertEquals("1,234.5", AmountInputRules.toDisplay("1234.5"))
        assertEquals("1,234.", AmountInputRules.toDisplay("1234."))
        assertEquals("999", AmountInputRules.toDisplay("999"))
        assertEquals("0", AmountInputRules.toDisplay("0"))
        assertEquals("", AmountInputRules.toDisplay(""))
    }

    @Test
    fun `显示串不会因为小数点在中间而错位`() {
        assertEquals("1,000.05", AmountInputRules.toDisplay("1000.05"))
    }

    // ---------------------------------------------------------------- 完成按钮

    @Test
    fun `金额为零时完成按钮禁用`() {
        assertFalse(AmountInputRules.isDoneEnabled(""))
        assertFalse(AmountInputRules.isDoneEnabled("0"))
        assertFalse(AmountInputRules.isDoneEnabled("0."))
        assertFalse(AmountInputRules.isDoneEnabled("0.00"))
    }

    @Test
    fun `金额大于零时完成按钮可用`() {
        assertTrue(AmountInputRules.isDoneEnabled("0.01"))
        assertTrue(AmountInputRules.isDoneEnabled("1"))
        assertTrue(AmountInputRules.isDoneEnabled("12.5"))
    }

    @Test
    fun `输入串转分`() {
        assertEquals(1250L, AmountInputRules.toCents("12.5"))
        assertEquals(1L, AmountInputRules.toCents("0.01"))
        assertEquals(0L, AmountInputRules.toCents(""))
        assertEquals(140000L, AmountInputRules.toCents("1400"))
    }

    // ---------------------------------------------------------------- 归一化

    @Test
    fun `归一化剔除非法字符与多余小数点`() {
        assertEquals("12.34", AmountInputRules.normalize("1a2b.3c4"))
        assertEquals("12.34", AmountInputRules.normalize("12.3.4"))
        assertEquals("12.", AmountInputRules.normalize("12."))
        assertEquals("7", AmountInputRules.normalize("007"))
    }

    @Test
    fun `连续输入一万元的完整序列`() {
        // 1 → 10 → 100 → 1000 → 1000. → 1000.0 → 1000.05
        var text = ""
        text = AmountInputRules.accept(text, "1")
        text = AmountInputRules.accept(text, "00")
        text = AmountInputRules.accept(text, "0")
        assertEquals(100000L, AmountInputRules.toCents(text))
        assertEquals("1,000", AmountInputRules.toDisplay(text))

        text = AmountInputRules.accept(text, ".")
        text = AmountInputRules.accept(text, "0")
        text = AmountInputRules.accept(text, "5")
        assertEquals("1000.05", text)
        assertEquals(100005L, AmountInputRules.toCents(text))
        assertEquals("1,000.05", AmountInputRules.toDisplay(text))
    }
}
