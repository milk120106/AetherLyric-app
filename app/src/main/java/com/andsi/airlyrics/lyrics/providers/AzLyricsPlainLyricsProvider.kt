package com.andsi.airlyrics.lyrics.providers

import com.andsi.airlyrics.logging.AppLogger
import com.andsi.airlyrics.lyrics.LyricsProviderResult
import com.andsi.airlyrics.lyrics.PlainLyricsProvider
import com.andsi.airlyrics.lyrics.PlainLyricsSearchRequest
import com.andsi.airlyrics.network.aether.AetherLyricApiClient
import com.andsi.airlyrics.settings.store.AetherLyricServerStore
import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * AZLyrics 線上歌詞來源 Provider。
 * 格式化藝人與歌曲名稱檢索 AZLyrics 頁面並擷取純文字歌詞，
 * 優先呼叫 AetherLyric-server /api/v1/align 進行時間軸錨點對齊，若無伺服器則自動降級為本地簡易對齊。
 */
object AzLyricsPlainLyricsProvider : PlainLyricsProvider {
    override val id: String = "azlyrics"
    override val name: String = "AZLyrics"
    override val continueOnFailure: Boolean = true

    override fun fetch(request: PlainLyricsSearchRequest): Result<LyricsProviderResult?> {
        return runCatching {
            request.cancellationToken?.throwIfCancellationRequested()
            val cleanArtist = request.artist.lowercase().replace(Regex("[^a-z0-9]"), "").trim()
            val cleanTitle = request.title.lowercase().replace(Regex("[^a-z0-9]"), "").trim()

            if (cleanArtist.isEmpty() || cleanTitle.isEmpty()) return@runCatching null

            AppLogger.i("AZLyricsProvider", "開始搜尋 AZLyrics 歌詞: ${request.title} - ${request.artist}")

            val url = "https://www.azlyrics.com/lyrics/$cleanArtist/$cleanTitle.html"
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            }

            if (conn.responseCode != 200) {
                AppLogger.w("AZLyricsProvider", "AZLyrics 網頁請求失敗: HTTP ${conn.responseCode}")
                return@runCatching null
            }

            val html = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

            val markerStart = "<!-- Usage of azlyrics.com content by any other means is prohibited without express written permission. -->"
            val markerEnd = "</div>"

            val startIdx = html.indexOf(markerStart)
            if (startIdx == -1) return@runCatching null

            val actualStart = startIdx + markerStart.length
            val endIdx = html.indexOf(markerEnd, actualStart)
            if (endIdx == -1) return@runCatching null

            val rawLyrics = html.substring(actualStart, endIdx)
                .replace("<br>", "\n")
                .replace("<br/>", "\n")
                .replace(Regex("<[^>]+>"), "")
                .trim()

            if (rawLyrics.isBlank()) return@runCatching null

            // 時間軸對齊：若開啟 AetherLyric 伺服器，優先送往 POST /api/v1/align，失敗或未開啟則使用本地簡易時間軸
            var alignedLrc: String? = null
            val serverUrl = AetherLyricServerStore.getServerUrl(request.context)
            val isServerEnabled = AetherLyricServerStore.isServerEnabled(request.context)
            val serverApiKey = AetherLyricServerStore.getAdminApiKey(request.context)

            if (isServerEnabled && serverUrl.isNotBlank()) {
                AppLogger.i("AZLyricsProvider", "呼叫 AetherLyric 伺服器進行時間軸對齊...")
                runBlocking {
                    val alignResult = AetherLyricApiClient.alignLyrics(
                        serverUrl = serverUrl,
                        referenceLrc = rawLyrics,
                        apiKey = serverApiKey
                    )
                    alignedLrc = alignResult.getOrNull()
                }
            }

            val finalLrc = alignedLrc ?: run {
                AppLogger.i("AZLyricsProvider", "使用本地簡易時間對齊計算時間軸")
                val lines = rawLyrics.lines().map(String::trim).filter(String::isNotEmpty)
                val stepMs = if (lines.isNotEmpty() && request.durationMs > 0) request.durationMs / lines.size else 3000L
                val lrcBuilder = StringBuilder()
                lines.forEachIndexed { index, line ->
                    val timeMs = index * stepMs
                    val minutes = timeMs / 60000
                    val seconds = (timeMs % 60000) / 1000
                    val hundredths = (timeMs % 1000) / 10
                    lrcBuilder.append(String.format("[%02d:%02d.%02d]%s\n", minutes, seconds, hundredths, line))
                }
                lrcBuilder.toString()
            }

            AppLogger.i("AZLyricsProvider", "歌詞檢索與對齊成功: ${request.title}")

            LyricsProviderResult(
                plainProviderId = id,
                plainProviderName = name,
                plainLrc = finalLrc,
                matchedTitle = request.title,
                matchedArtist = request.artist,
                matchedDurationMs = request.durationMs
            )
        }
    }
}
