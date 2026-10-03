// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.feature.category

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import com.noteone.app.core.data.model.Direction
import com.noteone.app.core.data.model.QuickAction
import com.noteone.app.core.design.AppColor
import com.noteone.app.core.design.AppType
import com.noteone.app.core.design.Space
import com.noteone.app.core.design.component.UndoSnackbarHost
import com.noteone.app.core.design.component.showUndoSnackbar
import com.noteone.app.feature.manage.ManageRow
import com.noteone.app.feature.manage.ManageScaffold
import com.noteone.app.feature.manage.ReorderableList
import kotlinx.coroutines.launch

/** 失败提示的停留时长。 */
private const val FAILURE_SNACKBAR_MS = 4_000L

/**
 * 快捷按钮管理页（二级页，不带底栏）。任务书 §5。
 *
 * 条目 = 名称 + 所属分类名（13sp `Muted`）+ 拖拽手柄；点条目编辑，长按手柄排序。
 * 上限 8 个（内置数据正好写满 8 个），达上限时底部「＋ 新建」置灰并提示。
 *
 * 页面顺序与记账页快捷区完全一致——两边都按 `sortOrder` 升序取数据。
 * **这里没有金额输入，也不许加**：点击语义是「选中该分类」，不是入账。
 */
@Composable
fun QuickActionManageScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuickActionManageViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showEditor by remember { mutableStateOf(false) }
    var editingAction by remember { mutableStateOf<QuickAction?>(null) }
    var deletingAction by remember { mutableStateOf<QuickAction?>(null) }

    val ackLabel = stringResource(R.string.settings_ack)
    val failedText = stringResource(R.string.settings_quick_failed)
    val uncategorizedText = stringResource(R.string.settings_uncategorized)

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
            title = stringResource(R.string.settings_quick_manage_title),
            onBack = onBack,
            primaryLabel = stringResource(R.string.settings_quick_new),
            primaryEnabled = !state.atLimit,
            primaryDisabledHint = if (state.atLimit) {
                stringResource(R.string.settings_quick_limit)
            } else {
                null
            },
            onPrimary = {
                editingAction = null
                showEditor = true
            },
        ) {
            if (state.actions.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_quick_empty),
                    style = AppType.Caption,
                    color = AppColor.Faint,
                    modifier = Modifier.padding(top = Space.XL),
                )
                return@ManageScaffold
            }

            ReorderableList(
                items = state.actions,
                keyOf = { it.id },
                onMove = viewModel::move,
                modifier = Modifier.fillMaxSize(),
            ) { index, action, _, handle ->
                ManageRow(
                    // 分类已被归档时回查不到可用分类，退化成「未分类」而不是空白
                    subtitle = state.categoryById[action.categoryId]?.name ?: uncategorizedText,
                    title = action.label,
                    onClick = {
                        editingAction = action
                        showEditor = true
                    },
                    divider = index < state.actions.lastIndex,
                    handle = handle,
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
        val editing = editingAction
        QuickActionEditorSheet(
            isNew = editing == null,
            initialLabel = editing?.label.orEmpty(),
            initialCategoryId = editing?.categoryId ?: 0L,
            initialDirection = editing?.direction ?: Direction.Expense,
            categories = state.categories,
            onSave = { label, categoryId, direction ->
                if (editing == null) {
                    viewModel.add(label, categoryId, direction)
                } else {
                    viewModel.update(editing.id, label, categoryId, direction)
                }
                showEditor = false
            },
            onDelete = {
                showEditor = false
                deletingAction = editing
            },
            onDismiss = { showEditor = false },
        )
    }

    val deleting = deletingAction
    if (deleting != null) {
        QuickActionDeleteSheet(
            label = deleting.label,
            onConfirm = {
                viewModel.archive(deleting.id)
                deletingAction = null
            },
            onDismiss = { deletingAction = null },
        )
    }
}
