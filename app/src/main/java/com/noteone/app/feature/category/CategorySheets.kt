// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

// `AppBottomSheet` 的签名里有 M3 的 `SheetState`（实验 API），所以调用方也要 opt-in。
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.noteone.app.feature.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noteone.app.R
import com.noteone.app.core.data.model.Category
import com.noteone.app.core.data.model.Direction
import com.noteone.app.core.design.AppColor
import com.noteone.app.core.design.AppType
import com.noteone.app.core.design.SemanticColor
import com.noteone.app.core.design.SemanticKeys
import com.noteone.app.core.design.Space
import com.noteone.app.core.design.component.AppBottomSheet
import com.noteone.app.core.design.component.CategoryChip
import com.noteone.app.core.design.component.PrimaryButton
import com.noteone.app.core.design.component.SecondaryButton
import com.noteone.app.core.design.component.TextToggleRow
import com.noteone.app.feature.manage.ColorDotPicker
import com.noteone.app.feature.manage.NameField

/** 分类网格的最大高度，超出可滚。 */
private val CategoryGridMaxHeight = 240.dp

/** 分类网格每行几个。 */
private const val CATEGORY_COLUMNS = 3

/**
 * 新建 / 编辑分类弹层（任务书 §4.2）。
 *
 * 新建：名称 + 方向（支出 / 收入）+ 色点。
 * 编辑：名称 + 色点 + 「归档」入口。**方向不可改**——仓库没有改方向的接口，
 * 而且改方向会让已有的历史记录落到不该出现的方向里。
 *
 * 删除 = 归档，且历史记录仍显示原分类名与色点，所以按钮文案是「归档」而不是「删除」。
 */
@Composable
fun CategoryEditorSheet(
    isNew: Boolean,
    initialName: String,
    initialColorKey: String,
    initialDirection: Int,
    onSave: (name: String, direction: Int, colorKey: String) -> Unit,
    onArchive: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var colorKey by remember { mutableStateOf(initialColorKey) }
    var direction by remember { mutableIntStateOf(initialDirection) }

    AppBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(
                if (isNew) R.string.settings_category_new_title else R.string.settings_category_edit_title,
            ),
            style = AppType.SectionTitle,
            color = AppColor.Ink,
        )
        Box(modifier = Modifier.height(Space.S))
        NameField(
            value = name,
            onValueChange = { name = it },
            hint = stringResource(R.string.settings_category_name_hint),
        )

        if (isNew) {
            Box(modifier = Modifier.height(Space.L))
            Text(
                text = stringResource(R.string.settings_direction_label),
                style = AppType.Caption,
                color = AppColor.Muted,
            )
            Box(modifier = Modifier.height(Space.S))
            TextToggleRow(
                labels = listOf(
                    stringResource(R.string.common_expense),
                    stringResource(R.string.common_income),
                ),
                selectedIndex = if (direction == Direction.Income) 1 else 0,
                onSelect = { direction = if (it == 1) Direction.Income else Direction.Expense },
            )
        }

        Box(modifier = Modifier.height(Space.L))
        Text(
            text = stringResource(R.string.settings_color_label),
            style = AppType.Caption,
            color = AppColor.Muted,
        )
        Box(modifier = Modifier.height(Space.S))
        ColorDotPicker(selectedKey = colorKey, onSelect = { colorKey = it })

        Box(modifier = Modifier.height(Space.XL))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.M)) {
            SecondaryButton(
                text = stringResource(R.string.settings_cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = stringResource(R.string.settings_save),
                onClick = { onSave(name.trim(), direction, colorKey) },
                enabled = name.isNotBlank(),
                modifier = Modifier.weight(1f),
            )
        }

        if (!isNew) {
            Box(modifier = Modifier.height(Space.M))
            SecondaryButton(
                text = stringResource(R.string.settings_category_archive_action),
                onClick = onArchive,
                contentColor = SemanticColor.of(SemanticKeys.Red).fg,
            )
        }
    }
}

/** 归档确认弹层。文案必须写清「已有记录仍会保留」（任务书 §4.3）。 */
@Composable
fun CategoryArchiveSheet(
    categoryName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.settings_category_archive_title, categoryName),
            style = AppType.SectionTitle,
            color = AppColor.Ink,
        )
        Box(modifier = Modifier.height(Space.S))
        Text(
            text = stringResource(R.string.settings_category_archive_body),
            style = AppType.Body,
            color = AppColor.Muted,
        )
        Box(modifier = Modifier.height(Space.XL))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.M)) {
            SecondaryButton(
                text = stringResource(R.string.settings_cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = stringResource(R.string.settings_category_archive_action),
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 分类选择网格：每行 3 个 `CategoryChip`，超出可滚。
 *
 * 快捷按钮的编辑弹层与「更多分类」这类场景共用；分类永远用「色点 + 文字」表达，
 * **不支持自定义图标**（用户已明确否定）。
 */
@Composable
fun CategoryChipGrid(
    categories: List<Category>,
    selectedId: Long,
    onSelect: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = CategoryGridMaxHeight)
            .verticalScroll(rememberScrollState()),
    ) {
        categories.chunked(CATEGORY_COLUMNS).forEach { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Space.S),
                horizontalArrangement = Arrangement.spacedBy(Space.S),
            ) {
                rowItems.forEach { category ->
                    CategoryChip(
                        name = category.name,
                        colorKey = category.colorKey,
                        selected = category.id == selectedId,
                        onClick = { onSelect(category) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(CATEGORY_COLUMNS - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * 按方向筛出可用的分类。
 *
 * 与记账页的分类 chip 行同一口径（`Both` 两个方向都能用），判定逻辑直接复用
 * ViewModel 里那份 [matchesDirection]，不另写一遍。
 */
fun availableCategoriesFor(categories: List<Category>, direction: Int): List<Category> =
    categories.filter { matchesDirection(it, direction) }
