// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.feature.book

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noteone.app.R
import com.noteone.app.core.data.model.Book
import com.noteone.app.core.design.SemanticColor
import com.noteone.app.core.design.Space
import com.noteone.app.core.design.component.UndoSnackbarHost
import com.noteone.app.core.design.component.showUndoSnackbar
import com.noteone.app.feature.manage.ColorDot
import com.noteone.app.feature.manage.ManageRow
import com.noteone.app.feature.manage.ManageScaffold
import com.noteone.app.feature.manage.ReorderableList
import com.noteone.app.feature.manage.StatusBadge
import kotlinx.coroutines.launch

/** 失败提示比成功提示停久一点，留出点「知道了」的时间。 */
private const val FAILURE_SNACKBAR_MS = 4_000L

/**
 * 账本管理页（二级页，不带底栏）。任务书 §3。
 *
 * 列表 + 底部固定「＋ 新建」。点条目编辑（改名 / 换色 / 删除），长按手柄拖拽排序。
 * 上限 10 个；**至少要保留一个账本**，所以只剩一个时不给删除入口。
 */
@Composable
fun BookManageScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookManageViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    /** 编辑弹层：null 表示关闭；[editingBook] 为 null 表示新建。 */
    var showEditor by remember { mutableStateOf(false) }
    var editingBook by remember { mutableStateOf<Book?>(null) }

    val ackLabel = stringResource(R.string.settings_ack)
    val failedText = stringResource(R.string.settings_book_failed)

    LaunchedEffect(viewModel) {
        viewModel.events.collect {
            scope.launch {
                snackbarHostState.showUndoSnackbar(
                    message = failedText,
                    holdMillis = FAILURE_SNACKBAR_MS,
                    actionLabel = ackLabel,
                    onUndo = {},
                )
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        ManageScaffold(
            title = stringResource(R.string.settings_book_manage_title),
            onBack = onBack,
            primaryLabel = stringResource(R.string.settings_book_new),
            primaryEnabled = !state.atLimit,
            primaryDisabledHint = if (state.atLimit) {
                stringResource(R.string.settings_book_limit)
            } else {
                null
            },
            onPrimary = {
                editingBook = null
                showEditor = true
            },
        ) {
            ReorderableList(
                items = state.books,
                keyOf = { it.id },
                onMove = viewModel::move,
                modifier = Modifier.fillMaxSize(),
            ) { index, book, _, handle ->
                ManageRow(
                    title = book.name,
                    onClick = {
                        editingBook = book
                        showEditor = true
                    },
                    divider = index < state.books.lastIndex,
                    handle = handle,
                    leading = { ColorDot(colorKey = book.colorKey) },
                    trailing = {
                        if (book.isDefault) {
                            StatusBadge(text = stringResource(R.string.settings_book_default_badge))
                        }
                    },
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
    }

    if (showEditor) {
        val editing = editingBook
        if (editing == null) {
            BookEditorSheet(
                isNew = true,
                initialName = "",
                // 新账本默认按顺序取色，避免每次都从同一个颜色开始
                initialColorKey = SemanticColor.keys[state.books.size % SemanticColor.keys.size],
                canDelete = false,
                onSave = { name, colorKey ->
                    viewModel.add(name, colorKey)
                    showEditor = false
                },
                onDelete = {},
                onDismiss = { showEditor = false },
            )
        } else {
            BookEditorSheet(
                isNew = false,
                initialName = editing.name,
                initialColorKey = editing.colorKey,
                canDelete = state.canDelete,
                onSave = { name, colorKey ->
                    viewModel.rename(editing.id, name)
                    viewModel.setColor(editing.id, colorKey)
                    showEditor = false
                },
                onDelete = {
                    showEditor = false
                    viewModel.requestDelete(editing)
                },
                onDismiss = { showEditor = false },
            )
        }
    }

    val pending = state.pendingDelete
    if (pending != null) {
        BookDeleteSheet(
            pending = pending,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
}
