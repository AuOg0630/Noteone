// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.feature.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noteone.app.core.common.DateFormats
import com.noteone.app.core.common.Money
import com.noteone.app.core.data.model.Category
import com.noteone.app.core.data.model.Direction
import com.noteone.app.core.data.model.Transaction
import com.noteone.app.core.data.repository.BookRepository
import com.noteone.app.core.data.repository.CategoryRepository
import com.noteone.app.core.data.repository.TransactionRepository
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
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/** 账单页收支筛选三态（规范 §5.2）。分类筛选在汇总页做。 */
enum class LedgerFilter { All, Expense, Income }

/** 按自然日分组后的一组记录。 */
data class LedgerDayGroup(
    /** `9 月 29 日`（跨年时带上年份） */
    val dateLabel: String,
    val expenseCents: Long,
    val incomeCents: Long,
    val items: List<Transaction>,
)

/** 账单页的全部渲染状态。 */
data class LedgerUiState(
    val month: YearMonth = YearMonth.now(),
    /** `2026 年 9 月` */
    val monthTitle: String = "",
    val filter: LedgerFilter = LedgerFilter.All,
    val searchActive: Boolean = false,
    val searchQuery: String = "",
    val groups: List<LedgerDayGroup> = emptyList(),
    /** 回查分类名与色点，**含已归档** */
    val categoryById: Map<Long, Category> = emptyMap(),
)

/** 账单页的一次性事件。 */
sealed interface LedgerEvent {

    /** 已软删除，2.5s 内可撤销。 */
    data class Deleted(val transactionId: Long) : LedgerEvent

    /** 写入失败。 */
    data object SaveFailed : LedgerEvent
}

/**
 * 账单页的 ViewModel。
 *
 * 月份用**显式切换**（`‹` `›` 或年月选择器），刻意不做「滚到顶自动加载上月」
 * ——规范里虽然写了，但显式切换更简单、更可预测（任务书 §3.2 明确赞成）。
 */
@HiltViewModel
class LedgerViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())
    private val filter = MutableStateFlow(LedgerFilter.All)
    private val searchActive = MutableStateFlow(false)
    private val searchQuery = MutableStateFlow("")

    private val _events = MutableSharedFlow<LedgerEvent>(extraBufferCapacity = 4)

    val events: SharedFlow<LedgerEvent> = _events.asSharedFlow()

    /** 当前账本、选中月份的记录。区间取**整个自然月**（浏览历史月份时不能按「到今天」截断）。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val records: Flow<List<Transaction>> =
        combine(bookRepository.observeCurrent(), month) { book, selected -> book.id to selected }
            .flatMapLatest { (bookId, selected) ->
                val range = fullMonthRange(selected)
                transactionRepository.observeByRange(bookId, range.first, range.last)
            }

    val uiState: StateFlow<LedgerUiState> = combine(
        records,
        categoryRepository.observeAll(),
        combine(month, filter) { selected, current -> selected to current },
        combine(searchQuery, searchActive) { query, active -> query to active },
    ) { recordList, categories, monthFilter, search ->
        val (selectedMonth, currentFilter) = monthFilter
        val (query, active) = search
        LedgerUiState(
            month = selectedMonth,
            monthTitle = DateFormats.yearMonth(selectedMonth.atDay(1)),
            filter = currentFilter,
            searchActive = active,
            searchQuery = query,
            groups = groupByDay(recordList, currentFilter, query),
            categoryById = categories.associateBy { it.id },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LedgerUiState(),
    )

    // ------------------------------------------------------------------ 用户操作

    fun selectMonth(target: YearMonth) {
        month.value = target
    }

    fun showPreviousMonth() {
        month.value = month.value.minusMonths(1)
    }

    /**
     * 下一个月。**封顶在当前月**：未来月份没有记录，翻过去只会看到空列表，
     * 所以顶部栏的 `›` 在当前月会置灰（`nextEnabled = false`）。
     */
    fun showNextMonth() {
        month.value = minOf(month.value.plusMonths(1), YearMonth.now())
    }

    fun setFilter(target: LedgerFilter) {
        filter.value = target
    }

    fun openSearch() {
        searchActive.value = true
    }

    fun closeSearch() {
        searchActive.value = false
        searchQuery.value = ""
    }

    fun setSearchQuery(value: String) {
        searchQuery.value = value
    }

    /** 左滑删除。成功后发 [LedgerEvent.Deleted]，由 UI 弹「已删除 + 撤销」。 */
    fun deleteTransaction(transactionId: Long) {
        viewModelScope.launch {
            try {
                transactionRepository.softDelete(transactionId)
                _events.emit(LedgerEvent.Deleted(transactionId))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _events.emit(LedgerEvent.SaveFailed)
            }
        }
    }

    /** 撤销删除。 */
    fun restoreTransaction(transactionId: Long) {
        viewModelScope.launch { transactionRepository.restore(transactionId) }
    }

    /**
     * 编辑面板保存。`createdAt` / `currency` / `recognizedText` 由数据层保留，
     * `updatedAt` 由数据层刷新；`source` 保持原值（编辑不改变一条记录的来源渠道）。
     */
    fun saveTransaction(transaction: Transaction) {
        viewModelScope.launch {
            try {
                transactionRepository.update(transaction)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _events.emit(LedgerEvent.SaveFailed)
            }
        }
    }
}

