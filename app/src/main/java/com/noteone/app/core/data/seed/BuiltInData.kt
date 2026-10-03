// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.data.seed

import com.noteone.app.core.data.model.CategoryScope
import com.noteone.app.core.data.model.Direction
import com.noteone.app.core.design.SemanticKeys

/**
 * 内置数据与业务上限。
 *
 * 这里是**唯一**的内置数据来源：首次启动写入用它，设置页「清空所有数据」后重建也用它。
 * 依据 `docs/开发规范.md` §7.3 / §7.4 / §7.5。
 */
object BuiltInData {

    // ------------------------------------------------------------------ 上限

    /** 账本数量上限。 */
    const val MAX_BOOKS = 10

    /** 快捷按钮数量上限。 */
    const val MAX_QUICK_ACTIONS = 8

    /** 快捷按钮 label 字数上限。 */
    const val QUICK_ACTION_LABEL_MAX = 6

    /** 备注字数上限（输入框硬限，超出不响应）。 */
    const val NOTE_MAX_LENGTH = 100

    /** 分类名 / 账本名字数上限。 */
    const val NAME_MAX_LENGTH = 10

    /** 软删除记录的保留天数，超过后由 `PurgeDeletedUseCase` 物理清除。 */
    const val SOFT_DELETE_RETENTION_DAYS = 7L

    /** 撤销窗口。 */
    const val UNDO_WINDOW_MS = 2500L

    // ------------------------------------------------------------------ 账本

    const val DEFAULT_BOOK_NAME = "我的账本"
    const val DEFAULT_BOOK_COLOR = SemanticKeys.Stone

    // ------------------------------------------------------------------ 分类

    data class SeedCategory(
        val name: String,
        /** 0 = 仅支出, 1 = 仅收入, 2 = 通用 */
        val direction: Int,
        val colorKey: String,
    )

    /** 8 个内置支出分类。 */
    val expenseCategories: List<SeedCategory> = listOf(
        SeedCategory("餐饮", CategoryScope.ExpenseOnly, SemanticKeys.Red),
        SeedCategory("交通", CategoryScope.ExpenseOnly, SemanticKeys.Blue),
        SeedCategory("购物", CategoryScope.ExpenseOnly, SemanticKeys.Yellow),
        SeedCategory("居住", CategoryScope.ExpenseOnly, SemanticKeys.Green),
        SeedCategory("娱乐", CategoryScope.ExpenseOnly, SemanticKeys.Purple),
        SeedCategory("学习", CategoryScope.ExpenseOnly, SemanticKeys.Clay),
        SeedCategory("医疗", CategoryScope.ExpenseOnly, SemanticKeys.Stone),
        SeedCategory("其他", CategoryScope.ExpenseOnly, SemanticKeys.Ink),
    )

    /** 5 个内置收入分类。 */
    val incomeCategories: List<SeedCategory> = listOf(
        SeedCategory("生活费", CategoryScope.IncomeOnly, SemanticKeys.Green),
        SeedCategory("兼职", CategoryScope.IncomeOnly, SemanticKeys.Blue),
        SeedCategory("奖学金", CategoryScope.IncomeOnly, SemanticKeys.Yellow),
        SeedCategory("红包", CategoryScope.IncomeOnly, SemanticKeys.Red),
        SeedCategory("其他", CategoryScope.IncomeOnly, SemanticKeys.Ink),
    )

    /** 全部 13 个内置分类。 */
    val categories: List<SeedCategory> get() = expenseCategories + incomeCategories

    // ------------------------------------------------------------------ 快捷按钮

    data class SeedQuickAction(
        val label: String,
        /** 指向内置分类的名字（同名时用 [direction] 区分） */
        val categoryName: String,
        val direction: Int,
    )

    /**
     * 8 个内置快捷按钮，正好达到上限。
     *
     * v1.4 起 **label 与分类名一一对应**。原来是「早餐 / 午餐 / 晚餐 → 餐饮」、
     * 「地铁公交 → 交通」这种写法：三个 chip 说的是同一个分类，看着像三个分类，
     * 还平白多占了两格（上限只有 8 个）。要更细的粒度（早餐 / 午餐）请自己在
     * 快捷按钮管理页加，默认值只求一眼看懂。
     */
    val quickActions: List<SeedQuickAction> = listOf(
        SeedQuickAction("餐饮", "餐饮", Direction.Expense),
        SeedQuickAction("交通", "交通", Direction.Expense),
        SeedQuickAction("购物", "购物", Direction.Expense),
        SeedQuickAction("居住", "居住", Direction.Expense),
        SeedQuickAction("娱乐", "娱乐", Direction.Expense),
        SeedQuickAction("学习", "学习", Direction.Expense),
        SeedQuickAction("医疗", "医疗", Direction.Expense),
        SeedQuickAction("生活费", "生活费", Direction.Income),
    )

    /**
     * v1.4 之前的 8 个内置快捷按钮。
     *
     * **只用来判断「老库里的快捷按钮还是不是原封不动的内置集合」**：
     * 完全一致才升级成 [quickActions]；用户自己动过任何一个就不碰（见 DatabaseInitializer）。
     */
    val legacyQuickActions: List<SeedQuickAction> = listOf(
        SeedQuickAction("早餐", "餐饮", Direction.Expense),
        SeedQuickAction("午餐", "餐饮", Direction.Expense),
        SeedQuickAction("晚餐", "餐饮", Direction.Expense),
        SeedQuickAction("地铁公交", "交通", Direction.Expense),
        SeedQuickAction("水果零食", "购物", Direction.Expense),
        SeedQuickAction("日用品", "购物", Direction.Expense),
        SeedQuickAction("打印复印", "学习", Direction.Expense),
        SeedQuickAction("生活费", "生活费", Direction.Income),
    )
}
