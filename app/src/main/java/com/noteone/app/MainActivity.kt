// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.noteone.app.core.design.AppTheme
import com.noteone.app.navigation.AppRoot
import dagger.hilt.android.AndroidEntryPoint

/**
 * 唯一的 Activity。
 *
 * 只做三件事：开启 edge-to-edge、套上 [AppTheme]、渲染 [AppRoot]。
 * 页面内容全部由 Compose 导航图决定。
 *
 * 系统栏固定为**深色图标**（`SystemBarStyle.light`），因为本项目只做浅色模式。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE),
        )

        setContent {
            AppTheme {
                AppRoot()
            }
        }
    }
}
