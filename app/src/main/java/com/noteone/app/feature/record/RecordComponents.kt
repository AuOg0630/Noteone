// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.feature.record

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.noteone.app.R
import com.noteone.app.core.data.model.Category
import com.noteone.app.core.data.model.Direction
import com.noteone.app.core.data.model.Transaction
import com.noteone.app.core.design.AppColor
import com.noteone.app.core.design.AppTheme
import com.noteone.app.core.design.AppType
import com.noteone.app.core.design.Dimension
import com.noteone.app.core.design.Motion
import com.noteone.app.core.design.SemanticColor
import com.noteone.app.core.design.SemanticKeys
import com.noteone.app.core.design.Space
import com.noteone.app.core.design.component.MoneySign
import com.noteone.app.core.design.component.MoneyText
import com.noteone.app.core.design.icon.AppIcons
import kotlinx.coroutines.delay

/** 超过这个位置之后的列表项不再做进入动效，避免滚动到深处时整屏都在动。 */
private const val MAX_STAGGER_INDEX = 8

/**
 * 列表项进入动效：`fade + translateY(8dp → 0)`，逐项 stagger 60ms（规范 §3.7）。
 *
 * 只动 opacity 与 transform，不动 width / height。记账页与账单页共用。
 */
@Composable
internal fun ListItemEnter(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (index >= MAX_STAGGER_INDEX) {
        Box(modifier = modifier) { content() }
        return
    }
    val enterOffsetPx = with(LocalDensity.current) { Motion.ListEnterOffset.toPx() }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * Motion.StaggerStep.toLong())
        progress.animateTo(1f, tween(Motion.Normal, easing = Motion.Ease))
    }
    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress.value
            translationY = enterOffsetPx * (1f - progress.value)
        },
    ) {
        content()
    }
}

/**
 * 记账页固定头部（高 44dp，无底部边框）。
 *
 * 左：当前账本名 17sp Medium + 右向小三角，点击弹账本切换弹层。
 * 右：本月结余小字（结余为负时用砖红深色，正时保持 `Muted`）。
 *
 * 这里是全页**唯一**保留的月度数字：首页只做记账，支出 / 收入 / 结余的完整
 * 视图在「汇总」Tab，最近账单在「账单」Tab。
 */
@Composable
internal fun RecordHeaderRow(
    bookName: String,
    balanceCents: Long,
    onSwitchBook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimension.HeaderRow),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clickable(onClick = onSwitchBook),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = bookName, style = AppType.SectionTitle, color = AppColor.Ink)
            Box(modifier = Modifier.width(Space.XS))
            Icon(
                painter = painterResource(id = AppIcons.CaretDown),
                contentDescription = stringResource(R.string.record_header_switch_book),
                tint = AppColor.Muted,
                modifier = Modifier.size(12.dp),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.record_header_month_balance),
                style = AppType.Caption,
                color = AppColor.Muted,
            )
            Box(modifier = Modifier.width(Space.XS))
            MoneyText(
                cents = balanceCents,
                style = AppType.Caption,
                sign = MoneySign.Auto,
                color = if (balanceCents < 0) {
                    SemanticColor.of(SemanticKeys.Red).fg
                } else {
                    AppColor.Muted
                },
            )
        }
    }
}

/** 分类色点：8dp 圆。用语义色的**深色字色**，浅底色在白底上看不见。 */
@Composable
internal fun CategoryDot(colorKey: String?) {
    Box(
        modifier = Modifier
            .size(Dimension.ColorDot)
            .clip(CircleShape)
            .background(SemanticColor.of(colorKey).fg),
    )
}

/**
 * 记录金额：支出前缀 `-`、收入前缀 `+`（规范 §3.4）。
 *
 * 库里 `amountCents` 恒为正数，方向由 `direction` 决定，所以符号必须由这里补上。
 */
@Composable
internal fun TransactionAmount(
    transaction: Transaction,
    style: TextStyle = AppType.AmountRow,
    modifier: Modifier = Modifier,
) {
    MoneyText(
        cents = transaction.amountCents,
        style = style,
        sign = if (transaction.direction == Direction.Income) MoneySign.Plus else MoneySign.Minus,
        modifier = modifier,
    )
}

// ---------------------------------------------------------------------- Preview

@Preview(name = "记账页 / 固定头部", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun RecordHeaderPreview() {
    AppTheme {
        Column(
            modifier = Modifier.padding(Space.L),
            verticalArrangement = Arrangement.spacedBy(Space.L),
        ) {
            RecordHeaderRow(bookName = "我的账本", balanceCents = 108600, onSwitchBook = {})
            RecordHeaderRow(bookName = "旅行账本", balanceCents = -4300, onSwitchBook = {})
        }
    }
}
