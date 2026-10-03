// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

// `AppBottomSheet` 的签名里有 M3 的 `SheetState`（实验 API），所以调用方也要 opt-in。
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.noteone.app.feature.record

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import com.noteone.app.core.data.model.Category
import com.noteone.app.core.design.AppColor
import com.noteone.app.core.design.AppType
import com.noteone.app.core.design.Dimension
import com.noteone.app.core.design.SemanticColor
import com.noteone.app.core.design.SemanticKeys
import com.noteone.app.core.design.Space
import com.noteone.app.core.design.component.AppBottomSheet
import com.noteone.app.core.design.component.CategoryChip
import com.noteone.app.core.design.component.Divider
import com.noteone.app.core.design.component.TextButtonSmall
import com.noteone.app.core.design.icon.AppIcons

/** 全量分类弹层的单元格宽度（三列），最大高度（超出可滚）。 */
private val SheetGridMaxHeight = 300.dp

/**
 * 账本切换底部弹层（规范 §5.1 固定头部）。
 *
 * 列出全部账本，带色点，当前项打勾；底部一行「管理账本 →」跳账本管理页。
 */
@Composable
internal fun BookSwitchSheet(
    books: List<Book>,
    currentBookId: Long,
    onSelect: (Long) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.record_book_sheet_title),
            style = AppType.SectionTitle,
            color = AppColor.Ink,
        )
        Box(modifier = Modifier.height(Space.S))
        books.forEachIndexed { index, book ->
            if (index > 0) Divider()
            BookRow(
                book = book,
                selected = book.id == currentBookId,
                onClick = { onSelect(book.id) },
            )
        }
        Divider()
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            TextButtonSmall(
                text = stringResource(R.string.record_book_manage),
                onClick = onManage,
            )
        }
    }
}

@Composable
private fun BookRow(
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

/**
 * 全量分类底部弹层（规范 §5.1 分类行）。
 *
 * 网格排列当前方向下所有**未归档**分类（与 chip 行同一数据源，只是能一眼看全），
 * 底部一行「管理分类 →」。选中某分类后立即回调并关闭由调用方决定。
 *
 * @param showManageEntry 账单编辑面板里复用本弹层时传 `false`——编辑面板内没有
 *                        跳转分类管理页的上下文
 */
@Composable
internal fun CategoryPickerSheet(
    categories: List<Category>,
    selectedCategoryId: Long?,
    onSelect: (Category) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
    showManageEntry: Boolean = true,
) {
    AppBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.record_category_sheet_title),
            style = AppType.SectionTitle,
            color = AppColor.Ink,
        )
        Box(modifier = Modifier.height(Space.M))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = SheetGridMaxHeight)
                .verticalScroll(rememberScrollState()),
        ) {
            categories.chunked(3).forEach { rowItems ->
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
                            selected = category.id == selectedCategoryId,
                            onClick = { onSelect(category) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(3 - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        if (showManageEntry) {
            Divider()
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                TextButtonSmall(
                    text = stringResource(R.string.record_category_manage),
                    onClick = onManage,
                )
            }
        }
    }
}
