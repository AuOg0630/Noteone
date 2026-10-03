// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.feature.report

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noteone.app.core.common.DateFormats
import com.noteone.app.core.data.model.Category
import com.noteone.app.core.data.model.CategoryScope
import com.noteone.app.core.data.model.Direction
import com.noteone.app.core.data.model.Transaction
import com.noteone.app.core.data.repository.BookRepository
import com.noteone.app.core.data.repository.CategoryRepository
import com.noteone.app.core.data.repository.SettingsRepository
import com.noteone.app.core.data.repository.TransactionRepository
import com.noteone.app.core.domain.TimeRange
import com.noteone.app.core.domain.TimeRanges
import com.noteone.app.export.CsvExportService
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** 时间范围 chip 的 5 个选项（规范 §5.3）。 */
enum class ReportPreset { ThisWeek, ThisMonth, Last3Months, ThisYear, Custom }

/** 汇总页的全部渲染状态。 */
data class ReportUiState(
    /** 首帧还没拿到 Room 的第一条数据时为 true，用来避免闪一下空状态。 */
    val loading: Boolean = true,
    val preset: ReportPreset = ReportPreset.ThisMonth,
    /** 自定义范围的 chip 文案：`9.01 – 9.29`；非自定义时为空串（预设用字符串资源渲染）。 */
    val customRangeLabel: String = "",
    /** 当前范围的实际起止自然日，同时作为自定义弹层的初值。 */
    val rangeStart: LocalDate = LocalDate.now(),
    val rangeEnd: LocalDate = LocalDate.now(),
    /** 趋势图底部两端的日期标注，按粒度给出不同格式。 */
    val trendStartLabel: String = "",
    val trendEndLabel: String = "",
    /** 分类筛选行可选的分类（未归档、且可用于支出）。 */
    val filterCategories: List<Category> = emptyList(),
    val selectedCategoryIds: Set<Long> = emptySet(),
    val summary: ReportSummary = ReportSummary(),
    val slices: List<CategorySlice> = emptyList(),
    val trend: List<TrendPoint> = emptyList(),
    /**
     * 本月预算进度（规范 §5.4）。预算没开或限额为 0 时为 `null`，整块不渲染。
     *
     * **口径固定「本自然月」**，不跟随页面上选的时间范围——它比的是「本月支出上限」。
     */
    val budget: BudgetProgress? = null,
    /** 当前范围内有没有记录（不分收支）。没有就显示空状态。 */
    val hasRecords: Boolean = false,
)

/** 汇总页的一次性事件。 */
sealed interface ReportEvent {

    /**
     * CSV 写入失败。
     *
     * **用户取消文件选择不产生事件**（规范 §11：静默返回，不提示）；
     * 导出成功也不提示——`UndoSnackbarHost` 只渲染一个动作按钮，
     * 在成功提示上挂「撤销」是错的。
     */
    data object ExportFailed : ReportEvent
}

/**
 * 汇总页的 ViewModel。
 *
 * 三卡、排行、趋势**由同一个 Flow 派生**，所以改时间范围或改分类筛选时三者必然同步
 * （规范 §5.3 的硬要求）。筛选与范围都只是页面内状态，退出页面即重置为「本月 / 全部」。
 */
