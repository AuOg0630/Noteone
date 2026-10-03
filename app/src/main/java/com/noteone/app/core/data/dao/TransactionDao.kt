// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.noteone.app.core.data.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

/**
 * 交易表 DAO。
 *
 * **只允许 `core/data` 内部使用**（其它模块一律走 `TransactionRepository`），
 * 因此声明为 internal。
 *
 * 所有业务查询都带 `deletedAt IS NULL`——软删除的记录对业务完全不可见。
 * 只有备份导出 / 清理任务才会显式读取已删除的行。
 */
@Dao
interface TransactionDao {

    // ------------------------------------------------------------------ 读

    @Query(
        """
        SELECT * FROM transactions
        WHERE bookId = :bookId AND deletedAt IS NULL
        ORDER BY occurredAt DESC, id DESC
        LIMIT :limit
        """
    )
    fun observeRecent(bookId: Long, limit: Int): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE bookId = :bookId AND occurredAt BETWEEN :startMs AND :endMs AND deletedAt IS NULL
        ORDER BY occurredAt DESC, id DESC
        """
    )
    fun observeByRange(bookId: Long, startMs: Long, endMs: Long): Flow<List<TransactionEntity>>

    /**
     * 带分类筛选的区间查询。调用方必须保证 [categoryIds] **非空**——
     * SQL 的 `IN ()` 是语法错误，空集合由 Repository 分流到 [observeByRange]。
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE bookId = :bookId AND occurredAt BETWEEN :startMs AND :endMs
          AND deletedAt IS NULL AND categoryId IN (:categoryIds)
        ORDER BY occurredAt DESC, id DESC
        """
    )
    fun observeByRangeAndCategories(
        bookId: Long,
        startMs: Long,
        endMs: Long,
        categoryIds: List<Long>,
    ): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE bookId = :bookId AND deletedAt IS NULL
        ORDER BY occurredAt DESC, id DESC
        """
    )
    fun observeAll(bookId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id AND deletedAt IS NULL")
    fun observeById(id: Long): Flow<TransactionEntity?>

    /**
     * 带 `deletedAt IS NULL`：已软删除的记录不能被编辑写回。
     *
     * 少了这个条件，`update()` 里的「先读后整行写回」会把 `deletedAt` 一起写回 `null`，
     * 被删掉的记录当场复活并重新出现在账单与统计里。
     */
    @Query("SELECT * FROM transactions WHERE id = :id AND deletedAt IS NULL")
    suspend fun getById(id: Long): TransactionEntity?

    /** 概览条 / 统计三卡用：某方向上区间内的合计。 */
    @Query(
        """
        SELECT COALESCE(SUM(amountCents), 0) FROM transactions
        WHERE bookId = :bookId AND direction = :direction
          AND occurredAt BETWEEN :startMs AND :endMs AND deletedAt IS NULL
        """
    )
    fun observeSum(bookId: Long, direction: Int, startMs: Long, endMs: Long): Flow<Long>

    @Query("SELECT COUNT(*) FROM transactions WHERE bookId = :bookId AND deletedAt IS NULL")
    suspend fun countActiveByBook(bookId: Long): Int

    /** 备份导出：连软删除的一起导出（规范 §6.1）。 */
    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC, id DESC")
    suspend fun getAllIncludingDeleted(): List<TransactionEntity>

    // ------------------------------------------------------------------ 写

    @Insert
    suspend fun insert(entity: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<TransactionEntity>)

    @Update
    suspend fun update(entity: TransactionEntity)

    @Query("UPDATE transactions SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun markDeleted(id: Long, deletedAt: Long)

    @Query("UPDATE transactions SET deletedAt = NULL WHERE id = :id")
    suspend fun clearDeleted(id: Long)

    /** 7 天前的软删除记录物理清除。 */
    @Query("DELETE FROM transactions WHERE deletedAt IS NOT NULL AND deletedAt < :thresholdMs")
    suspend fun purgeDeletedBefore(thresholdMs: Long): Int

    /** 删除账本时用户选「把记录移到其他账本」。 */
    @Query("UPDATE transactions SET bookId = :targetBookId, updatedAt = :now WHERE bookId = :sourceBookId")
    suspend fun moveBook(sourceBookId: Long, targetBookId: Long, now: Long)

    /** 删除账本时用户选「一并删除 N 条记录」。 */
    @Query("DELETE FROM transactions WHERE bookId = :bookId")
    suspend fun deleteAllOfBook(bookId: Long)

    /** 清空所有数据（设置页的危险操作，需二次确认 + 输入「清空」）。 */
    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}
