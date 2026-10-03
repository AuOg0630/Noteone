// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.export

import android.content.Context
import android.net.Uri
import com.noteone.app.core.data.model.Transaction
import com.noteone.app.core.data.repository.BookRepository
import com.noteone.app.core.data.repository.CategoryRepository
import com.noteone.app.core.data.repository.TransactionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CSV 写盘（Storage Access Framework）。
 *
 * 分工：**页面只负责弹出系统文件选择器拿到 `Uri`**，写文件全在这里做。
 * 这样「按筛选条件导出」和「导出全部数据」共用同一份序列化与写入代码。
 *
 * - 汇总页用 [exportFiltered]，范围 = 当前页面的时间范围 + 分类筛选
 * - 设置页（E）用 [exportAll]，导出当前账本的全部记录，不受任何页面筛选状态影响
 *
 * 失败一律**抛异常**，由调用方的 ViewModel 捕获后决定怎么提示
 * （规范 §11：用户取消文件选择是静默返回，只有写入失败才提示）。
 */
@Singleton
class CsvExportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val bookRepository: BookRepository,
) {

    /**
     * 导出**当前账本 + 指定时间范围 + 指定分类**的记录。
     *
     * @param categoryIds 空集合 = 不筛分类（汇总页的「全部」）
     * @return 写出的记录条数
     */
    suspend fun exportFiltered(
        uri: Uri,
        startMs: Long,
        endMs: Long,
        categoryIds: Set<Long> = emptySet(),
    ): Int = withContext(Dispatchers.IO) {
        val book = bookRepository.observeCurrent().first()
        val records = transactionRepository.observeByRange(book.id, startMs, endMs, categoryIds).first()
        write(uri, records)
    }

    /**
     * 导出**当前账本的全部记录**（全部时间、不做分类筛选）。
     *
     * 供设置页的「导出 CSV」使用——那里的语义是「当前全部数据」，
     * 不依赖汇总页的任何筛选状态。
     *
     * @return 写出的记录条数
     */
    suspend fun exportAll(uri: Uri): Int = withContext(Dispatchers.IO) {
        val book = bookRepository.observeCurrent().first()
        val records = transactionRepository.observeAll(book.id).first()
        write(uri, records)
    }

    private suspend fun write(uri: Uri, records: List<Transaction>): Int {
        val categories = categoryRepository.observeAll().first().associateBy { it.id }
        val bookNames = bookRepository.observeAll().first().associate { it.id to it.name }
        val text = CsvSerializer.serialize(records, categories, bookNames)

        // "wt" = write + truncate：SAF 返回的可能是已存在的文档，不截断会残留旧内容
        val stream = context.contentResolver.openOutputStream(uri, MODE_WRITE_TRUNCATE)
            ?: throw IOException("无法打开所选位置用于写入")
        stream.use { output ->
            // 编码固定 UTF-8，BOM 已经在 CsvSerializer 里拼好了
            output.write(text.toByteArray(Charsets.UTF_8))
            output.flush()
        }
        return records.size
    }

    private companion object {
        const val MODE_WRITE_TRUNCATE = "wt"
    }
}
