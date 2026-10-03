// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.data.mapper

import com.noteone.app.core.data.entity.BookEntity
import com.noteone.app.core.data.entity.CategoryEntity
import com.noteone.app.core.data.entity.QuickActionEntity
import com.noteone.app.core.data.entity.TransactionEntity
import com.noteone.app.core.data.model.Book
import com.noteone.app.core.data.model.Category
import com.noteone.app.core.data.model.QuickAction
import com.noteone.app.core.data.model.Transaction
import com.noteone.app.core.data.model.TransactionDraft
import com.noteone.app.core.data.seed.BuiltInData

/**
 * Entity ↔ 领域模型。只在 `core/data` 内部使用。
 *
 * 命名约定：**每个聚合有自己独立的扩展函数名**（`toBooks` / `toCategories` / …），
 * 不能用统一的 `List<*>.toModels()`——泛型擦除后它们的 JVM 签名完全一样，
 * 会导致 `Platform declaration clash` 编译错误。
 */

// ---------------------------------------------------------------- Entity → Model

internal fun BookEntity.toBook() = Book(
    id = id,
    name = name,
    colorKey = colorKey,
    sortOrder = sortOrder,
    isDefault = isDefault,
    archived = archived,
    createdAt = createdAt,
)

internal fun List<BookEntity>.toBooks(): List<Book> = map { it.toBook() }

internal fun CategoryEntity.toCategory() = Category(
    id = id,
    name = name,
    direction = direction,
    colorKey = colorKey,
    sortOrder = sortOrder,
    isBuiltin = isBuiltin,
    archived = archived,
)

internal fun List<CategoryEntity>.toCategories(): List<Category> = map { it.toCategory() }

internal fun QuickActionEntity.toQuickAction() = QuickAction(
    id = id,
    label = label,
    categoryId = categoryId,
    direction = direction,
    sortOrder = sortOrder,
    archived = archived,
)

internal fun List<QuickActionEntity>.toQuickActions(): List<QuickAction> = map { it.toQuickAction() }

internal fun TransactionEntity.toTransaction() = Transaction(
    id = id,
    bookId = bookId,
    amountCents = amountCents,
    direction = direction,
    categoryId = categoryId,
    note = note,
    occurredAt = occurredAt,
    source = source,
    deletedAt = deletedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    currency = currency,
    recognizedText = recognizedText,
)

internal fun List<TransactionEntity>.toTransactions(): List<Transaction> = map { it.toTransaction() }

// ---------------------------------------------------------------- Model → Entity

/** 新记录：主键自增，`createdAt` / `updatedAt` 取当前时间。 */
internal fun TransactionDraft.toNewEntity(now: Long) = TransactionEntity(
    bookId = bookId,
    amountCents = amountCents,
    direction = direction,
    categoryId = categoryId,
    note = note.take(BuiltInData.NOTE_MAX_LENGTH),
    occurredAt = occurredAt,
    createdAt = now,
    updatedAt = now,
    source = source,
    currency = currency,
    deletedAt = null,
    recognizedText = recognizedText,
)

/**
 * 用领域模型覆盖实体上**可编辑**的字段。
 *
 * `createdAt` / `currency` / `recognizedText` 由数据层保留，不参与覆盖——
 * 它们是历史事实，编辑金额和分类不应该把它们抹掉。
 */
internal fun TransactionEntity.applyEdit(
    edited: Transaction,
    now: Long,
): TransactionEntity = copy(
    bookId = edited.bookId,
    amountCents = edited.amountCents,
    direction = edited.direction,
    categoryId = edited.categoryId,
    note = edited.note.take(BuiltInData.NOTE_MAX_LENGTH),
    occurredAt = edited.occurredAt,
    source = edited.source,
    updatedAt = now,
)

/** 备份导入用：保留原主键与全部时间戳。 */
internal fun Transaction.toEntity() = TransactionEntity(
    id = id,
    bookId = bookId,
    amountCents = amountCents,
    direction = direction,
    categoryId = categoryId,
    note = note.take(BuiltInData.NOTE_MAX_LENGTH),
    occurredAt = occurredAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    source = source,
    currency = currency,
    deletedAt = deletedAt,
    recognizedText = recognizedText,
)

internal fun Book.toEntity() = BookEntity(
    id = id,
    name = name,
    colorKey = colorKey,
    sortOrder = sortOrder,
    isDefault = isDefault,
    archived = archived,
    createdAt = createdAt,
)

internal fun Category.toEntity() = CategoryEntity(
    id = id,
    name = name,
    direction = direction,
    colorKey = colorKey,
    sortOrder = sortOrder,
    isBuiltin = isBuiltin,
    archived = archived,
)

internal fun QuickAction.toEntity() = QuickActionEntity(
    id = id,
    label = label,
    categoryId = categoryId,
    direction = direction,
    sortOrder = sortOrder,
    archived = archived,
)
