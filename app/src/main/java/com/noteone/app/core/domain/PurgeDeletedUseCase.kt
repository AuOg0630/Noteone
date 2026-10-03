// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.domain

import com.noteone.app.core.data.repository.TransactionRepository
import com.noteone.app.core.data.repository.TransactionRepositoryImpl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 物理清除 7 天前的软删除记录。
 *
 * 规范 §7.1：删除一律软删除（`deletedAt`），撤销 = 清空 `deletedAt`，
 * **7 天后**由本用例物理清除。
 *
 * 在 `NoteoneApp.onCreate()` 的启动协程里跑一次即可，不需要周期任务。
 *
 * @return 本次清除的记录条数
 */
@Singleton
class PurgeDeletedUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
) {

    suspend operator fun invoke(
        nowMs: Long = System.currentTimeMillis(),
        retentionMillis: Long = TransactionRepositoryImpl.RETENTION_MILLIS,
    ): Int {
        val threshold = nowMs - retentionMillis
        return transactionRepository.purgeDeletedBefore(threshold)
    }
}
