package com.andsi.airlyrics.lyrics.providers

import com.andsi.airlyrics.lyrics.LyricsProviderResult
import com.andsi.airlyrics.lyrics.PlainLyricsProvider
import com.andsi.airlyrics.lyrics.PlainLyricsSearchRequest
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Bilibili (音訊與字幕歌詞解析) Provider。
 * 支援從 Bilibili 影音投稿中提取 CC 同步字幕並轉換為標準 LRC。
 */
object BilibiliLyricsProvider : PlainLyricsProvider {
    override val id: String = "bilibili"
    override val name: String = "Bilibili"
    override val continueOnFailure: Boolean = true

    override fun fetch(request: PlainLyricsSearchRequest): Result<LyricsProviderResult?> {
        return runCatching {
            request.cancellationToken?.throwIfCancellationRequested()
            val query = "${request.title} ${request.artist}".trim()
            if (query.isEmpty()) return@runCatching null

            val encQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://api.bilibili.com/x/web-interface/search/type?search_type=video&keyword=$encQuery"

            val conn = (URL(searchUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                setRequestProperty("Referer", "https://www.bilibili.com")
            }

            if (conn.responseCode != 200) return@runCatching null
            val searchJson = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val root = JSONObject(searchJson)
            val data = root.optJSONObject("data") ?: return@runCatching null
            val resultArr = data.optJSONArray("result") ?: return@runCatching null
            if (resultArr.length() == 0) return@runCatching null

            val firstItem = resultArr.getJSONObject(0)
            val bvid = firstItem.optString("bvid", "")
            val cid = firstItem.optLong("cid", 0L)
            val candidateTitle = firstItem.optString("title", request.title)
                .replace("<em class=\"keyword\">", "")
                .replace("</em>", "")
            val candidateAuthor = firstItem.optString("author", request.artist)

            if (bvid.isEmpty()) return@runCatching null

            request.cancellationToken?.throwIfCancellationRequested()

            // 查詢字幕 API
            val subUrl = "https://api.bilibili.com/x/player/v2?bvid=$bvid&cid=$cid"
            val subConn = (URL(subUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "Mozilla/5.0")
                setRequestProperty("Referer", "https://www.bilibili.com")
            }

            if (subConn.responseCode != 200) return@runCatching null
            val subResp = subConn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val subRoot = JSONObject(subResp)
            val subData = subRoot.optJSONObject("data")
            val subtitleObj = subData?.optJSONObject("subtitle")
            val subtitlesArr = subtitleObj?.optJSONArray("subtitles")
            if (subtitlesArr == null || subtitlesArr.length() == 0) return@runCatching null

            val subtitleUrl = subtitlesArr.getJSONObject(0).optString("subtitle_url", "")
            if (subtitleUrl.isEmpty()) return@runCatching null

            val fullSubUrl = if (subtitleUrl.startsWith("//")) "https:$subtitleUrl" else subtitleUrl
            val dlConn = (URL(fullSubUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
            }

            if (dlConn.responseCode != 200) return@runCatching null
            val dlJson = dlConn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val bodyObj = JSONObject(dlJson)
            val bodyArr = bodyObj.optJSONArray("body") ?: return@runCatching null
            if (bodyArr.length() == 0) return@runCatching null

            val sb = StringBuilder()
            for (i in 0 until bodyArr.length()) {
                val item = bodyArr.getJSONObject(i)
                val fromSec = item.optDouble("from", 0.0)
                val text = item.optString("content", "").trim()
                if (text.isNotEmpty()) {
                    val m = (fromSec / 60).toInt()
                    val s = fromSec % 60
                    sb.append(String.format(Locale.US, "[%02d:%05.2f]%s\n", m, s, text))
                }
            }

            val lrc = sb.toString().trim()
            if (lrc.isBlank()) return@runCatching null

            LyricsProviderResult(
                plainProviderId = id,
                plainProviderName = name,
                plainLrc = lrc,
                matchedTitle = candidateTitle,
                matchedArtist = candidateAuthor,
                matchedDurationMs = request.durationMs
            )
        }
    }
}
