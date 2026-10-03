// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.data.model

/**
 * 领域模型。UI 层只认这里，不认 `*Entity`。
 *
 * 与 `00_项目总纲与接口契约.md` §5.3 一致；为保证「导出的 JSON 能原样导回」，
 * 这里保留了实体上的全部列，新增字段一律带默认值，**不要调整已有字段的顺序**。
 */

data class Book(
    val id: Long,
    val name: String,
    val colorKey: String,
    val sortOrder: Int,
    val isDefault: Boolean,
    val archived: Boolean,
    val createdAt: Long,
)

data class Category(
    val id: Long,
    val name: String,
    /** 0 = 仅支出, 1 = 仅收入, 2 = 通用 */
    val direction: Int,
    val colorKey: String,
    val sortOrder: Int,
    val isBuiltin: Boolean,
    val archived: Boolean,
)

data class QuickAction(
    val id: Long,
    val label: String,
    val categoryId: Long,
    val direction: Int,
    val sortOrder: Int,
    val archived: Boolean,
)

data class Transaction(
    val id: Long,
    val bookId: Long,
    /** 分，恒为正数 */
    val amountCents: Long,
    /** 0 = 支出, 1 = 收入 */
    val direction: Int,
    val categoryId: Long?,
    val note: String,
    /** 业务时间，可被改为过去 */
    val occurredAt: Long,
    /** 0 = 手动, 1 = 磁贴 OCR, 2 = 快捷按钮, 3 = 编辑 */
    val source: Int,
    val deletedAt: Long?,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val currency: String = "CNY",
    val recognizedText: String? = null,
)

/** 新建一条记录的全部输入。`id` / `createdAt` / `updatedAt` 由数据层生成。 */
data class TransactionDraft(
    val bookId: Long,
    val amountCents: Long,
    val direction: Int,
    val categoryId: Long?,
    val note: String,
    val occurredAt: Long,
    val source: Int,
    val recognizedText: String? = null,
    val currency: String = "CNY",
)

/** 记账方向。 */
object Direction {
    const val Expense = 0
    const val Income = 1

    fun labelOf(direction: Int): String = if (direction == Income) "收入" else "支出"
}

/** 分类的适用范围。 */
object CategoryScope {
    const val ExpenseOnly = 0
    const val IncomeOnly = 1
    const val Both = 2

    /** 该分类是否可用于某个方向。 */
    fun matches(categoryDirection: Int, recordDirection: Int): Boolean = when (categoryDirection) {
        Both -> true
        IncomeOnly -> recordDirection == Direction.Income
        else -> recordDirection == Direction.Expense
    }
}

/** 记录来源，写入 `transactions.source`，CSV 导出时映射成中文。 */
object TransactionSource {
    const val Manual = 0
    const val TileOcr = 1
    const val QuickAction = 2
    const val Edit = 3

    /** CSV / JSON 里的可读来源名（规范 §10.1 的示例用「手动 / 显示磁贴」这类平实说法）。 */
    fun labelOf(source: Int): String = when (source) {
        TileOcr -> "磁贴识别"
        QuickAction -> "快捷按钮"
        Edit -> "编辑"
        else -> "手动"
    }
}
