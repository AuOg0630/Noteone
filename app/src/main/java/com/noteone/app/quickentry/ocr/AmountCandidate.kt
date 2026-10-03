// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.quickentry.ocr

/**
 * OCR 出来的一行文本。
 *
 * @param text 该行的全部文字（ML Kit 的 `Text.Line.text`）
 * @param centerY 该行包围盒中心的 y 坐标，与调用方传入的参考高度同一坐标系。
 *                "靠屏幕中部优先"这条排序规则靠它实现，所以不能省。
 */
data class OcrLine(
    val text: String,
    val centerY: Int,
)

/**
 * 一个金额候选（规范 §8.5）。
 *
 * 字段与任务书 §6.2 一一对应，顺序不变——`AmountCandidateTest` 的断言直接按名字取。
 */
data class AmountCandidate(
    /** 归一化后的金额，单位「分」，恒为正。 */
    val cents: Long,
    /** 命中的原始文本，含货币符号 / 千分位 / 元字，用于展示与排查。 */
    val raw: String,
    /** 是否带 `¥` `￥` `$` `元` `圆`——排序的第一权重。 */
    val hasCurrencySymbol: Boolean,
    val hasDecimal: Boolean,
    /** 所在行在输入列表里的下标。 */
    val lineIndex: Int,
    val centerY: Int,
)
