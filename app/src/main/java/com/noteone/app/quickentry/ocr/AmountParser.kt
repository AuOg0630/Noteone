// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.quickentry.ocr

import com.noteone.app.core.common.Money
import kotlin.math.abs

/**
 * 从 OCR 文本行里提取金额候选并排序。**纯函数，无任何 Android 依赖，必须有单测。**
 *
 * 依据 `docs/开发规范.md` §8.5 与 `分发/04_磁贴与屏幕识别.md` §6。
 *
 * 三步：
 * 1. **掩码**：把日期 / 时间 / 手机号形态的片段原地替换成等长空格。等长替换是刻意的——
 *    掩码串与原始串下标一一对应，后面取 `raw` 与判断「万 / 千」数量级时不用做偏移换算。
 * 2. **匹配**：主正则在掩码串上捞数字，逐个判定货币符号、小数点、数量级。
 * 3. **排序**：带货币符号 → 带小数点 → 数值大 → 靠屏幕中部，取前 [MAX_CANDIDATES] 个。
 */
object AmountParser {

    /** 面板最多列出几个候选（规范 §8.5）。 */
    const val MAX_CANDIDATES = 3

    /**
     * 主匹配。
     *
     * 与规范给的正则只有一处差异：千分位那一支写成 `(?:,\d{3})+` 而不是 `(?:,\d{3})*`。
     * 用 `*` 时 `\d{1,3}` 会先吃掉 `123`，于是 `1234` 这种四位整数会被截成 `123`；
     * 要求「至少一组逗号」之后，`1,234` 走第一支、`1234` 落到第二支的 `\d+`，两边都对。
     */
    private val AMOUNT = Regex(
        """([¥￥$])?\s?(\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)\s?([元圆])?""",
    )

    /** `2026-09-30` / `2026/9/30` / `2026 年 9 月 30 日`。 */
    private val FULL_DATE = Regex("""\d{4}\s?[-/.年]\s?\d{1,2}\s?[-/.月]\s?\d{1,2}\s?[日号]?""")

    /** `14:30` / `14:30:05`（半角与全角冒号都认）。 */
    private val CLOCK = Regex("""\d{1,2}\s?[:：]\s?\d{2}(?::\d{2})?""")

    /** `13812345678`。 */
    private val PHONE = Regex("""1[3-9]\d{9}""")

    /**
     * 只有年份的写法：`2026 年`、`2026年度账单`。
     *
     * [FULL_DATE] 要求年月日三段，[SHORT_DATE] 只认「月 / 日 / 号」，所以
     * `2026年9月 账单` 掩码后会漏出一个裸的 `2026`，被当成 2026 元（202600 分）的候选，
     * 甚至压过行内真实的小额整数金额。
     */
    private val YEAR_ONLY = Regex("""\d{4}\s?年""")

    /** 年份省掉的 `9 月 30 日`。 */
    private val SHORT_DATE = Regex("""\d{1,2}\s?[月日号]""")

    /** 掩码顺序有讲究：先长后短，否则 `2026 年 9 月 30 日` 会被 `9 月` 先咬掉一段。 */
    private val MASKS = listOf(FULL_DATE, YEAR_ONLY, CLOCK, PHONE, SHORT_DATE)

    /** 掩码后仍可能贴在数字后面的时间量词（`14 时`、`3 点`）。 */
    private const val TIME_QUANTIFIERS = "月日号时点"

    private const val WAN = 10_000L
    private const val QIAN = 1_000L

    /**
     * 提取候选。
     *
     * @param lines ML Kit 返回的文本行，顺序即阅读顺序
     * @param referenceHeight 判定「靠中部」时用的参考高度。实拍全屏时传屏幕高，
     *                        框选裁剪后传裁片高（此时"中部"指裁片的中部，正是想要的口径）
     * @param preferCurrencySymbol 设置页「优先选择带货币符号的数字」，默认开。
     *                             关掉时排序里去掉第一条权重，其余不变
     * @param limit 最多返回几个，默认 [MAX_CANDIDATES]
     */
    fun extract(
        lines: List<OcrLine>,
        referenceHeight: Int,
        preferCurrencySymbol: Boolean = true,
        limit: Int = MAX_CANDIDATES,
    ): List<AmountCandidate> {
        val found = ArrayList<AmountCandidate>()
        lines.forEachIndexed { lineIndex, line ->
            collectFromLine(line, lineIndex, found)
        }
        val ranked = rank(found, referenceHeight, preferCurrencySymbol)
        // 同一个金额出现多次（例如「¥14.00」与「14.00」各一行）只留排序最靠前的那条，
        // 否则候选 chip 会并排显示三个一模一样的金额，没有任何选择价值。
        return ranked.distinctBy { it.cents }.take(limit)
    }

