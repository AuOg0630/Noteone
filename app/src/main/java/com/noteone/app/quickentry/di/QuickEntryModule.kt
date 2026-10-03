// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.quickentry.di

import com.noteone.app.core.data.repository.BookRepository
import com.noteone.app.core.data.repository.CategoryRepository
import com.noteone.app.core.data.repository.QuickActionRepository
import com.noteone.app.core.data.repository.SettingsRepository
import com.noteone.app.core.data.repository.TransactionRepository
import com.noteone.app.quickentry.ocr.MlKitOcrClient
import com.noteone.app.quickentry.ocr.OcrClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * D 模块用到的数据入口集合。
 *
 * 为什么打成一包：`QuickEntryActivity` 的透明窗口、框选悬浮层、结果面板都在
 * Activity 之外自己管窗口，**没有 Hilt 管理的 ViewModel**，注入点只有 Activity 一个。
 * 打成一包后注入 1 个字段就够，也让"D 只依赖 core 的 Repository 接口"这件事一目了然。
 */
@Singleton
class QuickEntryRepositories @Inject constructor(
    val books: BookRepository,
    val categories: CategoryRepository,
    val quickActions: QuickActionRepository,
    val transactions: TransactionRepository,
    val settings: SettingsRepository,
)

/** D 模块的 Hilt 绑定。只绑一个接口，不引入任何新的第三方依赖。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class QuickEntryModule {

    @Binds
    abstract fun bindOcrClient(impl: MlKitOcrClient): OcrClient
}
