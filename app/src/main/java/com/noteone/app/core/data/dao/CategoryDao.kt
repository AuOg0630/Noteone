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
import com.noteone.app.core.data.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 分类 DAO。仅供 `core/data` 内部使用，外部走 `CategoryRepository`。
 *
 * 注意：分类**不做账本隔离**（全局共享），所以这里没有 `bookId` 条件。
 */
@Dao
interface CategoryDao {

    /** 记账页 chip 行 / 选择器用：不含已归档。 */
    @Query("SELECT * FROM categories WHERE archived = 0 ORDER BY sortOrder ASC, id ASC")
    fun observeActive(): Flow<List<CategoryEntity>>

    /**
     * 历史记录 join 用：**必须包含已归档分类**，否则历史账单会显示「未知分类」。
     * 归档只是让它不再出现在选择器里。
     */
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    fun observeAllIncludingArchived(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE archived = 1 ORDER BY sortOrder ASC, id ASC")
    fun observeArchived(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE name = :name AND direction = :direction LIMIT 1")
    suspend fun getByNameAndDirection(name: String, direction: Int): CategoryEntity?

    @Query("SELECT * FROM categories WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<CategoryEntity>

    /** 记账页默认选中「上次使用的分类」，缺失时退回到支出侧第一个分类。 */
    @Query("SELECT * FROM categories WHERE archived = 0 AND (direction = :direction OR direction = 2) ORDER BY sortOrder ASC, id ASC LIMIT 1")
    suspend fun firstForDirection(direction: Int): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM categories WHERE archived = 0 AND direction = :direction")
    suspend fun countActiveForDirection(direction: Int): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM categories WHERE direction = :direction")
    suspend fun maxSortOrder(direction: Int): Int

    @Insert
    suspend fun insert(entity: CategoryEntity): Long

    @Insert
    suspend fun insertAll(entities: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CategoryEntity>)

    @Update
    suspend fun update(entity: CategoryEntity)

    @Query("UPDATE categories SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE categories SET colorKey = :colorKey WHERE id = :id")
    suspend fun setColor(id: Long, colorKey: String)

    @Query("UPDATE categories SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("UPDATE categories SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)

    @Query("DELETE FROM categories WHERE id = :id AND isBuiltin = 0")
    suspend fun deleteNonBuiltin(id: Long)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}
