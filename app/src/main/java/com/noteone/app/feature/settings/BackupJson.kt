// SPDX-FileCopyrightText: 2026 AuOg
// SPDX-License-Identifier: GPL-3.0-or-later
//
// This file is part of Noteone, licensed under the GNU General Public
// License v3.0 or later. See the LICENSE file for the full text.

package com.noteone.app.feature.settings

import com.noteone.app.core.common.DateFormats
import com.noteone.app.core.data.model.Book
import com.noteone.app.core.data.model.Category
import com.noteone.app.core.data.model.QuickAction
import com.noteone.app.core.data.model.Transaction
import com.noteone.app.core.data.repository.BackupRepository
import com.noteone.app.core.data.repository.BackupSnapshot

/**
 * JSON 备份的默认文件名：`记账备份_20260929_2130.json`（任务书 §6.1）。
 *
 * 与 C 的 `defaultCsvFileName()` 同样的命名规则，放在模块里而不是页面里，
 * 保证以后多个入口导出时命名一致。
 */
fun defaultBackupFileName(now: Long = System.currentTimeMillis()): String =
    "记账备份_" + DateFormats.fileStamp(now) + ".json"

/** 解析结果。三种失败必须能区分——文案与处理方式都不同（任务书 §6.2）。 */
sealed interface BackupParseResult {

    data class Success(val snapshot: BackupSnapshot) : BackupParseResult

    /** 文件不是合法 JSON，或缺少必需字段。**数据保持不变**。 */
    data object Corrupted : BackupParseResult

    /** 版本号大于当前支持的版本，来自更新版本的 App。 */
    data object UnsupportedVersion : BackupParseResult
}

/**
 * 备份文件的手写序列化（不引第三方库，规范 §2.1）。
 *
 * 结构照 `05_设置与管理页.md` §6.1：
 * ```
 * { "version":1, "exportedAt":…, "books":[…], "categories":[…],
 *   "quickActions":[…], "transactions":[…] }
 * ```
 * 字段名与数据库列名逐字一致，`deletedAt` 也一并导出（备份必须是完整的）。
 *
 * **为什么自己写 JSON**：`org.json` 在 Android 运行时可用，但在 JVM 单测里是桩实现
 * （调用即抛 `Stub!`），而这段逻辑恰恰是最需要单测的部分——版本判定、转义、损坏文件识别。
 * 自己写一个只覆盖本项目格式的解析器，换来的是**可测**与**零依赖**。
 *
 * 解码是**严格模式**：任何一行缺必需字段就整体判定为损坏，不做部分导入。
 * 部分导入会让「恢复」变成半个烂摊子，比直接报错难查得多。
 */
object BackupJson {

    /** 当前支持的备份格式版本，跟随 `BackupRepository.CURRENT_VERSION`。 */
    const val SUPPORTED_VERSION: Int = BackupRepository.CURRENT_VERSION

    // ------------------------------------------------------------------ 编码

    fun encode(snapshot: BackupSnapshot): String = buildString {
        append('{')
        append("\"version\":").append(snapshot.version).append(',')
        append("\"exportedAt\":").append(snapshot.exportedAt).append(',')
        append("\"books\":")
        append(snapshot.books.joinToString(",", "[", "]") { bookJson(it) })
        append(',')
        append("\"categories\":")
        append(snapshot.categories.joinToString(",", "[", "]") { categoryJson(it) })
        append(',')
        append("\"quickActions\":")
        append(snapshot.quickActions.joinToString(",", "[", "]") { quickActionJson(it) })
        append(',')
        append("\"transactions\":")
        append(snapshot.transactions.joinToString(",", "[", "]") { transactionJson(it) })
        append('}')
    }

