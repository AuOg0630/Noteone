// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.feature.settings

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noteone.app.core.data.model.Book
import com.noteone.app.core.data.model.Direction
import com.noteone.app.core.data.repository.BackupRepository
import com.noteone.app.core.data.repository.BookRepository
import com.noteone.app.core.data.repository.SettingsRepository
import com.noteone.app.export.CsvExportService
import com.noteone.app.quickentry.state.A11yStatus
import com.noteone.app.quickentry.state.QuickEntryState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/** 「数据」分组里的四个动作。失败提示与「重试」都要知道是哪一个失败了。 */
enum class DataOperation { EXPORT_CSV, EXPORT_JSON, RESTORE, CLEAR }

/** 恢复失败的原因。三种文案完全不同，必须分开（任务书 §6.2）。 */
enum class RestoreFailure { CORRUPTED, UNSUPPORTED_VERSION, IO }

/** 设置页的一次性事件。**不放 UiState**，避免重组时重复弹 Snackbar。 */
sealed interface SettingsEvent {

    data class CsvExported(val count: Int) : SettingsEvent

    data object BackupExported : SettingsEvent

    data object Restored : SettingsEvent

    data class RestoreFailed(val reason: RestoreFailure) : SettingsEvent

    data object Cleared : SettingsEvent

    data class Failed(val operation: DataOperation) : SettingsEvent
}

/** 设置页的全部渲染状态。 */
data class SettingsUiState(
    val books: List<Book> = emptyList(),
    val currentBookId: Long = 0L,
    val currentBookName: String = "",
    val defaultDirection: Int = Direction.Expense,
    val budgetEnabled: Boolean = false,
    val budgetCents: Long = 0L,
    val preferCurrencySymbol: Boolean = true,
    val a11yStatus: A11yStatus = A11yStatus.DISABLED,
    val overlayGranted: Boolean = false,
    val versionName: String = "",
    /** 导入 / 导出 / 清空进行中，防止连点。 */
    val busy: Boolean = false,
    /** 无障碍服务掉线提示（只提示一次，由页面读取后调 [SettingsViewModel.onServiceLostNoticeShown] 清掉）。 */
    val serviceLostNotice: Boolean = false,
) {
    val isIncomeDirection: Boolean get() = defaultDirection == Direction.Income
}

/** 权限与系统状态的快照，非 Flow 来源，只能主动刷新。 */
private data class SystemState(
    val a11yStatus: A11yStatus = A11yStatus.DISABLED,
    val overlayGranted: Boolean = false,
)

/** DataStore / Room 来源的偏好快照。 */
private data class PreferenceState(
    val books: List<Book> = emptyList(),
    val currentBookId: Long = 0L,
    val defaultDirection: Int = Direction.Expense,
    val budgetEnabled: Boolean = false,
    val budgetCents: Long = 0L,
    val preferCurrencySymbol: Boolean = true,
)

