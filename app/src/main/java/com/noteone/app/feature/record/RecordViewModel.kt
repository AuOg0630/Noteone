// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.feature.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noteone.app.core.data.model.Book
import com.noteone.app.core.data.model.Category
import com.noteone.app.core.data.model.CategoryScope
import com.noteone.app.core.data.model.Direction
import com.noteone.app.core.data.model.TransactionDraft
import com.noteone.app.core.data.model.TransactionSource
import com.noteone.app.core.data.repository.BookRepository
import com.noteone.app.core.data.repository.CategoryRepository
import com.noteone.app.core.data.repository.SettingsRepository
import com.noteone.app.core.data.repository.TransactionRepository
import com.noteone.app.core.data.seed.BuiltInData
import com.noteone.app.core.domain.AmountInputRules
import com.noteone.app.core.domain.TimeRange
import com.noteone.app.core.domain.TimeRanges
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 记账页（首页）的全部渲染状态。
 *
 * 全是**渲染用的快照**，不含任何可写状态——可写状态在 [RecordViewModel.Editable] 里，
 * 两者在 ViewModel 里合并。这样「用户输入」与「数据库事实」的更新路径完全分开，
 * 不会因为一次数据库回流把用户正在输入的金额冲掉。
 *
 * v1.6 起只剩「记账」本身需要的字段：月度合计只用于固定头部的本月结余，
 * 支出 / 收入 / 结余的完整视图在「汇总」Tab。
 */
data class RecordUiState(
    val bookId: Long = 0L,
    val bookName: String = "",
    val books: List<Book> = emptyList(),
    val isIncome: Boolean = false,
    /** 原始输入串（无千分位），交给 `AmountInputRules` 处理 */
    val amountText: String = "",
    val note: String = "",
    /** 当前方向可用的分类（未归档） */
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: Long? = null,
    val monthExpenseCents: Long = 0L,
    val monthIncomeCents: Long = 0L,
    /** 回查分类名与色点用，**含已归档** */
    val categoryById: Map<Long, Category> = emptyMap(),
) {
    /** 本月结余 = 收入 − 支出。 */
    val monthBalanceCents: Long get() = monthIncomeCents - monthExpenseCents

    /** 「完成」是否可点：金额必须大于 0。 */
    val doneEnabled: Boolean get() = AmountInputRules.isDoneEnabled(amountText)
}

/** 记账页的一次性事件（不放进 UiState，避免重组时重复触发）。 */
sealed interface RecordEvent {

    /**
     * 写入成功。金额与分类名由 UI 拼成文案，ViewModel 不碰字符串资源。
     *
     * @param transactionId 撤销时要软删除的记录 id
     */
    data class Saved(
        val transactionId: Long,
        val cents: Long,
        val categoryName: String?,
    ) : RecordEvent

    /** 写入失败。**输入内容不清空**（规范 §11）。 */
    data object SaveFailed : RecordEvent
}

/**
 * 记账页（首页）的 ViewModel。
 *
 * 职责边界：
 * - 只持有状态 + 调 Repository，**不碰 DAO / DataStore**
 * - 分组、求和、归一化这类业务计算都写成**纯函数**放在文件末尾，便于单测
 * - 只有 [submit] 会写库，且只有用户点「完成」才会调到它
 */
