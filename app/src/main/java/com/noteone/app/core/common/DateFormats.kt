// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 日期时间的展示与导出格式。
 *
 * 全部基于 `java.time`（minSdk 30 已足够），**不做任何 `SimpleDateFormat`**，
 * 避免 Locale 差异导致格式漂移。
 */
object DateFormats {

    private val ZONE: ZoneId get() = ZoneId.systemDefault()

    /** 账单组头 / 列表：`9 月 29 日` */
    private val MONTH_DAY_CN = DateTimeFormatter.ofPattern("M 月 d 日")

    /** 账单页顶部：`2026 年 9 月` */
    private val YEAR_MONTH_CN = DateTimeFormatter.ofPattern("yyyy 年 M 月")

    /** 汇总页自定义范围 chip：`9.01 – 9.29` */
    private val MONTH_DAY_DOT = DateTimeFormatter.ofPattern("M.dd")

    /** CSV / JSON 导出：`2026-09-29` */
    private val ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** CSV 导出时间列：`12:30` */
    private val HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm")

    /** 文件默认名：`20260929_2130` */
    private val FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")

    /** 编辑面板日期行：`2026-09-29 12:30` */
    private val FULL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun toLocalDate(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(ZONE).toLocalDate()

    private fun toLocalDateTime(epochMillis: Long) =
        Instant.ofEpochMilli(epochMillis).atZone(ZONE).toLocalDateTime()

    /** `9 月 29 日` */
    fun monthDay(epochMillis: Long): String = MONTH_DAY_CN.format(toLocalDate(epochMillis))

    /** `9 月 29 日`，但跨年时补上年份：`2025 年 12 月 31 日` */
    fun monthDayWithYearIfNeeded(epochMillis: Long, today: LocalDate = LocalDate.now()): String {
        val date = toLocalDate(epochMillis)
        return if (date.year == today.year) {
            MONTH_DAY_CN.format(date)
        } else {
            "${date.year} 年 ${MONTH_DAY_CN.format(date)}"
        }
    }

    /** `2026 年 9 月` */
    fun yearMonth(epochMillis: Long): String = YEAR_MONTH_CN.format(toLocalDate(epochMillis))

    /** `2026 年 9 月` */
    fun yearMonth(date: LocalDate): String = YEAR_MONTH_CN.format(date)

    /** `9.01` */
    fun monthDayDot(date: LocalDate): String = MONTH_DAY_DOT.format(date)

    /** `9.01 – 9.29`（中间的减号是 en dash，不是连字符） */
    fun monthDayDotRange(start: LocalDate, end: LocalDate): String {
        val from = minOf(start, end)
        val to = maxOf(start, end)
        return "${monthDayDot(from)} – ${monthDayDot(to)}"
    }

    /** `2026-09-29` */
    fun isoDate(epochMillis: Long): String = ISO_DATE.format(toLocalDate(epochMillis))

    /** `2026-09-29` */
    fun isoDate(date: LocalDate): String = ISO_DATE.format(date)

    /** `12:30` */
    fun hourMinute(epochMillis: Long): String = HOUR_MINUTE.format(toLocalDateTime(epochMillis))

    /** `2026-09-29 12:30` */
    fun fullDateTime(epochMillis: Long): String = FULL_DATE_TIME.format(toLocalDateTime(epochMillis))

    /** 文件默认名时间戳：`记` 场景用 `记账_20260929_2130.csv` */
    fun fileStamp(epochMillis: Long = System.currentTimeMillis()): String =
        FILE_STAMP.format(toLocalDateTime(epochMillis))
}
