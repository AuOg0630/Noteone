// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.feature.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noteone.app.R
import com.noteone.app.core.design.AppColor
import com.noteone.app.core.design.AppType
import com.noteone.app.core.design.SemanticColor
import com.noteone.app.core.design.SemanticKeys
import com.noteone.app.core.design.Space
import com.noteone.app.core.design.component.AppScaffold
import com.noteone.app.core.design.component.Divider
import com.noteone.app.core.design.component.PrimaryButton
import com.noteone.app.feature.settings.SettingActionRow
import com.noteone.app.feature.settings.SettingStatusRow
import com.noteone.app.feature.settings.SettingsGroupLabel
import com.noteone.app.feature.settings.SettingsIntents
import com.noteone.app.feature.settings.SettingsNote
import com.noteone.app.feature.settings.a11yActionLabel
import com.noteone.app.feature.settings.a11yStatusLabel
import com.noteone.app.quickentry.state.A11yStatus

/**
 * 首次启动的权限引导页（v1.6 新增）。
 *
 * 只在用户第一次打开 App 时整屏出现一次，点「开始记账」后写标记、不再出现
 * （标记在 [OnboardingState]）。
 *
 * ## 为什么不再是「一次只讲一项」
 *
 * 规范 §8.8 原文要求「三项权限都只在用户首次点磁贴时才引导，不在 App 启动时弹」，
 * 且「引导页一次只讲一项权限」。这条已被用户改掉：**首次打开就要把需要什么讲清楚**。
 * 理由也站得住——磁贴那个入口是用户自己添加快捷设置之后才点的，等他走到那一步
 * 才发现要授权，等于先给了个坏体验。
 *
 * 采用 D 的 [com.noteone.app.quickentry.panel.QuickEntryGuide] 那种「一屏一项、
 * 弹三次」的形态在这里并不合适：首次启动连弹三次是最劝退的形态。这里改成
 * **一屏列全 + 每项各自带状态与「去授权」**，沿用设置页「分组标题 + 条目 + 1dp 分割线」
 * 的 accordion 规则（SKILL.md 第 5 章），视觉上与设置页完全一致。
 *
 * ## 页面标题为什么不是 24sp
 *
 * `AppType.PageTitle`（24sp）在 `ReportScreen` 的注释里被声明为「全站唯一」。这里
 * 用 [AppType.SectionTitle]（17sp Medium），首屏靠大段留白与主按钮建立层级，
 * 不为了这一页去推翻那条声明。
 *
 * @param onFinish 点「开始记账」；由 [AppRoot] 负责落标记并放行主界面
 */
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val openOverlay = { context.startActivity(SettingsIntents.overlay(context)) }
    val openAccessibility = { context.startActivity(SettingsIntents.accessibility(context)) }
    val openAppDetails = { context.startActivity(SettingsIntents.appDetails(context)) }

    // 悬浮窗与无障碍服务只能在系统设置里改，系统不会通知本进程；
    // 用户授完权返回时页面走 ON_RESUME，这里重新读一次状态。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AppScaffold(title = "", modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.onboarding_title),
                style = AppType.SectionTitle,
                color = AppColor.Ink,
            )
            Box(modifier = Modifier.height(Space.M))
            Text(
                text = stringResource(R.string.onboarding_intro),
                style = AppType.Body,
                color = AppColor.InkSecondary,
            )

            // ---------------------------------------------------------- 权限
            SettingsGroupLabel(text = stringResource(R.string.onboarding_group_permission))
            SettingStatusRow(
                text = stringResource(R.string.settings_overlay_title),
                statusText = stringResource(
                    if (state.overlayGranted) {
                        R.string.settings_overlay_on
                    } else {
                        R.string.settings_overlay_off
                    },
                ),
                dotColor = grantedDot(state.overlayGranted),
                actionLabel = if (state.overlayGranted) {
                    null
                } else {
                    stringResource(R.string.onboarding_action_grant)
                },
                onAction = if (state.overlayGranted) null else openOverlay,
            )
            SettingsNote(text = stringResource(R.string.onboarding_overlay_note))
            Divider()
            SettingStatusRow(
                text = stringResource(R.string.settings_a11y_title),
                statusText = a11yStatusLabel(state.a11yStatus),
                dotColor = grantedDot(state.a11yStatus == A11yStatus.RUNNING),
                actionLabel = a11yActionLabel(state.a11yStatus),
                onAction = if (state.a11yStatus == A11yStatus.RUNNING) null else openAccessibility,
            )
            SettingsNote(text = stringResource(R.string.settings_a11y_note))

            // ---------------------------------------------------------- 建议
            SettingsGroupLabel(text = stringResource(R.string.onboarding_group_keepalive))
            SettingActionRow(
                text = stringResource(R.string.settings_keepalive_title),
                actionLabel = stringResource(R.string.settings_keepalive_action),
                onAction = openAppDetails,
            )
            SettingsNote(text = stringResource(R.string.onboarding_keepalive_note))

            Box(modifier = Modifier.height(Space.L))
            Text(
                text = stringResource(R.string.onboarding_later_note),
                style = AppType.Caption,
                color = AppColor.Muted,
            )
            Box(modifier = Modifier.height(Space.L))
        }

        PrimaryButton(text = stringResource(R.string.onboarding_start), onClick = onFinish)
    }
}

/** 授权到位用绿点，其余一律灰点 —— 与设置页的状态圆点口径一致。 */
@Composable
private fun grantedDot(granted: Boolean) = if (granted) {
    SemanticColor.of(SemanticKeys.Green).fg
} else {
    AppColor.Faint
}
