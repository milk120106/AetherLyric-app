package com.andsi.airlyrics.lyrics.providers

import android.util.Base64
import com.andsi.airlyrics.lyrics.LyricsProviderResult
import com.andsi.airlyrics.lyrics.PlainLyricsProvider
import com.andsi.airlyrics.lyrics.PlainLyricsSearchRequest
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 酷狗音樂 (Kugou) 線上歌詞來源 Provider。
 * 透過公開社群 API 搜尋候選歌曲並獲取 Base64 編碼的 LRC 歌詞。
 */
object KugouPlainLyricsProvider : PlainLyricsProvider {
    override val id: String = "kugou"
    override val name: String = "Kugou"
    override val continueOnFailure: Boolean = true

    override fun fetch(request: PlainLyricsSearchRequest): Result<LyricsProviderResult?> {
        return runCatching {
            request.cancellationToken?.throwIfCancellationRequested()
            val query = "${request.title} ${request.artist}".trim()
            if (query.isEmpty()) return@runCatching null

            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "http://lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword=$encodedQuery&duration=${request.durationMs}"

            val conn = (URL(searchUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }

            if (conn.responseCode != 200) return@runCatching null
            val searchJson = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val root = JSONObject(searchJson)
            val candidates = root.optJSONArray("candidates") ?: return@runCatching null
            if (candidates.length() == 0) return@runCatching null

            val best = candidates.getJSONObject(0)
            val lyricId = best.optString("id")
            val accessKey = best.optString("accesskey")
            val candidateTitle = best.optString("song", request.title)
            val candidateArtist = best.optString("singer", request.artist)

            if (lyricId.isEmpty() || accessKey.isEmpty()) return@runCatching null

            request.cancellationToken?.throwIfCancellationRequested()

            val downloadUrl = "http://lyrics.kugou.com/download?ver=1&client=pc&id=$lyricId&accesskey=$accessKey&fmt=lrc&charset=utf8"
            val dlConn = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }

            if (dlConn.responseCode != 200) return@runCatching null
            val dlJson = dlConn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val dlRoot = JSONObject(dlJson)
            val contentBase64 = dlRoot.optString("content")
            if (contentBase64.isBlank()) return@runCatching null

            val lrcBytes = Base64.decode(contentBase64, Base64.DEFAULT)
            val lrcText = String(lrcBytes, StandardCharsets.UTF_8)
            if (lrcText.isBlank()) return@runCatching null

            LyricsProviderResult(
                plainProviderId = id,
                plainProviderName = name,
                plainLrc = lrcText,
                matchedTitle = candidateTitle,
                matchedArtist = candidateArtist,
                matchedDurationMs = request.durationMs
            )
        }
    }
}
