// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

// `AppBottomSheet` 的签名里有 M3 的 `SheetState`（实验 API），所以调用方也要 opt-in。
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.noteone.app.feature.book

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noteone.app.R
import com.noteone.app.core.data.model.Book
import com.noteone.app.core.design.AppColor
import com.noteone.app.core.design.AppType
import com.noteone.app.core.design.Dimension
import com.noteone.app.core.design.SemanticColor
import com.noteone.app.core.design.Space
import com.noteone.app.core.design.component.AppBottomSheet
import com.noteone.app.core.design.component.Divider
import com.noteone.app.core.design.component.TextButtonSmall
import com.noteone.app.core.design.icon.AppIcons

/**
 * 账本切换底部弹层（任务书 §2.3 「首页账本」）。
 *
 * 列出全部账本，带色点，当前项打勾；底部一行「管理账本 →」跳账本管理页。
 *
 * 这是**公开的公用件**：设置页的「首页账本」与记账页的固定头部都应该用它。
 * 记账页（B）目前有一份私有的同款实现（`feature/record/RecordSheets.kt` 的
 * `BookSwitchSheet`），建议后续由 B 换成这一份，消除重复——
 * 跨模块改动不在 E 的权限范围内，出入已记在交付说明里。
 */
@Composable
fun BookSwitcherSheet(
    books: List<Book>,
    currentBookId: Long,
    onSelect: (Long) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.settings_book_switch_title),
            style = AppType.SectionTitle,
            color = AppColor.Ink,
        )
        Box(modifier = Modifier.height(Space.S))
        books.forEachIndexed { index, book ->
            if (index > 0) Divider()
            BookSwitchRow(
                book = book,
                selected = book.id == currentBookId,
                onClick = { onSelect(book.id) },
            )
        }
        Divider()
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            TextButtonSmall(
                text = stringResource(R.string.settings_book_manage_entry),
                onClick = onManage,
            )
        }
    }
}

/** 弹层里的一行账本：色点 + 名称 + 选中打勾。 */
@Composable
private fun BookSwitchRow(
    book: Book,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimension.TouchMin)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(Dimension.ColorDot)
                .clip(CircleShape)
                .background(SemanticColor.of(book.colorKey).fg),
        )
        Box(modifier = Modifier.width(Space.M))
        Text(text = book.name, style = AppType.Body, color = AppColor.Ink)
        Spacer(modifier = Modifier.weight(1f))
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
