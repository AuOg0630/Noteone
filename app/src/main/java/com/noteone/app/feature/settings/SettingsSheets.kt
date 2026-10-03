// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

// `AppBottomSheet` 的签名里有 M3 的 `SheetState`（实验 API），所以调用方也要 opt-in。
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.noteone.app.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noteone.app.R
import com.noteone.app.core.common.Money
import com.noteone.app.core.data.model.Direction
import com.noteone.app.core.design.AppColor
import com.noteone.app.core.design.AppType
import com.noteone.app.core.design.Dimension
import com.noteone.app.core.design.Space
import com.noteone.app.core.design.component.AmountKeypad
import com.noteone.app.core.design.component.AppBottomSheet
import com.noteone.app.core.design.component.Divider
import com.noteone.app.core.design.component.MoneySign
import com.noteone.app.core.design.component.MoneyText
import com.noteone.app.core.design.component.PrimaryButton
import com.noteone.app.core.design.component.SecondaryButton
import com.noteone.app.core.design.icon.AppIcons
import com.noteone.app.core.domain.AmountInputRules
import com.noteone.app.feature.manage.NameField

/** 默认方向二选一（任务书 §2.3）。 */
@Composable
fun DirectionSheet(
    currentDirection: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        Direction.Expense to stringResource(R.string.common_expense),
        Direction.Income to stringResource(R.string.common_income),
    )
    AppBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.settings_default_direction),
            style = AppType.SectionTitle,
            color = AppColor.Ink,
        )
        Box(modifier = Modifier.height(Space.S))
        options.forEachIndexed { index, (direction, label) ->
            if (index > 0) Divider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimension.TouchMin)
                    .clickable { onSelect(direction) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = label, style = AppType.Body, color = AppColor.Ink)
                if (direction == currentDirection) {
                    Icon(
                        painter = painterResource(id = AppIcons.Check),
                        contentDescription = null,
                        tint = AppColor.Ink,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/**
 * 预算金额输入（任务书 §2.3）。
 *
 * 直接复用记账页的自绘键盘——金额输入全项目只有这一套组件，不给预算另写一个。
 * 键盘自带的「完成」就是确认，不再另放按钮（两个「完成」并排会让人不知道点哪个）。
 */
@Composable
fun BudgetAmountSheet(
    initialCents: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember {
        mutableStateOf(if (initialCents > 0L) Money.centsToPlain(initialCents) else "")
    }
    val cents = AmountInputRules.toCents(text)

    AppBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.settings_budget_edit_title),
            style = AppType.SectionTitle,
            color = AppColor.Ink,
        )
        Box(modifier = Modifier.height(Space.M))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            MoneyText(
                cents = cents,
                style = AppType.AmountXL,
                sign = MoneySign.None,
                color = if (text.isEmpty()) AppColor.Faint else AppColor.Ink,
            )
        }
        Box(modifier = Modifier.height(Space.M))
        AmountKeypad(
            onDigit = { text = AmountInputRules.accept(text, it) },
            onBackspace = { text = AmountInputRules.backspace(text) },
            onDone = { onConfirm(AmountInputRules.toCents(text)) },
            doneEnabled = AmountInputRules.isDoneEnabled(text),
        )
    }
}

/** 从备份恢复的二次确认（任务书 §6.2）。**必须先确认，再动数据。** */
@Composable
fun RestoreConfirmSheet(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.settings_restore_confirm_title),
            style = AppType.SectionTitle,
            color = AppColor.Ink,
        )
        Box(modifier = Modifier.height(Space.S))
        Text(
            text = stringResource(R.string.settings_restore_confirm_body),
            style = AppType.Body,
            color = AppColor.Muted,
        )
        Box(modifier = Modifier.height(Space.XL))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.M)) {
            SecondaryButton(
                text = stringResource(R.string.settings_cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = stringResource(R.string.settings_restore_confirm_ok),
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 清空所有数据的二次确认（任务书 §6.3）。
 *
 * 两级门槛：先看清后果，再**手打「清空」两个字**才点亮确认按钮。
 * 这样不会有任何一次误触能把数据抹掉。
 */
@Composable
fun ClearDataSheet(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val confirmWord = stringResource(R.string.settings_clear_confirm_word)
    var input by remember { mutableStateOf("") }

    AppBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.settings_clear_confirm_title),
            style = AppType.SectionTitle,
            color = AppColor.Ink,
        )
        Box(modifier = Modifier.height(Space.S))
        Text(
            text = stringResource(R.string.settings_clear_confirm_body),
            style = AppType.Body,
            color = AppColor.Muted,
        )
        Box(modifier = Modifier.height(Space.L))
        NameField(
            value = input,
            onValueChange = { input = it },
            hint = stringResource(R.string.settings_clear_confirm_hint),
            maxLength = confirmWord.length,
        )
        Box(modifier = Modifier.height(Space.XL))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.M)) {
            SecondaryButton(
                text = stringResource(R.string.settings_cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = stringResource(R.string.settings_clear_all),
                onClick = onConfirm,
                enabled = input.trim() == confirmWord,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
