// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.feature.settings

import com.noteone.app.core.data.model.Book
import com.noteone.app.core.data.model.Category
import com.noteone.app.core.data.model.Direction
import com.noteone.app.core.data.model.QuickAction
import com.noteone.app.core.data.model.Transaction
import com.noteone.app.core.data.model.TransactionSource
import com.noteone.app.core.data.repository.BackupRepository
import com.noteone.app.core.data.repository.BackupSnapshot
import com.noteone.app.core.design.SemanticKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JSON 备份的序列化测试。
 *
 * 重点覆盖四类最容易做错的地方：
 * 1. 转义（备注里可能有引号、逗号、换行）；
 * 2. `null` 字段（未分类的 `categoryId`、未删除的 `deletedAt`、没识别到文本的 `recognizedText`）；
 * 3. 软删除记录必须一起导出（备份是完整的，否则恢复会丢「还能撤销的删除」）；
 * 4. 版本判定与损坏识别——这两种失败的处理方式完全不同（拒绝导入 vs 提示文件损坏）。
 */
class BackupJsonTest {

    private val books = listOf(
        Book(1L, "我的账本", SemanticKeys.Stone, 0, isDefault = true, archived = false, createdAt = 1759_000_000_000L),
        Book(2L, "旅行", SemanticKeys.Blue, 1, isDefault = false, archived = false, createdAt = 1759_100_000_000L),
    )

    private val categories = listOf(
        Category(1L, "餐饮", 0, SemanticKeys.Red, 0, isBuiltin = true, archived = false),
        Category(2L, "交通", 0, SemanticKeys.Blue, 1, isBuiltin = true, archived = true),
    )

    private val quickActions = listOf(
        QuickAction(1L, "午餐", 1L, Direction.Expense, 0, archived = false),
        QuickAction(2L, "生活费", 9L, Direction.Income, 1, archived = false),
    )

    private val transactions = listOf(
        Transaction(
            id = 1L,
            bookId = 1L,
            amountCents = 1_400L,
            direction = Direction.Expense,
            categoryId = 1L,
            note = "楼下便利店",
            occurredAt = 1759_150_000_000L,
            source = TransactionSource.Manual,
            deletedAt = null,
            createdAt = 1759_150_000_000L,
            updatedAt = 1759_150_000_000L,
            currency = "CNY",
            recognizedText = null,
        ),
        Transaction(
            id = 2L,
            bookId = 1L,
            amountCents = 12_345_678L,
            direction = Direction.Income,
            categoryId = null,
            // 引号、逗号、反斜杠、换行全都要能原样回来
            note = "奖学金 \"一等\" \\ 分段\n第二行,含逗号",
            occurredAt = 1759_160_000_000L,
            source = TransactionSource.TileOcr,
            deletedAt = 1759_200_000_000L,
            createdAt = 1759_160_000_000L,
            updatedAt = 1759_170_000_000L,
            currency = "CNY",
            recognizedText = "实付 ¥12,345.68",
        ),
    )

    private fun snapshot(version: Int = BackupJson.SUPPORTED_VERSION) = BackupSnapshot(
        version = version,
        exportedAt = 1759_150_000_000L,
        books = books,
        categories = categories,
        quickActions = quickActions,
        transactions = transactions,
    )

    private fun decodedSnapshot(text: String): BackupSnapshot {
        val result = BackupJson.decode(text)
        assertTrue("应当解析成功，实际为 $result", result is BackupParseResult.Success)
        return (result as BackupParseResult.Success).snapshot
    }

    // ------------------------------------------------------------------ 正常路径

    @Test
    fun `编码后再解码内容完全一致`() {
        val original = snapshot()
        assertEquals(original, decodedSnapshot(BackupJson.encode(original)))
    }

    @Test
    fun `软删除记录与 null 字段都能原样带回来`() {
        val decoded = decodedSnapshot(BackupJson.encode(snapshot()))

        val deleted = decoded.transactions.first { it.id == 2L }
        assertEquals(1759_200_000_000L, deleted.deletedAt)
        assertEquals(null, deleted.categoryId)
        assertEquals("实付 ¥12,345.68", deleted.recognizedText)

        val alive = decoded.transactions.first { it.id == 1L }
        assertEquals(null, alive.deletedAt)
        assertEquals(1L, alive.categoryId)
    }

    @Test
    fun `含引号逗号反斜杠换行的备注不会破坏结构`() {
        val decoded = decodedSnapshot(BackupJson.encode(snapshot()))
        assertEquals(
            "奖学金 \"一等\" \\ 分段\n第二行,含逗号",
            decoded.transactions.first { it.id == 2L }.note,
        )
    }

