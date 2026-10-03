// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.feature.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noteone.app.core.data.model.Book
import com.noteone.app.core.data.repository.BookRepository
import com.noteone.app.core.data.repository.TransactionRepository
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

/** 删除账本时对其中记录的处理方式（任务书 §3.2）。 */
sealed interface BookDeleteStrategy {

    /** 连同该账本下的记录一起删除。 */
    data object DeleteRecords : BookDeleteStrategy

    /** 先把记录迁移到目标账本。 */
    data class MoveTo(val bookId: Long) : BookDeleteStrategy
}

/** 正在等待确认删除的账本。 */
data class PendingBookDelete(
    val book: Book,
    /** 该账本下未删除的记录条数，文案里要写清楚。 */
    val recordCount: Int,
    /** 可以作为迁移目标的其它账本。 */
    val targets: List<Book>,
)

data class BookManageUiState(
    val books: List<Book> = emptyList(),
    val pendingDelete: PendingBookDelete? = null,
) {
    /** 达到上限时「＋ 新建」置灰并提示。 */
    val atLimit: Boolean get() = books.size >= BuiltInData.MAX_BOOKS

    /** 至少要保留一个账本，只有一个时不给删除入口。 */
    val canDelete: Boolean get() = books.size > 1
}

/** 账本管理页的一次性事件。 */
sealed interface BookManageEvent {

    /** 新增 / 重命名 / 删除这类写操作失败。 */
    data object Failed : BookManageEvent
}

/**
 * 账本管理页（任务书 §3）的 ViewModel。
 *
 * 只走 [BookRepository] / [TransactionRepository]，不碰 DAO。
 * 编辑与删除的弹层状态放在这里而不是页面里，因为「删除前要先数出该账本有多少条记录」
 * 是一次挂起查询，页面里放不下。
 */
@HiltViewModel
class BookManageViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    private val books = MutableStateFlow<List<Book>>(emptyList())
    private val pendingDelete = MutableStateFlow<PendingBookDelete?>(null)

    private val _events = MutableSharedFlow<BookManageEvent>(extraBufferCapacity = 4)

    val events: SharedFlow<BookManageEvent> = _events.asSharedFlow()

    val uiState: StateFlow<BookManageUiState> = combine(books, pendingDelete) { list, pending ->
        BookManageUiState(books = list, pendingDelete = pending)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = BookManageUiState(),
    )

    init {
        viewModelScope.launch {
            bookRepository.observeAll().collect { books.value = it }
        }
    }

    // ------------------------------------------------------------------ 写操作

    /** 新建账本。达到上限时仓库会抛异常，这里兜底成一次失败提示（按钮本身已置灰）。 */
    fun add(name: String, colorKey: String) {
        viewModelScope.launch {
            runWrite { bookRepository.add(name, colorKey) }
        }
    }

    fun rename(id: Long, name: String) {
        viewModelScope.launch {
            runWrite { bookRepository.rename(id, name) }
        }
    }

    fun setColor(id: Long, colorKey: String) {
        viewModelScope.launch {
            runWrite { bookRepository.setColor(id, colorKey) }
        }
    }

    /**
     * 拖拽排序落位。把「起始下标 → 目标下标」翻译成完整的新顺序再整体写回，
     * 比逐条算 sortOrder 稳。
     */
    fun move(from: Int, to: Int) {
        val current = books.value
        if (from !in current.indices || to !in current.indices || from == to) return
        val reordered = current.toMutableList().apply { add(to, removeAt(from)) }
        viewModelScope.launch {
            runWrite { bookRepository.reorder(reordered.map { it.id }) }
        }
    }

    // ------------------------------------------------------------------ 删除流程

    /** 打开删除弹层前先数清楚该账本下有多少条记录——弹层里要写明「一并删除 N 条」。 */
    fun requestDelete(book: Book) {
        viewModelScope.launch {
            val count = runCatching { transactionRepository.countActiveByBook(book.id) }.getOrDefault(0)
            pendingDelete.value = PendingBookDelete(
                book = book,
                recordCount = count,
                targets = books.value.filter { it.id != book.id },
            )
        }
    }

    fun cancelDelete() {
        pendingDelete.value = null
    }

    fun confirmDelete(strategy: BookDeleteStrategy) {
        val pending = pendingDelete.value ?: return
        pendingDelete.value = null
        viewModelScope.launch {
            // 仓库的 delete 语义：moveToBookId == null 表示一并删除，非空表示迁移
            val target = when (strategy) {
                BookDeleteStrategy.DeleteRecords -> null
                is BookDeleteStrategy.MoveTo -> strategy.bookId
            }
            runWrite { bookRepository.delete(pending.book.id, target) }
        }
    }

    /** 统一的写操作外壳：取消要原样抛出，其余异常转成一次提示。 */
    private suspend fun runWrite(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _events.emit(BookManageEvent.Failed)
        }
    }
}
