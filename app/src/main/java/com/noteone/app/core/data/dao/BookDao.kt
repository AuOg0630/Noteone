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
import com.noteone.app.core.data.entity.BookEntity
import kotlinx.coroutines.flow.Flow

/** 账本 DAO。仅供 `core/data` 内部使用，外部走 `BookRepository`。 */
@Dao
interface BookDao {

    @Query("SELECT * FROM books WHERE archived = 0 ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY sortOrder ASC, id ASC")
    fun observeAllIncludingArchived(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeById(id: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): BookEntity?

    @Query("SELECT * FROM books ORDER BY sortOrder ASC, id ASC LIMIT 1")
    suspend fun getFirst(): BookEntity?

    @Query("SELECT COUNT(*) FROM books WHERE archived = 0")
    suspend fun countActive(): Int

    @Query("SELECT COUNT(*) FROM books")
    suspend fun countAll(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM books")
    suspend fun maxSortOrder(): Int

    @Insert
    suspend fun insert(entity: BookEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<BookEntity>)

    @Update
    suspend fun update(entity: BookEntity)

    @Query("UPDATE books SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE books SET colorKey = :colorKey WHERE id = :id")
    suspend fun setColor(id: Long, colorKey: String)

    @Query("UPDATE books SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)

    @Query("UPDATE books SET isDefault = :isDefault WHERE id = :id")
    suspend fun setDefault(id: Long, isDefault: Boolean)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteHard(id: Long)

    @Query("DELETE FROM books")
    suspend fun deleteAll()
}
