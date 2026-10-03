// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * `TimeRanges.resolve()` 的单元测试。
 *
 * 这是全项目最容易做错的地方，所以：
 * - **写死固定日期**，绝不使用 `LocalDate.now()`；
 * - **写死固定时区**，不依赖跑测试的机器的时区；
 * - 除了验证「是什么」，还显式验证「不是什么」（不是近 30 天、不是整月）。
 */
class TimeRangesTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun startMs(date: String): Long =
        LocalDateTime.parse("${date}T00:00:00.000").atZone(zone).toInstant().toEpochMilli()

    private fun endMs(date: String): Long =
        LocalDateTime.parse("${date}T23:59:59.999").atZone(zone).toInstant().toEpochMilli()

    private fun days(date: String): LocalDate = LocalDate.parse(date)

    @Test
    fun `本月 - 9月2日只含9月1日与9月2日`() {
        val today = days("2026-09-02")

        val range = TimeRanges.resolve(TimeRange.ThisMonth, today, zone)

        assertEquals(startMs("2026-09-01"), range.first)
        assertEquals(endMs("2026-09-02"), range.last)

        // 硬需求：不是「最近 30 天」
        assertNotEquals(startMs("2026-08-04"), range.first)
        assertNotEquals(today.minusDays(29).atStartOfDay(zone).toInstant().toEpochMilli(), range.first)

        // 也不是「整月到 9 月 30 日」
        assertTrue(range.last < endMs("2026-09-30"))

        // 正好覆盖 2 个自然日
        assertEquals(2, TimeRanges.daysOf(range, zone))
    }

    @Test
    fun `本周 - 9月2日周三从8月31日周一开始`() {
        val today = days("2026-09-02")

        val range = TimeRanges.resolve(TimeRange.ThisWeek, today, zone)

        assertEquals(startMs("2026-08-31"), range.first)
        assertEquals(endMs("2026-09-02"), range.last)
        assertEquals(3, TimeRanges.daysOf(range, zone))
    }

    @Test
    fun `本周 - 8月31日周一只有当天一天`() {
        val today = days("2026-08-31")

        val range = TimeRanges.resolve(TimeRange.ThisWeek, today, zone)

        assertEquals(startMs("2026-08-31"), range.first)
        assertEquals(endMs("2026-08-31"), range.last)
        assertEquals(1, TimeRanges.daysOf(range, zone))
    }

    @Test
    fun `本周 - 周首日固定为周一而不是周日`() {
        // 2026-09-06 是周日，本周一应该是 8-31（跨了上个月），不是 9-06
        val today = days("2026-09-06")

        val range = TimeRanges.resolve(TimeRange.ThisWeek, today, zone)

        assertEquals(startMs("2026-08-31"), range.first)
        assertEquals(endMs("2026-09-06"), range.last)
    }

    @Test
    fun `近3月 - 9月2日从7月1日开始`() {
        val today = days("2026-09-02")

        val range = TimeRanges.resolve(TimeRange.Last3Months, today, zone)

        assertEquals(startMs("2026-07-01"), range.first)
        assertEquals(endMs("2026-09-02"), range.last)
    }

    @Test
    fun `今年 - 9月2日从1月1日开始`() {
        val today = days("2026-09-02")

        val range = TimeRanges.resolve(TimeRange.ThisYear, today, zone)

        assertEquals(startMs("2026-01-01"), range.first)
        assertEquals(endMs("2026-09-02"), range.last)
    }

    @Test
    fun `自定义 - 起止都由用户指定且包含两端`() {
        val range = TimeRanges.resolve(
            TimeRange.Custom(days("2026-09-01"), days("2026-09-02")),
            days("2026-11-30"),
            zone,
        )

        assertEquals(startMs("2026-09-01"), range.first)
        assertEquals(endMs("2026-09-02"), range.last)
        assertEquals(2, TimeRanges.daysOf(range, zone))
    }

    @Test
    fun `自定义 - 起止颠倒时自动纠正`() {
        val range = TimeRanges.resolve(
            TimeRange.Custom(days("2026-09-02"), days("2026-09-01")),
            days("2026-11-30"),
            zone,
        )

        assertEquals(startMs("2026-09-01"), range.first)
        assertEquals(endMs("2026-09-02"), range.last)
    }

    @Test
    fun `所有预设的结束时间都是今天的最后一刻`() {
        val today = days("2026-09-02")
        val expectedEnd = endMs("2026-09-02")

        listOf(
            TimeRange.ThisWeek,
            TimeRange.ThisMonth,
            TimeRange.Last3Months,
            TimeRange.ThisYear,
        ).forEach { preset ->
            assertEquals("$preset 的结束时间应为今天的最后一刻", expectedEnd, TimeRanges.resolve(preset, today, zone).last)
        }
    }

    @Test
    fun `结束时间精确到毫秒且不越界到下一天`() {
        val today = days("2026-09-02")
        val range = TimeRanges.resolve(TimeRange.ThisMonth, today, zone)

        assertEquals(endMs("2026-09-02"), range.last)
        assertTrue(range.last < startMs("2026-09-03"))
    }

    @Test
    fun `已过天数用作日均分母 - 9月2日看本月是2天`() {
        val today = days("2026-09-02")
        val range = TimeRanges.resolve(TimeRange.ThisMonth, today, zone)

        assertEquals(2, TimeRanges.elapsedDaysOf(range, today, zone))
    }

    @Test
    fun `已过天数不会把未来天数算进分母`() {
        val today = days("2026-09-02")
        val range = TimeRanges.resolve(
            TimeRange.Custom(days("2026-09-01"), days("2026-09-30")),
            today,
            zone,
        )

        // 自定义范围结束日在未来，分母只算到今天
        assertEquals(2, TimeRanges.elapsedDaysOf(range, today, zone))
        // 但自然天数仍然是 30
        assertEquals(30, TimeRanges.daysOf(range, zone))
    }

    @Test
    fun `上一个周期 - 本周的上周是完整的上周一到上周日`() {
        val today = days("2026-09-02")

        val previous = TimeRanges.previous(TimeRange.ThisWeek, today, zone)!!

        assertEquals(startMs("2026-08-24"), previous.first)
        assertEquals(endMs("2026-08-30"), previous.last)
    }

    @Test
    fun `上一个周期 - 本月是完整的上一个月`() {
        val today = days("2026-09-02")

        val previous = TimeRanges.previous(TimeRange.ThisMonth, today, zone)!!

        assertEquals(startMs("2026-08-01"), previous.first)
        assertEquals(endMs("2026-08-31"), previous.last)
    }

    @Test
    fun `上一个周期 - 今年是完整的上一年`() {
        val today = days("2026-09-02")

        val previous = TimeRanges.previous(TimeRange.ThisYear, today, zone)!!

        assertEquals(startMs("2025-01-01"), previous.first)
        assertEquals(endMs("2025-12-31"), previous.last)
    }

    @Test
    fun `上一个周期 - 近3月是上一个完整的3个自然月`() {
        val today = days("2026-09-02")

        val previous = TimeRanges.previous(TimeRange.Last3Months, today, zone)!!

        assertEquals(startMs("2026-06-01"), previous.first)
        assertEquals(endMs("2026-08-31"), previous.last)
    }
}
