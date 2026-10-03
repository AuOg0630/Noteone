// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

// `AppBottomSheet` 的签名里有 M3 的 `SheetState`（实验 API），所以调用方也要 opt-in。
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.noteone.app.feature.book

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noteone.app.R
import com.noteone.app.core.design.AppColor
import com.noteone.app.core.design.AppType
import com.noteone.app.core.design.Dimension
import com.noteone.app.core.design.SemanticColor
import com.noteone.app.core.design.SemanticKeys
import com.noteone.app.core.design.Space
import com.noteone.app.core.design.component.AppBottomSheet
import com.noteone.app.core.design.component.Divider
import com.noteone.app.core.design.component.PrimaryButton
import com.noteone.app.core.design.component.SecondaryButton
import com.noteone.app.core.design.icon.AppIcons
import com.noteone.app.feature.manage.ColorDot
import com.noteone.app.feature.manage.ColorDotPicker
import com.noteone.app.feature.manage.NameField

/** 删除目标列表的最大高度，超出可滚（账本上限 10 个，10 行 × 44dp = 440dp 会超出弹层）。 */
private val TargetListMaxHeight = 180.dp

/**
 * 新建 / 编辑账本弹层（任务书 §3.2）。
 *
 * 名称 + 8 色色点选择。编辑态额外给一个「删除账本」入口（只剩一个账本时不给，
 * 仓库层也会拒绝删最后一个）。
 */
@Composable
fun BookEditorSheet(
    isNew: Boolean,
    initialName: String,
    initialColorKey: String,
    canDelete: Boolean,
    onSave: (name: String, colorKey: String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var colorKey by remember { mutableStateOf(initialColorKey) }
    val enabled = name.isNotBlank()

    AppBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(
                if (isNew) R.string.settings_book_new_title else R.string.settings_book_edit_title,
            ),
            style = AppType.SectionTitle,
            color = AppColor.Ink,
        )
        Box(modifier = Modifier.height(Space.S))
        NameField(
            value = name,
            onValueChange = { name = it },
            hint = stringResource(R.string.settings_book_name_hint),
        )
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
                onClick = { onSave(name.trim(), colorKey) },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
        }
        if (canDelete && !isNew) {
            Box(modifier = Modifier.height(Space.M))
            SecondaryButton(
                text = stringResource(R.string.settings_book_delete_action),
                onClick = onDelete,
                contentColor = SemanticColor.of(SemanticKeys.Red).fg,
            )
        }
    }
}

/**
 * 删除账本的二选一弹层（任务书 §3.2）。
 *
 * **禁止静默丢数据**：账本里还有记录时必须让用户明确选择「一并删除」还是「移到其他账本」。
 */
@Composable
fun BookDeleteSheet(
    pending: PendingBookDelete,
    onConfirm: (BookDeleteStrategy) -> Unit,
    onDismiss: () -> Unit,
) {
    val hasRecords = pending.recordCount > 0
    var moveMode by remember { mutableStateOf(false) }
    var targetId by remember { mutableLongStateOf(pending.targets.firstOrNull()?.id ?: 0L) }

    AppBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.settings_book_delete_title, pending.book.name),
            style = AppType.SectionTitle,
            color = AppColor.Ink,
        )
        Box(modifier = Modifier.height(Space.S))
        Text(
            text = if (hasRecords) {
                stringResource(R.string.settings_book_delete_body, pending.recordCount)
            } else {
                stringResource(R.string.settings_book_delete_body_empty)
            },
            style = AppType.Body,
            color = AppColor.Muted,
        )

        if (hasRecords) {
            Box(modifier = Modifier.height(Space.M))
            Divider()
            OptionRow(
                text = stringResource(
                    R.string.settings_book_delete_records,
                    pending.recordCount,
                ),
                selected = !moveMode,
                onClick = { moveMode = false },
            )
            Divider()
            OptionRow(
                text = stringResource(R.string.settings_book_delete_move),
                selected = moveMode,
                onClick = { moveMode = true },
            )
            if (moveMode) {
                Text(
                    text = stringResource(R.string.settings_book_delete_target),
                    style = AppType.Caption,
                    color = AppColor.Muted,
                    modifier = Modifier.padding(top = Space.S, bottom = Space.XS),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = TargetListMaxHeight)
                        .verticalScroll(rememberScrollState()),
                ) {
                    pending.targets.forEach { book ->
                        TargetRow(
                            name = book.name,
                            colorKey = book.colorKey,
                            selected = book.id == targetId,
                            onClick = { targetId = book.id },
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.height(Space.XL))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.M)) {
            SecondaryButton(
                text = stringResource(R.string.settings_cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = stringResource(R.string.settings_book_delete_action),
                onClick = {
                    val strategy = if (moveMode && targetId > 0L) {
                        BookDeleteStrategy.MoveTo(targetId)
                    } else {
                        BookDeleteStrategy.DeleteRecords
                    }
                    onConfirm(strategy)
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 二选一里的一个选项行：文字 + 选中打勾。 */
@Composable
private fun OptionRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimension.TouchMin)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = text, style = AppType.Body, color = AppColor.Ink)
        if (selected) {
            Icon(
                painter = painterResource(id = AppIcons.Check),
                contentDescription = null,
                tint = AppColor.Ink,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** 迁移目标账本的一行：色点 + 名称 + 选中打勾。 */
@Composable
private fun TargetRow(
    name: String,
    colorKey: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ColorDot(colorKey = colorKey)
            Box(modifier = Modifier.width(Space.M))
            Text(text = name, style = AppType.Body, color = AppColor.Ink)
        }
        if (selected) {
            Icon(
                painter = painterResource(id = AppIcons.Check),
                contentDescription = null,
                tint = AppColor.Ink,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
