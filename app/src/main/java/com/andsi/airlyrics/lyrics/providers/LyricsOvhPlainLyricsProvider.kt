package com.andsi.airlyrics.lyrics.providers

import com.andsi.airlyrics.lyrics.LyricsProviderResult
import com.andsi.airlyrics.lyrics.PlainLyricsProvider
import com.andsi.airlyrics.lyrics.PlainLyricsSearchRequest
import com.andsi.airlyrics.network.aether.AetherLyricApiClient
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Lyrics.ovh 線上西洋歌詞來源 Provider。
 * 查詢公開開源歌詞資料庫，並將純文字歌詞進行時間軸錨點對齊。
 */
object LyricsOvhPlainLyricsProvider : PlainLyricsProvider {
    override val id: String = "lyrics_ovh"
    override val name: String = "Lyrics.ovh"
    override val continueOnFailure: Boolean = true

    override fun fetch(request: PlainLyricsSearchRequest): Result<LyricsProviderResult?> {
        return runCatching {
            request.cancellationToken?.throwIfCancellationRequested()
            val artist = request.artist.trim()
            val title = request.title.trim()
            if (artist.isEmpty() || title.isEmpty()) return@runCatching null

            val encArtist = URLEncoder.encode(artist, "UTF-8").replace("+", "%20")
            val encTitle = URLEncoder.encode(title, "UTF-8").replace("+", "%20")
            val queryUrl = "https://api.lyrics.ovh/v1/$encArtist/$encTitle"

            val conn = (URL(queryUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }

            if (conn.responseCode != 200) return@runCatching null
            val resp = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val root = JSONObject(resp)
            val rawLyrics = root.optString("lyrics", "").trim()
            if (rawLyrics.isBlank()) return@runCatching null

            request.cancellationToken?.throwIfCancellationRequested()

            // 時間軸對齊
            val alignedLrc = if (rawLyrics.contains("[")) {
                rawLyrics
            } else {
                AetherLyricApiClient.alignPlainLyricsLocally(rawLyrics, request.durationMs)
            }

            LyricsProviderResult(
                plainProviderId = id,
                plainProviderName = name,
                plainLrc = alignedLrc,
                matchedTitle = title,
                matchedArtist = artist,
                matchedDurationMs = request.durationMs
            )
        }
    }
}