    private fun bookJson(book: Book): String = buildString {
        append('{')
        append("\"id\":").append(book.id).append(',')
        append("\"name\":").append(string(book.name)).append(',')
        append("\"colorKey\":").append(string(book.colorKey)).append(',')
        append("\"sortOrder\":").append(book.sortOrder).append(',')
        append("\"isDefault\":").append(book.isDefault).append(',')
        append("\"archived\":").append(book.archived).append(',')
        append("\"createdAt\":").append(book.createdAt)
        append('}')
    }

    private fun categoryJson(category: Category): String = buildString {
        append('{')
        append("\"id\":").append(category.id).append(',')
        append("\"name\":").append(string(category.name)).append(',')
        append("\"direction\":").append(category.direction).append(',')
        append("\"colorKey\":").append(string(category.colorKey)).append(',')
        append("\"sortOrder\":").append(category.sortOrder).append(',')
        append("\"isBuiltin\":").append(category.isBuiltin).append(',')
        append("\"archived\":").append(category.archived)
        append('}')
    }

    private fun quickActionJson(action: QuickAction): String = buildString {
        append('{')
        append("\"id\":").append(action.id).append(',')
        append("\"label\":").append(string(action.label)).append(',')
        append("\"categoryId\":").append(action.categoryId).append(',')
        append("\"direction\":").append(action.direction).append(',')
        append("\"sortOrder\":").append(action.sortOrder).append(',')
        append("\"archived\":").append(action.archived)
        append('}')
    }

    private fun transactionJson(transaction: Transaction): String = buildString {
        append('{')
        append("\"id\":").append(transaction.id).append(',')
        append("\"bookId\":").append(transaction.bookId).append(',')
        append("\"amountCents\":").append(transaction.amountCents).append(',')
        append("\"direction\":").append(transaction.direction).append(',')
        append("\"categoryId\":").append(transaction.categoryId ?: "null").append(',')
        append("\"note\":").append(string(transaction.note)).append(',')
        append("\"occurredAt\":").append(transaction.occurredAt).append(',')
        append("\"createdAt\":").append(transaction.createdAt).append(',')
        append("\"updatedAt\":").append(transaction.updatedAt).append(',')
        append("\"source\":").append(transaction.source).append(',')
        append("\"currency\":").append(string(transaction.currency)).append(',')
        append("\"deletedAt\":").append(transaction.deletedAt ?: "null").append(',')
        append("\"recognizedText\":").append(
            transaction.recognizedText?.let { string(it) } ?: "null",
        )
        append('}')
    }

