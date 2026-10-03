// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.data.repository

import androidx.room.withTransaction
import com.noteone.app.core.data.dao.QuickActionDao
import com.noteone.app.core.data.db.AppDatabase
import com.noteone.app.core.data.entity.QuickActionEntity
import com.noteone.app.core.data.mapper.toQuickAction
import com.noteone.app.core.data.mapper.toQuickActions
import com.noteone.app.core.data.model.Direction
import com.noteone.app.core.data.model.QuickAction
import com.noteone.app.core.data.seed.BuiltInData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 快捷用途按钮读写入口。上限 [BuiltInData.MAX_QUICK_ACTIONS] 个。
 *
 * **点击语义 = 选中该按钮对应的分类**，不是直接入账。所以这里只存
 * 「label + 分类 + 方向」，**没有金额字段，也不许加**（用户明确否定过默认金额）。
 */
interface QuickActionRepository {

    /** **含已归档**。 */
    fun observeAll(): Flow<List<QuickAction>>

    /** 不含已归档，按 `sortOrder` 升序。记账页与悬浮面板用这个。 */
    fun observeActive(): Flow<List<QuickAction>>

    suspend fun getById(id: Long): QuickAction?

    /** 新建。达到上限时抛出 [IllegalStateException]。 */
    suspend fun add(label: String, categoryId: Long, direction: Int): Long

    suspend fun updateContent(id: Long, label: String, categoryId: Long, direction: Int)

    /** 删除 = 归档（软删除）。 */
    suspend fun setArchived(id: Long, archived: Boolean)

    suspend fun reorder(orderedIds: List<Long>)

    suspend fun countActive(): Int
}

@Singleton
class QuickActionRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val quickActionDao: QuickActionDao,
) : QuickActionRepository {

    override fun observeAll(): Flow<List<QuickAction>> =
        quickActionDao.observeAllIncludingArchived().map { it.toQuickActions() }

    override fun observeActive(): Flow<List<QuickAction>> =
        quickActionDao.observeActive().map { it.toQuickActions() }

    override suspend fun getById(id: Long): QuickAction? = quickActionDao.getById(id)?.toQuickAction()

    override suspend fun add(label: String, categoryId: Long, direction: Int): Long {
        val trimmed = label.trim().take(BuiltInData.QUICK_ACTION_LABEL_MAX)
        require(trimmed.isNotEmpty()) { "按钮名称不能为空" }
        require(categoryId > 0L) { "必须选择一个分类" }
        // 上限校验、取 sortOrder、插入同事务（同 BookRepository.add）
        return database.withTransaction {
            check(quickActionDao.countActive() < BuiltInData.MAX_QUICK_ACTIONS) {
                "最多 ${BuiltInData.MAX_QUICK_ACTIONS} 个快捷按钮"
            }
            quickActionDao.insert(
                QuickActionEntity(
                    label = trimmed,
                    categoryId = categoryId,
                    direction = if (direction == Direction.Income) Direction.Income else Direction.Expense,
                    sortOrder = quickActionDao.maxSortOrder() + 1,
                    archived = false,
                ),
            )
        }
    }

    override suspend fun updateContent(id: Long, label: String, categoryId: Long, direction: Int) {
        val trimmed = label.trim().take(BuiltInData.QUICK_ACTION_LABEL_MAX)
        if (trimmed.isEmpty() || categoryId <= 0L) return
        quickActionDao.updateContent(
            id = id,
            label = trimmed,
            categoryId = categoryId,
            direction = if (direction == Direction.Income) Direction.Income else Direction.Expense,
        )
    }

    override suspend fun setArchived(id: Long, archived: Boolean) {
        quickActionDao.setArchived(id, archived)
    }

    override suspend fun reorder(orderedIds: List<Long>) {
        database.withTransaction {
            orderedIds.forEachIndexed { index, id -> quickActionDao.setSortOrder(id, index) }
        }
    }

    override suspend fun countActive(): Int = quickActionDao.countActive()
}
