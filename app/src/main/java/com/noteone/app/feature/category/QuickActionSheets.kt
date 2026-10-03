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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.noteone.app.R
import com.noteone.app.core.data.model.Category
import com.noteone.app.core.data.model.Direction
import com.noteone.app.core.data.seed.BuiltInData
import com.noteone.app.core.design.AppColor
import com.noteone.app.core.design.AppType
import com.noteone.app.core.design.SemanticColor
import com.noteone.app.core.design.SemanticKeys
import com.noteone.app.core.design.Space
import com.noteone.app.core.design.component.AppBottomSheet
import com.noteone.app.core.design.component.PrimaryButton
import com.noteone.app.core.design.component.SecondaryButton
import com.noteone.app.core.design.component.TextToggleRow
import com.noteone.app.feature.manage.NameField

/**
 * 新建 / 编辑快捷按钮弹层（任务书 §5.2）。
 *
 * 三项输入：名称（≤6 字）+ 方向 + 所属分类。
 * **没有金额输入，也不许加**——快捷按钮的点击语义是「选中该分类」，
 * 用户明确否定过「给按钮设默认金额」。
 *
 * 切换方向时若原选中的分类在该方向不可用，会自动落到该方向的第一个分类。
 */
@Composable
fun QuickActionEditorSheet(
    isNew: Boolean,
    initialLabel: String,
    initialCategoryId: Long,
    initialDirection: Int,
    categories: List<Category>,
    onSave: (label: String, categoryId: Long, direction: Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf(initialLabel) }
    var direction by remember { mutableIntStateOf(initialDirection) }
    var categoryId by remember { mutableLongStateOf(initialCategoryId) }

    val available = availableCategoriesFor(categories, direction)
    val effectiveCategoryId = available.firstOrNull { it.id == categoryId }?.id
        ?: available.firstOrNull()?.id
        ?: 0L

    AppBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(
                if (isNew) R.string.settings_quick_new_title else R.string.settings_quick_edit_title,
            ),
            style = AppType.SectionTitle,
            color = AppColor.Ink,
        )
        Box(modifier = Modifier.height(Space.S))
        NameField(
            value = label,
            onValueChange = { label = it },
            hint = stringResource(R.string.settings_quick_label_hint),
            maxLength = BuiltInData.QUICK_ACTION_LABEL_MAX,
        )

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

        Box(modifier = Modifier.height(Space.L))
        Text(
            text = stringResource(R.string.settings_quick_category),
            style = AppType.Caption,
            color = AppColor.Muted,
        )
        Box(modifier = Modifier.height(Space.S))
        if (available.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_quick_no_category),
                style = AppType.Caption,
                color = AppColor.Muted,
            )
        } else {
            CategoryChipGrid(
                categories = available,
                selectedId = effectiveCategoryId,
                onSelect = { categoryId = it.id },
            )
        }

        Box(modifier = Modifier.height(Space.XL))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.M)) {
            SecondaryButton(
                text = stringResource(R.string.settings_cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = stringResource(R.string.settings_save),
                onClick = { onSave(label.trim(), effectiveCategoryId, direction) },
                enabled = label.isNotBlank() && effectiveCategoryId > 0L,
                modifier = Modifier.weight(1f),
            )
        }

        if (!isNew) {
            Box(modifier = Modifier.height(Space.M))
            SecondaryButton(
                text = stringResource(R.string.settings_delete),
                onClick = onDelete,
                contentColor = SemanticColor.of(SemanticKeys.Red).fg,
            )
        }
    }
}

/** 删除（归档）快捷按钮的确认弹层。 */
@Composable
fun QuickActionDeleteSheet(
    label: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.settings_quick_delete_title, label),
            style = AppType.SectionTitle,
            color = AppColor.Ink,
        )
        Box(modifier = Modifier.height(Space.S))
        Text(
            text = stringResource(R.string.settings_quick_delete_body),
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
                text = stringResource(R.string.settings_delete),
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
