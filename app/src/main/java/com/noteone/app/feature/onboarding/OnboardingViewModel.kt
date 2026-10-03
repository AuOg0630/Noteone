// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.feature.onboarding

import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModel
import com.noteone.app.quickentry.state.A11yStatus
import com.noteone.app.quickentry.state.QuickEntryState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** 引导页上两项权限的当前状态。 */
data class OnboardingUiState(
    val overlayGranted: Boolean = false,
    val a11yStatus: A11yStatus = A11yStatus.DISABLED,
)

/**
 * 首次启动权限引导页的 ViewModel。
 *
 * 权限状态**没有 Flow 来源**：无障碍服务与悬浮窗都只能在系统设置里改，
 * 系统不会通知本进程。所以这里提供 [refresh]，由页面在 `ON_RESUME` 时调一次——
 * 用户去系统设置授完权返回，状态立刻跟上。
 *
 * 判定逻辑直接复用 D 的 [QuickEntryState.checkStatus]（设置页用的是同一个方法），
 * 不自己重写一套「服务在不在」的判断。
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())

    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** 重新读取悬浮窗与无障碍服务状态。页面 `ON_RESUME` 时调用。 */
    fun refresh() {
        _uiState.value = OnboardingUiState(
            overlayGranted = Settings.canDrawOverlays(context),
            a11yStatus = QuickEntryState.checkStatus(context),
        )
    }
}
