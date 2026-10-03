// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.navigation

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.noteone.app.core.design.AppColor
import com.noteone.app.core.design.Motion
import com.noteone.app.core.design.component.AppBottomBar
import com.noteone.app.core.design.component.AppRootScaffold
import com.noteone.app.feature.book.BookManageScreen
import com.noteone.app.feature.category.CategoryManageScreen
import com.noteone.app.feature.category.QuickActionManageScreen
import com.noteone.app.feature.ledger.LedgerScreen
import com.noteone.app.feature.onboarding.OnboardingScreen
import com.noteone.app.feature.onboarding.OnboardingState
import com.noteone.app.feature.record.RecordScreen
import com.noteone.app.feature.report.ReportScreen
import com.noteone.app.feature.settings.SettingsScreen
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 「重复点击当前 Tab」事件流。
 *
 * 规范 §4.2 要求：重复点当前 Tab 时把该页滚回顶部，而不是重建页面。
 * 页面自己决定要不要响应——用一个 `LazyListState` 收集这个流并
 * `animateScrollToItem(0)` 即可（B / C 用得上）。不收集也不会有什么副作用。
 */
object TabReselect {

    private val _events = MutableSharedFlow<Route>(extraBufferCapacity = 4)

    val events: SharedFlow<Route> = _events.asSharedFlow()

    fun emit(route: Route) {
        _events.tryEmit(route)
    }
}

/**
 * 应用外壳：首次启动的权限引导 + 底栏 + 导航图。
 *
 * 底栏只在 4 个顶级目的地显示；二级页（账本 / 分类 / 快捷按钮管理）不带底栏。
 * 首次启动时先整屏走一遍 [OnboardingScreen]，点「开始记账」后写标记放行，
 * 之后每次启动都直接进主界面（标记在 `OnboardingState`）。
 */
