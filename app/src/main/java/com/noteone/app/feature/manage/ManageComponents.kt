// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.feature.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.noteone.app.R
import com.noteone.app.core.data.seed.BuiltInData
import com.noteone.app.core.design.AppColor
import com.noteone.app.core.design.AppType
import com.noteone.app.core.design.Dimension
import com.noteone.app.core.design.Radius
import com.noteone.app.core.design.SemanticColor
import com.noteone.app.core.design.Space
import com.noteone.app.core.design.component.Divider

/** 色板选择器里色点的视觉直径（热区仍是 48dp）。 */
private val PickerDotSize = 24.dp

/** 色板选择器里选中态的描边圈直径。 */
private val PickerRingSize = 28.dp

/** 一行放 4 个色点：4 × 48dp 热区 = 192dp，远小于页面可用宽度。 */
private const val PICKER_COLUMNS = 4

/**
 * 分类色点（规范 §3.5：圆形、直径 8dp）。
 *
 * 取语义色的**深色字色**作为色点颜色——浅底色点在白底上几乎看不见。
 */
@Composable
fun ColorDot(
    colorKey: String,
    modifier: Modifier = Modifier,
    size: Dp = Dimension.ColorDot,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(SemanticColor.of(colorKey).fg),
    )
}

/**
 * 8 色色板选择器（任务书 §3.2 / §4.2）。
 *
 * 选中项加 1dp `Ink` 描边；**不支持自定义图标**（用户已明确否定），只有「色点 + 文字」。
 */
@Composable
fun ColorDotPicker(
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Space.S)) {
        SemanticColor.keys.chunked(PICKER_COLUMNS).forEach { rowKeys ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                rowKeys.forEach { key ->
                    val selected = key == selectedKey
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .clickable { onSelect(key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(PickerRingSize)
                                .clip(CircleShape)
                                .then(
                                    if (selected) {
                                        Modifier.border(Dimension.Divider, AppColor.Ink, CircleShape)
                                    } else {
                                        Modifier
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(PickerDotSize)
                                    .clip(CircleShape)
                                    .background(SemanticColor.of(key).fg),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 单行文本输入（账本名 / 分类名 / 按钮名）。
 *
 * 无边框输入框，仅下方 1dp `#EAEAEA`；占位符 `Faint`；超长直接不响应（规范 §11 的同款处理）。
 */
@Composable
fun NameField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    maxLength: Int = BuiltInData.NAME_MAX_LENGTH,
    textStyle: TextStyle = AppType.Body,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = { if (it.length <= maxLength) onValueChange(it) },
            singleLine = true,
            textStyle = textStyle.copy(color = AppColor.Ink),
            cursorBrush = SolidColor(AppColor.Ink),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Space.M),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(text = hint, style = textStyle, color = AppColor.Faint)
                    }
                    innerTextField()
                }
            },
        )
        Divider()
    }
}

/**
 * 覆盖样式的开关（规范 §2.2 / M0 交付说明 §5）。
 *
 * **必须覆盖颜色**：Material 3 的默认主色是紫色，不覆盖就直接违反视觉规范。
 * 选中轨道 `Ink`、未选中轨道 `Line`、滑块 `Canvas`（白）。
 * 边框色不显式传，交给 `AppTheme` 覆盖后的 `outline = Line`。
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = AppColor.Canvas,
            checkedTrackColor = AppColor.Ink,
            uncheckedThumbColor = AppColor.Canvas,
            uncheckedTrackColor = AppColor.Line,
        ),
    )
}

/** 列表项右侧的拖拽手柄。`handle` 由 [ReorderableList] 提供，长按它才会进入拖拽。 */
@Composable
fun DragHandle(
    handle: Modifier,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .then(handle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_grip),
            contentDescription = stringResource(R.string.settings_drag_handle),
            tint = AppColor.Faint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * 管理页的统一行：色点 + 主文字 +（可选）次要文字 +（可选）尾部内容 + 拖拽手柄。
 *
 * 高 56dp，分割线画在行**内部底边**，这样 [ReorderableList] 的行高数学不会被撑高。
 */
@Composable
fun ManageRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    divider: Boolean = true,
    showHandle: Boolean = true,
    handle: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimension.ListRow)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Box(modifier = Modifier.width(Space.M))
            }
            Text(
                text = title,
                style = AppType.Body,
                color = AppColor.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Box(modifier = Modifier.width(Space.M))
                Text(
                    text = subtitle,
                    style = AppType.Caption,
                    color = AppColor.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (trailing != null) trailing()
            if (showHandle) DragHandle(handle = handle)
        }
        if (divider) {
            Divider(modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** 「默认」这类状态徽标（规范 §3.5 允许标签胶囊/状态徽标用 9999，这里用 4dp 更克制）。 */
@Composable
fun StatusBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .border(Dimension.Divider, AppColor.Line, RoundedCornerShape(Radius.Input))
            .padding(horizontal = Space.S, vertical = Space.XS),
    ) {
        Text(text = text, style = AppType.Label, color = AppColor.Muted)
    }
}

/** 管理页内的分组小标题（13sp `Muted`）。 */
@Composable
fun ManageGroupLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = AppType.Caption,
        color = AppColor.Muted,
        modifier = modifier.padding(top = Space.XL, bottom = Space.S),
    )
}