/**
 * 设置页（Tab 4）的 ViewModel。
 *
 * 只有这里会调用 `BackupRepository` / `CsvExportService` / `QuickEntryState`；
 * **不碰 DAO 与 DataStore**（规范 §5.3 的硬要求）。
 *
 * 权限状态（无障碍、悬浮窗）来自系统而不是 Flow，所以用 [refreshSystemState] 主动刷新；
 * 页面在 `ON_RESUME` 时调一次，用户从系统设置返回后状态立刻跟上。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val csvExportService: CsvExportService,
) : ViewModel() {

    private val systemState = MutableStateFlow(SystemState())
    private val busy = MutableStateFlow(false)
    private val serviceLostNotice = MutableStateFlow(false)

    private val _events = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 4)

    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    private val preferences: Flow<PreferenceState> = combine(
        bookRepository.observeAll(),
        bookRepository.observeCurrent(),
        settingsRepository.defaultDirection,
        settingsRepository.budgetEnabled,
        settingsRepository.budgetCents,
    ) { books, current, direction, budgetEnabled, budgetCents ->
        PreferenceState(
            books = books,
            currentBookId = current.id,
            defaultDirection = direction,
            budgetEnabled = budgetEnabled,
            budgetCents = budgetCents,
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        preferences,
        settingsRepository.preferCurrencySymbol,
        combine(systemState, busy, serviceLostNotice) { system, working, notice ->
            Triple(system, working, notice)
        },
    ) { preference, preferSymbol, extra ->
        SettingsUiState(
            books = preference.books,
            currentBookId = preference.currentBookId,
            currentBookName = preference.books.firstOrNull { it.id == preference.currentBookId }?.name
                ?: preference.books.firstOrNull()?.name.orEmpty(),
            defaultDirection = preference.defaultDirection,
            budgetEnabled = preference.budgetEnabled,
            budgetCents = preference.budgetCents,
            preferCurrencySymbol = preferSymbol,
            a11yStatus = extra.first.a11yStatus,
            overlayGranted = extra.first.overlayGranted,
            versionName = appVersionName(),
            busy = extra.second,
            serviceLostNotice = extra.third,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(versionName = appVersionName()),
    )

    init {
        refreshSystemState()
        // 服务掉线提示「下次进入 App 时提示一次」，读取即清除（D 的约定）
        if (QuickEntryState.consumeServiceLostNotice(context)) {
            serviceLostNotice.value = true
        }
    }

    // ------------------------------------------------------------------ 系统状态

    /** 重新读取无障碍服务与悬浮窗权限状态。页面 `ON_RESUME` 时调用。 */
    fun refreshSystemState() {
        systemState.value = SystemState(
            a11yStatus = QuickEntryState.checkStatus(context),
            overlayGranted = android.provider.Settings.canDrawOverlays(context),
        )
    }

    fun onServiceLostNoticeShown() {
        serviceLostNotice.value = false
    }

    // ------------------------------------------------------------------ 偏好写入

    fun setDefaultDirection(direction: Int) {
        viewModelScope.launch { settingsRepository.setDefaultDirection(direction) }
    }

    fun setBudgetEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBudgetEnabled(enabled) }
    }

    fun setBudgetCents(cents: Long) {
        viewModelScope.launch { settingsRepository.setBudgetCents(cents.coerceAtLeast(0L)) }
    }

    fun setPreferCurrencySymbol(prefer: Boolean) {
        viewModelScope.launch { settingsRepository.setPreferCurrencySymbol(prefer) }
    }

    fun selectBook(bookId: Long) {
        viewModelScope.launch { bookRepository.setCurrent(bookId) }
    }

    // ------------------------------------------------------------------ 数据动作

    /** 导出**当前账本全部记录**为 CSV，不受汇总页筛选影响（任务书 §2.3）。 */
    fun exportCsv(uri: Uri) = runDataOperation(DataOperation.EXPORT_CSV) {
        val count = csvExportService.exportAll(uri)
        _events.emit(SettingsEvent.CsvExported(count))
    }

    /** 导出 JSON 备份（含软删除记录）。 */
    fun exportBackup(uri: Uri) = runDataOperation(DataOperation.EXPORT_JSON) {
        val snapshot = backupRepository.snapshot()
        // 编码是逐字段拼串 + 逐字符转义：几千条记录就是几 MB 的字符串操作。
        // viewModelScope 默认跑在 Main.immediate 上，不切线程会直接卡住界面。
        val text = withContext(Dispatchers.Default) { BackupJson.encode(snapshot) }
        withContext(Dispatchers.IO) {
            // 打不开所选位置时 writeText 自己抛 IOException，由 runDataOperation 兜住
            writeText(uri, text)
        }
        _events.emit(SettingsEvent.BackupExported)
    }

    /**
     * 从备份恢复。**调用前必须已经弹层二次确认。**
     *
     * 解析失败时数据一个字节都不动（任务书 §6.2）；`restore` 本身也是事务，
     * 中途失败整批回滚。
     */
    fun restore(uri: Uri) = runDataOperation(DataOperation.RESTORE) {
        val text = withContext(Dispatchers.IO) {
            readText(uri) ?: throw IOException("无法读取所选文件")
        }
        // 同理：解码同样不能放在主线程
        val parsed = withContext(Dispatchers.Default) { BackupJson.decode(text) }
        when (parsed) {
            BackupParseResult.Corrupted ->
                _events.emit(SettingsEvent.RestoreFailed(RestoreFailure.CORRUPTED))

            BackupParseResult.UnsupportedVersion ->
                _events.emit(SettingsEvent.RestoreFailed(RestoreFailure.UNSUPPORTED_VERSION))

            is BackupParseResult.Success -> {
                backupRepository.restore(parsed.snapshot)
                _events.emit(SettingsEvent.Restored)
            }
        }
    }

    /** 清空所有数据并重建默认数据。**调用前必须已经二次确认 + 输入「清空」。** */
    fun clearAll() = runDataOperation(DataOperation.CLEAR) {
        backupRepository.clearAll()
        _events.emit(SettingsEvent.Cleared)
    }

    /** 统一的「忙判定 + 异常转事件」外壳，四个数据动作共用。 */
    private fun runDataOperation(operation: DataOperation, block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (operation == DataOperation.RESTORE) {
                    _events.emit(SettingsEvent.RestoreFailed(RestoreFailure.IO))
                } else {
                    _events.emit(SettingsEvent.Failed(operation))
                }
            } finally {
                busy.value = false
            }
        }
    }

    // ------------------------------------------------------------------ 文件读写

    private fun writeText(uri: Uri, text: String) {
        // "wt" = write + truncate：SAF 返回的可能是已存在的文档，不截断会残留旧内容
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("无法打开所选位置用于写入")
        stream.use {
            it.write(text.toByteArray(Charsets.UTF_8))
            it.flush()
        }
    }

    private fun readText(uri: Uri): String? =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }

    /** 版本号只用于「关于」分组的只读展示，取不到就显示空。 */
    @Suppress("DEPRECATION")
    private fun appVersionName(): String = runCatching {
        val manager = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0)).versionName
        } else {
            manager.getPackageInfo(context.packageName, 0).versionName
        }
    }.getOrNull().orEmpty()
}
