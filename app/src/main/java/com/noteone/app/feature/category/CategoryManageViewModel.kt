// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.feature.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noteone.app.core.data.model.Category
import com.noteone.app.core.data.model.CategoryScope
import com.noteone.app.core.data.model.Direction
import com.noteone.app.core.data.repository.CategoryRepository
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

data class CategoryManageUiState(
    /** 当前分区：0 = 支出，1 = 收入（三态切换的视觉与账单页一致）。 */
    val direction: Int = Direction.Expense,
    /** 当前分区内**未归档**的分类。 */
    val categories: List<Category> = emptyList(),
    /** 当前分区内**已归档**的分类，可恢复。 */
    val archived: List<Category> = emptyList(),
)

/** 分类管理页的一次性事件。 */
sealed interface CategoryManageEvent {

    data object Failed : CategoryManageEvent
}

/**
 * 分类管理页（任务书 §4）的 ViewModel。
 *
 * 三条不能违反的语义：
 * 1. **删除 = 归档**（`archived = true`），永不物理删除，历史记录仍能显示原分类名与色点；
 * 2. 改名后历史记录同步显示新名字（记录只存 `categoryId`，改名天然生效）；
 * 3. 内置分类可以归档，但不可物理删除——本项目根本没有物理删除的入口。
 */
@HiltViewModel
class CategoryManageViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val direction = MutableStateFlow(Direction.Expense)
    private val allCategories = MutableStateFlow<List<Category>>(emptyList())

    private val _events = MutableSharedFlow<CategoryManageEvent>(extraBufferCapacity = 4)

    val events: SharedFlow<CategoryManageEvent> = _events.asSharedFlow()

    val uiState: StateFlow<CategoryManageUiState> = combine(
        direction,
        allCategories,
    ) { currentDirection, categories ->
        CategoryManageUiState(
            direction = currentDirection,
            categories = categories
                .filter { !it.archived && matchesDirection(it, currentDirection) }
                .sortedWith(compareBy({ it.sortOrder }, { it.id })),
            archived = categories
                .filter { it.archived && matchesDirection(it, currentDirection) }
                .sortedWith(compareBy({ it.sortOrder }, { it.id })),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = CategoryManageUiState(),
    )

    init {
        viewModelScope.launch {
            // 含已归档，管理页两个分区都要用到
            categoryRepository.observeAll().collect { allCategories.value = it }
        }
    }

    fun selectDirection(value: Int) {
        direction.value = if (value == Direction.Income) Direction.Income else Direction.Expense
    }

    // ------------------------------------------------------------------ 写操作

    fun add(name: String, direction: Int, colorKey: String) {
        write { categoryRepository.add(name, direction, colorKey) }
    }

    fun rename(id: Long, name: String) {
        write { categoryRepository.rename(id, name) }
    }

    fun setColor(id: Long, colorKey: String) {
        write { categoryRepository.setColor(id, colorKey) }
    }

    /** 删除 = 归档。 */
    fun archive(id: Long) {
        write { categoryRepository.setArchived(id, true) }
    }

    fun restore(id: Long) {
        write { categoryRepository.setArchived(id, false) }
    }

    /**
     * 拖拽排序落位。
     *
     * 只把**当前分区**的 id 按新顺序传下去：支出与收入两边的 `sortOrder` 各自独立编号，
     * 混在一起传会把另一侧的顺序也覆盖掉。
     */
    fun move(from: Int, to: Int) {
        val current = uiState.value.categories
        if (from !in current.indices || to !in current.indices || from == to) return
        val reordered = current.toMutableList().apply { add(to, removeAt(from)) }
        write { categoryRepository.reorder(reordered.map { it.id }) }
    }

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _events.emit(CategoryManageEvent.Failed)
            }
        }
    }
}

/**
 * 分类是否属于某个分区。
 *
 * `Both`（通用）在两边都出现——本项目的新建弹层只提供「支出 / 收入」两个选项，
 * 所以只有从备份导回的旧数据会出现 `Both`。
 */
internal fun matchesDirection(category: Category, direction: Int): Boolean =
    CategoryScope.matches(
        categoryDirection = category.direction,
        recordDirection = direction,
    ) || category.direction == CategoryScope.Both
