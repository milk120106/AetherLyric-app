package com.andsi.airlyrics.lyrics.providers

import com.andsi.airlyrics.lyrics.LyricsProviderResult
import com.andsi.airlyrics.lyrics.PlainLyricsProvider
import com.andsi.airlyrics.lyrics.PlainLyricsSearchRequest
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/**
 * Megalobiz 線上 LRC 歌詞搜尋 Provider。
 * 檢索 Megalobiz 同步時間軸資料庫並提取 LRC 檔案內容。
 */
object MegalobizPlainLyricsProvider : PlainLyricsProvider {
    override val id: String = "megalobiz"
    override val name: String = "Megalobiz"
    override val continueOnFailure: Boolean = true

    override fun fetch(request: PlainLyricsSearchRequest): Result<LyricsProviderResult?> {
        return runCatching {
            request.cancellationToken?.throwIfCancellationRequested()
            val query = "${request.title} ${request.artist}".trim()
            if (query.isEmpty()) return@runCatching null

            val encQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://www.megalobiz.com/search/all?qry=$encQuery&display=more"

            val conn = (URL(searchUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            }

            if (conn.responseCode != 200) return@runCatching null
            val html = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

            request.cancellationToken?.throwIfCancellationRequested()

            // 尋找第一個歌詞頁面連結 /lrc/maker/...
            val linkPattern = Pattern.compile("href=\"(/lrc/maker/[^\"]+)\"")
            val matcher = linkPattern.matcher(html)
            if (!matcher.find()) return@runCatching null

            val relativePath = matcher.group(1) ?: return@runCatching null
            val detailUrl = "https://www.megalobiz.com$relativePath"

            val detailConn = (URL(detailUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            }

            if (detailConn.responseCode != 200) return@runCatching null
            val detailHtml = detailConn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

            // 提取 <span id="lrc_..._details"> 裡面的 LRC 歌詞
            val lrcPattern = Pattern.compile("id=\"lrc_[^\"]+_details\">([\\s\\S]*?)</span>")
            val lrcMatcher = lrcPattern.matcher(detailHtml)
            if (!lrcMatcher.find()) return@runCatching null

            var rawLrc = lrcMatcher.group(1) ?: return@runCatching null
            rawLrc = rawLrc.replace("<br>", "\n").replace("<br/>", "\n").replace("<br />", "\n")
            rawLrc = android.text.Html.fromHtml(rawLrc, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()

            if (rawLrc.isBlank()) return@runCatching null

            LyricsProviderResult(
                plainProviderId = id,
                plainProviderName = name,
                plainLrc = rawLrc,
                matchedTitle = request.title,
                matchedArtist = request.artist,
                matchedDurationMs = request.durationMs
            )
        }
    }
}
