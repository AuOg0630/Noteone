// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noteone.app.R
import com.noteone.app.core.design.AppColor
import com.noteone.app.core.design.SemanticColor
import com.noteone.app.core.design.SemanticKeys
import com.noteone.app.core.design.Space
import com.noteone.app.core.design.component.AppScaffold
import com.noteone.app.core.design.component.Divider
import com.noteone.app.core.design.component.MoneyText
import com.noteone.app.core.design.component.UndoSnackbarHost
import com.noteone.app.core.design.component.showUndoSnackbar
import com.noteone.app.export.defaultCsvFileName
import com.noteone.app.feature.book.BookSwitcherSheet
import com.noteone.app.quickentry.state.A11yStatus
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** SAF 的 MIME 类型：系统文件选择器据此筛出可保存的位置。 */
private const val CSV_MIME_TYPE = "text/csv"
private const val JSON_MIME_TYPE = "application/json"

/** 失败提示的停留时间，比成功提示的 2.5s 长一些，留出点「重试」的时间。 */
private const val FAILURE_SNACKBAR_MS = 4_000L

/** `OpenDocument` 的筛选：**不限定 MIME**。 */
private val ANY_FILE = arrayOf("*/*")

/**
 * 设置页（Tab 4）。规范 §5.4 + 任务书 §2。
 *
 * **不用卡片盒**：靠「分组标题 + 条目 + 1dp 分割线」组织（SKILL.md 的 accordion 规则）。
 * 分割线只在条目之间，分组最后一条不加。
 *
 * 页面只做三件事：展示状态、收集用户操作、弹层/跳转。所有数据动作都在
 * [SettingsViewModel] 里完成，跳系统设置的四条 Intent 集中在 [SettingsIntents]。
 */