    @Test
    fun `字段名与数据库列名一致`() {
        val text = BackupJson.encode(snapshot())
        listOf(
            "version", "exportedAt", "books", "categories", "quickActions", "transactions",
            "colorKey", "isDefault", "sortOrder", "amountCents", "occurredAt", "updatedAt",
            "deletedAt", "recognizedText", "categoryId", "isBuiltin", "currency", "label",
        ).forEach { key ->
            assertTrue("缺少字段 $key", text.contains("\"$key\""))
        }
    }

    @Test
    fun `空集合是合法备份`() {
        val empty = BackupSnapshot(
            version = BackupJson.SUPPORTED_VERSION,
            exportedAt = 0L,
            books = emptyList(),
            categories = emptyList(),
            quickActions = emptyList(),
            transactions = emptyList(),
        )
        val decoded = decodedSnapshot(BackupJson.encode(empty))
        assertTrue(decoded.books.isEmpty() && decoded.transactions.isEmpty())
    }

    @Test
    fun `允许带 BOM 的文件`() {
        val text = "\uFEFF" + BackupJson.encode(snapshot())
        assertEquals(snapshot(), decodedSnapshot(text))
    }

    // ------------------------------------------------------------------ 失败路径

    @Test
    fun `版本高于当前支持版本时拒绝导入`() {
        val text = BackupJson.encode(snapshot(version = BackupJson.SUPPORTED_VERSION + 1))
        assertEquals(BackupParseResult.UnsupportedVersion, BackupJson.decode(text))
    }

    @Test
    fun `版本号非法视为损坏`() {
        assertTrue(BackupJson.decode("""{"version":0,"exportedAt":1,"books":[],"categories":[],"quickActions":[],"transactions":[]}""") is BackupParseResult.Corrupted)
        assertTrue(BackupJson.decode("""{"version":"1","exportedAt":1,"books":[],"categories":[],"quickActions":[],"transactions":[]}""") is BackupParseResult.Corrupted)
    }

    @Test
    fun `不是 JSON 的文件视为损坏`() {
        assertTrue(BackupJson.decode("这是一张图片，不是备份").let { it is BackupParseResult.Corrupted })
        assertTrue(BackupJson.decode("").let { it is BackupParseResult.Corrupted })
        assertTrue(BackupJson.decode("{}").let { it is BackupParseResult.Corrupted })
    }

    @Test
    fun `被截断的文件视为损坏`() {
        val text = BackupJson.encode(snapshot())
        val truncated = text.substring(0, text.length / 2)
        assertTrue(BackupJson.decode(truncated) is BackupParseResult.Corrupted)
    }

    @Test
    fun `缺必需字段的行视为损坏`() {
        val text = """
            {"version":1,"exportedAt":1,
             "books":[{"id":1,"colorKey":"stone","sortOrder":0,"isDefault":true,"archived":false,"createdAt":1}],
             "categories":[],"quickActions":[],"transactions":[]}
        """.trimIndent()
        assertTrue(BackupJson.decode(text) is BackupParseResult.Corrupted)
    }

    @Test
    fun `字段类型不对视为损坏`() {
        val text = """
            {"version":1,"exportedAt":1,
             "books":[{"id":1,"name":"我的账本","colorKey":"stone","sortOrder":"0","isDefault":true,"archived":false,"createdAt":1}],
             "categories":[],"quickActions":[],"transactions":[]}
        """.trimIndent()
        assertTrue(BackupJson.decode(text) is BackupParseResult.Corrupted)
    }

    @Test
    fun `整数不会退化成浮点`() {
        val decoded = decodedSnapshot(BackupJson.encode(snapshot()))
        assertEquals(12_345_678L, decoded.transactions.first { it.id == 2L }.amountCents)
        assertEquals(1759_150_000_000L, decoded.exportedAt)
    }

    // ------------------------------------------------------------------ 文件名

    @Test
    fun `备份文件名带时间戳与 json 后缀`() {
        val name = defaultBackupFileName(1759_150_000_000L)
        assertTrue("实际为 $name", Regex("""^记账备份_\d{8}_\d{4}\.json$""").matches(name))
    }

    @Test
    fun `当前支持的版本号来自 BackUpRepository`() {
        assertEquals(BackupRepository.CURRENT_VERSION, BackupJson.SUPPORTED_VERSION)
    }
}
