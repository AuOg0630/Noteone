// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.noteone.app.core.design.AppColor
import com.noteone.app.core.design.AppTheme
import com.noteone.app.core.design.AppType
import com.noteone.app.core.design.Dimension
import com.noteone.app.core.design.Radius
import com.noteone.app.core.design.Space

/**
 * 通用底部弹层（规范 §6.2）。
 *
 * 顶部圆角 16dp、背景 `#FFFFFF`、顶部 1dp `#EAEAEA`、遮罩 `#000000` 25%、
 * 顶部居中 32dp × 4dp、圆角 2dp 的 `#EAEAEA` 拖拽指示条。
 * Material 3 的默认主色 / 圆角 / 高程全部被 token 覆盖。
 *
 * 用于：账本切换、账单编辑、日期范围选择、全量分类选择、各类二次确认。
 * **自绘数字键盘不是弹层**，它常驻在记账卡片里，不要用这个包起来。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = Radius.Sheet, topEnd = Radius.Sheet),
        containerColor = AppColor.SurfaceCard,
        contentColor = AppColor.Ink,
        tonalElevation = 0.dp,
        scrimColor = AppColor.Scrim,
        dragHandle = { SheetDragHandle() },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    start = Dimension.PageHPadding,
                    end = Dimension.PageHPadding,
                    bottom = Space.L,
                ),
            content = content,
        )
    }
}

/** 顶部 1dp 分隔线 + 居中拖拽指示条。 */
@Composable
private fun SheetDragHandle() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimension.Divider)
                .background(AppColor.Line),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Space.M),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(Dimension.SheetHandleWidth)
                    .height(Dimension.SheetHandleHeight)
                    .clip(RoundedCornerShape(Radius.Bar))
                    .background(AppColor.Line),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "AppBottomSheet / 结构", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun AppBottomSheetPreview() {
    AppTheme {
        ModalBottomSheet(
            onDismissRequest = {},
            shape = RoundedCornerShape(topStart = Radius.Sheet, topEnd = Radius.Sheet),
            containerColor = AppColor.SurfaceCard,
            tonalElevation = 0.dp,
            scrimColor = AppColor.Scrim,
            dragHandle = { SheetDragHandle() },
        ) {
            Text(
                text = "账本切换",
                style = AppType.SectionTitle,
                color = AppColor.Ink,
                modifier = Modifier.padding(horizontal = Dimension.PageHPadding, vertical = Space.L),
            )
        }
    }
}
