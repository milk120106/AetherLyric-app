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
 * QQ 音樂線上歌詞來源 Provider。
 * 透過社群/公開 API 檢索歌曲並解析獲取 LRC 歌詞。
 */
object QqMusicPlainLyricsProvider : PlainLyricsProvider {
    override val id: String = "qq"
    override val name: String = "QQ Music"
    override val continueOnFailure: Boolean = true

    override fun fetch(request: PlainLyricsSearchRequest): Result<LyricsProviderResult?> {
        return runCatching {
            request.cancellationToken?.throwIfCancellationRequested()
            val query = "${request.title} ${request.artist}".trim()
            if (query.isEmpty()) return@runCatching null

            val encoded = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?p=1&n=5&w=$encoded&format=json"

            val conn = (URL(searchUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "Mozilla/5.0")
                setRequestProperty("Referer", "https://y.qq.com")
            }

            if (conn.responseCode != 200) return@runCatching null
            val searchJson = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val root = JSONObject(searchJson)
            val songList = root.optJSONObject("data")?.optJSONObject("song")?.optJSONArray("list")
                ?: return@runCatching null
            if (songList.length() == 0) return@runCatching null

            val song = songList.getJSONObject(0)
            val songMid = song.optString("songmid")
            val candidateTitle = song.optString("songname", request.title)
            val candidateArtist = song.optJSONArray("singer")?.optJSONObject(0)?.optString("name", request.artist) ?: request.artist

            if (songMid.isEmpty()) return@runCatching null

            request.cancellationToken?.throwIfCancellationRequested()

            val lyricUrl = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=$songMid&format=json&nobase64=0"
            val lyricConn = (URL(lyricUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "Mozilla/5.0")
                setRequestProperty("Referer", "https://y.qq.com")
            }

            if (lyricConn.responseCode != 200) return@runCatching null
            val lyricJson = lyricConn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val cleanJson = if (lyricJson.startsWith("MusicJsonCallback(") && lyricJson.endsWith(")")) {
                lyricJson.substring("MusicJsonCallback(".length, lyricJson.length - 1)
            } else lyricJson

            val lyricRoot = JSONObject(cleanJson)
            val encodedLyric = lyricRoot.optString("lyric")
            if (encodedLyric.isBlank()) return@runCatching null

            val lrcBytes = Base64.decode(encodedLyric, Base64.DEFAULT)
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
