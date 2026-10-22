package com.andsi.airlyrics.lyrics.providers

import com.andsi.airlyrics.lyrics.LyricsProviderResult
import com.andsi.airlyrics.lyrics.PlainLyricsProvider
import com.andsi.airlyrics.lyrics.PlainLyricsSearchRequest
import com.andsi.airlyrics.network.aether.AetherLyricApiClient
import com.andsi.airlyrics.settings.store.AetherLyricServerStore

/**
 * 官方社群倉庫 (AetherLyric Community) Provider。
 * 查詢 AetherLyric 開源社群共享歌詞資料庫與伺服器社群快取。
 */
object AetherLyricCommunityProvider : PlainLyricsProvider {
    override val id: String = "community"
    override val name: String = "AetherLyric Community"
    override val continueOnFailure: Boolean = true

    override fun fetch(request: PlainLyricsSearchRequest): Result<LyricsProviderResult?> {
        return runCatching {
            request.cancellationToken?.throwIfCancellationRequested()
            val serverUrl = AetherLyricServerStore.getServerUrl(request.context)
            if (serverUrl.isBlank()) return@runCatching null

            val hit = AetherLyricApiClient.searchLyricsBlocking(
                serverUrl = serverUrl,
                title = request.title,
                artist = request.artist,
                duration = if (request.durationMs > 0) request.durationMs / 1000f else null,
                context = request.context
            ).getOrNull()

            if (hit != null && hit.hit && hit.lrcContent.isNotBlank()) {
                LyricsProviderResult(
                    plainProviderId = id,
                    plainProviderName = name,
                    plainLrc = hit.lrcContent,
                    matchedTitle = hit.trackTitle,
                    matchedArtist = hit.artist,
                    matchedDurationMs = request.durationMs
                )
            } else {
                null
            }
        }
    }
}