@HiltViewModel
class RecordViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** 可写状态：只由用户操作驱动。 */
    private data class Editable(
        val amountText: String = "",
        val note: String = "",
        /** null = 跟随设置页的「默认方向」 */
        val directionOverride: Int? = null,
        val selectedCategoryId: Long? = null,
        val saving: Boolean = false,
    )

    private data class BookPart(
        val books: List<Book>,
        val defaultDirection: Int,
        val lastCategoryId: Long,
    )

    private data class StaticSnapshot(
        val books: List<Book>,
        val defaultDirection: Int,
        val lastCategoryId: Long,
        val activeCategories: List<Category>,
        val allCategories: List<Category>,
    )

    private data class TxSnapshot(
        val bookId: Long,
        val monthExpense: Long,
        val monthIncome: Long,
    )

    private val editable = MutableStateFlow(Editable())

    private val _events = MutableSharedFlow<RecordEvent>(extraBufferCapacity = 4)

    /** 成功 / 失败事件流，UI 收集后做微动效与 Snackbar。 */
    val events: SharedFlow<RecordEvent> = _events.asSharedFlow()

    /**
     * 只订阅本月合计（固定头部的「本月结余」要用）。
     *
     * v1.6 之前这里还并发了「最近 8 条」与「近 7 日全部记录」两个查询，
     * 首页把最近账单与趋势图去掉之后它们没有消费方了，一并撤掉。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val txSnapshot: Flow<TxSnapshot> = bookRepository.observeCurrent().flatMapLatest { book ->
        val month = TimeRanges.resolve(TimeRange.ThisMonth)
        combine(
            transactionRepository.observeSum(book.id, Direction.Expense, month.first, month.last),
            transactionRepository.observeSum(book.id, Direction.Income, month.first, month.last),
        ) { expense, income ->
            TxSnapshot(
                bookId = book.id,
                monthExpense = expense,
                monthIncome = income,
            )
        }
    }

    private val staticSnapshot: Flow<StaticSnapshot> = combine(
        combine(
            bookRepository.observeAll(),
            settingsRepository.defaultDirection,
            settingsRepository.lastUsedCategoryId,
        ) { books, defaultDirection, lastCategoryId -> BookPart(books, defaultDirection, lastCategoryId) },
        combine(
            categoryRepository.observeActive(),
            categoryRepository.observeAll(),
        ) { active, all -> active to all },
    ) { bookPart, categoryPart ->
        StaticSnapshot(
            books = bookPart.books,
            defaultDirection = bookPart.defaultDirection,
            lastCategoryId = bookPart.lastCategoryId,
            activeCategories = categoryPart.first,
            allCategories = categoryPart.second,
        )
    }

    val uiState: StateFlow<RecordUiState> = combine(
        staticSnapshot,
        txSnapshot,
        editable,
    ) { statics, tx, edit ->
        merge(statics, tx, edit)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecordUiState(),
    )

    // ------------------------------------------------------------------ 用户操作

    /** 数字键盘按键。输入规则一律交给 `AmountInputRules`，本模块不自己实现。 */
    fun onDigit(key: String) {
        editable.update { it.copy(amountText = AmountInputRules.accept(it.amountText, key)) }
    }

    fun onBackspace() {
        editable.update { it.copy(amountText = AmountInputRules.backspace(it.amountText)) }
    }

    /** 备注：硬限 100 字，超出不响应（规范 §11）。 */
    fun onNoteChange(value: String) {
        if (value.length > BuiltInData.NOTE_MAX_LENGTH) return
        editable.update { it.copy(note = value) }
    }

    fun onDirectionChange(isIncome: Boolean) {
        editable.update { it.copy(directionOverride = if (isIncome) Direction.Income else Direction.Expense) }
    }

    /** 点分类 chip 的统一入口：选中分类并记住它。 */
    fun onCategorySelected(categoryId: Long) {
        if (categoryId <= 0L) return
        editable.update { it.copy(selectedCategoryId = categoryId) }
        viewModelScope.launch { settingsRepository.setLastUsedCategoryId(categoryId) }
    }

    fun onBookSelected(bookId: Long) {
        if (bookId <= 0L) return
        viewModelScope.launch { bookRepository.setCurrent(bookId) }
    }

    /** 撤销删除（Snackbar 的「撤销」按钮）。 */
    fun undoDelete(transactionId: Long) {
        viewModelScope.launch { transactionRepository.restore(transactionId) }
    }

    /** 软删除某条记录（撤销窗口用完之后的兜底路径）。 */
    fun deleteTransaction(transactionId: Long) {
        viewModelScope.launch { transactionRepository.softDelete(transactionId) }
    }

    /**
     * 写入记录。**唯一的写入口，只有点「完成」才会调到。**
     *
     * 失败时不清空输入，发 [RecordEvent.SaveFailed]。
     */
    fun submit() {
        val state = uiState.value
        val cents = AmountInputRules.toCents(state.amountText)
        if (cents <= 0L || state.bookId <= 0L) return
        if (editable.value.saving) return

        val note = editable.value.note.trim()
        editable.update { it.copy(saving = true) }

        viewModelScope.launch {
            try {
                val newId = transactionRepository.insert(
                    TransactionDraft(
                        bookId = state.bookId,
                        amountCents = cents,
                        direction = if (state.isIncome) Direction.Income else Direction.Expense,
                        categoryId = state.selectedCategoryId,
                        note = note,
                        occurredAt = System.currentTimeMillis(),
                        source = TransactionSource.Manual,
                    ),
                )
                // 成功：清空金额与备注，**保留分类与方向**（连续记账）
                editable.update { it.copy(amountText = "", note = "", saving = false) }
                _events.emit(
                    RecordEvent.Saved(
                        transactionId = newId,
                        cents = cents,
                        categoryName = state.categoryById[state.selectedCategoryId]?.name,
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                editable.update { it.copy(saving = false) }
                _events.emit(RecordEvent.SaveFailed)
            }
        }
    }

    // ------------------------------------------------------------------ 合并状态

    private fun merge(
        statics: StaticSnapshot,
        tx: TxSnapshot,
        edit: Editable,
    ): RecordUiState {
        val book = statics.books.firstOrNull { it.id == tx.bookId } ?: statics.books.firstOrNull()
        val direction = edit.directionOverride ?: statics.defaultDirection
        val candidates = statics.activeCategories
            .filter { CategoryScope.matches(it.direction, direction) }
        val selected = resolveSelectedCategory(
            explicitId = edit.selectedCategoryId,
            lastUsedId = statics.lastCategoryId,
            candidates = candidates,
        )
        return RecordUiState(
            bookId = book?.id ?: 0L,
            bookName = book?.name.orEmpty(),
            books = statics.books,
            isIncome = direction == Direction.Income,
            amountText = edit.amountText,
            note = edit.note,
            categories = candidates,
            selectedCategoryId = selected,
            monthExpenseCents = tx.monthExpense,
            monthIncomeCents = tx.monthIncome,
            categoryById = statics.allCategories.associateBy { it.id },
        )
    }
}

// ---------------------------------------------------------------------- 纯函数

/**
 * 决定当前选中的分类。
 *
 * 优先级：用户显式选的（仍在该方向的可用分类里）→ 上次使用的 → 该方向的第一个。
 * 效果就是「切到收入时自动落到收入侧分类，切回支出时又能回到原来的选择」。
 */
internal fun resolveSelectedCategory(
    explicitId: Long?,
    lastUsedId: Long,
    candidates: List<Category>,
): Long? {
    if (explicitId != null && candidates.any { it.id == explicitId }) return explicitId
    if (lastUsedId > 0L) {
        val hit = candidates.firstOrNull { it.id == lastUsedId }
        if (hit != null) return hit.id
    }
    return candidates.firstOrNull()?.id
}
