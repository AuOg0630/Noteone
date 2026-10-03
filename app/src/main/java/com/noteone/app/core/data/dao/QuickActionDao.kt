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
import com.noteone.app.core.data.entity.QuickActionEntity
import kotlinx.coroutines.flow.Flow

/** 快捷用途按钮 DAO。仅供 `core/data` 内部使用，外部走 `QuickActionRepository`。 */
@Dao
interface QuickActionDao {

    @Query("SELECT * FROM quick_actions WHERE archived = 0 ORDER BY sortOrder ASC, id ASC")
    fun observeActive(): Flow<List<QuickActionEntity>>

    @Query("SELECT * FROM quick_actions ORDER BY sortOrder ASC, id ASC")
    fun observeAllIncludingArchived(): Flow<List<QuickActionEntity>>

    /** 备份导出与内置数据升级用：一次性取出全部（含已归档）。 */
    @Query("SELECT * FROM quick_actions ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllIncludingArchived(): List<QuickActionEntity>

    @Query("SELECT * FROM quick_actions WHERE id = :id")
    suspend fun getById(id: Long): QuickActionEntity?

    @Query("SELECT COUNT(*) FROM quick_actions WHERE archived = 0")
    suspend fun countActive(): Int

    @Query("SELECT COUNT(*) FROM quick_actions")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM quick_actions")
    suspend fun maxSortOrder(): Int

    @Insert
    suspend fun insert(entity: QuickActionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<QuickActionEntity>)

    @Update
    suspend fun update(entity: QuickActionEntity)

    @Query("UPDATE quick_actions SET label = :label, categoryId = :categoryId, direction = :direction WHERE id = :id")
    suspend fun updateContent(id: Long, label: String, categoryId: Long, direction: Int)

    @Query("UPDATE quick_actions SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("UPDATE quick_actions SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)

    @Query("DELETE FROM quick_actions")
    suspend fun deleteAll()
}
