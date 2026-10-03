// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.feature.ledger

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noteone.app.R
import com.noteone.app.core.data.model.Category
import com.noteone.app.core.data.model.Direction
import com.noteone.app.core.data.model.Transaction
import com.noteone.app.core.design.AppTheme
import com.noteone.app.core.design.SemanticKeys
import com.noteone.app.core.design.Space
import com.noteone.app.core.design.component.AppScaffold
import com.noteone.app.core.design.component.Divider
import com.noteone.app.core.design.component.EmptyState
import com.noteone.app.core.design.component.UndoSnackbarHost
import com.noteone.app.core.design.component.showUndoSnackbar
import com.noteone.app.core.design.icon.AppIcons
import com.noteone.app.feature.record.ListItemEnter
import com.noteone.app.navigation.Route
import com.noteone.app.navigation.TabReselect
import kotlinx.coroutines.launch
import java.time.YearMonth

/** 失败提示窗口。 */
private const val FAILURE_SNACKBAR_MS = 4_000L

/**
 * 账单页（Tab 2）。规范 §5.2。
 *
 * 顶部栏（月份切换 + 搜索）→ 三态筛选 → 按自然日分组的粘性列表。
 * 条目左滑删除（超过 40% 松手才触发）；点击打开复用记账卡片的编辑面板。
 */
@Composable
fun LedgerScreen(
    modifier: Modifier = Modifier,
    viewModel: LedgerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 文案在 composable 作用域里取好：`stringResource` 是 @Composable，
    // 不能在 `LaunchedEffect` 的挂起块里调用；lint 也不允许经由 `LocalContext` 取资源值。
    val deletedText = stringResource(R.string.ledger_deleted)
    val saveFailedText = stringResource(R.string.record_save_failed)
    val retryLabel = stringResource(R.string.record_retry)

    /** 正在编辑的记录；null = 面板关闭。 */
    var editing by remember { mutableStateOf<Transaction?>(null) }

    /** 最近一次保存的记录，供失败后「重试」。 */
    var lastEdited by remember { mutableStateOf<Transaction?>(null) }

    var showMonthPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LedgerEvent.Deleted -> scope.launch {
                    snackbarHostState.showUndoSnackbar(deletedText) {
                        viewModel.restoreTransaction(event.transactionId)
                    }
                }

                LedgerEvent.SaveFailed -> scope.launch {
                    snackbarHostState.showUndoSnackbar(
                        message = saveFailedText,
                        holdMillis = FAILURE_SNACKBAR_MS,
                        actionLabel = retryLabel,
                        onUndo = { lastEdited?.let(viewModel::saveTransaction) },
                    )
                }
            }
        }
    }

    // 重复点当前 Tab → 滚回顶部（规范 §4.2）
    LaunchedEffect(Unit) {
        TabReselect.events.collect { route ->
            if (route == Route.Ledger) listState.animateScrollToItem(0)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AppScaffold(title = "") {
            LedgerTopBar(
                monthTitle = state.monthTitle,
                searchActive = state.searchActive,
                searchQuery = state.searchQuery,
                onQueryChange = viewModel::setSearchQuery,
                onPreviousMonth = viewModel::showPreviousMonth,
                onNextMonth = viewModel::showNextMonth,
                nextEnabled = state.month < YearMonth.now(),
                onOpenMonthPicker = { showMonthPicker = true },
                onOpenSearch = viewModel::openSearch,
                onCloseSearch = viewModel::closeSearch,
            )
            Box(modifier = Modifier.height(Space.M))
            LedgerFilterRow(filter = state.filter, onFilterChange = viewModel::setFilter)
            Box(modifier = Modifier.height(Space.M))

            if (state.groups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        illustrationRes = AppIcons.IllEmptyLedger,
                        text = if (state.searchQuery.isNotBlank()) {
                            stringResource(R.string.ledger_empty_search)
                        } else {
                            stringResource(R.string.ledger_empty_month)
                        },
                    )
                }
            } else {
                LedgerList(
                    state = state,
                    listState = listState,
                    onOpen = { editing = it },
                    onDelete = { viewModel.deleteTransaction(it.id) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
        }

        UndoSnackbarHost(
            state = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = Space.S),
        )

        if (showMonthPicker) {
            MonthPickerSheet(
                current = state.month,
                onSelect = { selected ->
                    viewModel.selectMonth(selected)
                    showMonthPicker = false
                },
                onDismiss = { showMonthPicker = false },
            )
        }

        editing?.let { transaction ->
            TransactionEditSheet(
                transaction = transaction,
                // 选择器里只放未归档的分类（规范 §11：归档的分类不再出现在列表里），
                // 而列表回查分类名与色点用的是含归档的 categoryById。
                categories = state.categoryById.values.filterNot { it.archived },
                onDismiss = { editing = null },
                onSave = { updated ->
                    lastEdited = updated
                    editing = null
                    viewModel.saveTransaction(updated)
                },
                onDelete = { target ->
                    editing = null
                    viewModel.deleteTransaction(target.id)
                },
            )
        }
    }
}

