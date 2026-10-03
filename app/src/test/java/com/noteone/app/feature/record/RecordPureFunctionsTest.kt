// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.feature.record

import com.noteone.app.core.data.model.Category
import com.noteone.app.core.design.SemanticKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 记账页的纯函数测试：默认分类解析。
 *
 * 金额、分组、求和都写成纯函数就是为了这件事——不用起数据库、不用起 Compose 就能断言。
 *
 * v1.6 起首页去掉了近 7 日趋势，`buildDayBars` / `lastSevenDaysRange` 与其单测一并移除。
 */
class RecordPureFunctionsTest {

    private fun category(id: Long, name: String, direction: Int) =
        Category(id, name, direction, SemanticKeys.Ink, 0, true, false)

    private val expenseCandidates = listOf(
        category(1L, "餐饮", 0),
        category(2L, "交通", 0),
    )

    @Test
    fun `用户显式选中的分类优先`() {
        assertEquals(2L, resolveSelectedCategory(explicitId = 2L, lastUsedId = 1L, candidates = expenseCandidates))
    }

    @Test
    fun `显式选中的分类不在当前方向时回退到上次使用的`() {
        // 从支出切到收入：原来显式选中的「餐饮」(id=1) 不在收入候选里，
        // 于是回退到「上次使用的分类」——收入侧的「生活费」(id=9)。
        val incomeCandidates = listOf(
            category(9L, "生活费", 1),
            category(10L, "兼职", 1),
        )
        assertEquals(
            9L,
            resolveSelectedCategory(explicitId = 1L, lastUsedId = 9L, candidates = incomeCandidates),
        )
    }

    @Test
    fun `上次使用的分类失效时落到第一个`() {
        assertEquals(1L, resolveSelectedCategory(explicitId = null, lastUsedId = 999L, candidates = expenseCandidates))
    }

    @Test
    fun `上次使用的分类有效时直接用它`() {
        assertEquals(2L, resolveSelectedCategory(explicitId = null, lastUsedId = 2L, candidates = expenseCandidates))
    }

    @Test
    fun `没有任何候选分类时返回 null`() {
        assertNull(resolveSelectedCategory(explicitId = 1L, lastUsedId = 1L, candidates = emptyList()))
    }
}
