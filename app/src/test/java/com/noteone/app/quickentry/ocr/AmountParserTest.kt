// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.quickentry.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `AmountParser` 的单测（规范 §8.5 明确要求：给固定的 OCR 文本行集合，断言候选提取与排序结果）。
 *
 * 覆盖：带 `¥` / 带小数 / 纯整数 / 日期干扰 / 时间干扰 / 订单号干扰 / 手机号干扰 /
 * 多金额并存与排序 / 万元千元数量级 / 候选去重 / 只取前 3 个。
 */
class AmountParserTest {

    private val screenHeight = 1000

    private fun candidates(vararg texts: String): List<AmountCandidate> {
        val lines = texts.mapIndexed { index, text ->
            OcrLine(text = text, centerY = 380 + index * 60)
        }
        return AmountParser.extract(lines, screenHeight)
    }

    private fun candidate(
        cents: Long,
        centerY: Int,
        symbol: Boolean = false,
        decimal: Boolean = false,
    ) = AmountCandidate(
        cents = cents,
        raw = cents.toString(),
        hasCurrencySymbol = symbol,
        hasDecimal = decimal,
        lineIndex = 0,
        centerY = centerY,
    )

    // ------------------------------------------------------------------ 基本匹配

    @Test
    fun `带货币符号与小数点的金额`() {
        val result = candidates("实付 ¥14.00")
        assertEquals(1, result.size)
        assertEquals(1400L, result[0].cents)
        assertTrue(result[0].hasCurrencySymbol)
        assertTrue(result[0].hasDecimal)
        assertEquals("¥14.00", result[0].raw)
    }

    @Test
    fun `不带符号的纯整数按元解析`() {
        val result = candidates("合计 1400")
        assertEquals(1, result.size)
        assertEquals(140000L, result[0].cents)
        assertFalse(result[0].hasCurrencySymbol)
        assertFalse(result[0].hasDecimal)
    }

    @Test
    fun `千分位与元字`() {
        val result = candidates("订单金额 1,234.56元")
        assertEquals(1, result.size)
        assertEquals(123456L, result[0].cents)
        assertTrue(result[0].hasCurrencySymbol)
        assertTrue(result[0].hasDecimal)
    }

    @Test
    fun `四位整数不会被截成三位`() {
        // 主正则的千分位分支写成 `(?:,\d{3})+` 之后，1234 才会落到第二分支拿到完整值
        val result = candidates("1234")
        assertEquals(1, result.size)
        assertEquals(123400L, result[0].cents)
    }

    @Test
    fun `中文数量级 万 与 千`() {
        assertEquals(1_500_000L, candidates("1.5万")[0].cents)
        assertEquals(1_500_000L, candidates("1.5 万")[0].cents)
        assertEquals(300_000L, candidates("3千")[0].cents)
        // 万 / 千 是数量级不是时间量词，不能被剔除
        assertEquals(1, candidates("1.5万").size)
    }

    // ------------------------------------------------------------------ 干扰排除

    @Test
    fun `日期形态被整体剔除`() {
        assertTrue(candidates("2026-09-30").isEmpty())
        assertTrue(candidates("2026/9/30").isEmpty())
        assertTrue(candidates("2026 年 9 月 30 日").isEmpty())
        assertTrue(candidates("9 月 30 日").isEmpty())
    }

    @Test
    fun `时间形态被整体剔除`() {
        assertTrue(candidates("14:30").isEmpty())
        assertTrue(candidates("2026-09-30 14:30").isEmpty())
    }

    @Test
    fun `手机号被剔除`() {
        assertTrue(candidates("13812345678").isEmpty())
    }

    @Test
    fun `超长整数被剔除`() {
        // 13 位无小数 —— 订单号 / 卡号 / 时间戳
        assertTrue(candidates("订单号 1234567890123").isEmpty())
        assertTrue(candidates("6222021234567890").isEmpty())
    }

    @Test
    fun `紧贴时间量词的数字被剔除`() {
        // 行首后接"月"的规则，扩展成"紧贴即剔除"，才能拦住 9 月 30 日 里的 30
        assertTrue(candidates("3 时后自动失效").none { it.cents == 300L })
        assertTrue(candidates("30 日到账").isEmpty())
    }

    // ------------------------------------------------------------------ 排序