    /** 排序规则（规范 §8.5）。单独抽出来是为了让单测能直接断言排序结果。 */
    fun rank(
        candidates: List<AmountCandidate>,
        referenceHeight: Int,
        preferCurrencySymbol: Boolean = true,
    ): List<AmountCandidate> {
        val middle = referenceHeight / 2
        val bySymbol = compareByDescending<AmountCandidate> { it.hasCurrencySymbol }
        val byDecimal = compareByDescending<AmountCandidate> { it.hasDecimal }
        val byValue = compareByDescending<AmountCandidate> { it.cents }
        val byCenter = compareBy<AmountCandidate> { abs(it.centerY - middle) }
        val comparator = if (preferCurrencySymbol) {
            bySymbol.then(byDecimal).then(byValue).then(byCenter)
        } else {
            byDecimal.then(byValue).then(byCenter)
        }
        return candidates.sortedWith(comparator)
    }

    /**
     * 全角 → 半角。
     *
     * 中文 OCR 经常把金额识别成全角（`１４．００`），而 Kotlin 正则走 `java.util.regex`，
     * 默认没有 `UNICODE_CHARACTER_CLASS`，`\d` 与 `\.` 只认 ASCII ——
     * 一个字符都匹配不上，整行直接落空、面板只能降级成手动输入。
     * 全角与半角一一对应，改写后长度不变，掩码串与原文的下标仍然对齐。
     */
    private fun String.toHalfWidth(): String = map { char ->
        when {
            char in '０'..'９' -> char - 0xFEE0
            char == '．' -> '.'
            char == '，' -> ','
            char == '：' -> ':'
            char == '￥' -> '¥'
            char == '＄' -> '$'
            else -> char
        }
    }.joinToString(separator = "")

    /** 把文本行里的日期 / 时间 / 手机号换成等长空格，保持下标对齐。 */
    fun mask(line: String): String {
        var result = line
        for (pattern in MASKS) {
            result = pattern.replace(result) { match -> " ".repeat(match.value.length) }
        }
        return result
    }

    // ------------------------------------------------------------------ 内部实现

    private fun collectFromLine(line: OcrLine, lineIndex: Int, into: MutableList<AmountCandidate>) {
        // 先归一化再掩码：两步都是一一对应改写，下标始终与原文对齐
        val text = line.text.toHalfWidth()
        val masked = mask(text)
        if (masked.isBlank()) return

        var cursor = 0
        while (cursor <= masked.length) {
            val match = AMOUNT.find(masked, cursor) ?: break
            cursor = match.range.last + 1
            candidateOf(text, masked, match, lineIndex, line.centerY)?.let(into::add)
        }
    }

    private fun candidateOf(
        text: String,
        masked: String,
        match: MatchResult,
        lineIndex: Int,
        centerY: Int,
    ): AmountCandidate? {
        val prefixSymbol = match.groupValues[1]
        val numberText = match.groupValues[2]
        val suffixSymbol = match.groupValues[3]
        if (numberText.isEmpty()) return null

        val raw = text.substring(match.range.first, match.range.last + 1).trim()
        val digits = numberText.count { it.isDigit() }
        val hasDecimal = numberText.contains('.')

        // 规则 2：连续 ≥ 10 位且无小数点的整数 —— 订单号 / 卡号 / 时间戳
        if (!hasDecimal && digits >= 10) return null

        // 主正则尾部的 `\s?` 会顺带吃掉数字后面的空格（例如 `1.5 万`、`3 时后`），
        // 所以判断"紧贴的是哪个字"时要跳过这些空白，否则量词会被漏判。
        var probe = match.range.last + 1
        while (probe < masked.length && masked[probe].isWhitespace()) probe++
        val afterMatch = masked.getOrNull(probe)

        // 规则 4：数字紧贴时间量词 —— 行首的 `9 月`、`14 时`，以及年份省掉时后半段的 `30 日`。
        // 规范只写了"行首且后接月/日/时"，这里放宽成"紧贴即剔除"，理由见 KDoc。
        // `1,234.56 元` 的 `元` 不在量词集合里，不受影响。
        if (afterMatch != null && afterMatch in TIME_QUANTIFIERS) return null

        val base = Money.yuanToCents(numberText.replace(",", ""))
        if (base <= 0L) return null

        val cents = applyMagnitude(base, afterMatch) ?: return null

        return AmountCandidate(
            cents = cents,
            raw = raw,
            hasCurrencySymbol = !prefixSymbol.isNullOrEmpty() || !suffixSymbol.isNullOrEmpty(),
            hasDecimal = hasDecimal,
            lineIndex = lineIndex,
            centerY = centerY,
        )
    }

    /**
     * 中文数量级：数字后紧跟 `万` → ×10000、`千` → ×1000。
     *
     * 直接对「分」乘：元 → 分本身是 ×100，所以 `1.5 万` 走"150 分 × 10000"得到 1,500,000 分
     * 也就是 15,000 元，与「先转元再乘再转分」等价，且全程整数运算。
     */
    private fun applyMagnitude(baseCents: Long, after: Char?): Long? {
        val multiplier = when (after) {
            '万' -> WAN
            '千' -> QIAN
            else -> return baseCents
        }
        if (baseCents > Long.MAX_VALUE / multiplier) return null
        return baseCents * multiplier
    }
}
