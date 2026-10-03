// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app

import android.app.Application
import com.noteone.app.core.data.db.DatabaseInitializer
import com.noteone.app.core.domain.PurgeDeletedUseCase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 应用入口。数据库单例与 DataStore 单例都由 Hilt 提供（规范 §2.3）。
 *
 * 启动时做两件一次性的事情，都放在 IO 线程，不阻塞首帧：
 * 1. 首次启动写入默认账本 + 13 个内置分类 + 8 个内置快捷按钮（幂等）；
 * 2. 物理清除 7 天前的软删除记录（`PurgeDeletedUseCase`）。
 */
@HiltAndroidApp
class NoteoneApp : Application() {

    @Inject
    lateinit var databaseInitializer: DatabaseInitializer

    @Inject
    lateinit var purgeDeletedUseCase: PurgeDeletedUseCase

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            databaseInitializer.initializeIfNeeded()
            purgeDeletedUseCase()
        }
    }
}
