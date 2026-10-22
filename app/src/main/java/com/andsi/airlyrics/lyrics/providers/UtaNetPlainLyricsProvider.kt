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
 * Uta-Net 日本流行與動漫歌詞來源 Provider。
 * 支援日語歌曲搜尋並自動進行時間軸錨點對齊。
 */
object UtaNetPlainLyricsProvider : PlainLyricsProvider {
    override val id: String = "uta_net"
    override val name: String = "Uta-Net"
    override val continueOnFailure: Boolean = true

    override fun fetch(request: PlainLyricsSearchRequest): Result<LyricsProviderResult?> {
        return runCatching {
            request.cancellationToken?.throwIfCancellationRequested()
            val query = "${request.title} ${request.artist}".trim()
            if (query.isEmpty()) return@runCatching null

            val encQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://www.uta-net.com/search/?Aselect=2&Keyword=$encQuery"

            val conn = (URL(searchUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            }

            if (conn.responseCode != 200) return@runCatching null
            val html = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

            request.cancellationToken?.throwIfCancellationRequested()

            // 尋找歌曲詳情連結 /song/(\d+)/
            val linkPattern = Pattern.compile("href=\"(/song/\\d+/)\"")
            val matcher = linkPattern.matcher(html)
            if (!matcher.find()) return@runCatching null

            val relativePath = matcher.group(1) ?: return@runCatching null
            val detailUrl = "https://www.uta-net.com$relativePath"

            val detailConn = (URL(detailUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            }

            if (detailConn.responseCode != 200) return@runCatching null
            val detailHtml = detailConn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

            // 提取 <div id="kashi_area"> 裡面的歌詞
            val kashiPattern = Pattern.compile("id=\"kashi_area\"[^>]*>([\\s\\S]*?)</div>")
            val kashiMatcher = kashiPattern.matcher(detailHtml)
            if (!kashiMatcher.find()) return@runCatching null

            var rawLyrics = kashiMatcher.group(1) ?: return@runCatching null
            rawLyrics = rawLyrics.replace("<br>", "\n").replace("<br/>", "\n").replace("<br />", "\n")
            rawLyrics = android.text.Html.fromHtml(rawLyrics, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()

            if (rawLyrics.isBlank()) return@runCatching null

            val aligned = AetherLyricApiClient.alignPlainLyricsLocally(rawLyrics, request.durationMs)

            LyricsProviderResult(
                plainProviderId = id,
                plainProviderName = name,
                plainLrc = aligned,
                matchedTitle = request.title,
                matchedArtist = request.artist,
                matchedDurationMs = request.durationMs
            )
        }
    }
}