@Composable
fun AppRoot() {
    val context = LocalContext.current

    // 只在进程内缓存一次：标记写成功之后立刻放行。
    // 进程被杀后重建会重新读一次 SharedPreferences，读到的仍是 true。
    var onboardingFinished by remember { mutableStateOf(OnboardingState.isFinished(context)) }

    if (!onboardingFinished) {
        // 引导页自己也是「无底栏」形态，复用根脚手架把导航栏 inset 交给 AppScaffold。
        AppRootScaffold {
            OnboardingScreen(
                onFinish = {
                    OnboardingState.markFinished(context)
                    onboardingFinished = true
                },
            )
        }
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = Route.fromPath(backStackEntry?.destination?.route)
    val currentTab = currentRoute?.takeIf { Route.isTopLevel(it) }

    val bottomBar: (@Composable () -> Unit)? = if (currentTab != null) {
        {
            AppBottomBar(
                current = currentTab,
                onSelect = { target -> selectTab(navController, currentTab, target) },
            )
        }
    } else {
        null
    }

    AppRootScaffold(bottomBar = bottomBar) {
        AppNavHost(navController = navController)
    }
}

/**
 * 切换 Tab。**不做返回栈**：用 `saveState` / `restoreState` 保留各页自己的滚动位置，
 * 重复点当前 Tab 时改为发出「滚回顶部」事件。
 */
private fun selectTab(navController: NavHostController, current: Route, target: Route) {
    if (target == current) {
        TabReselect.emit(target)
        return
    }
    navController.navigate(target.path) {
        popUpTo(Route.Record.path) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * 页面容器：**每个目的地都自己铺一层不透明底色**。
 *
 * 转场期间（以及预测性返回跟手期间）NavHost 会同时可见两个目的地。
 * 目的地本身是透明的话，两页的文字会直接叠在一起——这是「返回时画面一团乱」的根因所在。
 * 底色统一放在这里、而不是交给各页面自己画，是为了以后新增页面不会再漏。
 */
@Composable
private fun PageSurface(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColor.Canvas),
    ) {
        content()
    }
}

/**
 * 导航图。
 *
 * 页面切换动效（规范 §3.7）：**横向「左右相接」**，250ms。
 *
 * ## 预测性返回为什么必须显式配置（本次修复的根因）
 *
 * 之前的写法只给了 `enterTransition / exitTransition / popEnterTransition / popExitTransition`，
 * 没有给 `predictivePopEnter` / `predictivePopExit`。而 `navigation-compose` 对「手势跟手阶段」
 * 有**另外一套默认值**（见 `DefaultNavTransitions`）：
 *
 * - 退出的当前页：`scaleOut(targetScale = 0.7f)` —— **整页等比缩到 70%，不淡出、仍然完全不透明**；
 * - 露出的前一页：`fadeIn(spring)` —— 原地淡入。
 *
 * 也就是说，从二级页侧滑返回时，屏幕上是「缩小后的当前页 + 原地的前一页」两张满屏文字叠在一起，
 * 而且两张都不透明，于是就成了「设置」上压着「账本管理」、「＋新建」浮在设置页中间的样子。
 * 这跟用户看到的「预测性返回特别抽象」完全一致。
 *
 * 现在把四项都显式给全，跟手阶段和正常返回走同一套规则。
 *
 * ## 为什么不再是原来的 `fade + translateY(12dp)`
 *
 * 1. 淡入淡出的本质是两页同时半透明地画在同一个位置。本项目每页都是满屏文字，
 *    两页一叠就是互相穿透的重影；
 * 2. 预测性返回是**横向**手势。纵向位移动效在跟手阶段和手指的方向完全对不上。
 *
 * 现在的做法：两张页面**等速反向**平移，接缝严丝合缝——
 * 既不会互相重叠（无重影），也不会露出后面的底色（无空白）；
 * 返回时被手指推走的就是当前页，前一页从左侧滑回来，方向与手势一致。
 *
 * 返回用的曲线是**线性**：跟手阶段位移与手指一一对应；
 * 前进用的仍是全站统一曲线 `Motion.Ease`。
 * 同一对 enter / exit 必须共用同一条曲线，否则两条边会错开、露出底色。
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val pushSlide: FiniteAnimationSpec<IntOffset> =
        tween(durationMillis = Motion.Normal, easing = Motion.Ease)
    val backSlide: FiniteAnimationSpec<IntOffset> =
        tween(durationMillis = Motion.Normal, easing = LinearEasing)

    NavHost(
        navController = navController,
        startDestination = Route.Record.path,
        modifier = modifier,
        // 前进：新页从右侧盖上来，当前页向左让位
        enterTransition = { slideInHorizontally(animationSpec = pushSlide) { it } },
        exitTransition = { slideOutHorizontally(animationSpec = pushSlide) { -it } },
        // 返回：当前页跟着手指向右滑走，前一页从左侧滑回来
        popEnterTransition = { slideInHorizontally(animationSpec = backSlide) { -it } },
        popExitTransition = { slideOutHorizontally(animationSpec = backSlide) { it } },
        // 预测性返回（手势跟手阶段）必须单独给一份：不给的话平台默认是
        // scaleOut(0.7) + fadeIn(spring) ——「当前页缩到 70% 原样压在前一页上」，
        // 两张不透明的满屏文字叠在一起就是这么来的。这里与 pop 用同一套规则，
        // 手势拖到哪一页、屏幕就走到哪一页。
        predictivePopEnterTransition = { slideInHorizontally(animationSpec = backSlide) { -it } },
        predictivePopExitTransition = { slideOutHorizontally(animationSpec = backSlide) { it } },
    ) {
        composable(Route.Record.path) {
            PageSurface {
                // B 模块追加：记账页需要跳到账本管理、分类管理。
                // 导航控制器归本文件所有，所以只能在这里把回调传下去（B 不改 core/ 与 navigation/）。
                // v1.6：首页去掉「查看全部 → 最近账单」与「快捷用途 → 编辑」两条入口，
                // 所以不再需要 onOpenLedger / onOpenQuickActionManage。
                RecordScreen(
                    onOpenBookManage = { navController.navigateSafely(Route.BookManage.path) },
                    onOpenCategoryManage = { navController.navigateSafely(Route.CategoryManage.path) },
                )
            }
        }
        composable(Route.Ledger.path) {
            PageSurface { LedgerScreen() }
        }
        composable(Route.Report.path) {
            PageSurface { ReportScreen() }
        }
        composable(Route.Settings.path) {
            PageSurface {
                SettingsScreen(
                    onOpenBookManage = { navController.navigateSafely(Route.BookManage.path) },
                    onOpenCategoryManage = { navController.navigateSafely(Route.CategoryManage.path) },
                    onOpenQuickActionManage = { navController.navigateSafely(Route.QuickActionManage.path) },
                )
            }
        }
        composable(Route.BookManage.path) {
            PageSurface { BookManageScreen(onBack = { navController.popBackStack() }) }
        }
        composable(Route.CategoryManage.path) {
            PageSurface { CategoryManageScreen(onBack = { navController.popBackStack() }) }
        }
        composable(Route.QuickActionManage.path) {
            PageSurface { QuickActionManageScreen(onBack = { navController.popBackStack() }) }
        }
    }
}

/** 二级页入口防连点：同一个目的地不会压入两次。 */
private fun NavHostController.navigateSafely(path: String) {
    navigate(path) { launchSingleTop = true }
}