// ---------------------------------------------------------------------- 纯函数

/** 某个自然月的完整区间：1 日 00:00:00.000 → 月末 23:59:59.999。 */
internal fun fullMonthRange(
    month: YearMonth,
    zone: ZoneId = ZoneId.systemDefault(),
): LongRange = TimeRanges.startOfDay(month.atDay(1), zone)..TimeRanges.endOfDay(month.atEndOfMonth(), zone)

/**
 * 按自然日分组。输入已按 `occurredAt DESC, id DESC` 排好，分组后仍然保持这个顺序。
 *
 * 组头的小计按**筛选后**的记录算：筛「支出」时只显示支出小计，符合直觉。
 */
internal fun groupByDay(
    records: List<Transaction>,
    filter: LedgerFilter,
    query: String,
    zone: ZoneId = ZoneId.systemDefault(),
): List<LedgerDayGroup> {
    val buckets = LinkedHashMap<LocalDate, MutableList<Transaction>>()
    records.forEach { record ->
        val directionMatches = when (filter) {
            LedgerFilter.All -> true
            LedgerFilter.Expense -> record.direction == Direction.Expense
            LedgerFilter.Income -> record.direction == Direction.Income
        }
        if (!directionMatches || !matchesQuery(record, query)) return@forEach
        val date = TimeRanges.toLocalDate(record.occurredAt, zone)
        buckets.getOrPut(date) { mutableListOf() }.add(record)
    }
    return buckets.map { (date, items) ->
        LedgerDayGroup(
            dateLabel = DateFormats.monthDayWithYearIfNeeded(TimeRanges.startOfDay(date, zone)),
            expenseCents = items.filter { it.direction == Direction.Expense }.sumOf { it.amountCents },
            incomeCents = items.filter { it.direction == Direction.Income }.sumOf { it.amountCents },
            items = items,
        )
    }
}

/**
 * 搜索匹配：**备注关键词** 或 **金额区间**（规范 §5.2）。
 * 不做正则、不做复杂表达式——金额区间只认 `10-50` 这种写法。
 */
internal fun matchesQuery(record: Transaction, query: String): Boolean {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return true
    val range = parseAmountRange(trimmed)
    if (range != null) return record.amountCents in range
    return record.note.contains(trimmed, ignoreCase = true)
}

/**
 * 解析 `10-50` / `10~50` / `10 至 50` 这类金额区间，单位「元」，返回「分」区间。
 *
 * 解析不出来返回 `null`（调用方退回到关键词匹配）。
 */
internal fun parseAmountRange(query: String): LongRange? {
    val separators = "-–—~至"
    val index = query.indexOfFirst { it in separators }
    if (index <= 0 || index >= query.length - 1) return null
    val left = Money.yuanToCentsOrNull(query.substring(0, index)) ?: return null
    val right = Money.yuanToCentsOrNull(query.substring(index + 1)) ?: return null
    if (left < 0L || right < 0L) return null
    return if (left <= right) left..right else right..left
}
