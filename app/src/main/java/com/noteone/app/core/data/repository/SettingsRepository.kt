// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import com.noteone.app.core.data.dao.BookDao
import com.noteone.app.core.data.dao.CategoryDao
import com.noteone.app.core.data.model.Direction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 轻量偏好存储（DataStore Preferences）。
 *
 * key 与默认值严格照 `docs/开发规范.md` §7.2。
 */
interface SettingsRepository {

    /**
     * 当前账本 id。
     *
     * **自愈**：存储里的账本已被删除、或从未设置过时，会自动回退到默认账本并写回存储，
     * 所以调用方拿到的一定是一个真实存在的账本 id（除非一个账本都没有，此时为 0）。
     */
    val currentBookId: Flow<Long>

    /** 记账页默认方向，0 = 支出。 */
    val defaultDirection: Flow<Int>

    /** 记账页默认选中的分类，默认「餐饮」。 */
    val lastUsedCategoryId: Flow<Long>

    val budgetEnabled: Flow<Boolean>

    /** 预算金额（分）。 */
    val budgetCents: Flow<Long>

    /** OCR 候选排序是否优先带货币符号的数字，默认开。 */
    val preferCurrencySymbol: Flow<Boolean>

    /** 磁贴首次引导是否已完成。 */
    val tileFirstRunDone: Flow<Boolean>

    suspend fun setCurrentBookId(id: Long)

    suspend fun setDefaultDirection(direction: Int)

    suspend fun setLastUsedCategoryId(id: Long)

    suspend fun setBudgetEnabled(enabled: Boolean)

    suspend fun setBudgetCents(cents: Long)

    suspend fun setPreferCurrencySymbol(prefer: Boolean)

    suspend fun setTileFirstRunDone(done: Boolean)
}

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val bookDao: BookDao,
    private val categoryDao: CategoryDao,
) : SettingsRepository {

    override val currentBookId: Flow<Long> = dataStore.data
        .map { it[Keys.CurrentBookId] }
        .distinctUntilChanged()
        .map { stored -> resolveCurrentBookId(stored) }
        .distinctUntilChanged()

    override val defaultDirection: Flow<Int> = dataStore.data
        .map { it[Keys.DefaultDirection] ?: Direction.Expense }
        .distinctUntilChanged()

    /**
     * 记账页默认选中的分类。
     *
     * 未设置过、或原分类已被归档时，回退到支出侧第一个分类并写回——
     * 因为内置分类里「餐饮」的 `sortOrder` 是 0，这条回退规则正好实现规范 §7.2
     * 「`last_used_category_id` 默认餐饮」。
     */
    override val lastUsedCategoryId: Flow<Long> = dataStore.data
        .map { it[Keys.LastUsedCategoryId] }
        .distinctUntilChanged()
        .map { stored -> resolveLastUsedCategoryId(stored) }
        .distinctUntilChanged()

    override val budgetEnabled: Flow<Boolean> = dataStore.data
        .map { it[Keys.BudgetEnabled] ?: false }
        .distinctUntilChanged()

    override val budgetCents: Flow<Long> = dataStore.data
        .map { it[Keys.BudgetCents] ?: 0L }
        .distinctUntilChanged()

    override val preferCurrencySymbol: Flow<Boolean> = dataStore.data
        .map { it[Keys.PreferCurrencySymbol] ?: true }
        .distinctUntilChanged()

    override val tileFirstRunDone: Flow<Boolean> = dataStore.data
        .map { it[Keys.TileFirstRunDone] ?: false }
        .distinctUntilChanged()

    override suspend fun setCurrentBookId(id: Long) {
        dataStore.edit { it[Keys.CurrentBookId] = id }
    }

    override suspend fun setDefaultDirection(direction: Int) {
        dataStore.edit { it[Keys.DefaultDirection] = direction }
    }

    override suspend fun setLastUsedCategoryId(id: Long) {
        dataStore.edit { it[Keys.LastUsedCategoryId] = id }
    }

    override suspend fun setBudgetEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BudgetEnabled] = enabled }
    }

    override suspend fun setBudgetCents(cents: Long) {
        dataStore.edit { it[Keys.BudgetCents] = cents }
    }

    override suspend fun setPreferCurrencySymbol(prefer: Boolean) {
        dataStore.edit { it[Keys.PreferCurrencySymbol] = prefer }
    }

    override suspend fun setTileFirstRunDone(done: Boolean) {
        dataStore.edit { it[Keys.TileFirstRunDone] = done }
    }

    /** 存储值失效时回退到默认账本 / 第一个账本，并把回退结果写回，避免每次都重新计算。 */
    private suspend fun resolveCurrentBookId(stored: Long?): Long {
        if (stored != null && stored > 0L && bookDao.getById(stored) != null) return stored
        val fallback = bookDao.getDefault()?.id ?: bookDao.getFirst()?.id ?: 0L
        if (fallback > 0L && fallback != stored) {
            dataStore.edit { it[Keys.CurrentBookId] = fallback }
        }
        return fallback
    }

    /** 存储的分类已被归档 / 不存在时，回退到支出侧第一个分类。 */
    private suspend fun resolveLastUsedCategoryId(stored: Long?): Long {
        if (stored != null && stored > 0L) {
            val category = categoryDao.getById(stored)
            if (category != null && !category.archived) return stored
        }
        val fallback = categoryDao.firstForDirection(Direction.Expense)?.id ?: 0L
        if (fallback > 0L && fallback != stored) {
            dataStore.edit { it[Keys.LastUsedCategoryId] = fallback }
        }
        return fallback
    }

    private object Keys {
        val CurrentBookId = longPreferencesKey("current_book_id")
        val DefaultDirection = intPreferencesKey("default_direction")
        val LastUsedCategoryId = longPreferencesKey("last_used_category_id")
        val BudgetEnabled = booleanPreferencesKey("budget_enabled")
        val BudgetCents = longPreferencesKey("budget_cents")
        val PreferCurrencySymbol = booleanPreferencesKey("prefer_currency_symbol")
        val TileFirstRunDone = booleanPreferencesKey("tile_first_run_done")
    }
}
