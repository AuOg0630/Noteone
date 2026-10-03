// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.data.repository

import androidx.room.withTransaction
import com.noteone.app.core.data.dao.BookDao
import com.noteone.app.core.data.db.AppDatabase
import com.noteone.app.core.data.entity.BookEntity
import com.noteone.app.core.data.mapper.toBooks
import com.noteone.app.core.data.model.Book
import com.noteone.app.core.data.seed.BuiltInData
import com.noteone.app.core.design.SemanticColor
import com.noteone.app.core.design.SemanticKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** 账本读写入口。上限 [BuiltInData.MAX_BOOKS] 个。 */
interface BookRepository {

    fun observeAll(): Flow<List<Book>>

    /** 当前账本。切换后所有页面数据随之切换。 */
    fun observeCurrent(): Flow<Book>

    suspend fun setCurrent(id: Long)

    /** 新建账本，返回新 id。达到上限时抛出 [IllegalStateException]。 */
    suspend fun add(name: String, colorKey: String): Long

    suspend fun rename(id: Long, name: String)

    suspend fun setColor(id: Long, colorKey: String)

    /**
     * 删除账本。**禁止静默丢数据**：
     * - `moveToBookId == null` → 连同该账本的记录一起删除
     * - `moveToBookId != null` → 先把记录迁移到目标账本再删除
     *
     * 只剩一个账本时**不做任何操作**（调用方应提前把按钮置灰）。
     * 若被删的是当前账本，会自动切到默认账本。
     */
    suspend fun delete(id: Long, moveToBookId: Long?)

    /** 拖拽排序。传入按新顺序排列的 id 列表。 */
    suspend fun reorder(orderedIds: List<Long>)

    suspend fun countActive(): Int
}

@Singleton
class BookRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val bookDao: BookDao,
    private val settingsRepository: SettingsRepository,
) : BookRepository {

    override fun observeAll(): Flow<List<Book>> = bookDao.observeAll().map { it.toBooks() }

    override fun observeCurrent(): Flow<Book> =
        combine(settingsRepository.currentBookId, bookDao.observeAll()) { id, entities ->
            // 先统一转成领域模型再回退，避免 Entity 与 Model 混在一支表达式里导致类型上推成 Any
            val books = entities.toBooks()
            books.firstOrNull { it.id == id }
                ?: books.firstOrNull { it.isDefault }
                ?: books.firstOrNull()
                ?: placeholderBook()
        }.distinctUntilChanged()

    override suspend fun setCurrent(id: Long) {
        if (bookDao.getById(id) == null) return
        settingsRepository.setCurrentBookId(id)
    }

    // 上限校验、取 sortOrder、插入必须在同一个事务里：
    // 三次独立事务的话，并发调用会读到同一个旧计数，突破上限或写出重复 sortOrder。
    override suspend fun add(name: String, colorKey: String): Long {
        val trimmed = name.trim().take(BuiltInData.NAME_MAX_LENGTH)
        require(trimmed.isNotEmpty()) { "账本名不能为空" }
        val color = if (SemanticColor.contains(colorKey)) colorKey else SemanticKeys.Stone
        return database.withTransaction {
            check(bookDao.countActive() < BuiltInData.MAX_BOOKS) {
                "最多 ${BuiltInData.MAX_BOOKS} 个账本"
            }
            bookDao.insert(
                BookEntity(
                    name = trimmed,
                    colorKey = color,
                    sortOrder = bookDao.maxSortOrder() + 1,
                    isDefault = false,
                    archived = false,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    override suspend fun rename(id: Long, name: String) {
        val trimmed = name.trim().take(BuiltInData.NAME_MAX_LENGTH)
        if (trimmed.isEmpty()) return
        bookDao.rename(id, trimmed)
    }

    override suspend fun setColor(id: Long, colorKey: String) {
        if (!SemanticColor.contains(colorKey)) return
        bookDao.setColor(id, colorKey)
    }

    override suspend fun delete(id: Long, moveToBookId: Long?) {
        val total = bookDao.countActive()
        if (total <= 1) return // 不能删除最后一个账本

        val target = bookDao.getById(id) ?: return

        database.withTransaction {
            val transactionDao = database.transactionDao()
            if (moveToBookId != null && moveToBookId != id) {
                transactionDao.moveBook(sourceBookId = id, targetBookId = moveToBookId, now = System.currentTimeMillis())
            } else {
                transactionDao.deleteAllOfBook(id)
            }
            bookDao.deleteHard(id)
            // 删掉的是默认账本时，把默认标记移交出去
            if (target.isDefault) {
                val next = bookDao.getFirst()
                if (next != null && next.id != id) bookDao.setDefault(next.id, true)
            }
        }

        // 只有被删的正好是当前账本时才需要切换，否则会把用户切到别的账本
        if (settingsRepository.currentBookId.first() == id) {
            val fallback = bookDao.getDefault() ?: bookDao.getFirst()
            if (fallback != null) {
                settingsRepository.setCurrentBookId(fallback.id)
            }
        }
    }

    override suspend fun reorder(orderedIds: List<Long>) {
        database.withTransaction {
            orderedIds.forEachIndexed { index, id -> bookDao.setSortOrder(id, index) }
        }
    }

    override suspend fun countActive(): Int = bookDao.countActive()

    /**
     * 数据初始化完成前的极短暂窗口里，`books` 表还是空的。
     * 这里给一个 id = 0 的占位账本，让 UI 不会因为 `Flow` 没有值而卡住；
     * 初始化一完成就会被真实账本替换掉（id = 0 查不到任何记录）。
     */
    private fun placeholderBook() = Book(
        id = 0L,
        name = BuiltInData.DEFAULT_BOOK_NAME,
        colorKey = BuiltInData.DEFAULT_BOOK_COLOR,
        sortOrder = 0,
        isDefault = true,
        archived = false,
        createdAt = 0L,
    )
}
