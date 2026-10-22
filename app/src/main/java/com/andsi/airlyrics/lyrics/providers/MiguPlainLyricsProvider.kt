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
 * 咪咕音樂 (Migu) 線上歌詞來源 Provider。
 * 透過公開咪咕 API 檢索歌曲候選並取得標準 LRC 歌詞內容。
 */
object MiguPlainLyricsProvider : PlainLyricsProvider {
    override val id: String = "migu"
    override val name: String = "Migu"
    override val continueOnFailure: Boolean = true

    override fun fetch(request: PlainLyricsSearchRequest): Result<LyricsProviderResult?> {
        return runCatching {
            request.cancellationToken?.throwIfCancellationRequested()
            val query = "${request.title} ${request.artist}".trim()
            if (query.isEmpty()) return@runCatching null

            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://c.musicapp.migu.cn/MIGUM2.0/v1.0/content/search_all.do?text=$encodedQuery&pageNo=1&pageSize=5&searchSwitch=song"

            val conn = (URL(searchUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                setRequestProperty("channel", "0146951")
            }

            if (conn.responseCode != 200) return@runCatching null
            val searchJson = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val root = JSONObject(searchJson)
            val songResultData = root.optJSONObject("songResultData") ?: return@runCatching null
            val resultList = songResultData.optJSONArray("result") ?: return@runCatching null
            if (resultList.length() == 0) return@runCatching null

            val best = resultList.getJSONObject(0)
            val candidateTitle = best.optString("name", request.title)
            val singersArr = best.optJSONArray("singers")
            val candidateArtist = singersArr?.optJSONObject(0)?.optString("name", request.artist) ?: request.artist
            val copyrightId = best.optString("copyrightId", "")

            request.cancellationToken?.throwIfCancellationRequested()

            // 取得歌詞
            var lrcContent = ""
            if (copyrightId.isNotEmpty()) {
                val lrcUrl = "https://c.musicapp.migu.cn/MIGUM2.0/v1.0/content/resourceinfo.do?copyrightId=$copyrightId&resourceType=2"
                val lrcConn = (URL(lrcUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6000
                    readTimeout = 6000
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }
                if (lrcConn.responseCode == 200) {
                    val lrcResp = lrcConn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                    val lrcRoot = JSONObject(lrcResp)
                    val resource = lrcRoot.optJSONArray("resource")?.optJSONObject(0)
                    val rawLrcUrl = resource?.optString("rateFormats") ?: ""
                    // 若有 lyricUrl 則下載
                    val directLrcUrl = resource?.optString("lyricUrl", "") ?: ""
                    if (directLrcUrl.isNotBlank()) {
                        val dlConn = (URL(directLrcUrl).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 5000
                            readTimeout = 5000
                        }
                        if (dlConn.responseCode == 200) {
                            lrcContent = dlConn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                        }
                    }
                }
            }

            if (lrcContent.isBlank()) {
                // 嘗試備用 API 抓取
                val lyricApiUrl = "https://music.migu.cn/v3/api/music/audioPlayer/getLyric?copyrightId=$copyrightId"
                val apiConn = (URL(lyricApiUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("Referer", "https://music.migu.cn")
                }
                if (apiConn.responseCode == 200) {
                    val apiResp = apiConn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                    val apiRoot = JSONObject(apiResp)
                    lrcContent = apiRoot.optString("lyric", "")
                }
            }

            if (lrcContent.isBlank()) return@runCatching null

            LyricsProviderResult(
                plainProviderId = id,
                plainProviderName = name,
                plainLrc = lrcContent,
                matchedTitle = candidateTitle,
                matchedArtist = candidateArtist,
                matchedDurationMs = request.durationMs
            )
        }
    }
}
