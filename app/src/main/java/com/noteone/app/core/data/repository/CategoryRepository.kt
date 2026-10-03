// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.data.repository

import androidx.room.withTransaction
import com.noteone.app.core.data.dao.CategoryDao
import com.noteone.app.core.data.db.AppDatabase
import com.noteone.app.core.data.entity.CategoryEntity
import com.noteone.app.core.data.mapper.toCategories
import com.noteone.app.core.data.mapper.toCategory
import com.noteone.app.core.data.model.Category
import com.noteone.app.core.data.model.CategoryScope
import com.noteone.app.core.data.seed.BuiltInData
import com.noteone.app.core.design.SemanticColor
import com.noteone.app.core.design.SemanticKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 分类读写入口。
 *
 * **分类不做账本隔离**，全局共享（规范 §7.1 的关键设计决定）。
 *
 * 删除分类 = **归档**（`archived = true`），历史记录仍显示原分类名与色点，
 * 所以任何按 `categoryId` 回查分类的地方都必须用 [observeAll]（含归档），
 * 只有记账页的分类选择器才用 [observeActive]。
 */
interface CategoryRepository {

    /** **含已归档**。历史账单回查分类名与色点时必须用这个。 */
    fun observeAll(): Flow<List<Category>>

    /** 不含已归档。记账页 chip 行、分类选择器、管理页主列表用这个。 */
    fun observeActive(): Flow<List<Category>>

    fun observeArchived(): Flow<List<Category>>

    suspend fun getById(id: Long): Category?

    suspend fun getByIds(ids: List<Long>): List<Category>

    /** 指定方向上第一个可用分类（未归档），用于「默认选中餐饮」这类回退。 */
    suspend fun firstForDirection(direction: Int): Category?

    suspend fun add(name: String, direction: Int, colorKey: String): Long

    suspend fun rename(id: Long, name: String)

    suspend fun setColor(id: Long, colorKey: String)

    /** 归档 / 恢复。**内置分类可以归档，但不可物理删除。** */
    suspend fun setArchived(id: Long, archived: Boolean)

    /** 拖拽排序，传入同一方向上按新顺序排列的 id 列表。 */
    suspend fun reorder(orderedIds: List<Long>)

    suspend fun countActive(direction: Int): Int
}

@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val categoryDao: CategoryDao,
) : CategoryRepository {

    override fun observeAll(): Flow<List<Category>> =
        categoryDao.observeAllIncludingArchived().map { it.toCategories() }

    override fun observeActive(): Flow<List<Category>> =
        categoryDao.observeActive().map { it.toCategories() }

    override fun observeArchived(): Flow<List<Category>> =
        categoryDao.observeArchived().map { it.toCategories() }

    override suspend fun getById(id: Long): Category? = categoryDao.getById(id)?.toCategory()

    override suspend fun getByIds(ids: List<Long>): List<Category> {
        if (ids.isEmpty()) return emptyList()
        return categoryDao.getByIds(ids).toCategories()
    }

    override suspend fun firstForDirection(direction: Int): Category? =
        categoryDao.firstForDirection(direction)?.toCategory()

    override suspend fun add(name: String, direction: Int, colorKey: String): Long {
        val trimmed = name.trim().take(BuiltInData.NAME_MAX_LENGTH)
        require(trimmed.isNotEmpty()) { "分类名不能为空" }
        val safeDirection = when (direction) {
            CategoryScope.IncomeOnly -> CategoryScope.IncomeOnly
            CategoryScope.Both -> CategoryScope.Both
            else -> CategoryScope.ExpenseOnly
        }
        val color = if (SemanticColor.contains(colorKey)) colorKey else SemanticKeys.Ink
        // 取 sortOrder 与插入同事务，避免并发插入写出重复的 sortOrder
        return database.withTransaction {
            val sortBase = if (safeDirection == CategoryScope.IncomeOnly) {
                categoryDao.maxSortOrder(CategoryScope.IncomeOnly)
            } else {
                categoryDao.maxSortOrder(CategoryScope.ExpenseOnly)
            }
            categoryDao.insert(
                CategoryEntity(
                    name = trimmed,
                    direction = safeDirection,
                    colorKey = color,
                    sortOrder = sortBase + 1,
                    isBuiltin = false,
                    archived = false,
                ),
            )
        }
    }

    override suspend fun rename(id: Long, name: String) {
        val trimmed = name.trim().take(BuiltInData.NAME_MAX_LENGTH)
        if (trimmed.isEmpty()) return
        categoryDao.rename(id, trimmed)
    }

    override suspend fun setColor(id: Long, colorKey: String) {
        if (!SemanticColor.contains(colorKey)) return
        categoryDao.setColor(id, colorKey)
    }

    override suspend fun setArchived(id: Long, archived: Boolean) {
        categoryDao.setArchived(id, archived)
    }

    override suspend fun reorder(orderedIds: List<Long>) {
        database.withTransaction {
            orderedIds.forEachIndexed { index, id -> categoryDao.setSortOrder(id, index) }
        }
    }

    override suspend fun countActive(direction: Int): Int = categoryDao.countActiveForDirection(direction)
}
