// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * 汇总页的时间范围预设。
 *
 * 全部按**自然周期**计算（依据 `docs/开发规范.md` §9.1），周首日固定为周一。
 */
sealed interface TimeRange {

    data object ThisWeek : TimeRange

    data object ThisMonth : TimeRange

    data object Last3Months : TimeRange

    data object ThisYear : TimeRange

    /** 自定义闭区间。`start` 与 `end` 均为自然日。 */
    data class Custom(val start: LocalDate, val end: LocalDate) : TimeRange
}

/**
 * 时间范围 → 毫秒区间。
 *
 * ## 唯一一条最容易做错的规则
 * **所有预设的结束时间都是「今天的最后一刻」，不是周期的最后一刻。**
 *
 * 9 月 2 日点「本月」= `2026-09-01 00:00:00.000` → `2026-09-02 23:59:59.999`，
 * 只包含 9 月 1 日和 9 月 2 日，**不是近 30 天**，也不包含 9 月 3–30 日的空数据。
 *
 * **唯一的例外是 `Custom`**：它的结束时间用用户选定的结束日，不受「今天」限制，
 * 所以允许把范围选到未来（此时日均支出的分母由 `elapsedDaysOf` 截到今天）。
 *
 * 返回值是 `LongRange`，`first` = 起始毫秒，`last` = 结束毫秒（含）。
 * 时区取设备本地时区。
 */
object TimeRanges {

    /** 周首日固定周一。 */
    val FirstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY

    fun resolve(
        range: TimeRange,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): LongRange {
        val startDate = startDateOf(range, today)
        val endDate = endDateOf(range, today)
        return startOfDay(startDate, zone)..endOfDay(endDate, zone)
    }

    /**
     * 上一个等长周期，供周期对比使用。
     * **本项目不做周期对比（用户已明确否定）**，此方法仅为满足接口契约而保留，当前无人调用。
     *
     * 上一个周期是**完整周期**（周 → 上周一到上周日；月 → 上月 1 日到上月末），
     * 因为已经结束的周期不存在「到今天为止」的问题。
     */
    fun previous(
        range: TimeRange,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): LongRange? {
        return when (range) {
            TimeRange.ThisWeek -> {
                val monday = mondayOf(today)
                startOfDay(monday.minusWeeks(1), zone)..endOfDay(monday.minusDays(1), zone)
            }

            TimeRange.ThisMonth -> {
                val firstOfMonth = today.withDayOfMonth(1)
                val lastMonthEnd = firstOfMonth.minusDays(1)
                startOfDay(lastMonthEnd.withDayOfMonth(1), zone)..endOfDay(lastMonthEnd, zone)
            }

            TimeRange.Last3Months -> {
                val firstOfMonth = today.withDayOfMonth(1)
                val previousStart = firstOfMonth.minusMonths(3)
                startOfDay(previousStart, zone)..endOfDay(firstOfMonth.minusDays(1), zone)
            }

            TimeRange.ThisYear -> {
                val firstOfYear = today.withDayOfYear(1)
                startOfDay(firstOfYear.minusYears(1), zone)..endOfDay(firstOfYear.minusDays(1), zone)
            }

            is TimeRange.Custom -> {
                val start = minOf(range.start, range.end)
                val end = maxOf(range.start, range.end)
                val days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1
                startOfDay(start.minusDays(days), zone)..endOfDay(end.minusDays(days), zone)
            }
        }
    }

    /** 该范围覆盖的自然日天数（含起止两端）。 */
    fun daysOf(range: LongRange, zone: ZoneId = ZoneId.systemDefault()): Int {
        val start = toLocalDate(range.first, zone)
        val end = toLocalDate(range.last, zone)
        return (java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1).toInt()
    }

    /**
     * 「范围内已过天数（含今天）」——日均支出的分母。
     *
     * 对预设范围而言，结束日就是今天，所以等于 [daysOf]；对自定义范围而言，
     * 如果结束日在未来，分母只算到今天就够了（未来还没有支出）。
     */
    fun elapsedDaysOf(
        range: LongRange,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Int {
        val start = toLocalDate(range.first, zone)
        val end = minOf(toLocalDate(range.last, zone), today)
        if (end.isBefore(start)) return 1
        return (java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1).toInt()
    }

    private fun startDateOf(range: TimeRange, today: LocalDate): LocalDate = when (range) {
        TimeRange.ThisWeek -> mondayOf(today)
        TimeRange.ThisMonth -> today.withDayOfMonth(1)
        TimeRange.Last3Months -> today.withDayOfMonth(1).minusMonths(2)
        TimeRange.ThisYear -> today.withDayOfYear(1)
        is TimeRange.Custom -> minOf(range.start, range.end)
    }

    /**
     * 结束日。
     *
     * 四个预设都是**今天**（规范 §9.1 的关键规则）；
     * 自定义范围用**用户选定的结束日**，不受今天影响——所以用户可以把范围选到未来。
     */
    private fun endDateOf(range: TimeRange, today: LocalDate): LocalDate = when (range) {
        is TimeRange.Custom -> maxOf(range.start, range.end)
        else -> today
    }

    /** 本周一（今天是周一时返回今天本身）。 */
    private fun mondayOf(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(FirstDayOfWeek))

    /** 自然日 00:00:00.000 */
    fun startOfDay(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    /** 自然日 23:59:59.999 */
    fun endOfDay(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    fun toLocalDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        java.time.Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
}
