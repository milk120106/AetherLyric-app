package com.andsi.airlyrics.lyrics.providers

import com.andsi.airlyrics.lyrics.LyricsProviderResult
import com.andsi.airlyrics.lyrics.PlainLyricsProvider
import com.andsi.airlyrics.lyrics.PlainLyricsSearchRequest

/**
 * 自訂 JSON 外掛源 (Custom JSON) Provider 適配器。
 * 查詢使用者在擴充商店或外部安裝的啟用中自訂 JSON 歌詞來源。
 */
object CustomJsonLyricsProvider : PlainLyricsProvider {
    override val id: String = "custom_json"
    override val name: String = "Custom JSON"
    override val continueOnFailure: Boolean = true

    override fun fetch(request: PlainLyricsSearchRequest): Result<LyricsProviderResult?> {
        return runCatching {
            request.cancellationToken?.throwIfCancellationRequested()
            CustomJsonPluginStore.findFirstFromEnabledPlugins(request)
        }
    }
}
