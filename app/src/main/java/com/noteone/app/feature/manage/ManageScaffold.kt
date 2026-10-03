// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.feature.manage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.noteone.app.core.design.AppColor
import com.noteone.app.core.design.AppType
import com.noteone.app.core.design.Space
import com.noteone.app.core.design.component.AppScaffold
import com.noteone.app.core.design.component.PageHeader
import com.noteone.app.core.design.component.PrimaryButton

/**
 * 三个管理页（账本 / 分类 / 快捷按钮）共用的骨架（任务书 §7）。
 *
 * 自上而下：页头（`‹` + 标题 17sp Medium）→ 可选子头部（分类页的支出 / 收入分区切换）
 * → 占满剩余高度的内容区（通常是 [ReorderableList]）→ 底部固定主按钮。
 *
 * 几条固定规则：
 * - **不带底栏**：这三个页面都是二级页，`AppScaffold` 会在无底栏时自动补导航栏 inset
 * - 内容区左右边距 16dp 由 `AppScaffold` 统一给，页面自己不要再加
 * - 主按钮固定不滚动；`primaryEnabled = false` 时用 [primaryDisabledHint] 说明原因
 *   （规范要求「达到上限时置灰并提示」）
 * - 列表项高 56dp（`Dimension.ListRow`），分割线只在条目之间
 */
@Composable
fun ManageScaffold(
    title: String,
    onBack: () -> Unit,
    primaryLabel: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    primaryDisabledHint: String? = null,
    subHeader: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // AppScaffold 的标题走 PageTitle(24sp)，二级页要的是 44dp 页头 + 17sp 标题，
    // 所以这里传空标题，页头由 PageHeader 画。
    AppScaffold(title = "", modifier = modifier) {
        PageHeader(title = title, onBack = onBack)

        if (subHeader != null) {
            Box(modifier = Modifier.height(Space.M))
            subHeader()
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            content()
        }

        if (!primaryEnabled && primaryDisabledHint != null) {
            Text(
                text = primaryDisabledHint,
                style = AppType.Caption,
                color = AppColor.Faint,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Space.S),
            )
        }
        PrimaryButton(text = primaryLabel, onClick = onPrimary, enabled = primaryEnabled)
    }
}
