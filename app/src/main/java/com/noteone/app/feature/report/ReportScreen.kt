// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.feature.report

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.noteone.app.core.design.Space
import com.noteone.app.core.design.component.AppScaffold
import com.noteone.app.core.design.component.EmptyState
import com.noteone.app.core.design.component.UndoSnackbarHost
import com.noteone.app.core.design.component.showUndoSnackbar
import com.noteone.app.core.design.icon.AppIcons
import com.noteone.app.export.defaultCsvFileName
import com.noteone.app.navigation.Route
import com.noteone.app.navigation.TabReselect
import kotlinx.coroutines.launch

/** SAF 的 MIME 类型。系统文件选择器据此筛出可保存的位置。 */
private const val CSV_MIME_TYPE = "text/csv"

/** 失败提示窗口，比成功撤销的 2.5s 长一点，留出点「重试」的时间。 */
private const val FAILURE_SNACKBAR_MS = 4_000L

/**
 * 汇总页（Tab 3）。规范 §5.3。
 *
 * 自上而下：页头（标题 + 导出）→ 时间范围 chip → 分类筛选 chip → 三卡 + 日均支出
 * → 分类排行 → 趋势图。三卡 / 排行 / 趋势由同一个 Flow 派生，筛选变化必然联动。
 *
 * 这是全站唯一使用 24sp 页面标题的页面。
 */
@Composable
fun ReportScreen(
    modifier: Modifier = Modifier,
    viewModel: ReportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    /** 是否正在选自定义范围。 */
    var showCustomRange by remember { mutableStateOf(false) }

    /** 最近一次导出的目标位置，供失败后「重试」。 */
    var lastExportUri by remember { mutableStateOf<Uri?>(null) }

    // 文案在 composable 作用域里取好：`stringResource` 不能在挂起块里调用
    val exportFailedText = stringResource(R.string.report_export_failed)
    val retryLabel = stringResource(R.string.report_export_retry)

    // 用户取消文件选择时回调拿到 null，直接什么都不做（规范 §11：静默返回）
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(CSV_MIME_TYPE),
    ) { uri: Uri? ->
        if (uri != null) {
            lastExportUri = uri
            viewModel.exportCsv(uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ReportEvent.ExportFailed -> scope.launch {
                    snackbarHostState.showUndoSnackbar(
                        message = exportFailedText,
                        holdMillis = FAILURE_SNACKBAR_MS,
                        actionLabel = retryLabel,
                        onUndo = { lastExportUri?.let(viewModel::exportCsv) },
                    )
                }
            }
        }
    }

    // 重复点当前 Tab → 滚回顶部（规范 §4.2）
    LaunchedEffect(Unit) {
        TabReselect.events.collect { route ->
            if (route == Route.Report) scrollState.animateScrollTo(0)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AppScaffold(title = "") {
            ReportHeader(onExport = { exportLauncher.launch(defaultCsvFileName()) })
            Box(modifier = Modifier.height(Space.L))
            ReportPresetRow(
                preset = state.preset,
                customRangeLabel = state.customRangeLabel,
                onSelect = { preset ->
                    // 「自定义」先弹范围选择器，用户确定后才提交（取消则保持原范围）
                    if (preset == ReportPreset.Custom) showCustomRange = true else viewModel.selectPreset(preset)
                },
            )
            Box(modifier = Modifier.height(Space.M))
            ReportCategoryFilterRow(
                categories = state.filterCategories,
                selectedIds = state.selectedCategoryIds,
                onSelectAll = viewModel::clearCategories,
                onToggle = viewModel::toggleCategory,
            )
            Box(modifier = Modifier.height(Space.L))

            when {
                // 首帧还没拿到 Room 的第一条数据：留白，避免闪一下空状态
                state.loading -> Spacer(modifier = Modifier.weight(1f))

                !state.hasRecords -> Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        illustrationRes = AppIcons.IllEmptyReport,
                        text = stringResource(R.string.report_empty),
                    )
                }

                else -> Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                ) {
                    ReportSummaryCards(summary = state.summary)
                    Box(modifier = Modifier.height(Space.M))
                    ReportDailyAverageRow(cents = state.summary.dailyAverageCents)

                    // 预算没开或限额为 0 时 budget 为 null，整块不渲染
                    state.budget?.let { budget ->
                        Box(modifier = Modifier.height(Space.XXL))
                        BudgetProgressRow(progress = budget)
                    }
                    Box(modifier = Modifier.height(Space.XXL))

                    // 范围内只有收入时支出为 0，此时不占位一个空标题
                    if (state.slices.isNotEmpty()) {
                        CategoryRankingSection(slices = state.slices)
                        Box(modifier = Modifier.height(Space.XXL))
                    }

                    TrendSection(
                        points = state.trend,
                        startLabel = state.trendStartLabel,
                        endLabel = state.trendEndLabel,
                    )
                    Box(modifier = Modifier.height(Space.L))
                }
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

    if (showCustomRange) {
        CustomRangeSheet(
            initialStart = state.rangeStart,
            initialEnd = state.rangeEnd,
            onConfirm = { start, end ->
                viewModel.applyCustomRange(start, end)
                showCustomRange = false
            },
            onDismiss = { showCustomRange = false },
        )
    }
}
