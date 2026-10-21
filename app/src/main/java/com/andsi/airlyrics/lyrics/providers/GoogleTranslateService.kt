package com.andsi.airlyrics.lyrics.providers

import android.content.Context
import com.andsi.airlyrics.logging.AppLogger
import com.andsi.airlyrics.lyrics.parser.LrcParser
import com.andsi.airlyrics.lyrics.parser.ParsedTtmlDocument
import com.andsi.airlyrics.lyrics.parser.TtmlParseResult
import com.andsi.airlyrics.lyrics.parser.TtmlParser
import com.andsi.airlyrics.lyrics.parser.toTtmlString
import com.andsi.airlyrics.settings.store.AetherLyricServerStore
import com.andsi.airlyrics.settings.store.LyricsSettingsStore
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * 本地 Google 翻譯服務 (GoogleTranslateService)。
 * 當未連線至伺服器或取得的歌詞不含翻譯且使用者開啟「啟用本地 Google 翻譯」時，
 * 自動將外文歌詞逐行翻譯為目標語言（預設 zh-TW 繁體中文），並保留原始 LRC/TTML 時間軸標籤以供雙語對照與獨立顯示。
 */
object GoogleTranslateService {
    private const val CONNECT_TIMEOUT_MS = 6000
    private const val READ_TIMEOUT_MS = 8000
    private const val CHUNK_SIZE = 35

    /**
     * 判斷歌詞是否已內含雙語翻譯。
     */
    fun hasExistingTranslation(plainLrc: String, translatedLrc: String?): Boolean {
        if (!translatedLrc.isNullOrBlank()) return true
        if (plainLrc.contains("<tt", ignoreCase = true)) {
            val ttmlRes = TtmlParser.parse(plainLrc)
            if (ttmlRes is TtmlParseResult.Success) {
                return ttmlRes.document.lines.any { it.translations.isNotEmpty() }
            }
        }
        val parsed = LrcParser.parse(plainLrc)
        return parsed.count { !it.isMetadata && it.hasTranslation() } >= 2
    }

    /**
     * 將原始 LRC、TTML 或純文字歌詞翻譯為目標語言字串（帶時間戳以供解析器對齊）。
     */
    fun translateLrcIfNeeded(
        context: Context,
        plainLrc: String,
        existingTranslatedLrc: String?,
        targetLang: String = LyricsSettingsStore.getTranslateTargetLang(context)
    ): String? {
        if (plainLrc.isBlank()) return existingTranslatedLrc
        if (hasExistingTranslation(plainLrc, existingTranslatedLrc)) return existingTranslatedLrc

        return runCatching {
            if (plainLrc.contains("<tt", ignoreCase = true)) {
                val ttmlRes = TtmlParser.parse(plainLrc)
                if (ttmlRes is TtmlParseResult.Success) {
                    val eligibleLines = ttmlRes.document.lines.filter { it.text.isNotBlank() }
                    if (eligibleLines.isNotEmpty()) {
                        val originalTexts = eligibleLines.map { it.text.trim() }
                        val translatedTexts = translateBatchBlocking(context, originalTexts, targetLang)
                        if (translatedTexts.size == eligibleLines.size) {
                            var changedCount = 0
                            val sb = StringBuilder()
                            eligibleLines.indices.forEach { idx ->
                                val orig = eligibleLines[idx]
                                val trans = translatedTexts[idx].trim()
                                if (trans.isNotEmpty() && !trans.equals(orig.text.trim(), ignoreCase = true)) {
                                    changedCount++
                                }
                                val timeTag = formatTimeTag(orig.startMs)
                                sb.append("[").append(timeTag).append("]").append(trans).append("\n")
                            }
                            if (changedCount > 0) return@runCatching sb.toString().trimEnd()
                        }
                    }
                }
            }

            val parsedLines = LrcParser.parse(plainLrc)
            val eligibleLines = parsedLines.filter { !it.isMetadata && it.text.isNotBlank() }

            if (eligibleLines.isNotEmpty()) {
                val originalTexts = eligibleLines.map { it.text.trim() }
                val translatedTexts = translateBatchBlocking(context, originalTexts, targetLang)
                if (translatedTexts.size != eligibleLines.size) return@runCatching existingTranslatedLrc

                var changedCount = 0
                val sb = StringBuilder()
                eligibleLines.indices.forEach { idx ->
                    val orig = eligibleLines[idx]
                    val trans = translatedTexts[idx].trim()
                    if (trans.isNotEmpty() && !trans.equals(orig.text.trim(), ignoreCase = true)) {
                        changedCount++
                    }
                    val timeTag = formatTimeTag(orig.timeMs)
                    sb.append("[").append(timeTag).append("]").append(trans).append("\n")
                }
                if (changedCount == 0) null else sb.toString().trimEnd()
            } else {
                // 純文字無時間戳歌詞
                val rawLines = plainLrc.lines().map { it.trim() }.filter { it.isNotBlank() }
                if (rawLines.isEmpty()) return@runCatching existingTranslatedLrc
                val translatedLines = translateBatchBlocking(context, rawLines, targetLang)
                if (translatedLines.isEmpty()) null else translatedLines.joinToString("\n")
            }
        }.onFailure { err ->
            AppLogger.w("GoogleTranslate", "本地 Google 翻譯失敗: ${err.message}")
        }.getOrDefault(existingTranslatedLrc)
    }

