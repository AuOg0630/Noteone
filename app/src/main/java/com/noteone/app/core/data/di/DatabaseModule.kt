// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.noteone.app.core.data.dao.BookDao
import com.noteone.app.core.data.dao.CategoryDao
import com.noteone.app.core.data.dao.QuickActionDao
import com.noteone.app.core.data.dao.TransactionDao
import com.noteone.app.core.data.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/** 数据库与 DataStore 的单例提供。数据库单例、DataStore 单例都由 Hilt 产出（规范 §2.3）。 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            // 禁止 fallbackToDestructiveMigration：改表必须写 Migration
            .addMigrations(*AppDatabase.Migrations)
            .build()

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { context.preferencesDataStoreFile(SETTINGS_DATASTORE_NAME) },
        )

    // DAO 由这里统一提供，注入到 Repository 实现里。
    // 约定：**只有 core/data/repository/ 下的实现可以注入 DAO**，
    // feature/* 与 navigation/ 一律通过 Repository 接口访问。

    @Provides
    fun provideBookDao(database: AppDatabase): BookDao = database.bookDao()

    @Provides
    fun provideTransactionDao(database: AppDatabase): TransactionDao = database.transactionDao()

    @Provides
    fun provideCategoryDao(database: AppDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideQuickActionDao(database: AppDatabase): QuickActionDao = database.quickActionDao()

    private const val SETTINGS_DATASTORE_NAME = "settings"
}
