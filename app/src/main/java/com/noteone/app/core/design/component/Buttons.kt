// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.design.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.noteone.app.core.design.AppColor
import com.noteone.app.core.design.AppTheme
import com.noteone.app.core.design.AppType
import com.noteone.app.core.design.Dimension
import com.noteone.app.core.design.Radius
import com.noteone.app.core.design.SemanticColor
import com.noteone.app.core.design.SemanticKeys
import com.noteone.app.core.design.Space

/**
 * 主按钮（规范 §6.4）。
 *
 * `#111111` 底 + `#FFFFFF` 文字 15sp Medium + 6dp 圆角 + 高 48dp + **零阴影**。
 * 按下态用底色变 `#333333`（全项目统一用底色变化，不用 scale）。
 * 禁用态 `#EAEAEA` 底 + `#A8A6A1` 字。
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val container = when {
        !enabled -> AppColor.Line
        pressed -> AppColor.Pressed
        else -> AppColor.Ink
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimension.TouchMin)
            .background(color = container, shape = RoundedCornerShape(Radius.Button))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = AppType.Button,
            color = if (enabled) AppColor.Canvas else AppColor.Faint,
        )
    }
}

/**
 * 次按钮（规范 §6.5）。
 *
 * `#FFFFFF` 底 + 1dp `#EAEAEA` 边框 + 6dp 圆角 + `#111111` 15sp Medium。
 * 「删除」这类危险语义由调用方通过 [contentColor] 传
 * `SemanticColor.of(SemanticKeys.Red).fg`（`#9F2F2D`）。
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    contentColor: Color = AppColor.Ink,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimension.TouchMin)
            .background(color = AppColor.Canvas, shape = RoundedCornerShape(Radius.Button))
            .border(
                BorderStroke(Dimension.Divider, AppColor.Line),
                shape = RoundedCornerShape(Radius.Button),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = AppType.Button,
            color = if (enabled) contentColor else AppColor.Faint,
        )
    }
}

/**
 * 纯文字小按钮。用于页面右上角「导出」、行尾「查看全部 →」等 13sp `Muted` 场景
 * （规范 §5.3 / §5.4）。视觉不加边框、不加底色，热区自动扩到 48dp。
 */
@Composable
fun TextButtonSmall(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = AppColor.Muted,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = AppType.Caption, color = if (enabled) color else AppColor.Faint)
    }
}

@Preview(name = "按钮 / 主次与文字", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun ButtonsPreview() {
    AppTheme {
        Column(
            modifier = Modifier.padding(Space.L),
            verticalArrangement = Arrangement.spacedBy(Space.M),
        ) {
            PrimaryButton(text = "完成", onClick = {})
            PrimaryButton(text = "完成（禁用）", onClick = {}, enabled = false)
            SecondaryButton(
                text = "删除",
                onClick = {},
                contentColor = SemanticColor.of(SemanticKeys.Red).fg,
            )
            TextButtonSmall(text = "查看全部 →", onClick = {})
        }
    }
}
