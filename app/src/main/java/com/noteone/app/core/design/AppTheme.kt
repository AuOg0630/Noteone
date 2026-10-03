// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * 全站主题。
 *
 * 设计要点：
 * 1. **只有浅色模式**，不跟随系统深色（用户明确要求，且本文件只定义浅色 token）。
 * 2. M3 的配色被显式覆盖为 [AppColor] 的中性色，避免默认紫色泄漏到 `Switch` /
 *    `DatePicker` / `TextField` / 涟漪等系统组件上。这是最容易漏、也最容易被验收打回的一条。
 * 3. 只借用 M3 的组件骨架，视觉全部由 [AppColor] / [AppType] / [Space] / [Radius] 决定。
 *
 * 系统栏（状态栏 / 导航栏）的透明与深色图标在 `MainActivity` 里通过
 * `enableEdgeToEdge()` 一次性设置，不在这里做。
 */
private val AppColorScheme = lightColorScheme(
    primary = AppColor.Ink,
    onPrimary = Color.White,
    primaryContainer = AppColor.Ink,
    onPrimaryContainer = Color.White,
    secondary = AppColor.InkSecondary,
    onSecondary = Color.White,
    secondaryContainer = AppColor.Surface,
    onSecondaryContainer = AppColor.Ink,
    tertiary = AppColor.Muted,
    onTertiary = Color.White,
    background = AppColor.Canvas,
    onBackground = AppColor.Ink,
    surface = AppColor.SurfaceCard,
    onSurface = AppColor.Ink,
    surfaceVariant = AppColor.Surface,
    onSurfaceVariant = AppColor.Muted,
    surfaceContainer = AppColor.Surface,
    surfaceContainerHigh = AppColor.Surface,
    surfaceContainerHighest = AppColor.Surface,
    surfaceContainerLow = AppColor.CanvasWarm,
    surfaceContainerLowest = AppColor.Canvas,
    outline = AppColor.Line,
    outlineVariant = AppColor.Line,
    error = AppColor.Ink,
    onError = Color.White,
    errorContainer = Color(0xFFFDEBEC),
    onErrorContainer = Color(0xFF9F2F2D),
    scrim = AppColor.Scrim,
    inverseSurface = AppColor.Ink,
    inverseOnSurface = Color.White,
    inversePrimary = Color.White,
    surfaceTint = Color.Transparent,
)

private val AppTypography = Typography(
    displayLarge = AppType.PageTitle,
    headlineLarge = AppType.PageTitle,
    headlineMedium = AppType.PageTitle,
    headlineSmall = AppType.SectionTitle,
    titleLarge = AppType.SectionTitle,
    titleMedium = AppType.SectionTitle,
    titleSmall = AppType.Body,
    bodyLarge = AppType.Body,
    bodyMedium = AppType.Body,
    bodySmall = AppType.Caption,
    labelLarge = AppType.Button,
    labelMedium = AppType.Label,
    labelSmall = AppType.Label,
)

/** 默认文字样式：不指定 `style` 的 `Text` 走正文，避免落到 M3 的默认值。 */
private val DefaultTextStyle: TextStyle = AppType.Body

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        content = { ProvideTextStyle(DefaultTextStyle, content) },
    )
}
