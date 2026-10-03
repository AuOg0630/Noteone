// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.core.design

import androidx.compose.ui.graphics.Color

/**
 * 一组语义色的「浅底 + 同色相深字」。
 *
 * 用途仅限：chip 底色与文字色、列表色点、统计条形、负结余数字。
 * **禁止**用于大面积背景、卡片底色、按钮底色（依据 `docs/开发规范.md` §3.2）。
 */
data class SemanticPair(val bg: Color, val fg: Color)

/**
 * 8 个语义色板。
 *
 * `red / blue / green / yellow` 来自 SKILL.md 第 4 章；
 * `purple / clay / stone / ink` 是按同样规则做的色板延伸，用于让 8 个以上分类可区分。
 */
object SemanticColor {

    private val palette: Map<String, SemanticPair> = mapOf(
        SemanticKeys.Red to SemanticPair(Color(0xFFFDEBEC), Color(0xFF9F2F2D)),
        SemanticKeys.Blue to SemanticPair(Color(0xFFE1F3FE), Color(0xFF1F6C9F)),
        SemanticKeys.Green to SemanticPair(Color(0xFFEDF3EC), Color(0xFF346538)),
        SemanticKeys.Yellow to SemanticPair(Color(0xFFFBF3DB), Color(0xFF956400)),
        SemanticKeys.Purple to SemanticPair(Color(0xFFF0EDFB), Color(0xFF52469B)),
        SemanticKeys.Clay to SemanticPair(Color(0xFFFBEEE4), Color(0xFF8A5220)),
        SemanticKeys.Stone to SemanticPair(Color(0xFFF1EFE8), Color(0xFF5F5E5A)),
        SemanticKeys.Ink to SemanticPair(Color(0xFFEAEAEA), Color(0xFF2F3437)),
    )

    /** 全部 8 个 key，顺序固定，供色板选择器使用。 */
    val keys: List<String> = listOf(
        SemanticKeys.Red,
        SemanticKeys.Blue,
        SemanticKeys.Green,
        SemanticKeys.Yellow,
        SemanticKeys.Purple,
        SemanticKeys.Clay,
        SemanticKeys.Stone,
        SemanticKeys.Ink,
    )

    /** 未知 key 回退到 ink，保证脏数据不会导致崩溃或出现规范外的颜色。 */
    fun of(key: String?): SemanticPair = palette[key] ?: palette.getValue(SemanticKeys.Ink)

    /** 只有确实存在的 key 才返回 true，数据库写入前可用来校验。 */
    fun contains(key: String?): Boolean = key != null && palette.containsKey(key)
}

/** 语义色 key 常量。数据库里存的是这些字符串。 */
object SemanticKeys {
    const val Red = "red"
    const val Blue = "blue"
    const val Green = "green"
    const val Yellow = "yellow"
    const val Purple = "purple"
    const val Clay = "clay"
    const val Stone = "stone"
    const val Ink = "ink"
}
