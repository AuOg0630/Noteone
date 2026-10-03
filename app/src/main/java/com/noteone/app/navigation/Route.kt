// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.navigation

/**
 * 全部导航目的地。
 *
 * 前 4 个是顶级目的地（带底栏、切 Tab 不做返回栈）；
 * 后 3 个是二级页面（从设置页进入、**不带底栏**、左上角返回）。
 *
 * 路径字符串一旦冻结不要改，否则 `AppNavHost` 与底栏会同时失联。
 */
sealed class Route(val path: String) {

    data object Record : Route("record")

    data object Ledger : Route("ledger")

    data object Report : Route("report")

    data object Settings : Route("settings")

    data object BookManage : Route("book_manage")

    data object CategoryManage : Route("category_manage")

    data object QuickActionManage : Route("quick_action_manage")

    companion object {

        /** 带底栏的顶级目的地，顺序＝底栏从左到右的顺序。 */
        val topLevel: List<Route> = listOf(Record, Ledger, Report, Settings)

        /** 二级页面，不带底栏。 */
        val secondary: List<Route> = listOf(BookManage, CategoryManage, QuickActionManage)

        fun isTopLevel(route: Route): Boolean = topLevel.contains(route)

        fun isTopLevelPath(path: String?): Boolean = topLevel.any { it.path == path }

        fun fromPath(path: String?): Route? = (topLevel + secondary).firstOrNull { it.path == path }
    }
}
