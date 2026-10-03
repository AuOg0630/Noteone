// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.feature.ledger

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.noteone.app.R
import com.noteone.app.core.data.model.Category
import com.noteone.app.core.data.model.Transaction
import com.noteone.app.core.design.AppColor
import com.noteone.app.core.design.AppType
import com.noteone.app.core.design.Dimension
import com.noteone.app.core.design.Motion
import com.noteone.app.core.design.SemanticColor
import com.noteone.app.core.design.SemanticKeys
import com.noteone.app.core.design.Space
import com.noteone.app.core.design.component.MoneySign
import com.noteone.app.core.design.component.MoneyText
import com.noteone.app.core.design.component.TextToggleRow
import com.noteone.app.core.design.icon.AppIcons
import com.noteone.app.feature.record.CategoryDot
import com.noteone.app.feature.record.TransactionAmount
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 左滑露出的删除区宽度。够放下「删除」与一个舒服的热区。 */
private val RevealWidth = 88.dp

/** 松手触发删除的阈值：滑出超过 40%（规范 §5.2）。 */
private const val SWIPE_TRIGGER_RATIO = 0.4f

/**
 * 账单页顶部栏（高 44dp）：`‹` 年月 `›` 与搜索。
 *
 * 搜索态下整条变成输入框，按**备注关键词 + 金额区间**过滤（规范 §5.2）。
 */
@Composable
internal fun LedgerTopBar(
    monthTitle: String,
    searchActive: Boolean,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    nextEnabled: Boolean,
    onOpenMonthPicker: () -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimension.HeaderRow),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (searchActive) {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            BasicTextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                textStyle = AppType.Body.copy(color = AppColor.Ink),
                singleLine = true,
                cursorBrush = SolidColor(AppColor.Ink),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = stringResource(R.string.ledger_search_hint),
                                style = AppType.Body,
                                color = AppColor.Faint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            IconAction(
                iconRes = AppIcons.Close,
                contentDescription = stringResource(R.string.ledger_search_close),
                onClick = onCloseSearch,
            )
            return@Row
        }

        IconAction(
            iconRes = AppIcons.ArrowLeft,
            contentDescription = stringResource(R.string.ledger_prev_month),
            onClick = onPreviousMonth,
        )
        Text(
            text = monthTitle,
            style = AppType.SectionTitle,
            color = AppColor.Ink,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clickable(
                    onClickLabel = stringResource(R.string.ledger_open_month_picker),
                    onClick = onOpenMonthPicker,
                )
                .padding(horizontal = Space.S),
        )
        IconAction(
            iconRes = AppIcons.ArrowRight,
            contentDescription = stringResource(R.string.ledger_next_month),
            enabled = nextEnabled,
            onClick = onNextMonth,
        )
        Box(modifier = Modifier.weight(1f))
        IconAction(
            iconRes = AppIcons.Search,
            contentDescription = stringResource(R.string.ledger_search),
            onClick = onOpenSearch,
        )
    }
}

/** 「全部 / 支出 / 收入」三态筛选。视觉与记账页的方向切换完全一致（复用 `TextToggleRow`）。 */
@Composable
internal fun LedgerFilterRow(
    filter: LedgerFilter,
    onFilterChange: (LedgerFilter) -> Unit,
) {
    TextToggleRow(
        labels = listOf(
            stringResource(R.string.ledger_filter_all),
            stringResource(R.string.common_expense),
            stringResource(R.string.common_income),
        ),
        selectedIndex = filter.ordinal,
        onSelect = { index -> onFilterChange(LedgerFilter.entries[index]) },
    )
}

/**
 * 粘性组头：`9 月 29 日 · 支出 104.00`（13sp `Muted`，背景 `#FFFFFF`）。
 *
 * 筛选态会自动调整小计项：筛「收入」时只显示收入小计；「全部」下两项都有则都显示。
 */