@Composable
fun SettingsScreen(
    onOpenBookManage: () -> Unit,
    onOpenCategoryManage: () -> Unit,
    onOpenQuickActionManage: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 弹 Snackbar 时要用带参数的字符串：用 LocalResources 而不是 LocalContext.getString，
    // 后者拿到的是非 configuration-aware 的 Resources（lint 会报 LocalContextGetResourceValueCall）。
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showDirectionSheet by remember { mutableStateOf(false) }
    var showBudgetSheet by remember { mutableStateOf(false) }
    var showBookSheet by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    /** 最近一次导出的目标位置，失败后「重试」直接写回同一个文件。 */
    var lastExportUri by remember { mutableStateOf<Uri?>(null) }

    // ------------------------------------------------------------------ 文案

    val ackLabel = stringResource(R.string.settings_ack)
    val retryLabel = stringResource(R.string.settings_retry)
    val exportCsvFailedText = stringResource(R.string.settings_export_failed)
    val exportJsonFailedText = stringResource(R.string.settings_backup_failed)
    val restoreCorruptedText = stringResource(R.string.settings_restore_corrupted)
    val restoreUnsupportedText = stringResource(R.string.settings_restore_unsupported)
    val restoreFailedText = stringResource(R.string.settings_restore_failed)
    val clearFailedText = stringResource(R.string.settings_clear_failed)
    val serviceLostText = stringResource(R.string.settings_service_lost)
    val reenableLabel = stringResource(R.string.settings_a11y_action_reenable)

    // ------------------------------------------------------------------ SAF

    // 用户取消文件选择时回调拿到 null，直接什么都不做（规范 §11：静默返回）
    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(CSV_MIME_TYPE),
    ) { uri: Uri? ->
        if (uri != null) {
            lastExportUri = uri
            viewModel.exportCsv(uri)
        }
    }
    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(JSON_MIME_TYPE),
    ) { uri: Uri? ->
        if (uri != null) {
            lastExportUri = uri
            viewModel.exportBackup(uri)
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        // 选完文件先弹二次确认，用户点了「覆盖并恢复」才真正读盘与写库
        if (uri != null) {
            pendingRestoreUri = uri
            showRestoreConfirm = true
        }
    }

    /** 失败后的重试。全部重走原路径，不做任何静默兜底。 */
    fun retry(operation: DataOperation) {
        when (operation) {
            DataOperation.EXPORT_CSV -> lastExportUri?.let { viewModel.exportCsv(it) }
            DataOperation.EXPORT_JSON -> lastExportUri?.let { viewModel.exportBackup(it) }
            DataOperation.CLEAR -> viewModel.clearAll()
            DataOperation.RESTORE -> restoreLauncher.launch(ANY_FILE)
        }
    }

    // ------------------------------------------------------------------ 事件

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.CsvExported -> scope.launch {
                    snackbarHostState.showUndoSnackbar(
                        message = resources.getString(R.string.settings_export_csv_done, event.count),
                        actionLabel = ackLabel,
                        onUndo = {},
                    )
                }

                SettingsEvent.BackupExported -> scope.launch {
                    snackbarHostState.showUndoSnackbar(
                        message = resources.getString(R.string.settings_backup_done),
                        actionLabel = ackLabel,
                        onUndo = {},
                    )
                }

                SettingsEvent.Restored -> scope.launch {
                    snackbarHostState.showUndoSnackbar(
                        message = resources.getString(R.string.settings_restore_done),
                        actionLabel = ackLabel,
                        onUndo = {},
                    )
                }

                SettingsEvent.Cleared -> scope.launch {
                    snackbarHostState.showUndoSnackbar(
                        message = resources.getString(R.string.settings_clear_done),
                        actionLabel = ackLabel,
                        onUndo = {},
                    )
                }

                is SettingsEvent.RestoreFailed -> scope.launch {
                    val message = when (event.reason) {
                        RestoreFailure.CORRUPTED -> restoreCorruptedText
                        RestoreFailure.UNSUPPORTED_VERSION -> restoreUnsupportedText
                        RestoreFailure.IO -> restoreFailedText
                    }
                    snackbarHostState.showUndoSnackbar(
                        message = message,
                        holdMillis = FAILURE_SNACKBAR_MS,
                        actionLabel = retryLabel,
                        onUndo = { restoreLauncher.launch(ANY_FILE) },
                    )
                }

                is SettingsEvent.Failed -> scope.launch {
                    val message = when (event.operation) {
                        DataOperation.EXPORT_CSV -> exportCsvFailedText
                        DataOperation.EXPORT_JSON -> exportJsonFailedText
                        DataOperation.RESTORE -> restoreFailedText
                        DataOperation.CLEAR -> clearFailedText
                    }
                    snackbarHostState.showUndoSnackbar(
                        message = message,
                        holdMillis = FAILURE_SNACKBAR_MS,
                        actionLabel = retryLabel,
                        onUndo = { retry(event.operation) },
                    )
                }
            }
        }
    }

    // 无障碍服务掉线提示：只提示一次，读取后立刻清标记。
    //
    // key 必须是 Unit。以前拿 state.serviceLostNotice 当 key：下面的
    // onServiceLostNoticeShown() 一执行就把标志翻回 false，key 随之变化，
    // Compose 立刻取消这个 effect —— 正挂在 showSnackbar 挂起点上的协程被一起取消，
    // 提示刚弹出来就被自己撤掉，用户根本来不及看见，更点不到「去重新开启」。
    // 现在改成订阅标志位：置位才提示，清标记不会打断正在显示的 Snackbar。
    LaunchedEffect(Unit) {
        snapshotFlow { state.serviceLostNotice }
            .filter { it }
            .collect {
                viewModel.onServiceLostNoticeShown()
                snackbarHostState.showUndoSnackbar(
                    message = serviceLostText,
                    holdMillis = FAILURE_SNACKBAR_MS,
                    actionLabel = reenableLabel,
                    onUndo = { context.startActivity(SettingsIntents.accessibility(context)) },
                )
            }
    }

    // 从系统设置返回时重新读一遍权限状态（无障碍 / 悬浮窗都只能在系统里改）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshSystemState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ------------------------------------------------------------------ 内容

    Box(modifier = modifier.fillMaxSize()) {
        AppScaffold(title = stringResource(R.string.settings_title)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                // ---------------------------------------------------- 账本
                SettingsGroupLabel(text = stringResource(R.string.settings_group_book), first = true)
                SettingRow(
                    text = stringResource(R.string.settings_book_manage),
                    onClick = onOpenBookManage,
                )
                Divider()
                SettingRow(
                    text = stringResource(R.string.settings_book_current),
                    value = state.currentBookName,
                    onClick = { showBookSheet = true },
                )

                // ---------------------------------------------------- 记账
                SettingsGroupLabel(text = stringResource(R.string.settings_group_record))
                SettingRow(
                    text = stringResource(R.string.settings_category_manage),
                    onClick = onOpenCategoryManage,
                )
                Divider()
                SettingRow(
                    text = stringResource(R.string.settings_quick_action_manage),
                    onClick = onOpenQuickActionManage,
                )
                Divider()
                SettingRow(
                    text = stringResource(R.string.settings_default_direction),
                    value = stringResource(
                        if (state.isIncomeDirection) R.string.common_income else R.string.common_expense,
                    ),
                    onClick = { showDirectionSheet = true },
                )

                // ---------------------------------------------------- 预算
                SettingsGroupLabel(text = stringResource(R.string.settings_group_budget))
                SettingSwitchRow(
                    text = stringResource(R.string.settings_budget_toggle),
                    checked = state.budgetEnabled,
                    onCheckedChange = viewModel::setBudgetEnabled,
                )
                if (state.budgetEnabled) {
                    Divider()
                    SettingRow(
                        text = stringResource(R.string.settings_budget_amount),
                        valueContent = {
                            MoneyText(
                                cents = state.budgetCents,
                                color = AppColor.Muted,
                            )
                        },
                        onClick = { showBudgetSheet = true },
                    )
                }

                // ---------------------------------------------------- 磁贴与识别
                SettingsGroupLabel(text = stringResource(R.string.settings_group_quick))
                SettingRow(
                    text = stringResource(R.string.settings_tile_guide_title),
                    showArrow = false,
                )
                SettingsNote(text = stringResource(R.string.settings_tile_guide_body))
                Divider()
                SettingStatusRow(
                    text = stringResource(R.string.settings_a11y_title),
                    statusText = a11yStatusLabel(state.a11yStatus),
                    dotColor = if (state.a11yStatus == A11yStatus.RUNNING) {
                        SemanticColor.of(SemanticKeys.Green).fg
                    } else {
                        AppColor.Faint
                    },
                    actionLabel = a11yActionLabel(state.a11yStatus),
                    onAction = { context.startActivity(SettingsIntents.accessibility(context)) },
                )
                SettingsNote(text = stringResource(R.string.settings_a11y_note))
                Divider()
                SettingRow(
                    text = stringResource(R.string.settings_overlay_title),
                    value = stringResource(
                        if (state.overlayGranted) {
                            R.string.settings_overlay_on
                        } else {
                            R.string.settings_overlay_off
                        },
                    ),
                    onClick = { context.startActivity(SettingsIntents.overlay(context)) },
                )
                Divider()
                SettingActionRow(
                    text = stringResource(R.string.settings_keepalive_title),
                    actionLabel = stringResource(R.string.settings_keepalive_action),
                    onAction = { context.startActivity(SettingsIntents.appDetails(context)) },
                )
                SettingsNote(text = stringResource(R.string.settings_keepalive_body))
                Divider()
                SettingRow(
                    text = stringResource(R.string.settings_import_from_image),
                    onClick = { context.startActivity(SettingsIntents.pickImage(context)) },
                )
                Divider()
                SettingSwitchRow(
                    text = stringResource(R.string.settings_prefer_symbol_title),
                    checked = state.preferCurrencySymbol,
                    onCheckedChange = viewModel::setPreferCurrencySymbol,
                )

                // ---------------------------------------------------- 数据
                SettingsGroupLabel(text = stringResource(R.string.settings_group_data))
                SettingRow(
                    text = stringResource(R.string.settings_export_csv),
                    onClick = { csvLauncher.launch(defaultCsvFileName()) },
                )
                Divider()
                SettingRow(
                    text = stringResource(R.string.settings_export_json),
                    onClick = { backupLauncher.launch(defaultBackupFileName()) },
                )
                Divider()
                SettingRow(
                    text = stringResource(R.string.settings_restore_json),
                    onClick = { restoreLauncher.launch(ANY_FILE) },
                )
                Divider()
                SettingRow(
                    text = stringResource(R.string.settings_clear_all),
                    showArrow = false,
                    danger = true,
                    onClick = { showClearConfirm = true },
                )

                // ---------------------------------------------------- 关于
                SettingsGroupLabel(text = stringResource(R.string.settings_group_about))
                SettingRow(
                    text = stringResource(R.string.settings_version),
                    value = state.versionName,
                    showArrow = false,
                )
                Divider()
                SettingsNote(text = stringResource(R.string.settings_privacy_note))
                Box(modifier = Modifier.height(Space.L))
            }
        }

        // 成功 / 失败提示都从页面顶部进入（与记账页一致）
        UndoSnackbarHost(
            state = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = Space.S),
        )
    }

    // ------------------------------------------------------------------ 弹层

    if (showBookSheet) {
        BookSwitcherSheet(
            books = state.books,
            currentBookId = state.currentBookId,
            onSelect = { bookId ->
                viewModel.selectBook(bookId)
                showBookSheet = false
            },
            onManage = {
                showBookSheet = false
                onOpenBookManage()
            },
            onDismiss = { showBookSheet = false },
        )
    }

    if (showDirectionSheet) {
        DirectionSheet(
            currentDirection = state.defaultDirection,
            onSelect = { direction ->
                viewModel.setDefaultDirection(direction)
                showDirectionSheet = false
            },
            onDismiss = { showDirectionSheet = false },
        )
    }

    if (showBudgetSheet) {
        BudgetAmountSheet(
            initialCents = state.budgetCents,
            onConfirm = { cents ->
                viewModel.setBudgetCents(cents)
                showBudgetSheet = false
            },
            onDismiss = { showBudgetSheet = false },
        )
    }

    if (showRestoreConfirm) {
        RestoreConfirmSheet(
            onConfirm = {
                showRestoreConfirm = false
                pendingRestoreUri?.let { viewModel.restore(it) }
                pendingRestoreUri = null
            },
            onDismiss = {
                showRestoreConfirm = false
                pendingRestoreUri = null
            },
        )
    }

    if (showClearConfirm) {
        ClearDataSheet(
            onConfirm = {
                showClearConfirm = false
                viewModel.clearAll()
            },
            onDismiss = { showClearConfirm = false },
        )
    }
}
