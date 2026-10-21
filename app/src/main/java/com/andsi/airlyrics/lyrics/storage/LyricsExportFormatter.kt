package com.andsi.airlyrics.lyrics.storage

import android.content.Context
import android.os.Environment
import com.andsi.airlyrics.ui.model.LocalLyricsUiItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 歌詞匯出格式定義與轉換工具：
 * 支援「標準 LRC（僅原文，保留 [mm:ss.xx] 時間軸）」、
 * 「雙語 LRC（原文與翻譯同行或雙時間軸 LRC）」、
 * 「純文字 TXT（自動剝除所有時間戳標籤）」三種匯出格式。
 */
enum class LyricsExportFormat(val displayTitle: String, val extension: String) {
    STANDARD_LRC("標準 LRC（僅原文，保留 [mm:ss.xx] 時間軸）", "lrc"),
    BILINGUAL_LRC("雙語 LRC（原文與翻譯同行或雙時間軸 LRC）", "lrc"),
    PLAIN_TXT("純文字 TXT（自動剝除所有時間戳標籤）", "txt")
}

object LyricsExportFormatter {
    private val lineTimestampRegex = Regex("""^(\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?\])+""")
    private val allTimestampRegex = Regex("""\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?\]|<\d{1,3}:\d{2}(?:[.:]\d{1,3})?>""")
    private val metaTagRegex = Regex("""^\[(ti|ar|al|by|offset|length|re|ve):.*\]$""", RegexOption.IGNORE_CASE)

    fun formatLyrics(rawContent: String, format: LyricsExportFormat): String {
        val lines = rawContent.lines()
        return when (format) {
            LyricsExportFormat.BILINGUAL_LRC -> {
                rawContent.trim()
            }
            LyricsExportFormat.STANDARD_LRC -> {
                val seenTimestamps = HashSet<String>()
                val out = mutableListOf<String>()
                for (line in lines) {
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
                val out = mutableListOf<String>()
                for (line in lines) {
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
        }
        val exportFile = File(downloadDir, "AetherLyric_${safeName}_${suffix}.${format.extension}")
        exportFile.writeText(formatted, Charsets.UTF_8)
        return exportFile
    }

    fun exportBatchSongs(
        context: Context,
        items: List<LyricsStorage.LocalLyricsItem>,
        format: LyricsExportFormat
    ): File {
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

        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
        if (!downloadDir.exists()) downloadDir.mkdirs()
        val exportFile = File(
            downloadDir,
            "AetherLyric_lyrics_export_${System.currentTimeMillis()}.${format.extension}"
        )
        exportFile.writeText(sb.toString(), Charsets.UTF_8)
        return exportFile
    }
}
