package com.andsi.airlyrics.lyrics.storage

import android.content.Context
import android.os.Environment
import com.andsi.airlyrics.lyrics.parser.LrcParser
import com.andsi.airlyrics.lyrics.parser.TtmlParser
import com.andsi.airlyrics.lyrics.parser.TtmlParseResult
import com.andsi.airlyrics.lyrics.parser.lrcToTtml
import com.andsi.airlyrics.lyrics.parser.toPlainLrc
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 歌詞匯出格式定義與轉換工具：
 * 提供四種匯出選項：
 * 1. 標準 LRC
 * 2. 雙語 LRC
 * 3. 純文字 TXT
 * 4. TTML (雙語/逐字)
 */
enum class LyricsExportFormat(val displayTitle: String, val extension: String) {
    STANDARD_LRC("標準 LRC", "lrc"),
    BILINGUAL_LRC("雙語 LRC", "lrc"),
    PLAIN_TXT("純文字 TXT", "txt"),
    TTML("TTML (雙語/逐字)", "ttml")
}

object LyricsExportFormatter {
    private val lineTimestampRegex = Regex("""^(\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?\])+""")
    private val allTimestampRegex = Regex("""\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?\]|<\d{1,3}:\d{2}(?:[.:]\d{1,3})?>""")
    private val metaTagRegex = Regex("""^\[(ti|ar|al|by|offset|length|re|ve):.*\]$""", RegexOption.IGNORE_CASE)

    fun formatLyrics(rawContent: String, format: LyricsExportFormat): String {
        return when (format) {
            LyricsExportFormat.TTML -> {
                if (rawContent.contains("<tt", ignoreCase = true)) {
                    rawContent.trim()
                } else {
                    lrcToTtml(rawContent)
                }
            }
            LyricsExportFormat.BILINGUAL_LRC -> {
                if (rawContent.contains("<tt", ignoreCase = true)) {
                    when (val res = TtmlParser.parse(rawContent)) {
                        is TtmlParseResult.Success -> res.document.toPlainLrc()
                        else -> rawContent.trim()
                    }
                } else {
                    rawContent.trim()
                }
            }
            LyricsExportFormat.STANDARD_LRC -> {
                val baseLrc = if (rawContent.contains("<tt", ignoreCase = true)) {
                    when (val res = TtmlParser.parse(rawContent)) {
                        is TtmlParseResult.Success -> res.document.toPlainLrc()
                        else -> rawContent.trim()
                    }
                } else {
                    rawContent
                }
                val seenTimestamps = HashSet<String>()
                val out = mutableListOf<String>()
                for (line in baseLrc.lines()) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) continue
                    val match = lineTimestampRegex.find(trimmed)
                    if (match != null) {
                        val tsPrefix = match.value
                        if (!seenTimestamps.add(tsPrefix)) {
                            // 相同時間戳的第二行視為翻譯行，於標準 LRC（僅原文）模式略過
                            continue
                        }
                        val body = trimmed.substring(tsPrefix.length).trim()
                        val originalOnlyBody = if (body.contains(" / ")) {
                            body.substringBefore(" / ").trim()
                        } else {
                            body
                        }
                        out.add("$tsPrefix$originalOnlyBody")
                    } else {
                        out.add(trimmed)
                    }
                }
                out.joinToString("\n").trim()
            }
            LyricsExportFormat.PLAIN_TXT -> {
                val baseLrc = if (rawContent.contains("<tt", ignoreCase = true)) {
                    when (val res = TtmlParser.parse(rawContent)) {
                        is TtmlParseResult.Success -> res.document.toPlainLrc()
                        else -> rawContent.trim()
                    }
                } else {
                    rawContent
                }
                val out = mutableListOf<String>()
                for (line in baseLrc.lines()) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || metaTagRegex.matches(trimmed)) continue
                    val stripped = allTimestampRegex.replace(trimmed, "").trim()
                    if (stripped.isNotEmpty()) {
                        out.add(stripped)
                    }
                }
                out.joinToString("\n").trim()
            }
        }
    }

    fun exportSingleSong(
        context: Context,
        title: String,
        artist: String,
        rawContent: String,
        format: LyricsExportFormat
    ): File {
        val formatted = formatLyrics(rawContent, format)
        val safeName = "${title.ifBlank { "未知歌曲" }}_${artist.ifBlank { "未知演出者" }}"
            .replace(Regex("""[\\/:*?"<>|\s]+"""), "_")
            .take(64)
        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
        if (!downloadDir.exists()) downloadDir.mkdirs()
        val suffix = when (format) {
            LyricsExportFormat.STANDARD_LRC -> "standard"
            LyricsExportFormat.BILINGUAL_LRC -> "bilingual"
            LyricsExportFormat.PLAIN_TXT -> "plain"
            LyricsExportFormat.TTML -> "ttml"
        }
        val exportFile = File(downloadDir, "AetherLyric_${safeName}_${suffix}.${format.extension}")
        exportFile.writeText(formatted, Charsets.UTF_8)
        return exportFile
    }

    fun formatBatchContent(
        context: Context,
        items: List<LyricsStorage.LocalLyricsItem>,
        format: LyricsExportFormat
    ): String {
        val sb = StringBuilder()
        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        sb.append("=== AetherLyric 本地歌詞匯出庫 (${format.displayTitle}，共 ${items.size} 首) ===\n")
        sb.append("匯出時間: $nowStr\n\n")

        items.forEach { item ->
            val rawText = LyricsStorage.readLocalLyricsItemText(
                context,
                item,
                LyricsStorage.LocalLyricsEditTarget.PLAIN
            ).orEmpty()
            val formatted = formatLyrics(rawText, format)
            sb.append("----------------------------------------\n")
            sb.append("歌曲: ${item.title} - ${item.artist}\n")
            sb.append("檔案: ${item.name}\n")
            sb.append("內容:\n$formatted\n\n")
        }
        return sb.toString()
    }

    fun exportBatchSongs(
        context: Context,
        items: List<LyricsStorage.LocalLyricsItem>,
        format: LyricsExportFormat
    ): File {
        val content = formatBatchContent(context, items, format)
        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
        if (!downloadDir.exists()) downloadDir.mkdirs()
        val exportFile = File(
            downloadDir,
            "AetherLyric_lyrics_export_${System.currentTimeMillis()}.${format.extension}"
        )
        exportFile.writeText(content, Charsets.UTF_8)
        return exportFile
    }
}
