// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.export

import com.noteone.app.core.common.DateFormats
import com.noteone.app.core.common.Money
import com.noteone.app.core.data.model.Category
import com.noteone.app.core.data.model.Direction
import com.noteone.app.core.data.model.Transaction
import com.noteone.app.core.data.model.TransactionSource

/**
 * CSV 序列化（规范 §10.1）。**纯函数，不碰 Android API**，所以单测可以直接断言字符串。
 *
 * 四条容易踩的规格：
 * 1. **UTF-8 with BOM** —— 加 BOM 才能让 Excel 双击打开时中文不乱码，这是最常见的坑；
 * 2. 字段含 **逗号 / 双引号 / 换行** 时用双引号包裹，并把 `"` 转义成 `""`；
 * 3. 金额是**纯数字字符串**（不带 `¥`、不带千分位）；
 * 4. 行分隔用 `\r\n`（Excel 与 RFC 4180 都认这个）。
 *
 * 行顺序沿用 Repository 的返回顺序（`occurredAt DESC, id DESC`），不在这里重排——
 * 导出内容和账单页看到的顺序一致。
 */
object CsvSerializer {

    /** UTF-8 BOM。写成 UTF-8 字节时是 `EF BB BF`。 */
    const val BOM: String = "\uFEFF"

    /** 唯一合法的行分隔符。 */
    const val ROW_SEPARATOR: String = "\r\n"

    /** 表头，不要改顺序（规范 §10.1 已冻结）。 */
    const val HEADER: String = "日期,时间,类型,分类,金额,备注,来源,账本"

    /**
     * 整个文件的内容（含 BOM 与表头）。
     *
     * @param categories 分类 id → 分类。**要传含归档的全量分类**，归档分类的历史记录仍要写出名字。
     * @param bookNames 账本 id → 账本名。
     */
    fun serialize(
        transactions: List<Transaction>,
        categories: Map<Long, Category>,
        bookNames: Map<Long, String>,
    ): String = buildString {
        append(BOM)
        append(HEADER)
        append(ROW_SEPARATOR)
        transactions.forEach { record ->
            append(
                rowOf(
                    transaction = record,
                    categoryName = record.categoryId?.let(categories::get)?.name.orEmpty(),
                    bookName = bookNames[record.bookId].orEmpty(),
                ),
            )
            append(ROW_SEPARATOR)
        }
    }

    /**
     * 一行记录。
     *
     * 未分类的记录**分类列留空**（与规范 §10.1 示例里空备注的写法一致），不写「未分类」，
     * 这样 CSV 里不会混进界面文案。
     */
    fun rowOf(transaction: Transaction, categoryName: String, bookName: String): String = listOf(
        DateFormats.isoDate(transaction.occurredAt),
        DateFormats.hourMinute(transaction.occurredAt),
        Direction.labelOf(transaction.direction),
        categoryName,
        Money.centsToPlain(transaction.amountCents),
        transaction.note,
        TransactionSource.labelOf(transaction.source),
        bookName,
    ).joinToString(separator = ",") { escape(it) }

    /** 只在必要时加引号：含 `,` `"` `\n` `\r` 的字段才包裹并转义。 */
    fun escape(value: String): String {
        val needsQuoting = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuoting) return value
        return '"' + value.replace("\"", "\"\"") + '"'
    }
}

/**
 * 默认文件名：`记账_20260929_2130.csv`（规范 §10.1）。
 *
 * 放在 `export/` 而不是某个页面里，是为了让汇总页与设置页导出时用同一个命名规则。
 */
fun defaultCsvFileName(now: Long = System.currentTimeMillis()): String =
    "记账_" + DateFormats.fileStamp(now) + ".csv"
