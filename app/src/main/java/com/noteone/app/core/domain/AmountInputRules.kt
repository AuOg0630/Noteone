// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.domain

import com.noteone.app.core.common.Money

/**
 * 自绘数字键盘的输入规则。**首页记账卡片与悬浮识别面板共用，不要各写一套。**
 *
 * 内部状态 [current] 是「原始输入串」：只含数字与至多一个小数点，**不含千分位**。
 * 显示时再用 [toDisplay] 加上千分位，这样状态与展示分离，退格和进位都不会错。
 *
 * 依据 `docs/开发规范.md` §6.1。
 */
object AmountInputRules {

    /** 整数部分最多 9 位。 */
    const val MAX_INT_DIGITS = 9

    /** 小数部分最多 2 位。 */
    const val MAX_DECIMALS = 2

    const val DOT = "."

    /**
     * 处理一次按键。
     *
     * @param current 当前原始输入串，例如 `"12"`、`"12."`、`"12.5"`、`""`
     * @param key 键值：`"0"`–`"9"`、`"00"`、`"."`
     * @return 新的原始输入串；按键无效时原样返回 [current]
     */
    fun accept(current: String, key: String): String {
        val normalized = normalize(current)
        val result = when (key) {
            DOT -> acceptDot(normalized)
            "00" -> acceptDoubleZero(normalized)
            else -> {
                if (key.length != 1 || !key[0].isDigit()) normalized
                else acceptDigit(normalized, key[0])
            }
        }
        return normalize(result)
    }

    /** 退格。空串退格仍是空串。 */
    fun backspace(current: String): String =
        if (current.isEmpty()) "" else current.dropLast(1)

    /** 原始串 → 带千分位的显示串。空串返回空串，由调用方决定显示 `0.00` 还是占位。 */
    fun toDisplay(current: String): String {
        val raw = normalize(current)
        if (raw.isEmpty()) return ""
        val dotIndex = raw.indexOf('.')
        val intPart = if (dotIndex >= 0) raw.substring(0, dotIndex) else raw
        val decPart = if (dotIndex >= 0) raw.substring(dotIndex) else ""
        return groupThousands(intPart) + decPart
    }

    /** 写入数据库前用：去掉结尾孤零零的小数点（`"12."` → `"12"`）。 */
    fun normalize(current: String): String {
        var value = current.filter { it.isDigit() || it == '.' }
        // 只保留第一个小数点
        val firstDot = value.indexOf('.')
        if (firstDot >= 0) {
            value = value.substring(0, firstDot + 1) +
                value.substring(firstDot + 1).replace(".", "")
        }
        // 去掉多余的前导 0（保留单个 0，以及 "0." 这种形式）
        val dot = value.indexOf('.')
        val intPart = if (dot >= 0) value.substring(0, dot) else value
        val trimmedInt = intPart.trimStart('0')
        val rebuiltInt = if (trimmedInt.isEmpty()) intPart.take(1) else trimmedInt
        value = rebuiltInt + if (dot >= 0) value.substring(dot) else ""
        // 裁剪到规则上限
        value = clamp(value)
        return value
    }

    /** 原始串 → 分。空串或非法输入为 0。 */
    fun toCents(current: String): Long = Money.yuanToCents(normalize(current))

    /** 「完成」按钮是否可用：金额必须大于 0。 */
    fun isDoneEnabled(current: String): Boolean = toCents(current) > 0L

    // ---------------------------------------------------------------- 内部实现

    private fun acceptDot(current: String): String {
        if (current.contains('.')) return current
        if (current.isEmpty()) return "0."
        return current + DOT
    }

    private fun acceptDoubleZero(current: String): String {
        // 金额为 0 时不响应，避免出现 "000"
        if (isZero(current)) return current
        // 等价于连按两次 "0"
        return acceptDigit(acceptDigit(current, '0'), '0')
    }

    private fun acceptDigit(current: String, digit: Char): String {
        // 前导 0 自动替换："0" + "5" → "5"
        if (current == "0" && digit != '0') return digit.toString()

        val dotIndex = current.indexOf('.')
        return if (dotIndex >= 0) {
            val decimals = current.length - dotIndex - 1
            if (decimals >= MAX_DECIMALS) current else current + digit
        } else {
            if (current.length >= MAX_INT_DIGITS) current else current + digit
        }
    }

    private fun isZero(current: String): Boolean =
        current.isEmpty() || current == "0" || current == "0."

    private fun clamp(value: String): String {
        val dotIndex = value.indexOf('.')
        return if (dotIndex >= 0) {
            val intPart = value.substring(0, dotIndex).take(MAX_INT_DIGITS)
            val decPart = value.substring(dotIndex + 1).take(MAX_DECIMALS)
            val safeInt = if (intPart.isEmpty()) "0" else intPart
            if (decPart.isEmpty() && !value.endsWith(".")) safeInt else "$safeInt.$decPart"
        } else {
            value.take(MAX_INT_DIGITS)
        }
    }

    private fun groupThousands(intPart: String): String {
        if (intPart.length <= 3) return intPart
        val builder = StringBuilder(intPart.length + intPart.length / 3)
        var count = 0
        for (index in intPart.indices.reversed()) {
            builder.append(intPart[index])
            count++
            if (count % 3 == 0 && index != 0) builder.append(',')
        }
        return builder.reverse().toString()
    }
}