    /** JSON 字符串字面量：转义 `"` `\` 与控制字符，其余（含中文）原样输出。 */
    private fun string(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (char < ' ') append("\\u%04x".format(char.code)) else append(char)
            }
        }
        append('"')
    }

    // ------------------------------------------------------------------ 解码

    fun decode(text: String): BackupParseResult {
        val cleaned = text.trimStart('\uFEFF')
        val root = runCatching { JsonReader(cleaned).readDocument() }.getOrNull()
            ?: return BackupParseResult.Corrupted
        val rootMap = root as? Map<*, *> ?: return BackupParseResult.Corrupted

        val version = rootMap.long("version")?.toInt() ?: return BackupParseResult.Corrupted
        if (version < 1) return BackupParseResult.Corrupted
        if (version > SUPPORTED_VERSION) return BackupParseResult.UnsupportedVersion

        val exportedAt = rootMap.long("exportedAt") ?: return BackupParseResult.Corrupted
        val books = rootMap.list("books") { toBook(it) } ?: return BackupParseResult.Corrupted
        val categories = rootMap.list("categories") { toCategory(it) } ?: return BackupParseResult.Corrupted
        val quickActions = rootMap.list("quickActions") { toQuickAction(it) } ?: return BackupParseResult.Corrupted
        val transactions = rootMap.list("transactions") { toTransaction(it) }
            ?: return BackupParseResult.Corrupted

        return BackupParseResult.Success(
            BackupSnapshot(
                version = version,
                exportedAt = exportedAt,
                books = books,
                categories = categories,
                quickActions = quickActions,
                transactions = transactions,
            ),
        )
    }

    // ------------------------------------------------------------------ 行解析

    private fun toBook(row: Map<*, *>): Book? = Book(
        id = row.long("id") ?: return null,
        name = row.text("name") ?: return null,
        colorKey = row.text("colorKey") ?: return null,
        sortOrder = row.long("sortOrder")?.toInt() ?: return null,
        isDefault = row.flag("isDefault") ?: return null,
        archived = row.flag("archived") ?: false,
        createdAt = row.long("createdAt") ?: return null,
    )

    private fun toCategory(row: Map<*, *>): Category? = Category(
        id = row.long("id") ?: return null,
        name = row.text("name") ?: return null,
        direction = row.long("direction")?.toInt() ?: return null,
        colorKey = row.text("colorKey") ?: return null,
        sortOrder = row.long("sortOrder")?.toInt() ?: return null,
        isBuiltin = row.flag("isBuiltin") ?: return null,
        archived = row.flag("archived") ?: false,
    )

    private fun toQuickAction(row: Map<*, *>): QuickAction? = QuickAction(
        id = row.long("id") ?: return null,
        label = row.text("label") ?: return null,
        categoryId = row.long("categoryId") ?: return null,
        direction = row.long("direction")?.toInt() ?: return null,
        sortOrder = row.long("sortOrder")?.toInt() ?: return null,
        archived = row.flag("archived") ?: false,
    )

    /**
     * 记录行。`Transaction` 的模型层对 `createdAt` / `updatedAt` / `currency` /
     * `recognizedText` 有默认值，所以这几项允许缺失——老版本导出的文件也能导回来。
     */
    private fun toTransaction(row: Map<*, *>): Transaction? = Transaction(
        id = row.long("id") ?: return null,
        bookId = row.long("bookId") ?: return null,
        amountCents = row.long("amountCents") ?: return null,
        direction = row.long("direction")?.toInt() ?: return null,
        categoryId = row.nullableLong("categoryId"),
        note = row.text("note") ?: "",
        occurredAt = row.long("occurredAt") ?: return null,
        source = row.long("source")?.toInt() ?: return null,
        deletedAt = row.nullableLong("deletedAt"),
        createdAt = row.long("createdAt") ?: 0L,
        updatedAt = row.long("updatedAt") ?: 0L,
        currency = row.text("currency") ?: "CNY",
        recognizedText = row.text("recognizedText"),
    )

    // ------------------------------------------------------------------ 取值助手

    /** 字段存在但类型不对时返回 null（视为损坏），字段不存在也返回 null。 */
    private fun <T> Map<*, *>.list(key: String, transform: (Map<*, *>) -> T?): List<T>? {
        val raw = this[key] as? List<*> ?: return null
        val result = ArrayList<T>(raw.size)
        for (item in raw) {
            val map = item as? Map<*, *> ?: return null
            val converted = transform(map) ?: return null
            result.add(converted)
        }
        return result
    }

    /**
     * 只接受**整数**。
     *
     * [JsonReader.readNumber] 对「小数」和「超出 Long 精度」的整数都会退回 Double；
     * 老写法直接 `toLong()` 会把 `1.5`、`12345678901234567890` 静默截断/饱和成合法 Long
     * 照常导入（id、amountCents、时间戳全在受害范围内）。这里让它们走到「类型不符 → 文件已损坏」。
     */
    private fun Map<*, *>.long(key: String): Long? = when (val value = this[key]) {
        is Long -> value
        is Double -> value.takeIf {
            it % 1.0 == 0.0 && it >= Long.MIN_VALUE.toDouble() && it <= Long.MAX_VALUE.toDouble()
        }?.toLong()

        else -> null
    }

    private fun Map<*, *>.nullableLong(key: String): Long? =
        if (this[key] == null) null else long(key)

    private fun Map<*, *>.text(key: String): String? = this[key] as? String

    private fun Map<*, *>.flag(key: String): Boolean? = this[key] as? Boolean
}