@Composable
internal fun LedgerDayHeader(
    dateLabel: String,
    expenseCents: Long,
    incomeCents: Long,
) {
    val expenseLabel = stringResource(R.string.common_expense)
    val incomeLabel = stringResource(R.string.common_income)
    val parts = buildList {
        if (expenseCents > 0L || incomeCents == 0L) add(expenseLabel to expenseCents)
        if (incomeCents > 0L) add(incomeLabel to incomeCents)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColor.Canvas)
            .padding(vertical = Space.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = dateLabel, style = AppType.Caption, color = AppColor.Muted)
        parts.forEach { (label, cents) ->
            Box(modifier = Modifier.width(Space.XS))
            Text(text = "·", style = AppType.Caption, color = AppColor.Muted)
            Box(modifier = Modifier.width(Space.XS))
            Text(text = label, style = AppType.Caption, color = AppColor.Muted)
            Box(modifier = Modifier.width(Space.XS))
            MoneyText(
                cents = cents,
                style = AppType.Caption,
                sign = MoneySign.None,
                color = AppColor.Muted,
            )
        }
    }
}

/** 账单条目：高 56dp，分类色点 + 分类名 + 备注（截断）+ 金额。 */
@Composable
internal fun LedgerTransactionRow(
    transaction: Transaction,
    category: Category?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimension.ListRow)
            .background(AppColor.Canvas)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryDot(colorKey = category?.colorKey)
        Box(modifier = Modifier.width(Space.M))
        Text(
            text = category?.name ?: stringResource(R.string.record_uncategorized),
            style = AppType.Body,
            color = AppColor.Ink,
            maxLines = 1,
        )
        Box(modifier = Modifier.width(Space.M))
        Text(
            text = transaction.note,
            style = AppType.Caption,
            color = AppColor.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(modifier = Modifier.width(Space.M))
        TransactionAmount(transaction = transaction)
    }
}

/**
 * 左滑露出删除区。
 *
 * - 删除区背景 `#FFFFFF`、左侧 1dp 分隔线、文字「删除」15sp 砖红深色
 * - **滑出超过 40% 松手即删**，未超过回弹
 * - 只动 `transform`（`offset`），不碰 width / height
 *
 * 不做多选批量删除（用户已明确否定）。
 */
@Composable
internal fun SwipeToDeleteRow(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val revealPx = with(LocalDensity.current) { RevealWidth.toPx() }
    val thresholdPx = revealPx * SWIPE_TRIGGER_RATIO
    var offsetX by remember { mutableFloatStateOf(0f) }
    val deleteLabel = stringResource(R.string.ledger_delete)
    val deleteColor = SemanticColor.of(SemanticKeys.Red).fg

    Box(modifier = modifier.fillMaxWidth()) {
        // 底层：删除区。只有一个带 1dp 左侧分隔线的白底文字按钮。
        Row(
            modifier = Modifier.matchParentSize(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(Dimension.Divider)
                    .fillMaxHeight()
                    .background(AppColor.Line),
            )
            Box(
                modifier = Modifier
                    .width(RevealWidth - Dimension.Divider)
                    .fillMaxHeight()
                    .background(AppColor.Canvas)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = deleteLabel, style = AppType.Body, color = deleteColor)
            }
        }

        // 前景：真实内容，跟着手指左移
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount).coerceIn(-revealPx, 0f)
                        },
                        onDragEnd = { settle(scope, offsetX, thresholdPx, onDelete) { offsetX = it } },
                        onDragCancel = { settle(scope, offsetX, 0f, null) { offsetX = it } },
                    )
                },
        ) {
            content()
        }
    }
}

/** 松手后的收尾：超过阈值就删除，否则回弹到 0。 */
private fun settle(
    scope: kotlinx.coroutines.CoroutineScope,
    current: Float,
    thresholdPx: Float,
    onDelete: (() -> Unit)?,
    apply: (Float) -> Unit,
) {
    if (onDelete != null && -current >= thresholdPx) onDelete()
    scope.launch {
        animate(
            initialValue = current,
            targetValue = 0f,
            animationSpec = tween(Motion.Normal, easing = Motion.Ease),
        ) { value, _ -> apply(value) }
    }
}

/** 20dp 图标 + 48dp 热区的统一小按钮。`enabled = false` 时降为 `Faint`。 */
@Composable
private fun IconAction(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = if (enabled) AppColor.Muted else AppColor.Faint,
            modifier = Modifier.size(20.dp),
        )
    }
}