    /**
     * 若為 TTML 格式，將整句純文字翻譯乾淨合併回 <p> 標籤中 (<span ttm:role="x-translation">)，
     * 保留 TTML 原有結構與時間軸。
     */
    fun mergeTranslationIntoTtml(ttmlContent: String, translatedTexts: List<String>): String {
        val parseRes = TtmlParser.parse(ttmlContent)
        if (parseRes !is TtmlParseResult.Success) return ttmlContent
        val lines = parseRes.document.lines
        val eligibleIndices = lines.indices.filter { lines[it].text.isNotBlank() }
        if (eligibleIndices.size != translatedTexts.size) return ttmlContent

        val updatedLines = lines.mapIndexed { idx, line ->
            val matchPos = eligibleIndices.indexOf(idx)
            if (matchPos >= 0) {
                val trans = translatedTexts[matchPos].trim()
                if (trans.isNotEmpty() && !line.translations.contains(trans)) {
                    line.copy(translations = line.translations + trans)
                } else {
                    line
                }
            } else {
                line
            }
        }
        return ParsedTtmlDocument(updatedLines, parseRes.document.timingMode).toTtmlString()
    }

    /**
     * 批次呼叫 Google Translate API 翻譯多行歌詞。
     */
    fun translateBatchBlocking(
        context: Context,
        lines: List<String>,
        targetLang: String = "zh-TW"
    ): List<String> {
        if (lines.isEmpty()) return emptyList()
        val results = mutableListOf<String>()
        val chunks = lines.chunked(CHUNK_SIZE)
        for (chunk in chunks) {
            val joined = chunk.joinToString("\n")
            val translatedChunk = translateSingleChunk(context, joined, targetLang)
            val splitLines = translatedChunk.split("\n")
            if (splitLines.size == chunk.size) {
                results.addAll(splitLines.map { it.trim() })
            } else {
                // 若換行數量不一致則逐行回退或對齊
                for (i in chunk.indices) {
                    results.add(splitLines.getOrNull(i)?.trim() ?: chunk[i])
                }
            }
        }
        return results
    }

    private fun translateSingleChunk(
        context: Context,
        text: String,
        targetLang: String
    ): String {
        val encodedText = URLEncoder.encode(text, "UTF-8")
        val encodedLang = URLEncoder.encode(targetLang, "UTF-8")
        val urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$encodedLang&dt=t&q=$encodedText"

        val conn = (AetherLyricServerStore.openConnectionWithProxy(context, urlStr) as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            setRequestProperty("Accept", "application/json")
        }

        if (conn.responseCode != HttpURLConnection.HTTP_OK) {
            throw IllegalStateException("Google Translate HTTP ${conn.responseCode}")
        }

        val responseBody = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        val outerArray = JSONArray(responseBody)
        val sentencesArray = outerArray.optJSONArray(0) ?: return text
        val sb = StringBuilder()
        for (i in 0 until sentencesArray.length()) {
            val segment = sentencesArray.optJSONArray(i) ?: continue
            val translatedPiece = segment.optString(0, "")
            sb.append(translatedPiece)
        }
        return sb.toString()
    }

    private fun formatTimeTag(timeMs: Long): String {
        val minutes = timeMs / 60_000L
        val seconds = (timeMs % 60_000L) / 1_000L
        val centiseconds = (timeMs % 1_000L) / 10L
        return String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, centiseconds)
    }
}
