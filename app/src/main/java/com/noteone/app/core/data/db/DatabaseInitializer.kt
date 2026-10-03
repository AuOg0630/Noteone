// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.data.db

import androidx.room.withTransaction
import com.noteone.app.core.data.entity.BookEntity
import com.noteone.app.core.data.entity.CategoryEntity
import com.noteone.app.core.data.entity.QuickActionEntity
import com.noteone.app.core.data.seed.BuiltInData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 首次启动初始化 / 清空数据后的重建。
 *
 * 写入三样东西：默认账本「我的账本」、13 个内置分类、8 个内置快捷按钮（规范 §7.3 / §7.4 / §7.5）。
 * 整个过程在一个事务里，任一步失败都不会留下半套数据。
 *
 * 幂等：已有数据时直接跳过，所以可以在每次启动时安全调用。
 */
@Singleton
class DatabaseInitializer @Inject constructor(
    private val database: AppDatabase,
) {

    /** 首次启动调用。已有数据时什么也不做（除了 §7.4 的那次内置快捷按钮升级）。 */
    suspend fun initializeIfNeeded() {
        database.withTransaction {
            val bookDao = database.bookDao()
            if (bookDao.countAll() == 0) {
                insertDefaultBook()
            }
            insertBuiltInCategoriesIfNeeded()
            insertBuiltInQuickActionsIfNeeded()
            upgradeBuiltInQuickActionsIfNeeded()
        }
    }

    /**
     * 清空数据后重建（设置页「清空所有数据」用，规范 §6.3）。
     * 调用前请确保四张表已清空。
     */
    suspend fun rebuildDefaults() {
        database.withTransaction {
            insertDefaultBook()
            insertBuiltInCategoriesIfNeeded()
            insertBuiltInQuickActionsIfNeeded()
        }
    }

    private suspend fun insertDefaultBook() {
        database.bookDao().insert(
            BookEntity(
                name = BuiltInData.DEFAULT_BOOK_NAME,
                colorKey = BuiltInData.DEFAULT_BOOK_COLOR,
                sortOrder = 0,
                isDefault = true,
                archived = false,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun insertBuiltInCategoriesIfNeeded() {
        val categoryDao = database.categoryDao()
        if (categoryDao.count() > 0) return

        var expenseOrder = 0
        var incomeOrder = 0
        val rows = BuiltInData.categories.map { seed ->
            val order = if (seed.direction == com.noteone.app.core.data.model.Direction.Income) {
                incomeOrder++
            } else {
                expenseOrder++
            }
            CategoryEntity(
                name = seed.name,
                direction = seed.direction,
                colorKey = seed.colorKey,
                sortOrder = order,
                isBuiltin = true,
                archived = false,
            )
        }
        categoryDao.insertAll(rows)
    }

    /**
     * v1.4：把「上一个版本原封不动的内置快捷按钮」换成新的一套。
     *
     * 老的一套是「早餐 / 午餐 / 晚餐 → 餐饮」这类写法：三个 chip 指向同一个分类，
     * 看着像三个分类，还白占两格（上限 8 个）。新的一套 label 与分类名一一对应。
     *
     * **判定条件故意很严：必须与老内置集合逐条一致（label + 指向的分类 + 方向 + 数量）。**
     * 用户自己动过任何一个按钮（改名 / 换分类 / 增删 / 排序），就整体不碰 ——
     * 升级内置默认值绝不能覆盖用户配置。
     */
    private suspend fun upgradeBuiltInQuickActionsIfNeeded() {
        val quickActionDao = database.quickActionDao()
        val categoryDao = database.categoryDao()
        val existing = quickActionDao.getAllIncludingArchived()
        if (existing.isEmpty()) return

        val legacy = BuiltInData.legacyQuickActions
        if (existing.size != legacy.size) return
        val isUntouched = legacy.all { seed ->
            val category = categoryDao.getByNameAndDirection(seed.categoryName, seed.direction)
            existing.any { row ->
                row.label == seed.label &&
                    row.categoryId == category?.id &&
                    row.direction == seed.direction &&
                    !row.archived
            }
        }
        if (!isUntouched) return

        quickActionDao.deleteAll()
        insertBuiltInQuickActions()
    }

    private suspend fun insertBuiltInQuickActionsIfNeeded() {
        val quickActionDao = database.quickActionDao()
        if (quickActionDao.count() > 0) return

        insertBuiltInQuickActions()
    }

    private suspend fun insertBuiltInQuickActions() {
        val quickActionDao = database.quickActionDao()
        val categoryDao = database.categoryDao()
        val rows = BuiltInData.quickActions.mapIndexedNotNull { index, seed ->
            val category = categoryDao.getByNameAndDirection(seed.categoryName, seed.direction)
                ?: return@mapIndexedNotNull null
            QuickActionEntity(
                label = seed.label,
                categoryId = category.id,
                direction = seed.direction,
                sortOrder = index,
                archived = false,
            )
        }
        if (rows.isNotEmpty()) {
            quickActionDao.upsertAll(rows)
        }
    }
}
