// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.feature.record

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noteone.app.R
import com.noteone.app.core.design.Space
import com.noteone.app.core.design.component.AppScaffold
import com.noteone.app.core.design.component.UndoSnackbarHost
import com.noteone.app.core.design.component.formatMoneyText
import com.noteone.app.core.design.component.showUndoSnackbar
import kotlinx.coroutines.launch
import java.util.Locale

/** 失败提示窗口比成功窗口长一点，给用户反应「重试」的时间。 */
private const val FAILURE_SNACKBAR_MS = 4_000L

/**
 * 记账页（首页 / Tab 1）。规范 §5.1。
 *
 * 页面只有两段：**账本头部** 与 **记账卡片**。卡片 `weight(1f)` 撑满其余高度，
 * 多出来的空间全部给金额区，键盘因此落在底栏正上方——既没有半屏空白，
 * 也是拇指最舒服的位置。
 *
 * ## 首页为什么不再有别的区块
 *
 * v1.6 起这里只做「记一笔」这一件事：
 *
 * - 本月概览 / 近 7 日趋势 / 最近账单 → 「汇总」与「账单」两个 Tab 给得更细
 * - 快捷用途 chip 行 → 与卡片内的分类行**功能完全重叠**（内置快捷按钮的名字与
 *   分类名一字不差，屏幕上就是两行一样的 chip）。它只在两处还有意义：
 *   「设置 → 快捷按钮」里管理，「记一笔」悬浮面板里当快速分类。首页不再复制一份
 *
 * 保留的月度数字只有固定头部右上角的「本月结余」，它属于账本上下文，不算统计区。
 */
@Composable
fun RecordScreen(
    onOpenBookManage: () -> Unit = {},
    onOpenCategoryManage: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: RecordViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 文案在 composable 作用域里取好：`stringResource` 是 @Composable，
    // 不能用在 `LaunchedEffect` 的挂起块里；而 lint 也不允许在 composable 里
    // 通过 `LocalContext.current.getString` 取值（Configuration 变化时会是旧值）。
    // 带占位符的两条先取模板，事件到达时再用当前 Locale 格式化。
    val savedWithCategoryTemplate = stringResource(R.string.record_saved_with_category)
    val savedPlainTemplate = stringResource(R.string.record_saved_plain)
    val saveFailedText = stringResource(R.string.record_save_failed)
    val retryLabel = stringResource(R.string.record_retry)

    /** 每次成功入账 +1，驱动金额区的 scale 微动效。 */
    var pulse by remember { mutableIntStateOf(0) }
    var showBookSheet by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is RecordEvent.Saved -> {
                    pulse += 1
                    val amount = formatMoneyText(event.cents)
                    val locale = Locale.getDefault()
                    val message = if (event.categoryName != null) {
                        savedWithCategoryTemplate.format(locale, amount, event.categoryName)
                    } else {
                        savedPlainTemplate.format(locale, amount)
                    }
                    scope.launch {
                        snackbarHostState.showUndoSnackbar(message) {
                            viewModel.undoDelete(event.transactionId)
                        }
                    }
                }

                RecordEvent.SaveFailed -> {
                    scope.launch {
                        snackbarHostState.showUndoSnackbar(
                            message = saveFailedText,
                            holdMillis = FAILURE_SNACKBAR_MS,
                            actionLabel = retryLabel,
                            onUndo = { viewModel.submit() },
                        )
                    }
                }
            }
        }
    }

    // 注意：这里**不再**订阅 `TabReselect`。首页已没有任何滚动容器，
    // 重复点当前 Tab 的「滚回顶部」没有落脚点，留着也只是一段永远不生效的代码。

    Box(modifier = modifier.fillMaxSize()) {
        AppScaffold(title = "") {
            RecordHeaderRow(
                bookName = state.bookName,
                balanceCents = state.monthBalanceCents,
                onSwitchBook = { showBookSheet = true },
            )
            Box(modifier = Modifier.height(Space.M))
            RecordForm(
                isIncome = state.isIncome,
                onDirectionChange = viewModel::onDirectionChange,
                amountText = state.amountText,
                onDigit = viewModel::onDigit,
                onBackspace = viewModel::onBackspace,
                onDone = viewModel::submit,
                doneEnabled = state.doneEnabled,
                categories = state.categories,
                selectedCategoryId = state.selectedCategoryId,
                onCategorySelect = { viewModel.onCategorySelected(it.id) },
                onOpenCategoryPicker = { showCategorySheet = true },
                note = state.note,
                onNoteChange = viewModel::onNoteChange,
                pulse = pulse,
                // 撑满头部与底栏之间的全部高度：多出来的空间给金额区，键盘贴底
                modifier = Modifier.weight(1f),
                fillHeight = true,
            )
        }

        // 成功 / 失败的提示都从页面顶部进入（规范 §5.1）
        UndoSnackbarHost(
            state = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = Space.S),
        )

        if (showBookSheet) {
            BookSwitchSheet(
                books = state.books,
                currentBookId = state.bookId,
                onSelect = { bookId ->
                    viewModel.onBookSelected(bookId)
                    showBookSheet = false
                },
                onManage = {
                    showBookSheet = false
                    onOpenBookManage()
                },
                onDismiss = { showBookSheet = false },
            )
        }

        if (showCategorySheet) {
            CategoryPickerSheet(
                categories = state.categories,
                selectedCategoryId = state.selectedCategoryId,
                onSelect = { category ->
                    viewModel.onCategorySelected(category.id)
                    showCategorySheet = false
                },
                onManage = {
                    showCategorySheet = false
                    onOpenCategoryManage()
                },
                onDismiss = { showCategorySheet = false },
            )
        }
    }
}