    @Test
    fun `多金额并存时按 符号 小数 数值 排序`() {
        val result = candidates("优惠 2.00", "实付 ¥14.00", "共 3 项")
        assertEquals(listOf(1400L, 200L, 300L), result.map { it.cents })
        assertTrue(result[0].hasCurrencySymbol)
        assertTrue(result[1].hasDecimal && !result[2].hasDecimal)
    }

    @Test
    fun `同一个金额出现多次只留一条`() {
        val result = candidates("¥14.00", "14.00")
        assertEquals(1, result.size)
        assertEquals(1400L, result[0].cents)
        // 留下来的应该是排序最靠前的那条（带货币符号）
        assertTrue(result[0].hasCurrencySymbol)
    }

    @Test
    fun `最多返回三个候选`() {
        val result = candidates("¥30.00", "¥20.00", "¥10.00", "¥5.00")
        assertEquals(AmountParser.MAX_CANDIDATES, result.size)
        assertEquals(listOf(3000L, 2000L, 1000L), result.map { it.cents })
    }

    @Test
    fun `排序规则四 靠屏幕中部优先`() {
        val ranked = AmountParser.rank(
            listOf(
                candidate(cents = 1000, centerY = 50),
                candidate(cents = 1000, centerY = 520),
                candidate(cents = 1000, centerY = 980),
            ),
            referenceHeight = 1000,
        )
        assertEquals(listOf(520, 50, 980), ranked.map { it.centerY })
    }

    @Test
    fun `优先级高于位置 数值大的在前`() {
        val ranked = AmountParser.rank(
            listOf(
                candidate(cents = 100, centerY = 500),
                candidate(cents = 9999, centerY = 20),
            ),
            referenceHeight = 1000,
        )
        assertEquals(listOf(9999L, 100L), ranked.map { it.cents })
    }

    @Test
    fun `关闭优先货币符号后 带小数的在前`() {
        val result = AmountParser.extract(
            lines = listOf(OcrLine("14.00", 400), OcrLine("¥2.00", 460)),
            referenceHeight = screenHeight,
            preferCurrencySymbol = false,
        )
        // 两条都带小数 ⇒ 比数值，14.00 在前（开启该策略时结果正好相反）
        assertEquals(listOf(1400L, 200L), result.map { it.cents })

        val preferred = AmountParser.extract(
            lines = listOf(OcrLine("14.00", 400), OcrLine("¥2.00", 460)),
            referenceHeight = screenHeight,
            preferCurrencySymbol = true,
        )
        assertEquals(listOf(200L, 1400L), preferred.map { it.cents })
    }

    // ------------------------------------------------------------------ 边界

    @Test
    fun `空输入与无数字文本返回空列表`() {
        assertTrue(AmountParser.extract(emptyList(), screenHeight).isEmpty())
        assertTrue(candidates("支付成功").isEmpty())
        assertTrue(candidates("").isEmpty())
    }

    @Test
    fun `行号与行位置被正确带出`() {
        val lines = listOf(
            OcrLine("支付成功", 100),
            OcrLine("¥14.00", 460),
        )
        val result = AmountParser.extract(lines, screenHeight)
        assertEquals(1, result.size)
        assertEquals(1, result[0].lineIndex)
        assertEquals(460, result[0].centerY)
    }

    @Test
    fun `掩码长度与原文一致 保证下标对齐`() {
        val line = "2026-09-30 支付 ¥14.00"
        assertEquals(line.length, AmountParser.mask(line).length)
    }

    // ------------------------------------------------------------------ 回归（真实 OCR 文本）

    @Test
    fun `只有年份的标题不会变成金额候选`() {
        // 修复前："2026年9月" 掩码后漏出裸的 2026，产出 202600 分的候选，
        // 甚至会压过行里真实的小额整数金额。
        assertTrue(candidates("2026年9月 账单").isEmpty())
        assertTrue(candidates("2026年度账单").isEmpty())
        // 但真正的「2026 元」不能被误伤
        assertEquals(202_600L, candidates("合计 2026 元").single().cents)
    }

    @Test
    fun `全角数字与小数点也能识别`() {
        // 中文 OCR 常把金额识别成全角，Kotlin 正则默认只认 ASCII，改前整行落空。
        assertEquals(1_400L, candidates("支付 １４．００").single().cents)
        assertEquals(1_234_56L, candidates("￥１，２３４．５６").single().cents)
    }
}
