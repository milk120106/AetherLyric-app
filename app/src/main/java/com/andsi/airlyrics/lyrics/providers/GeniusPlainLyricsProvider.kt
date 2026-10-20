package com.andsi.airlyrics.lyrics.providers

import com.andsi.airlyrics.lyrics.LyricsProviderResult
import com.andsi.airlyrics.lyrics.PlainLyricsProvider
import com.andsi.airlyrics.lyrics.PlainLyricsSearchRequest
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Genius 線上歌詞來源 Provider。
 * 檢索 Genius 公開搜尋 API，若檢索成功則轉換為相容的純文字 LRC 格式。
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
            val searchUrl = "https://genius.com/api/search/multi?q=$encoded"

            val conn = (URL(searchUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }

            if (conn.responseCode != 200) return@runCatching null
            val searchJson = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val root = JSONObject(searchJson)
            val sections = root.optJSONObject("response")?.optJSONArray("sections") ?: return@runCatching null

            var hitSongTitle: String? = null
            var hitArtist: String? = null
            var songPath: String? = null

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

            if (songPath == null) return@runCatching null
            request.cancellationToken?.throwIfCancellationRequested()

            // 讀取歌曲頁面提取純文字歌詞
            val songUrl = "https://genius.com$songPath"
            val songConn = (URL(songUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            }

            if (songConn.responseCode != 200) return@runCatching null
            val html = songConn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

            // 解析歌詞容器 (Genius Lyrics container)
            val lyricsContainerRegex = Regex("""data-lyrics-container="true"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
            val matches = lyricsContainerRegex.findAll(html).toList()
            if (matches.isEmpty()) return@runCatching null

            val rawLyrics = matches.joinToString("\n") { it.groupValues[1] }
                .replace("<br/>", "\n")
                .replace("<br>", "\n")
                .replace(Regex("<[^>]+>"), "")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#x27;", "'")
                .trim()

            if (rawLyrics.isBlank()) return@runCatching null

            // 封裝為非時間戳記純文字行 (或依段落平均時間戳記)
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

            LyricsProviderResult(
                plainProviderId = id,
                plainProviderName = name,
                plainLrc = lrcBuilder.toString(),
                matchedTitle = hitSongTitle ?: request.title,
                matchedArtist = hitArtist ?: request.artist,
                matchedDurationMs = request.durationMs
            )
        }
    }
}
