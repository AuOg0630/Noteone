// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.design.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.noteone.app.R
import com.noteone.app.core.design.AppColor
import com.noteone.app.core.design.AppTheme
import com.noteone.app.core.design.AppType
import com.noteone.app.core.design.Dimension
import com.noteone.app.core.design.Radius
import com.noteone.app.core.design.Space
import com.noteone.app.core.design.icon.AppIcons
import com.noteone.app.navigation.Route

/**
 * 底部导航栏（规范 §4.2）。
 *
 * 4 个等宽带、高 56dp + 导航栏 inset、背景 `#FFFFFF`、顶部 1dp `#EAEAEA`。
 *
 * 选中态是**三重信号**（形状 / 颜色 / 指示线）但只用一种颜色、零阴影、零胶囊底色：
 * 实心图标 + `Ink` 文字 500 字重 + 图标下方 4dp 处一条 `2dp × 16dp`、圆角 1dp 的 `#111111` 短横线。
 *
 * 除按压涟漪外无任何动效，选中项不做缩放。
 */
@Composable
fun AppBottomBar(
    current: Route,
    onSelect: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().background(AppColor.Canvas)) {
        Divider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 顺序不能反：navigationBarsPadding 必须在外层。
                // 写成 `.height(56dp).navigationBarsPadding()` 的话，inset 会被这 56dp 吃掉，
                // 四个 Tab 只剩 `56 - inset` 的高度（三分键导航 inset 48dp 时只剩 8dp）。
                .navigationBarsPadding()
                .height(Dimension.BottomBar),
        ) {
            BottomBarItems.forEach { item ->
                BottomBarTab(
                    item = item,
                    selected = item.route == current,
                    onClick = { onSelect(item.route) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private data class BottomBarItem(
    val route: Route,
    @StringRes val labelRes: Int,
    @DrawableRes val iconLine: Int,
    @DrawableRes val iconFill: Int,
)

private val BottomBarItems = listOf(
    BottomBarItem(Route.Record, R.string.nav_record, AppIcons.RecordLine, AppIcons.RecordFill),
    BottomBarItem(Route.Ledger, R.string.nav_ledger, AppIcons.LedgerLine, AppIcons.LedgerFill),
    BottomBarItem(Route.Report, R.string.nav_report, AppIcons.ReportLine, AppIcons.ReportFill),
    BottomBarItem(Route.Settings, R.string.nav_settings, AppIcons.SettingsLine, AppIcons.SettingsFill),
)

@Composable
private fun BottomBarTab(
    item: BottomBarItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (selected) AppColor.Ink else AppColor.Muted

    Box(
        modifier = modifier
            .height(Dimension.BottomBar)
            // 用 selectable 而不是 clickable：TalkBack 才会播报「已选中」，
            // 与 DirectionToggle 里的 TextToggleRow 保持一致
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(id = if (selected) item.iconFill else item.iconLine),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp),
            )
            Box(modifier = Modifier.height(Space.XS))
            Box(
                modifier = Modifier
                    .width(Dimension.IndicatorWidth)
                    .height(Dimension.IndicatorHeight)
                    .clip(RoundedCornerShape(Radius.Input))
                    .background(if (selected) AppColor.Ink else Color.Transparent),
            )
            Box(modifier = Modifier.height(Space.XS))
            Text(
                text = stringResource(item.labelRes),
                style = AppType.Label.copy(
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                ),
                color = contentColor,
            )
        }
    }
}

@Preview(name = "AppBottomBar / 记账选中", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun AppBottomBarPreview() {
    AppTheme {
        AppBottomBar(current = Route.Record, onSelect = {})
    }
}