/**
 * 只覆盖本项目备份格式的极简 JSON 读取器。
 *
 * 支持：对象、数组、字符串（含 `\uXXXX` 转义）、整数、小数、布尔、`null`。
 * 遇到任何语法问题直接抛异常，由调用方统一转成「文件已损坏」。
 */
private class JsonReader(private val text: String) {

    private var position = 0

    fun readDocument(): Any? {
        val value = readValue()
        skipWhitespace()
        if (position != text.length) fail()
        return value
    }

    private fun readValue(): Any? {
        skipWhitespace()
        if (position >= text.length) fail()
        return when (val char = text[position]) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> readString()
            't' -> readKeyword("true", true)
            'f' -> readKeyword("false", false)
            'n' -> readKeyword("null", null)
            else -> if (char == '-' || char.isDigit()) readNumber() else fail()
        }
    }

    private fun readObject(): Map<String, Any?> {
        expect('{')
        val result = LinkedHashMap<String, Any?>()
        skipWhitespace()
        if (peek() == '}') {
            position++
            return result
        }
        while (true) {
            skipWhitespace()
            val key = readString()
            skipWhitespace()
            expect(':')
            result[key] = readValue()
            skipWhitespace()
            when (peek()) {
                ',' -> position++
                '}' -> {
                    position++
                    return result
                }
                else -> fail()
            }
        }
    }

    private fun readArray(): List<Any?> {
        expect('[')
        val result = ArrayList<Any?>()
        skipWhitespace()
        if (peek() == ']') {
            position++
            return result
        }
        while (true) {
            result.add(readValue())
            skipWhitespace()
            when (peek()) {
                ',' -> position++
                ']' -> {
                    position++
                    return result
                }
                else -> fail()
            }
        }
    }

    private fun readString(): String {
        expect('"')
        val builder = StringBuilder()
        while (true) {
            if (position >= text.length) fail()
            when (val char = text[position++]) {
                '"' -> return builder.toString()
                '\\' -> builder.append(readEscape())
                else -> builder.append(char)
            }
        }
    }

    private fun readEscape(): Char {
        if (position >= text.length) fail()
        return when (val char = text[position++]) {
            '"' -> '"'
            '\\' -> '\\'
            '/' -> '/'
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                if (position + 4 > text.length) fail()
                val hex = text.substring(position, position + 4)
                position += 4
                hex.toIntOrNull(16)?.toChar() ?: fail()
            }
            else -> fail()
        }
    }

    private fun readNumber(): Any {
        val start = position
        if (peek() == '-') position++
        var isDecimal = false
        while (position < text.length) {
            val char = text[position]
            if (char.isDigit()) {
                position++
            } else if (char == '.' || char == 'e' || char == 'E' || char == '+' || char == '-') {
                isDecimal = isDecimal || char == '.' || char == 'e' || char == 'E'
                position++
            } else {
                break
            }
        }
        val raw = text.substring(start, position)
        if (raw.isEmpty()) fail()
        if (!isDecimal) {
            raw.toLongOrNull()?.let { return it }
            // 超出 Long 精度时退回 Double：导出的 id / 时间戳不可能这么大，
            // 但损坏文件里可能出现，让它走到「类型不符 → 损坏」而不是崩溃
            raw.toDoubleOrNull()?.let { return it }
        } else {
            raw.toDoubleOrNull()?.let { return it }
        }
        fail()
    }

    private fun <T> readKeyword(word: String, value: T): T {
        if (!text.startsWith(word, position)) fail()
        position += word.length
        return value
    }

    private fun skipWhitespace() {
        while (position < text.length && text[position].isWhitespace()) position++
    }

    private fun peek(): Char {
        if (position >= text.length) fail()
        return text[position]
    }

    private fun expect(char: Char): Unit {
        if (position >= text.length || text[position] != char) fail()
        position++
    }

    private fun fail(): Nothing = throw IllegalArgumentException("非法的 JSON：位置 $position")
}
