// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.data.di

import com.noteone.app.core.data.repository.BackupRepository
import com.noteone.app.core.data.repository.BackupRepositoryImpl
import com.noteone.app.core.data.repository.BookRepository
import com.noteone.app.core.data.repository.BookRepositoryImpl
import com.noteone.app.core.data.repository.CategoryRepository
import com.noteone.app.core.data.repository.CategoryRepositoryImpl
import com.noteone.app.core.data.repository.QuickActionRepository
import com.noteone.app.core.data.repository.QuickActionRepositoryImpl
import com.noteone.app.core.data.repository.SettingsRepository
import com.noteone.app.core.data.repository.SettingsRepositoryImpl
import com.noteone.app.core.data.repository.TransactionRepository
import com.noteone.app.core.data.repository.TransactionRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Repository 接口 → 实现的绑定。
 *
 * **UI 层只注入接口**（`TransactionRepository` 等），永远不要注入 `*Impl`，
 * 更不要注入 DAO 或 DataStore。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindBookRepository(impl: BookRepositoryImpl): BookRepository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindQuickActionRepository(impl: QuickActionRepositoryImpl): QuickActionRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository
}
