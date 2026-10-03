// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.design.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.noteone.app.core.design.AppColor
import com.noteone.app.core.design.AppTheme
import com.noteone.app.core.design.AppType
import com.noteone.app.core.design.Space
import com.noteone.app.core.design.icon.AppIcons

/**
 * 空状态（规范 §5.2 / §5.3）。
 *
 * 墨线插画（单色连续线稿 + 一个 offset 的语义 pastel 几何形）+ 13sp `Muted` 文案。
 * **不带按钮**——空状态不做「去记一笔」这类引导，用户已经在记账页了。
 *
 * @param illustration 插画，项目内置两张：账单页用 `AppIcons.IllEmptyLedger`，
 *                     汇总页用 `AppIcons.IllEmptyReport`
 * @param text 13sp `Muted` 文案，例如「本月还没有记录」
 */
@Composable
fun EmptyState(
    illustration: Painter,
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = Space.XL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.L),
    ) {
        Image(
            painter = illustration,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
        )
        Text(text = text, style = AppType.Caption, color = AppColor.Muted)
    }
}

/** [EmptyState] 的 drawable 便捷重载。 */
@Composable
fun EmptyState(
    @DrawableRes illustrationRes: Int,
    text: String,
    modifier: Modifier = Modifier,
) {
    EmptyState(illustration = painterResource(id = illustrationRes), text = text, modifier = modifier)
}

@Preview(name = "EmptyState / 账单页", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun EmptyStateLedgerPreview() {
    AppTheme {
        Box(modifier = Modifier.padding(Space.XXL)) {
            EmptyState(illustrationRes = AppIcons.IllEmptyLedger, text = "本月还没有记录")
        }
    }
}

@Preview(name = "EmptyState / 汇总页", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun EmptyStateReportPreview() {
    AppTheme {
        Box(modifier = Modifier.padding(Space.XXL)) {
            EmptyState(illustrationRes = AppIcons.IllEmptyReport, text = "该范围内没有记录")
        }
    }
}
