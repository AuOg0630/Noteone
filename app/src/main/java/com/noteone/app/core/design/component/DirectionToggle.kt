// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.noteone.app.R
import com.noteone.app.core.design.AppColor
import com.noteone.app.core.design.AppTheme
import com.noteone.app.core.design.AppType
import com.noteone.app.core.design.Dimension
import com.noteone.app.core.design.Radius
import com.noteone.app.core.design.Space

/**
 * 「− 支出 / + 收入」方向切换（规范 §5.1 / §6.3）。
 *
 * 形态要点：**不用胶囊底色**。未选中 `Muted` 400 字重，选中 `Ink` 500 字重
 * 且下方一条 `2dp × 16dp` 的 `#111111` 短横线。
 *
 * @param isIncome true = 收入，false = 支出
 */
@Composable
fun DirectionToggle(
    isIncome: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val expenseLabel = stringResource(R.string.common_expense)
    val incomeLabel = stringResource(R.string.common_income)
    TextToggleRow(
        labels = listOf("− $expenseLabel", "+ $incomeLabel"),
        selectedIndex = if (isIncome) 1 else 0,
        onSelect = { onChange(it == 1) },
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Space.XL),
    )
}

/**
 * 与 [DirectionToggle] 同一视觉的语言切换组，供账单页「全部 / 支出 / 收入」三态筛选，
 * 以及管理页的「支出分类 / 收入分类」分区切换复用（B / E 用）。
 *
 * **不要为这两个场景另写一套视觉**，规范要求它们看起来完全一致。
 */
@Composable
fun TextToggleRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(Space.XL),
) {
    Row(modifier = modifier, horizontalArrangement = horizontalArrangement) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Column(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = { onSelect(index) },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = label,
                    style = AppType.Body.copy(
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    ),
                    color = if (selected) AppColor.Ink else AppColor.Muted,
                )
                Box(
                    modifier = Modifier
                        .padding(top = Space.XS)
                        .width(Dimension.IndicatorWidth)
                        .height(Dimension.IndicatorHeight)
                        .clip(RoundedCornerShape(Radius.Input))
                        .background(if (selected) AppColor.Ink else androidx.compose.ui.graphics.Color.Transparent),
                )
            }
        }
    }
}

@Preview(name = "DirectionToggle / 支出", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun DirectionToggleExpensePreview() {
    AppTheme {
        Box(modifier = Modifier.padding(Space.L)) {
            DirectionToggle(isIncome = false, onChange = {})
        }
    }
}

@Preview(name = "DirectionToggle / 收入", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun DirectionToggleIncomePreview() {
    AppTheme {
        Box(modifier = Modifier.padding(Space.L)) {
            DirectionToggle(isIncome = true, onChange = {})
        }
    }
}

@Preview(name = "TextToggleRow / 账单筛选", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun TextToggleRowPreview() {
    AppTheme {
        Box(modifier = Modifier.padding(Space.L).fillMaxHeight()) {
            TextToggleRow(
                labels = listOf("全部", "支出", "收入"),
                selectedIndex = 0,
                onSelect = {},
            )
        }
    }
}
