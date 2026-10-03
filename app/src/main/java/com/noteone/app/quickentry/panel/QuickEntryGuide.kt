// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.quickentry.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.noteone.app.R
import com.noteone.app.core.design.AppColor
import com.noteone.app.core.design.AppTheme
import com.noteone.app.core.design.AppType
import com.noteone.app.core.design.Dimension
import com.noteone.app.core.design.Radius
import com.noteone.app.core.design.Space
import com.noteone.app.core.design.component.PrimaryButton
import com.noteone.app.core.design.component.SecondaryButton
import com.noteone.app.core.design.component.TextButtonSmall

/**
 * 权限引导页（规范 §8.8）。
 *
 * **一次只讲一项权限**，讲清用途再给按钮——所以它是三个独立的调用，
 * 而不是一个把三项权限堆在一起的清单。
 *
 * 形态：整屏 `Scrim` 遮罩 + 居中卡片（卡片本身零阴影，遮罩已经建立了层级关系），
 * 点遮罩空白 = 取消。这是 D 模块唯一会盖住整个屏幕的界面。
 */
@Composable
fun QuickEntryGuide(
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val scrimInteraction = remember { MutableInteractionSource() }
    val cardShape = RoundedCornerShape(Radius.Card)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppColor.Scrim)
            .clickable(
                interactionSource = scrimInteraction,
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.XXL)
                // 阻止点击穿透到遮罩：卡片自己吃掉点击
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .background(AppColor.SurfaceCard, cardShape)
                .border(Dimension.Divider, AppColor.Line, cardShape)
                .padding(20.dp),
        ) {
            Text(text = title, style = AppType.SectionTitle, color = AppColor.Ink)
            Spacer(modifier = Modifier.height(Space.M))
            Text(text = body, style = AppType.Caption, color = AppColor.Muted)
            Spacer(modifier = Modifier.height(Space.XL))

            PrimaryButton(text = primaryLabel, onClick = onPrimary)

            if (secondaryLabel != null && onSecondary != null) {
                Spacer(modifier = Modifier.height(Space.M))
                SecondaryButton(text = secondaryLabel, onClick = onSecondary)
            }

            Spacer(modifier = Modifier.height(Space.M))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                TextButtonSmall(
                    text = stringResource(R.string.quick_guide_cancel),
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Preview(name = "权限引导 / 无障碍", showBackground = true, backgroundColor = 0xFF9E9E9E)
@Composable
private fun QuickEntryGuidePreview() {
    AppTheme {
        Box(modifier = Modifier.height(520.dp)) {
            QuickEntryGuide(
                title = "需要无障碍服务",
                body = "本应用需要无障碍服务来读取当前屏幕画面，用于识别金额。" +
                    "只读取画面，不读取窗口内容，不模拟任何点击。",
                primaryLabel = "去开启",
                secondaryLabel = "从图片识别",
                onPrimary = {},
                onSecondary = {},
                onDismiss = {},
            )
        }
    }
}

@Preview(name = "权限引导 / 悬浮窗", showBackground = true, backgroundColor = 0xFF9E9E9E)
@Composable
private fun QuickEntryGuideOverlayPreview() {
    AppTheme {
        Box(modifier = Modifier.height(420.dp)) {
            QuickEntryGuide(
                title = "需要悬浮窗权限",
                body = "框选金额的区域和记账面板都以悬浮窗显示，需要这一项权限。",
                primaryLabel = "去授权",
                onPrimary = {},
                onDismiss = {},
            )
        }
    }
}
