// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.noteone.app.R
import com.noteone.app.core.design.AppColor
import com.noteone.app.core.design.AppTheme
import com.noteone.app.core.design.AppType
import com.noteone.app.core.design.Dimension
import com.noteone.app.core.design.Space
import com.noteone.app.core.design.icon.AppIcons

/**
 * 是否有底栏。
 *
 * 页面需要知道这件事才能算对底部留白：有底栏时底栏自己吃掉了导航栏 inset，
 * 页面只需再留 16dp；没有底栏（二级页）时页面要自己补上导航栏 inset。
 * 由 [AppRootScaffold] 提供。
 */
val LocalBottomBarVisible = staticCompositionLocalOf { true }

/**
 * 根脚手架：承接 4 个顶级 Tab（带底栏）与二级页（不带底栏）两种形态。
 *
 * 刻意**不用 Material 3 的 `Scaffold`**：它的 inset 推导规则会让页面留白出现
 * 「有时多一段、有时少一段」的不确定结果。这里用一个显式的 Column——
 * 内容占满底栏以上的全部空间，inset 由页面与底栏各自负责，行为完全可预测。
 *
 * @param bottomBar 底栏内容；二级页传 `null`
 */
@Composable
fun AppRootScaffold(
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalBottomBarVisible provides (bottomBar != null)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColor.Canvas),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                content()
            }
            bottomBar?.invoke()
        }
    }
}

/**
 * 页面容器（规范 §3.4）。
 *
 * - 状态栏下留白 24dp
 * - 页面左右边距 16dp
 * - 页面底部（底栏上）留白 16dp
 * - 无底栏时额外补上系统导航栏 inset
 */
@Composable
fun AppScaffold(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val hasBottomBar = LocalBottomBarVisible.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .then(if (hasBottomBar) Modifier else Modifier.navigationBarsPadding())
            .padding(
                start = Dimension.PageHPadding,
                end = Dimension.PageHPadding,
                top = Dimension.ContentTop,
                bottom = Dimension.ContentBottom,
            ),
    ) {
        if (title.isNotEmpty()) {
            Text(text = title, style = AppType.PageTitle, color = AppColor.Ink)
            Box(modifier = Modifier.height(Space.L))
        }
        content()
    }
}

/**
 * 二级页 / 弹层内的统一页头（高 44dp）：返回箭头 + 标题 17sp Medium + 可选右侧动作。
 *
 * 设置页的三个管理页要有「不带底栏 + 左上角返回」的页头，这里提供基础件，
 * 具体骨架由 E 在其 `ManageScaffold` 里组合。
 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimension.HeaderRow),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(id = AppIcons.ArrowLeft),
                        contentDescription = stringResource(R.string.common_back),
                        tint = AppColor.Ink,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Text(text = title, style = AppType.SectionTitle, color = AppColor.Ink)
        }
        if (trailing != null) trailing()
    }
}

/**
 * 区块标题。`actionLabel` 非空时在右侧渲染一个 13sp `Muted` 的动作文字
 * （记账页的「查看全部 →」就是这样）。
 */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = Space.M),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = text, style = AppType.SectionTitle, color = AppColor.Ink)
        if (actionLabel != null && onAction != null) {
            TextButtonSmall(text = actionLabel, onClick = onAction)
        }
    }
}

/** 全站唯一的分割线：1dp `#EAEAEA`。 */
@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimension.Divider)
            .background(AppColor.Line),
    )
}

@Preview(name = "AppScaffold / 带标题", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun AppScaffoldPreview() {
    AppTheme {
        AppRootScaffold {
            AppScaffold(title = "汇总") {
                SectionHeader(text = "最近账单", actionLabel = "查看全部 →", onAction = {})
                Divider()
            }
        }
    }
}

@Preview(name = "PageHeader / 二级页", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun PageHeaderPreview() {
    AppTheme {
        Box(modifier = Modifier.padding(Space.L)) {
            PageHeader(title = "账本管理", onBack = {})
        }
    }
}
