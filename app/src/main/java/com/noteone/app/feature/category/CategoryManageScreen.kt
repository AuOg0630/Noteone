// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.feature.category

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import com.noteone.app.core.data.model.Category
import com.noteone.app.core.data.model.Direction
import com.noteone.app.core.design.AppColor
import com.noteone.app.core.design.AppType
import com.noteone.app.core.design.SemanticColor
import com.noteone.app.core.design.Space
import com.noteone.app.core.design.component.TextButtonSmall
import com.noteone.app.core.design.component.TextToggleRow
import com.noteone.app.core.design.component.UndoSnackbarHost
import com.noteone.app.core.design.component.showUndoSnackbar
import com.noteone.app.feature.manage.ColorDot
import com.noteone.app.feature.manage.ManageGroupLabel
import com.noteone.app.feature.manage.ManageRow
import com.noteone.app.feature.manage.ManageScaffold
import com.noteone.app.feature.manage.ReorderableList
import kotlinx.coroutines.launch

/** 失败提示的停留时长。 */
private const val FAILURE_SNACKBAR_MS = 4_000L

/**
 * 分类管理页（二级页，不带底栏）。任务书 §4。
 *
 * 支出 / 收入两个分区（三态切换的视觉与账单页、记账页完全一致），
 * 条目 = 色点 + 名称 + 拖拽手柄；点条目编辑，长按手柄排序。
 *
 * **删除 = 归档**：归档后不再出现在记账页，但历史记录仍显示原分类名与色点，
 * 并且可以在「已归档」区恢复。
 */
@Composable
fun CategoryManageScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoryManageViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showEditor by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var archivingCategory by remember { mutableStateOf<Category?>(null) }

    val ackLabel = stringResource(R.string.settings_ack)
    val failedText = stringResource(R.string.settings_category_failed)

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
            title = stringResource(R.string.settings_category_manage_title),
            onBack = onBack,
            primaryLabel = stringResource(R.string.settings_category_new),
            primaryEnabled = true,
            onPrimary = {
                editingCategory = null
                showEditor = true
            },
            subHeader = {
                TextToggleRow(
                    labels = listOf(
                        stringResource(R.string.settings_tab_expense),
                        stringResource(R.string.settings_tab_income),
                    ),
                    selectedIndex = if (state.direction == Direction.Income) 1 else 0,
                    onSelect = {
                        viewModel.selectDirection(
                            if (it == 1) Direction.Income else Direction.Expense,
                        )
                    },
                )
            },
        ) {
            if (state.categories.isEmpty() && state.archived.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_category_empty),
                    style = AppType.Caption,
                    color = AppColor.Faint,
                    modifier = Modifier.padding(top = Space.XL),
                )
                return@ManageScaffold
            }

            ReorderableList(
                items = state.categories,
                keyOf = { it.id },
                onMove = viewModel::move,
                modifier = Modifier.fillMaxSize(),
                // 已归档区固定在列表末尾，不参与拖拽；没有归档分类时它自己不渲染
                trailingContent = {
                    ArchivedCategories(items = state.archived, onRestore = viewModel::restore)
                },
            ) { index, category, _, handle ->
                ManageRow(
                    title = category.name,
                    onClick = {
                        editingCategory = category
                        showEditor = true
                    },
                    divider = index < state.categories.lastIndex,
                    handle = handle,
                    leading = { ColorDot(colorKey = category.colorKey) },
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
        val editing = editingCategory
        CategoryEditorSheet(
            isNew = editing == null,
            initialName = editing?.name.orEmpty(),
            initialColorKey = editing?.colorKey ?: SemanticColor.keys.first(),
            initialDirection = editing?.direction ?: state.direction,
            onSave = { name, direction, colorKey ->
                if (editing == null) {
                    viewModel.add(name, direction, colorKey)
                } else {
                    viewModel.rename(editing.id, name)
                    viewModel.setColor(editing.id, colorKey)
                }
                showEditor = false
            },
            onArchive = {
                showEditor = false
                archivingCategory = editing
            },
            onDismiss = { showEditor = false },
        )
    }

    val archiving = archivingCategory
    if (archiving != null) {
        CategoryArchiveSheet(
            categoryName = archiving.name,
            onConfirm = {
                viewModel.archive(archiving.id)
                archivingCategory = null
            },
            onDismiss = { archivingCategory = null },
        )
    }
}

/** 「已归档」折叠区：条目不可拖拽，行尾一个「恢复」。没有归档分类时整块不渲染。 */
@Composable
private fun ArchivedCategories(
    items: List<Category>,
    onRestore: (Long) -> Unit,
) {
    if (items.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        ManageGroupLabel(text = stringResource(R.string.settings_category_archived))
        items.forEachIndexed { index, category ->
            ManageRow(
                title = category.name,
                divider = index < items.lastIndex,
                showHandle = false,
                leading = { ColorDot(colorKey = category.colorKey) },
                trailing = {
                    TextButtonSmall(
                        text = stringResource(R.string.settings_category_restore),
                        onClick = { onRestore(category.id) },
                    )
                },
            )
        }
    }
}
