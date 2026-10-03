// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.design.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.noteone.app.core.design.AppColor
import com.noteone.app.core.design.AppTheme
import com.noteone.app.core.design.AppType
import com.noteone.app.core.design.Dimension
import com.noteone.app.core.design.Radius
import com.noteone.app.core.design.SemanticColor
import com.noteone.app.core.design.SemanticKeys
import com.noteone.app.core.design.Space
import com.noteone.app.core.design.icon.AppIcons

/** chip 统一圆角与外边距（规范 §6.3）。 */
private val ChipShape = RoundedCornerShape(Radius.Input)

/**
 * 分类 chip（规范 §6.3）。
 *
 * 未选中：`#FFFFFF` 底 + 1dp `#EAEAEA` 边框 + `Muted` 文字；
 * 选中：**该分类的语义色底 + 深色字 + 无边框**。
 *
 * @param colorKey 该分类的语义色 key，取值见 [SemanticKeys]
 */
@Composable
fun CategoryChip(
    name: String,
    colorKey: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pair = SemanticColor.of(colorKey)
    AppChipShell(
        onClick = onClick,
        modifier = modifier,
        background = if (selected) pair.bg else AppColor.Canvas,
        borderColor = if (selected) null else AppColor.Line,
        contentColor = if (selected) pair.fg else AppColor.Muted,
    ) {
        Text(text = name, style = AppType.Label, color = if (selected) pair.fg else AppColor.Muted)
    }
}

/**
 * 快捷用途 chip（规范 §5.1）。
 *
 * 视觉与 [CategoryChip] 完全一致——「点击 = 选中该按钮对应的分类」，
 * 所以它必须看起来就是分类 chip，不能让用户以为点一下就入账。
 */
@Composable
fun QuickActionChip(
    label: String,
    colorKey: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CategoryChip(
        name = label,
        colorKey = colorKey,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * 非分类的选择 chip：时间范围、金额候选、分类筛选（规范 §5.3 / §8.6）。
 *
 * 选中态用 `#111111` 底 + `#FFFFFF` 字——因为这里没有语义色可依附，
 * 用中性墨色才不会和分类的语义色混淆。**不要给时间范围 chip 套语义色。**
 */
@Composable
fun SelectionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppChipShell(
        onClick = onClick,
        modifier = modifier,
        background = if (selected) AppColor.Ink else AppColor.Canvas,
        borderColor = if (selected) null else AppColor.Line,
        contentColor = if (selected) AppColor.Canvas else AppColor.Muted,
    ) {
        Text(
            text = label,
            style = AppType.Label,
            color = if (selected) AppColor.Canvas else AppColor.Muted,
        )
    }
}

/**
 * 行尾动作 chip：带一个尾部图标的文字 chip，用于
 * 「分类 ▾」（打开全量分类弹层）、「编辑」（跳快捷按钮管理）等（规范 §5.1）。
 */
@Composable
fun ActionChip(
    label: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppChipShell(
        onClick = onClick,
        modifier = modifier,
        background = AppColor.Canvas,
        borderColor = AppColor.Line,
        contentColor = AppColor.Muted,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.XS),
        ) {
            Text(text = label, style = AppType.Label, color = AppColor.Muted)
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = AppColor.Muted,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/**
 * chip 外壳。统一处理：高 32dp、4dp 圆角、左右 12dp 内边距、
 * 视觉不放大但热区扩到 48dp、按下态底色 `#EAEAEA`。
 */
@Composable
private fun AppChipShell(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color,
    borderColor: Color?,
    contentColor: Color,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = ChipShape

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(Dimension.Chip)
            .background(color = if (pressed) AppColor.Line else background, shape = shape)
            .then(
                if (borderColor != null) {
                    Modifier.border(BorderStroke(Dimension.Divider, borderColor), shape)
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = Dimension.ChipHPadding),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides contentColor,
            content = content,
        )
    }
}

@Preview(name = "Chip / 分类与筛选", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun ChipsPreview() {
    AppTheme {
        Column(
            modifier = Modifier.padding(Space.L),
            verticalArrangement = Arrangement.spacedBy(Space.M),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                CategoryChip("餐饮", SemanticKeys.Red, true, {})
                CategoryChip("交通", SemanticKeys.Blue, false, {})
                CategoryChip("购物", SemanticKeys.Yellow, false, {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                QuickActionChip("午餐", SemanticKeys.Red, true, {})
                QuickActionChip("地铁公交", SemanticKeys.Blue, false, {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                SelectionChip("本周", false, {})
                SelectionChip("本月", true, {})
                SelectionChip("近 3 月", false, {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                ActionChip("分类", AppIcons.CaretDown, {})
                ActionChip("编辑", AppIcons.Sliders, {})
            }
        }
    }
}