/**
 * 按自然日分组的列表。组头用 `stickyHeader` 吸顶。
 *
 * 逐项进入动效的索引是**全局位置**，靠 DSL 阶段累加出来的 `offset` 算，
 * 不能在 item 内容里自增——item 内容是懒执行的，顺序会乱。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LedgerList(
    state: LedgerUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onOpen: (Transaction) -> Unit,
    onDelete: (Transaction) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(state = listState, modifier = modifier) {
        var offset = 0
        state.groups.forEach { group ->
            val startIndex = offset
            stickyHeader(key = "day_${group.dateLabel}") {
                LedgerDayHeader(
                    dateLabel = group.dateLabel,
                    expenseCents = group.expenseCents,
                    incomeCents = group.incomeCents,
                )
            }
            itemsIndexed(items = group.items, key = { _, item -> item.id }) { indexInGroup, transaction ->
                ListItemEnter(index = startIndex + indexInGroup) {
                    SwipeToDeleteRow(onDelete = { onDelete(transaction) }) {
                        LedgerTransactionRow(
                            transaction = transaction,
                            category = state.categoryById[transaction.categoryId],
                            onClick = { onOpen(transaction) },
                        )
                    }
                }
                Divider()
            }
            offset += group.items.size
        }
    }
}

// ---------------------------------------------------------------------- Preview

@Preview(name = "账单页 / 组头与条目", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun LedgerStaticPreview() {
    val now = System.currentTimeMillis()
    val categories = listOf(
        Category(1, "餐饮", 0, SemanticKeys.Red, 0, true, false),
        Category(2, "交通", 0, SemanticKeys.Blue, 1, true, false),
        Category(9, "生活费", 1, SemanticKeys.Green, 0, true, false),
    )
    AppTheme {
        Column(modifier = Modifier.padding(Space.L)) {
            LedgerFilterRow(filter = LedgerFilter.All, onFilterChange = {})
            Box(modifier = Modifier.height(Space.M))
            LedgerDayHeader(dateLabel = "9 月 29 日", expenseCents = 10400, incomeCents = 0)
            Divider()
            LedgerTransactionRow(
                transaction = Transaction(1, 1, 1400, Direction.Expense, 1, "食堂", now, 0, null),
                category = categories[0],
                onClick = {},
            )
            Divider()
            LedgerTransactionRow(
                transaction = Transaction(2, 1, 400, Direction.Expense, 2, "", now, 0, null),
                category = categories[1],
                onClick = {},
            )
            Divider()
            LedgerDayHeader(dateLabel = "9 月 28 日", expenseCents = 0, incomeCents = 150000)
            Divider()
            LedgerTransactionRow(
                transaction = Transaction(3, 1, 150000, Direction.Income, 9, "九月生活费", now, 0, null),
                category = categories[2],
                onClick = {},
            )
            Box(modifier = Modifier.height(Space.XL))
            Column(verticalArrangement = Arrangement.spacedBy(Space.S)) {
                LedgerTopBar(
                    monthTitle = "2026 年 9 月",
                    searchActive = false,
                    searchQuery = "",
                    onQueryChange = {},
                    onPreviousMonth = {},
                    onNextMonth = {},
                    nextEnabled = false,
                    onOpenMonthPicker = {},
                    onOpenSearch = {},
                    onCloseSearch = {},
                )
                LedgerTopBar(
                    monthTitle = "2026 年 9 月",
                    searchActive = true,
                    searchQuery = "10-50",
                    onQueryChange = {},
                    onPreviousMonth = {},
                    onNextMonth = {},
                    nextEnabled = true,
                    onOpenMonthPicker = {},
                    onOpenSearch = {},
                    onCloseSearch = {},
                )
            }
        }
    }
}
