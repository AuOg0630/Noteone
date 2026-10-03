// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.design.component

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 给「**行首是数据项、行尾固定一个动作 chip**」的横滑行用的 [LazyListState]。
 *
 * ### 它修的是什么
 *
 * 这是真机验收（2026-09-30）抓到的一个真实缺陷：记账页的分类行与快捷用途行
 * **一进页面就停在最右端**，左边的「餐饮 / 交通 / 购物」和「早餐 / 午餐 / 晚餐」
 * 直接看不见，要手动往右滑才出来。
 *
 * 机制（不是猜的，是被现象反推 + 代码结构验证过的）：
 *
 * 1. ViewModel 的 `uiState` 有 `initialValue = RecordUiState()`，`categories` 与
 *    `quickActions` 一开始都是 emptyList；
 * 2. 于是 `LazyRow` 第一次组合时**只有行尾那一个** `item(key = "category_picker")` /
 *    `item(key = "quick_action_edit")`，它占着 index 0；
 * 3. 数据到达后，真正的 chip 被**插到这个动作 chip 前面**（动作 chip 从 index 0 变成
 *    index 8）；
 * 4. `LazyListState` 的滚动位置是**按首项 key 锚定**的 —— 它会努力让
 *    `"category_picker"` 继续待在行首，于是整行直接滚到了最右端。
 *
 * 对照证据：汇总页的分类筛选行是 `item(key = "all")` + `items(categories)`，
 * 静态项在最前、数据项往后追加，首项 key 始终是 `"all"` 且始终在 index 0 —— 那一行
 * 就完全正常。两者的差别只有「静态项在前还是在后」。
 *
 * ### 用法
 *
 * ```kotlin
 * LazyRow(
 *     state = rememberAlignedLazyListState(categories.size),
 *     modifier = modifier.fillMaxWidth(),
 *     horizontalArrangement = Arrangement.spacedBy(Space.S),
 * ) {
 *     items(categories, key = { it.id }) { ... }
 *     item(key = "category_picker") { ... }
 * }
 * ```
 *
 * **只对齐一次**：数据首次非空时把位置拉回 [initialFirstVisibleItemIndex]，
 * 之后用户的滑动、列表的增删都不再干预（不会跟用户抢滚动条）。
 *
 * @param dataSize 行内**数据项**的个数，不含行尾动作 chip；传 0 表示尚未加载。
 * @param initialFirstVisibleItemIndex 对齐到的下标。默认 0。若将来要「默认把选中的
 *        那个 chip 滚进视野」，把它改成选中项的下标即可，其余不动。
 */
@Composable
fun rememberAlignedLazyListState(
    dataSize: Int,
    initialFirstVisibleItemIndex: Int = 0,
): LazyListState {
    val state = rememberLazyListState()
    var aligned by remember { mutableStateOf(false) }
    LaunchedEffect(dataSize) {
        if (!aligned && dataSize > 0) {
            state.scrollToItem(initialFirstVisibleItemIndex.coerceAtLeast(0))
            aligned = true
        }
    }
    return state
}
