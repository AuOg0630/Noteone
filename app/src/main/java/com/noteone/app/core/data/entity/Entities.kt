// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 表结构定义。依据 `docs/开发规范.md` §7.1。
 *
 * 三条铁律（任何改动都不得违反）：
 * 1. 金额一律以**分**存 `Long`，禁止 `Double` / `Float`。
 * 2. 删除一律**软删除**（`deletedAt`），7 天后由 `PurgeDeletedUseCase` 物理清除。
 * 3. `exportSchema = true`，禁止 `fallbackToDestructiveMigration`；改表必须写 `Migration`。
 */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** 语义色 key，用于账本标识色点。取值见 SemanticKeys。 */
    val colorKey: String,
    val sortOrder: Int,
    val isDefault: Boolean,
    val archived: Boolean = false,
    val createdAt: Long,
)

@Entity(
    tableName = "transactions",
    indices = [
        Index("bookId", "occurredAt"),
        Index("categoryId"),
        Index("deletedAt"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT,
        ),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    /** 恒为正数，方向由 [direction] 决定 */
    val amountCents: Long,
    /** 0 = 支出, 1 = 收入 */
    val direction: Int,
    /** null = 未分类。故意不做外键：分类被「归档」而不是删除，历史记录要能继续显示原分类。 */
    val categoryId: Long?,
    val note: String = "",
    /** 业务时间（epoch millis），可被用户改为过去任意日期（补录） */
    val occurredAt: Long,
    /** 不可变的创建时间 */
    val createdAt: Long,
    val updatedAt: Long,
    /** 0 = 手动, 1 = 磁贴 OCR, 2 = 快捷按钮, 3 = 编辑 */
    val source: Int,
    val currency: String = "CNY",
    /** 软删除标记；撤销 = 置回 null */
    val deletedAt: Long? = null,
    /** OCR 原始文本行，**只存文本不存图像** */
    val recognizedText: String? = null,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** 0 = 仅支出, 1 = 仅收入, 2 = 通用 */
    val direction: Int,
    val colorKey: String,
    val sortOrder: Int,
    val isBuiltin: Boolean,
    val archived: Boolean = false,
)

@Entity(tableName = "quick_actions")
data class QuickActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val categoryId: Long,
    val direction: Int,
    val sortOrder: Int,
    val archived: Boolean = false,
)
