package com.andsi.airlyrics.lyrics.providers

import com.andsi.airlyrics.lyrics.LyricsProviderResult
import com.andsi.airlyrics.lyrics.PlainLyricsProvider
import com.andsi.airlyrics.lyrics.PlainLyricsSearchRequest
import com.andsi.airlyrics.network.aether.AetherLyricApiClient
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/**
 * PetitLyrics 日本同步歌詞來源 Provider。
 * 支援獲取動漫與日語流行歌曲之時間軸 LRC 歌詞。
 */
object PetitLyricsPlainLyricsProvider : PlainLyricsProvider {
    override val id: String = "petit_lyrics"
    override val name: String = "PetitLyrics"
    override val continueOnFailure: Boolean = true

    override fun fetch(request: PlainLyricsSearchRequest): Result<LyricsProviderResult?> {
        return runCatching {
            request.cancellationToken?.throwIfCancellationRequested()
            val title = request.title.trim()
            val artist = request.artist.trim()
            if (title.isEmpty()) return@runCatching null

            val encTitle = URLEncoder.encode(title, "UTF-8")
            val encArtist = URLEncoder.encode(artist, "UTF-8")
            val searchUrl = "https://petitlyrics.com/search_result?title=$encTitle&artist=$encArtist"

            val conn = (URL(searchUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }

            if (conn.responseCode != 200) return@runCatching null
            val html = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

            request.cancellationToken?.throwIfCancellationRequested()

            // 尋找歌詞詳細頁面 /lyrics/(\d+)
            val linkPattern = Pattern.compile("href=\"(/lyrics/\\d+)\"")
            val matcher = linkPattern.matcher(html)
            if (!matcher.find()) return@runCatching null

            val relativePath = matcher.group(1) ?: return@runCatching null
            val detailUrl = "https://petitlyrics.com$relativePath"

            val detailConn = (URL(detailUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }

            if (detailConn.responseCode != 200) return@runCatching null
            val detailHtml = detailConn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

            // 提取 <canvas id="lyrics"> 或 <div class="lyrics_text"> 內容
            val textPattern = Pattern.compile("<div class=\"lyrics_text\"[^>]*>([\\s\\S]*?)</div>")
            val textMatcher = textPattern.matcher(detailHtml)
            var rawLyrics = ""
            if (textMatcher.find()) {
                rawLyrics = textMatcher.group(1) ?: ""
            }

            if (rawLyrics.isBlank()) {
                val canvasPattern = Pattern.compile("id=\"lyrics_data\"[^>]*>([\\s\\S]*?)<")
                val canvasMatcher = canvasPattern.matcher(detailHtml)
                if (canvasMatcher.find()) {
                    rawLyrics = canvasMatcher.group(1) ?: ""
                }
            }

            rawLyrics = rawLyrics.replace("<br>", "\n").replace("<br/>", "\n").replace("<br />", "\n")
            rawLyrics = android.text.Html.fromHtml(rawLyrics, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()
            if (rawLyrics.isBlank()) return@runCatching null

            val aligned = if (rawLyrics.contains("[")) rawLyrics else AetherLyricApiClient.alignPlainLyricsLocally(rawLyrics, request.durationMs)

            LyricsProviderResult(
                plainProviderId = id,
                plainProviderName = name,
                plainLrc = aligned,
                matchedTitle = title,
                matchedArtist = artist,
                matchedDurationMs = request.durationMs
            )
        }
    }
}
