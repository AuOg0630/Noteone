// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.noteone.app.core.data.dao.BookDao
import com.noteone.app.core.data.dao.CategoryDao
import com.noteone.app.core.data.dao.QuickActionDao
import com.noteone.app.core.data.dao.TransactionDao
import com.noteone.app.core.data.entity.BookEntity
import com.noteone.app.core.data.entity.CategoryEntity
import com.noteone.app.core.data.entity.QuickActionEntity
import com.noteone.app.core.data.entity.TransactionEntity

/**
 * 本地数据库。
 *
 * 两条硬性约束（规范 §7.1 与 §13）：
 * 1. `exportSchema = true`，schema 落在 `app/schemas/`，**必须纳入版本控制**。
 * 2. **禁止 `fallbackToDestructiveMigration()`**。改表必须在 [Migrations] 里追加 `Migration`。
 *
 * DAO 全部 `internal`：跨模块只能通过 `core/data/repository` 下的 Repository 访问。
 */
@Database(
    entities = [
        BookEntity::class,
        TransactionEntity::class,
        CategoryEntity::class,
        QuickActionEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao

    abstract fun transactionDao(): TransactionDao

    abstract fun categoryDao(): CategoryDao

    abstract fun quickActionDao(): QuickActionDao

    companion object {
        const val NAME = "noteone.db"

        const val VERSION = 1

        /**
         * 迁移列表。当前是第一版，为空。
         *
         * 改表示例（**不要**用销毁式迁移）：
         * ```
         * private val MIGRATION_1_2 = object : Migration(1, 2) {
         *     override fun migrate(db: SupportSQLiteDatabase) {
         *         db.execSQL("ALTER TABLE transactions ADD COLUMN tag TEXT")
         *     }
         * }
         * // 然后 version = 2，Migrations += MIGRATION_1_2
         * ```
         */
        val Migrations: Array<Migration> = emptyArray()
    }
}
