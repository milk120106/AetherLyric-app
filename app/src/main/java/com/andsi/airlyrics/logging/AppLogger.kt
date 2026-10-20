package com.andsi.airlyrics.logging

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * 全域執行日誌管理器。
 * 保留本次 App 啟動期間的關鍵執行事件（如權限變更、來源查詢、API 呼叫狀態與 Intent 攔截等），
 * 提供等級過濾、關鍵字搜尋、剪貼簿複製與 TXT 匯出功能。
 */
object AppLogger {
    private const val MAX_LOGS = 1000
    private val idCounter = AtomicLong(1L)
    private val logs = Collections.synchronizedList(ArrayList<LogEntry>())
    private val listeners = Collections.synchronizedList(ArrayList<(LogEntry) -> Unit>())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    enum class LogLevel {
        INFO, WARN, ERROR
    }

    data class LogEntry(
        val id: Long,
        val timestamp: Long,
        val level: LogLevel,
        val tag: String,
        val message: String
    ) {
        fun formattedTime(): String {
            return SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
        }

        fun fullFormattedString(): String {
            return "[${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))}] [${level.name}] [$tag] $message"
        }
    }

    fun i(tag: String, message: String) {
        log(LogLevel.INFO, tag, message)
        Log.i(tag, message)
    }

    fun w(tag: String, message: String) {
        log(LogLevel.WARN, tag, message)
        Log.w(tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val fullMsg = if (throwable != null) "$message\n${throwable.stackTraceToString()}" else message
        log(LogLevel.ERROR, tag, fullMsg)
        Log.e(tag, fullMsg, throwable)
    }

    private fun log(level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(
            id = idCounter.getAndIncrement(),
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message
        )
        synchronized(logs) {
            logs.add(entry)
            if (logs.size > MAX_LOGS) {
                logs.removeAt(0)
            }
        }
        synchronized(listeners) {
            listeners.forEach { listener ->
                try {
                    listener(entry)
                } catch (_: Exception) { }
            }
        }
    }

    fun getAll(): List<LogEntry> {
        return synchronized(logs) { ArrayList(logs) }
    }

    fun filter(query: String? = null, level: LogLevel? = null): List<LogEntry> {
        val current = getAll()
        val q = query?.trim()?.lowercase().orEmpty()
        return current.filter { entry ->
            val matchLevel = level == null || entry.level == level
            val matchQuery = q.isEmpty() || entry.tag.lowercase().contains(q) || entry.message.lowercase().contains(q)
            matchLevel && matchQuery
        }
    }

    fun clear() {
        synchronized(logs) {
            logs.clear()
        }
        i("AppLogger", "執行日誌已清除")
    }

    fun addListener(listener: (LogEntry) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (LogEntry) -> Unit) {
        listeners.remove(listener)
    }

    fun exportText(): String {
        val all = getAll()
        val sb = StringBuilder()
        sb.append("=== AetherLyric 執行日誌導出 (共 ${all.size} 筆) ===\n")
        sb.append("導出時間: ${dateFormat.format(Date())}\n\n")
        all.forEach {
            sb.append(it.fullFormattedString()).append("\n")
        }
        return sb.toString()
    }

    fun exportToFile(context: Context): Result<File> {
        return runCatching {
            val text = exportText()
            val fileName = "AetherLyric_log_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.txt"
            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
            val file = File(downloadDir, fileName)
            file.writeText(text, Charsets.UTF_8)
            file
        }
    }
}
