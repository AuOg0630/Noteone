// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.common

/**
 * 金额工具。
 *
 * 铁律：**金额一律以「分」存 `Long`，全程整数运算，禁止经过 `Double` / `Float`。**
 * 禁止使用 `DecimalFormat`（依赖 Locale，可能产出 `1.234,56` 这类结果）。
 *
 * 位置说明：`docs/开发规范.md` §2.2 把「金额格式化（分↔元）」划给 `core/common`，
 * 因此这里就是 `Money` 的唯一实现处。`00_项目总纲与接口契约.md` §5.4 的代码块把它
 * 画在领域层内，只是排版位置差异，全项目统一从 `core.common` 导入。
 */
object Money {

    private const val CENTS_PER_YUAN = 100L

    /**
     * 元字符串 → 分，例如 `"12.5"` → `1250`、`"0.01"` → `1`。
     *
     * 宽松解析：会先剔除空白与千分位逗号，容忍 `¥` `￥` `元` 等符号。
     * 小数位超过 2 位时**截断**（不四舍五入），因为输入规则本身只允许 2 位小数。
     * 无法解析出任何数字时返回 `0`；需要区分「解析失败」的调用方请用 [yuanToCentsOrNull]。
     */
    fun yuanToCents(input: String): Long = yuanToCentsOrNull(input) ?: 0L

    /** 同 [yuanToCents]，但解析不出数字时返回 `null`。 */
    fun yuanToCentsOrNull(input: String): Long? {
        val sanitized = sanitize(input)
        if (sanitized.isEmpty()) return null

        val negative = sanitized.startsWith("-")
        val body = sanitized.removePrefix("-").removePrefix("+")
        if (body.isEmpty()) return null

        val dotIndex = body.indexOf('.')
        val intPart = if (dotIndex >= 0) body.substring(0, dotIndex) else body
        val rawDecPart = if (dotIndex >= 0) body.substring(dotIndex + 1) else ""

        // 小数点后出现第二个小数点或非数字，视为非法
        if (rawDecPart.any { !it.isDigit() }) return null
        if (intPart.any { !it.isDigit() }) return null
        if (intPart.isEmpty() && rawDecPart.isEmpty()) return null

        val decPart = rawDecPart.take(2).padEnd(2, '0')
        if (decPart.any { !it.isDigit() }) return null

        val yuan = if (intPart.isEmpty()) 0L else (intPart.toLongOrNull() ?: return null)
        val cents = decPart.toLong()

        // 防溢出：分是 Long，元的上限约为 9.2e16，这里只做一次保守校验
        if (yuan > MAX_YUAN) return null

        val value = yuan * CENTS_PER_YUAN + cents
        return if (negative) -value else value
    }

    /**
     * 分 → 无千分位的普通字符串，例如 `123456` → `"1234.56"`。
     * 负数带前导 `-`。
     */
    fun centsToPlain(cents: Long): String {
        val negative = cents < 0
        val abs = if (negative) -cents else cents
        val yuan = abs / CENTS_PER_YUAN
        val fraction = abs % CENTS_PER_YUAN
        return buildString {
            if (negative) append('-')
            append(yuan)
            append('.')
            append(fraction.toString().padStart(2, '0'))
        }
    }

    /**
     * 分 → 带千分位、固定 2 位小数的字符串，例如 `123456` → `"1,234.56"`。
     * 负数带前导 `-`（如 `-1400` → `"-14.00"`）。
     *
     * 这是全项目金额显示的唯一格式化入口，统一由 `MoneyText` 调用。
     */
    fun formatThousands(cents: Long): String {
        val negative = cents < 0
        val abs = if (negative) -cents else cents
        val yuan = abs / CENTS_PER_YUAN
        val fraction = abs % CENTS_PER_YUAN
        return buildString {
            if (negative) append('-')
            append(groupDigits(yuan))
            append('.')
            append(fraction.toString().padStart(2, '0'))
        }
    }

    /**
     * 分 → 「亿元」单位的紧凑写法，例如 `123456789` → `"1.23"`（配合 `亿` 后缀使用）。
     * 只在数值 ≥ 1 亿元时才有意义，调用方负责判定阈值。
     */
    fun centsToYi(cents: Long): String {
        val negative = cents < 0
        val abs = if (negative) -cents else cents
        val yi = abs / YI_IN_CENTS
        val remainder = abs % YI_IN_CENTS
        // 取两位小数：余数占一个亿的比例，保留 2 位（整数运算 + 四舍五入到分位）
        val fraction = (remainder * 100 + YI_IN_CENTS / 2) / YI_IN_CENTS
        val intPart = if (fraction == 100L) yi + 1 else yi
        val fracPart = if (fraction == 100L) 0L else fraction
        return buildString {
            if (negative) append('-')
            append(intPart)
            append('.')
            append(fracPart.toString().padStart(2, '0'))
        }
    }

    /** 是否达到「按亿元显示」的阈值（1 亿元）。 */
    fun shouldUseYi(cents: Long): Boolean {
        val abs = if (cents < 0) -cents else cents
        return abs >= YI_IN_CENTS
    }

    private const val YI_IN_CENTS = 100_000_000L * CENTS_PER_YUAN
    private const val MAX_YUAN = 9_000_000_000_000_000L

    private fun sanitize(input: String): String {
        val builder = StringBuilder(input.length)
        for (ch in input) {
            when {
                ch.isDigit() || ch == '.' -> builder.append(ch)
                ch == ',' || ch == '，' -> Unit // 千分位分隔符
                (ch == '-' || ch == '+') && builder.isEmpty() -> builder.append(ch)
                else -> Unit // ¥ ￥ 元 圆 空格 等一律忽略
            }
        }
        return builder.toString()
    }

    /** 从右往左每 3 位插一个逗号。纯整数运算，不碰 Locale。 */
    private fun groupDigits(value: Long): String {
        val digits = value.toString()
        if (digits.length <= 3) return digits
        val builder = StringBuilder(digits.length + digits.length / 3)
        var count = 0
        for (index in digits.indices.reversed()) {
            builder.append(digits[index])
            count++
            if (count % 3 == 0 && index != 0) builder.append(',')
        }
        return builder.reverse().toString()
    }
}
