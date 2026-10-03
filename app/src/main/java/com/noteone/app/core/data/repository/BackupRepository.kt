// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.data.repository

import androidx.room.withTransaction
import com.noteone.app.core.data.db.AppDatabase
import com.noteone.app.core.data.db.DatabaseInitializer
import com.noteone.app.core.data.mapper.toBooks
import com.noteone.app.core.data.mapper.toCategories
import com.noteone.app.core.data.mapper.toEntity
import com.noteone.app.core.data.mapper.toQuickActions
import com.noteone.app.core.data.mapper.toTransactions
import com.noteone.app.core.data.model.Book
import com.noteone.app.core.data.model.Category
import com.noteone.app.core.data.model.QuickAction
import com.noteone.app.core.data.model.Transaction
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全量数据快照。字段名与数据库列名一致，直接对应 `05_设置与管理页.md` §6.1 的 JSON 结构。
 *
 * 包含已软删除的记录（`deletedAt` 一并带出），保证备份是完整的。
 */
data class BackupSnapshot(
    val version: Int = BackupRepository.CURRENT_VERSION,
    val exportedAt: Long,
    val books: List<Book>,
    val categories: List<Category>,
    val quickActions: List<QuickAction>,
    val transactions: List<Transaction>,
)

/**
 * JSON 备份 / 恢复 / 清空。
 *
 * 序列化格式由 E 负责（手写 JSON，不引第三方库）；这里只负责把数据整体取出、
 * 整体写回，并保证**导入要么全成功要么全回滚**。
 */
interface BackupRepository {

    /** 导出全部数据（含软删除记录）。 */
    suspend fun snapshot(): BackupSnapshot

    /**
     * 覆盖式导入。整个过程在一个事务里：中途抛异常则全部回滚，现有数据不变。
     * 调用方必须先做二次确认。
     */
    suspend fun restore(snapshot: BackupSnapshot)

    /**
     * 清空所有数据并重建默认账本与内置分类 / 快捷按钮（规范 §6.3）。
     * 效果等同首次启动。
     */
    suspend fun clearAll()

    companion object {
        /** 当前支持的备份格式版本。更大的版本号来自更新版本，应拒绝导入。 */
        const val CURRENT_VERSION = 1
    }
}

@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val databaseInitializer: DatabaseInitializer,
    private val settingsRepository: SettingsRepository,
) : BackupRepository {

    override suspend fun snapshot(): BackupSnapshot = database.withTransaction {
        // 四次查询必须在**同一个事务**里：否则导出期间只要有一次并发写入
        // （启动期清理、磁贴/快捷面板插入、别处新建账本），就会做出
        // 「transactions.bookId 指向 books 里不存在的账本」这种不一致快照 ——
        // 这份备份将来导入时会撞上外键 RESTRICT 整体回滚，用户只看到「导入失败」。
        BackupSnapshot(
            exportedAt = System.currentTimeMillis(),
            books = database.bookDao().observeAllIncludingArchived().first().toBooks(),
            categories = database.categoryDao().observeAllIncludingArchived().first().toCategories(),
            quickActions = database.quickActionDao().observeAllIncludingArchived().first().toQuickActions(),
            transactions = database.transactionDao().getAllIncludingDeleted().toTransactions(),
        )
    }

    override suspend fun restore(snapshot: BackupSnapshot) {
        require(snapshot.version <= BackupRepository.CURRENT_VERSION) {
            "备份来自更新版本，无法导入"
        }
        database.withTransaction {
            // 先清空再写入，顺序必须是「子表 → 父表」，否则外键 RESTRICT 会拦下来
            database.transactionDao().deleteAll()
            database.quickActionDao().deleteAll()
            database.categoryDao().deleteAll()
            database.bookDao().deleteAll()

            // 恢复顺序必须是「父表 → 子表」
            database.bookDao().insertAll(snapshot.books.map { it.toEntity() })
            database.categoryDao().upsertAll(snapshot.categories.map { it.toEntity() })
            database.quickActionDao().upsertAll(snapshot.quickActions.map { it.toEntity() })
            database.transactionDao().insertAll(snapshot.transactions.map { it.toEntity() })

            // 一个账本都没有的备份（异常文件）→ 立刻补默认账本，避免 App 处于无账本状态
            if (database.bookDao().countAll() == 0) {
                databaseInitializer.initializeIfNeeded()
            }
        }
    }

    override suspend fun clearAll() {
        database.withTransaction {
            database.transactionDao().deleteAll()
            database.quickActionDao().deleteAll()
            database.categoryDao().deleteAll()
            database.bookDao().deleteAll()
            databaseInitializer.rebuildDefaults()
        }
        // 账本被清空重建后，指针要跟着指向新账本
        val newBookId = database.bookDao().getDefault()?.id ?: database.bookDao().getFirst()?.id ?: 0L
        settingsRepository.setCurrentBookId(newBookId)
        settingsRepository.setLastUsedCategoryId(0L)
    }
}
