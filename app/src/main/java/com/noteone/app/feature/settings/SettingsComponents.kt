// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noteone.app.R
import com.noteone.app.core.design.AppColor
import com.noteone.app.core.design.AppType
import com.noteone.app.core.design.SemanticColor
import com.noteone.app.core.design.SemanticKeys
import com.noteone.app.core.design.Space
import com.noteone.app.core.design.component.TextButtonSmall
import com.noteone.app.core.design.icon.AppIcons
import com.noteone.app.feature.manage.AppSwitch
import com.noteone.app.quickentry.state.A11yStatus

/** 设置页条目行高（任务书 §2.2）。 */
private val SettingRowHeight = 52.dp

/** 状态圆点直径，与分类色点一致（规范 §3.5）。 */
private val StatusDotSize = 8.dp

/** 危险动作的文字色（规范 §3.2 的砖红深色）。 */
private val DangerColor = SemanticColor.of(SemanticKeys.Red).fg

/**
 * 分组标题（13sp `Muted`，上间距 24dp、下间距 8dp）。
 *
 * 设置页**不用卡片盒**，靠「分组标题 + 条目 + 1dp 分割线」组织，
 * 这是 SKILL.md 明确要求的 accordion 处理方式。第一组不需要上间距
 * （`AppScaffold` 已经留了 24dp）。
 */
@Composable
fun SettingsGroupLabel(
    text: String,
    modifier: Modifier = Modifier,
    first: Boolean = false,
) {
    Text(
        text = text,
        style = AppType.Caption,
        color = AppColor.Muted,
        modifier = modifier.padding(top = if (first) Space.S else Space.XL, bottom = Space.S),
    )
}

/**
 * 普通条目：左文字 15sp `Ink`，右侧可选值 13sp `Muted` + 可选箭头 16dp。
 *
 * `onClick` 为 null 时不可点（例如「版本」只读）。危险条目用 [danger] 传砖红文字。
 */
@Composable
fun SettingRow(
    text: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    showArrow: Boolean = true,
    danger: Boolean = false,
    onClick: (() -> Unit)? = null,
    valueContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SettingRowHeight)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            style = AppType.Body,
            color = if (danger) DangerColor else AppColor.Ink,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (valueContent != null) {
                valueContent()
            } else if (value != null) {
                Text(text = value, style = AppType.Caption, color = AppColor.Muted)
            }
            if (showArrow) {
                Box(modifier = Modifier.width(Space.S))
                Icon(
                    painter = painterResource(id = AppIcons.CaretRight),
                    contentDescription = null,
                    tint = AppColor.Muted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** 条目 = 一段说明 + 一个跳系统设置的文字按钮（「后台保活」「添加磁贴」用）。 */
@Composable
fun SettingActionRow(
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SettingRowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = text, style = AppType.Body, color = AppColor.Ink)
        TextButtonSmall(text = actionLabel, onClick = onAction, color = AppColor.Ink)
    }
}

/** 带开关的条目（开关样式已在 [com.noteone.app.feature.manage.AppSwitch] 里覆盖）。 */
@Composable
fun SettingSwitchRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SettingRowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = text, style = AppType.Body, color = AppColor.Ink)
        AppSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

/**
 * 带状态圆点的条目（无障碍服务）。
 *
 * 三态必须能区分「未开启」与「已被系统关闭」——后者直接命中国产 ROM 杀进程的场景，
 * 处理方式完全不同（规范 §8.8）。
 *
 * @param dotColor 绿点表示运行中，灰点表示其余两种状态
 * @param actionLabel 为 null 时不渲染右侧动作
 */
@Composable
fun SettingStatusRow(
    text: String,
    statusText: String,
    dotColor: Color,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SettingRowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = text, style = AppType.Body, color = AppColor.Ink)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(StatusDotSize)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Box(modifier = Modifier.width(Space.XS))
            Text(text = statusText, style = AppType.Caption, color = AppColor.Muted)
            if (actionLabel != null && onAction != null) {
                Box(modifier = Modifier.width(Space.S))
                TextButtonSmall(text = actionLabel, onClick = onAction, color = AppColor.Ink)
            }
        }
    }
}

/** 条目下方的说明文字：13sp `Muted`，可折行（「后台保活」「添加磁贴」「隐私说明」用）。 */
@Composable
fun SettingsNote(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = AppType.Caption,
        color = AppColor.Muted,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = Space.M),
    )
}

/**
 * 无障碍服务三态对应的短状态词。
 *
 * 刻意**没有**用 D 交付说明 §4.1 给的三条整句（`无障碍服务运行中` 等）：
 * 条目行左侧已经写了「无障碍服务」，整句会出现「无障碍服务　无障碍服务运行中」的重复，
 * 且一行的宽度也放不下。出入已记在交付说明里。
 *
 * 放在这里而不是页面私有：**首次启动的权限引导页要用同一套映射**，
 * 两处各写一份迟早会漂移成两套状态词。
 */
@Composable
fun a11yStatusLabel(status: A11yStatus): String = stringResource(
    when (status) {
        A11yStatus.RUNNING -> R.string.settings_a11y_state_running
        A11yStatus.DISABLED -> R.string.settings_a11y_state_disabled
        A11yStatus.KILLED_BY_SYSTEM -> R.string.settings_a11y_state_killed
    },
)

/** 运行中不需要动作；另外两种都要跳系统设置，但文案不同（「去开启」/「去重新开启」）。 */
@Composable
fun a11yActionLabel(status: A11yStatus): String? = when (status) {
    A11yStatus.RUNNING -> null
    A11yStatus.DISABLED -> stringResource(R.string.settings_a11y_action_enable)
    A11yStatus.KILLED_BY_SYSTEM -> stringResource(R.string.settings_a11y_action_reenable)
}
