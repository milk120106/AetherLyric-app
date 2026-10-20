package com.andsi.airlyrics.lyrics.providers

import com.andsi.airlyrics.logging.AppLogger
import com.andsi.airlyrics.lyrics.LyricsProviderResult
import com.andsi.airlyrics.lyrics.PlainLyricsProvider
import com.andsi.airlyrics.lyrics.PlainLyricsSearchRequest
import com.andsi.airlyrics.network.aether.AetherLyricApiClient
import com.andsi.airlyrics.settings.store.AetherLyricServerStore
import com.andsi.airlyrics.settings.store.LyricsSettingsStore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Genius 線上歌詞來源 Provider。
 * 支援自訂 BYOK API 金鑰，若成功取得純文字歌詞，優先呼叫 AetherLyric-server /api/v1/align 對齊時間軸，
 * 伺服器離線或失敗時自動無縫降級為本地簡易時間對齊。
 */
object GeniusPlainLyricsProvider : PlainLyricsProvider {
    override val id: String = "genius"
    override val name: String = "Genius"
    override val continueOnFailure: Boolean = true

    override fun fetch(request: PlainLyricsSearchRequest): Result<LyricsProviderResult?> {
        return runCatching {
            request.cancellationToken?.throwIfCancellationRequested()
            val query = "${request.title} ${request.artist}".trim()
            if (query.isEmpty()) return@runCatching null

            val encoded = URLEncoder.encode(query, "UTF-8")
            val apiKey = LyricsSettingsStore.getGeniusApiKey(request.context)
            val searchUrl = if (!apiKey.isNullOrBlank()) {
                "https://api.genius.com/search?q=$encoded"
            } else {
                "https://genius.com/api/search/multi?q=$encoded"
            }

            AppLogger.i("GeniusProvider", "開始搜尋歌詞: $query (BYOK: ${!apiKey.isNullOrBlank()})")

            val conn = (URL(searchUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                if (!apiKey.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }
            }

            if (conn.responseCode != 200) {
                AppLogger.w("GeniusProvider", "Genius 搜尋請求失敗: HTTP ${conn.responseCode}")
                return@runCatching null
            }

            val searchJson = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val root = JSONObject(searchJson)

            var hitSongTitle: String? = null
            var hitArtist: String? = null
            var songPath: String? = null

            if (!apiKey.isNullOrBlank()) {
                // 官方 API 結構: response.hits[].result
                val hits = root.optJSONObject("response")?.optJSONArray("hits")
                if (hits != null && hits.length() > 0) {
                    val hit = hits.getJSONObject(0).optJSONObject("result")
                    hitSongTitle = hit?.optString("title")
                    hitArtist = hit?.optJSONObject("primary_artist")?.optString("name")
                    songPath = hit?.optString("path")
                }
            } else {
                // 公開網頁結構: response.sections[].hits[].result
                val sections = root.optJSONObject("response")?.optJSONArray("sections")
                if (sections != null) {
                    for (i in 0 until sections.length()) {
                        val sec = sections.getJSONObject(i)
                        if (sec.optString("type") == "song") {
                            val hits = sec.optJSONArray("hits") ?: continue
                            if (hits.length() > 0) {
                                val hit = hits.getJSONObject(0).optJSONObject("result") ?: continue
                                hitSongTitle = hit.optString("title")
                                hitArtist = hit.optJSONObject("primary_artist")?.optString("name")
                                songPath = hit.optString("path")
                                break
                            }
                        }
                    }
                }
            }

            if (songPath == null) {
                AppLogger.i("GeniusProvider", "未找到相符歌曲: $query")
                return@runCatching null
            }

            request.cancellationToken?.throwIfCancellationRequested()

            // 讀取歌曲頁面提取純文字歌詞
            val songUrl = if (songPath.startsWith("http")) songPath else "https://genius.com$songPath"
            val songConn = (URL(songUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            }

            if (songConn.responseCode != 200) {
                AppLogger.w("GeniusProvider", "無法讀取歌詞網頁: HTTP ${songConn.responseCode}")
                return@runCatching null
            }

            val html = songConn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

            // 解析歌詞容器 (Genius Lyrics container)
            val lyricsContainerRegex = Regex("""data-lyrics-container="true"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
            val matches = lyricsContainerRegex.findAll(html).toList()
            if (matches.isEmpty()) {
                AppLogger.w("GeniusProvider", "網頁中未解析到歌詞容器內容")
                return@runCatching null
            }

            val rawLyrics = matches.joinToString("\n") { it.groupValues[1] }
                .replace("<br/>", "\n")
                .replace("<br>", "\n")
                .replace(Regex("<[^>]+>"), "")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#x27;", "'")
                .trim()

            if (rawLyrics.isBlank()) return@runCatching null

            // 時間軸對齊：若開啟 AetherLyric 伺服器，優先送往 POST /api/v1/align，失敗或未開啟則使用本地簡易時間軸
            var alignedLrc: String? = null
            val serverUrl = AetherLyricServerStore.getServerUrl(request.context)
            val isServerEnabled = AetherLyricServerStore.isServerEnabled(request.context)
            val serverApiKey = AetherLyricServerStore.getAdminApiKey(request.context)

            if (isServerEnabled && serverUrl.isNotBlank()) {
                AppLogger.i("GeniusProvider", "呼叫 AetherLyric 伺服器進行時間軸對齊...")
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
                AppLogger.i("GeniusProvider", "使用本地簡易時間對齊計算時間軸")
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

            AppLogger.i("GeniusProvider", "歌詞檢索與對齊成功: ${hitSongTitle ?: request.title}")

            LyricsProviderResult(
                plainProviderId = id,
                plainProviderName = name,
                plainLrc = finalLrc,
                matchedTitle = hitSongTitle ?: request.title,
                matchedArtist = hitArtist ?: request.artist,
                matchedDurationMs = request.durationMs
            )
        }
    }
}
