// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.feature.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noteone.app.core.data.model.Category
import com.noteone.app.core.data.model.QuickAction
import com.noteone.app.core.data.repository.CategoryRepository
import com.noteone.app.core.data.repository.QuickActionRepository
import com.noteone.app.core.data.seed.BuiltInData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuickActionManageUiState(
    /** 未归档的快捷按钮，按 `sortOrder` 升序——与记账页快捷区的顺序同源。 */
    val actions: List<QuickAction> = emptyList(),
    /** 回查分类名与色点用，**含已归档**（历史按钮指向的分类可能已被归档）。 */
    val categoryById: Map<Long, Category> = emptyMap(),
    /** 未归档分类，供编辑弹层选择。 */
    val categories: List<Category> = emptyList(),
) {
    /** 达到上限时「＋ 新建」置灰并提示。 */
    val atLimit: Boolean get() = actions.size >= BuiltInData.MAX_QUICK_ACTIONS
}

/** 快捷按钮管理页的一次性事件。 */
sealed interface QuickActionManageEvent {

    data object Failed : QuickActionManageEvent
}

/**
 * 快捷按钮管理页（任务书 §5）的 ViewModel。
 *
 * 快捷按钮**只存「label + 分类 + 方向」**，没有金额字段也不许加——
 * 点击语义是「选中该分类」，不是直接入账（用户明确否定过默认金额）。
 */
@HiltViewModel
class QuickActionManageViewModel @Inject constructor(
    private val quickActionRepository: QuickActionRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val actions = MutableStateFlow<List<QuickAction>>(emptyList())
    private val allCategories = MutableStateFlow<List<Category>>(emptyList())

    private val _events = MutableSharedFlow<QuickActionManageEvent>(extraBufferCapacity = 4)

    val events: SharedFlow<QuickActionManageEvent> = _events.asSharedFlow()

    val uiState: StateFlow<QuickActionManageUiState> = combine(
        actions,
        allCategories,
    ) { actionList, categories ->
        QuickActionManageUiState(
            actions = actionList,
            categoryById = categories.associateBy { it.id },
            categories = categories.filter { !it.archived },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = QuickActionManageUiState(),
    )

    init {
        viewModelScope.launch {
            quickActionRepository.observeActive().collect { actions.value = it }
        }
        viewModelScope.launch {
            categoryRepository.observeAll().collect { allCategories.value = it }
        }
    }

    // ------------------------------------------------------------------ 写操作

    fun add(label: String, categoryId: Long, direction: Int) {
        write { quickActionRepository.add(label, categoryId, direction) }
    }

    fun update(id: Long, label: String, categoryId: Long, direction: Int) {
        write { quickActionRepository.updateContent(id, label, categoryId, direction) }
    }

    /** 删除 = 归档（软删除）。 */
    fun archive(id: Long) {
        write { quickActionRepository.setArchived(id, true) }
    }

    /** 拖拽排序落位，顺序与记账页快捷区一致（都按 `sortOrder` 升序）。 */
    fun move(from: Int, to: Int) {
        val current = actions.value
        if (from !in current.indices || to !in current.indices || from == to) return
        val reordered = current.toMutableList().apply { add(to, removeAt(from)) }
        write { quickActionRepository.reorder(reordered.map { it.id }) }
    }

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _events.emit(QuickActionManageEvent.Failed)
            }
        }
    }
}