@HiltViewModel
class ReportViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
    private val csvExportService: CsvExportService,
) : ViewModel() {

    /** 当前时间范围。默认「本月」（规范 §5.3）。 */
    private val timeRange = MutableStateFlow<TimeRange>(TimeRange.ThisMonth)

    /** 分类筛选。空集合 = 「全部」。 */
    private val selectedCategoryIds = MutableStateFlow<Set<Long>>(emptySet())

    private val _events = MutableSharedFlow<ReportEvent>(extraBufferCapacity = 4)

    val events: SharedFlow<ReportEvent> = _events.asSharedFlow()

    private data class Query(val bookId: Long, val range: TimeRange, val categoryIds: Set<Long>)

    private data class Snapshot(
        val range: TimeRange,
        val resolved: LongRange,
        val categoryIds: Set<Long>,
        val records: List<Transaction>,
    )

    private data class BudgetSnapshot(
        val enabled: Boolean,
        val limitCents: Long,
        val spentCents: Long,
    )

    private val query: Flow<Query> = combine(
        bookRepository.observeCurrent(),
        timeRange,
        selectedCategoryIds,
    ) { book, range, categoryIds -> Query(book.id, range, categoryIds) }

    /**
     * 区间内的记录。`today` 在每次重新查询时取一次，所以跨零点后只要数据有变动就会自动跟上。
     *
     * 注意：**时间范围一定走 `TimeRanges.resolve()`**，页面里不允许手写日期计算。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val snapshot: Flow<Snapshot> = query.flatMapLatest { request ->
        val resolved = TimeRanges.resolve(request.range, LocalDate.now())
        transactionRepository
            .observeByRange(request.bookId, resolved.first, resolved.last, request.categoryIds)
            .map { records -> Snapshot(request.range, resolved, request.categoryIds, records) }
    }

    /**
     * 本月预算进度。**独立于 [snapshot]**：口径固定「本自然月」，不跟页面上选的筛选走。
     *
     * 预算没开或限额为 0 时直接出 0，**不查库**——没开预算的用户不该为这一行付一次查询。
     * 判空（决定整块要不要渲染）交给 `ReportAggregator.budget()`，那是个可单测的纯函数。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val budgetSnapshot: Flow<BudgetSnapshot> = combine(
        bookRepository.observeCurrent(),
        settingsRepository.budgetEnabled,
        settingsRepository.budgetCents,
    ) { book, enabled, limit -> Triple(book.id, enabled, limit) }
        .flatMapLatest { (bookId, enabled, limit) ->
            val month = TimeRanges.resolve(TimeRange.ThisMonth, LocalDate.now())
            val spent = if (enabled && limit > 0L) {
                transactionRepository.observeSum(bookId, Direction.Expense, month.first, month.last)
            } else {
                flowOf(0L)
            }
            spent.map { BudgetSnapshot(enabled = enabled, limitCents = limit, spentCents = it) }
        }

    val uiState: StateFlow<ReportUiState> = combine(
        snapshot,
        categoryRepository.observeAll(),
        budgetSnapshot,
    ) { current, categories, budget -> buildState(current, categories, budget) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReportUiState(),
        )

    // ------------------------------------------------------------------ 用户操作

    /**
     * 切换时间范围预设。**「自定义」不在这里处理**——它要先弹日期范围选择器，
     * 由页面调用 [applyCustomRange] 提交结果（用户取消时保持原范围不变）。
     */
    fun selectPreset(preset: ReportPreset) {
        timeRange.value = when (preset) {
            ReportPreset.ThisWeek -> TimeRange.ThisWeek
            ReportPreset.ThisMonth -> TimeRange.ThisMonth
            ReportPreset.Last3Months -> TimeRange.Last3Months
            ReportPreset.ThisYear -> TimeRange.ThisYear
            ReportPreset.Custom -> return
        }
    }

    /** 提交自定义范围。两端顺序反过来也能正确落库（`resolve()` 会归一化）。 */
    fun applyCustomRange(start: LocalDate, end: LocalDate) {
        timeRange.value = TimeRange.Custom(minOf(start, end), maxOf(start, end))
    }

    /** 多选分类。选中任一分类后「全部」自动取消，由 UI 用 `selectedCategoryIds.isEmpty()` 判定。 */
    fun toggleCategory(categoryId: Long) {
        selectedCategoryIds.update { current ->
            if (categoryId in current) current - categoryId else current + categoryId
        }
    }

    /** 点「全部」= 清空所有选择。 */
    fun clearCategories() {
        selectedCategoryIds.value = emptySet()
    }

    /**
     * 导出当前筛选条件下的记录到用户选定的位置。
     *
     * `uri` 由页面通过 `ActivityResultContracts.CreateDocument` 拿到；
     * 用户取消时页面不会调用本方法（静默返回）。
     */
    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            try {
                val resolved = TimeRanges.resolve(timeRange.value, LocalDate.now())
                csvExportService.exportFiltered(
                    uri = uri,
                    startMs = resolved.first,
                    endMs = resolved.last,
                    categoryIds = selectedCategoryIds.value,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _events.emit(ReportEvent.ExportFailed)
            }
        }
    }

    // ------------------------------------------------------------------ 状态组装

    private fun buildState(
        current: Snapshot,
        categories: List<Category>,
        budget: BudgetSnapshot,
    ): ReportUiState {
        val today = LocalDate.now()
        val start = TimeRanges.toLocalDate(current.resolved.first)
        val end = TimeRanges.toLocalDate(current.resolved.last)
        val granularity = ReportAggregator.granularityFor(current.resolved)

        return ReportUiState(
            loading = false,
            preset = presetOf(current.range),
            customRangeLabel = if (current.range is TimeRange.Custom) {
                DateFormats.monthDayDotRange(start, end)
            } else {
                ""
            },
            rangeStart = start,
            rangeEnd = end,
            trendStartLabel = trendLabel(start, granularity),
            trendEndLabel = trendLabel(end, granularity),
            filterCategories = categories.filter {
                !it.archived && CategoryScope.matches(it.direction, Direction.Expense)
            },
            selectedCategoryIds = current.categoryIds,
            summary = ReportAggregator.summarize(
                transactions = current.records,
                // 日均支出的分母：范围内已过天数（含今天），不是自然天数、也不是有记账的天数
                elapsedDays = TimeRanges.elapsedDaysOf(current.resolved, today),
            ),
            slices = ReportAggregator.byCategory(current.records, categories),
            trend = ReportAggregator.trend(current.records, current.resolved, granularity),
            budget = ReportAggregator.budget(
                enabled = budget.enabled,
                limitCents = budget.limitCents,
                spentCents = budget.spentCents,
            ),
            hasRecords = current.records.isNotEmpty(),
        )
    }

    private fun presetOf(range: TimeRange): ReportPreset = when (range) {
        TimeRange.ThisWeek -> ReportPreset.ThisWeek
        TimeRange.ThisMonth -> ReportPreset.ThisMonth
        TimeRange.Last3Months -> ReportPreset.Last3Months
        TimeRange.ThisYear -> ReportPreset.ThisYear
        is TimeRange.Custom -> ReportPreset.Custom
    }

    /** 按日粒度标 `9.01`，按月粒度标 `2026 年 9 月`——后者标 `9.01` 会误导。 */
    private fun trendLabel(date: LocalDate, granularity: Granularity): String = when (granularity) {
        Granularity.DAILY -> DateFormats.monthDayDot(date)
        Granularity.MONTHLY -> DateFormats.yearMonth(date)
    }
}
