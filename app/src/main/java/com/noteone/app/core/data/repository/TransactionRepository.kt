// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.data.repository

import androidx.room.withTransaction
import com.noteone.app.core.data.dao.TransactionDao
import com.noteone.app.core.data.db.AppDatabase
import com.noteone.app.core.data.mapper.applyEdit
import com.noteone.app.core.data.mapper.toNewEntity
import com.noteone.app.core.data.mapper.toTransaction
import com.noteone.app.core.data.mapper.toTransactions
import com.noteone.app.core.data.mapper.toEntity
import com.noteone.app.core.data.model.Transaction
import com.noteone.app.core.data.model.TransactionDraft
import com.noteone.app.core.data.seed.BuiltInData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 记账记录的读写入口。
 *
 * **UI 层不许直接碰 DAO**，一律走这里。查询一律只看未删除的记录。
 */
interface TransactionRepository {

    /** 最近 N 条（按业务时间倒序），记账页「最近账单」用。 */
    fun observeRecent(bookId: Long, limit: Int): Flow<List<Transaction>>

    /**
     * 区间内的记录（`startMs` / `endMs` 含端点，来自 `TimeRanges.resolve()`）。
     *
     * @param categoryIds 分类筛选；空集合表示不筛。汇总页的「全部」就是传空集合。
     */
    fun observeByRange(
        bookId: Long,
        startMs: Long,
        endMs: Long,
        categoryIds: Set<Long> = emptySet(),
    ): Flow<List<Transaction>>

    /** 某账本的全部记录（未删除），账单页按月份自行筛选时用。 */
    fun observeAll(bookId: Long): Flow<List<Transaction>>

    /** 单条（编辑面板用）。 */
    fun observeById(id: Long): Flow<Transaction?>

    suspend fun getById(id: Long): Transaction?

    /** 某方向上区间内的合计（分）。概览条与统计三卡用。 */
    fun observeSum(bookId: Long, direction: Int, startMs: Long, endMs: Long): Flow<Long>

    /** 写入一条新记录，返回新 id。**只有用户点了「完成」才允许调用。** */
    suspend fun insert(draft: TransactionDraft): Long

    /** 编辑保存。`createdAt` / `currency` / `recognizedText` 保持不变，`updatedAt` 刷新。 */
    suspend fun update(transaction: Transaction)

    /** 软删除（可撤销）。 */
    suspend fun softDelete(id: Long)

    /** 撤销软删除。 */
    suspend fun restore(id: Long)

    /** 物理清除 7 天前的软删除记录，返回清除条数。启动时跑一次。 */
    suspend fun purgeDeletedBefore(thresholdMs: Long): Int

    suspend fun countActiveByBook(bookId: Long): Int

    /** 备份导出：含软删除的记录。 */
    suspend fun getAllIncludingDeleted(): List<Transaction>
}

@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val transactionDao: TransactionDao,
) : TransactionRepository {

    override fun observeRecent(bookId: Long, limit: Int): Flow<List<Transaction>> =
        transactionDao.observeRecent(bookId, limit).map { it.toTransactions() }

    override fun observeByRange(
        bookId: Long,
        startMs: Long,
        endMs: Long,
        categoryIds: Set<Long>,
    ): Flow<List<Transaction>> {
        // SQL 的 IN () 是语法错误，所以空集合必须走不带筛选的查询
        return if (categoryIds.isEmpty()) {
            transactionDao.observeByRange(bookId, startMs, endMs)
        } else {
            transactionDao.observeByRangeAndCategories(bookId, startMs, endMs, categoryIds.toList())
        }.map { it.toTransactions() }
    }

    override fun observeAll(bookId: Long): Flow<List<Transaction>> =
        transactionDao.observeAll(bookId).map { it.toTransactions() }

    override fun observeById(id: Long): Flow<Transaction?> =
        transactionDao.observeById(id).map { it?.toTransaction() }

    override suspend fun getById(id: Long): Transaction? = transactionDao.getById(id)?.toTransaction()

    override fun observeSum(bookId: Long, direction: Int, startMs: Long, endMs: Long): Flow<Long> =
        transactionDao.observeSum(bookId, direction, startMs, endMs)

    override suspend fun insert(draft: TransactionDraft): Long {
        require(draft.amountCents > 0) { "金额必须大于 0，方向由 direction 决定" }
        return transactionDao.insert(draft.toNewEntity(System.currentTimeMillis()))
    }

    override suspend fun update(transaction: Transaction) {
        require(transaction.amountCents > 0) { "金额必须大于 0，方向由 direction 决定" }
        // 「先读整行 → 再整行写回」必须在同一个事务里：
        // 两步之间若发生 softDelete，写回会把 deletedAt 一起覆盖成 null，记录复活。
        database.withTransaction {
            val existing = transactionDao.getById(transaction.id) ?: return@withTransaction
            transactionDao.update(existing.applyEdit(transaction, System.currentTimeMillis()))
        }
    }

    override suspend fun softDelete(id: Long) {
        transactionDao.markDeleted(id, System.currentTimeMillis())
    }

    override suspend fun restore(id: Long) {
        transactionDao.clearDeleted(id)
    }

    override suspend fun purgeDeletedBefore(thresholdMs: Long): Int =
        transactionDao.purgeDeletedBefore(thresholdMs)

    override suspend fun countActiveByBook(bookId: Long): Int =
        transactionDao.countActiveByBook(bookId)

    override suspend fun getAllIncludingDeleted(): List<Transaction> =
        transactionDao.getAllIncludingDeleted().toTransactions()

    /** 保留天数换算成毫秒阈值，供 `PurgeDeletedUseCase` 使用。 */
    companion object {
        val RETENTION_MILLIS: Long = BuiltInData.SOFT_DELETE_RETENTION_DAYS * 24 * 60 * 60 * 1000
    }
}
