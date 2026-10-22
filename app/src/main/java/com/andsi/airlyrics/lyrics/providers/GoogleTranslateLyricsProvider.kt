package com.andsi.airlyrics.lyrics.providers

import com.andsi.airlyrics.lyrics.LyricsProviderResult
import com.andsi.airlyrics.lyrics.PlainLyricsProvider
import com.andsi.airlyrics.lyrics.PlainLyricsSearchRequest
import com.andsi.airlyrics.settings.store.LyricsSettingsStore

/**
 * 本地 Google 翻譯 (Translation Provider) 適配器。
 * 支援將原始歌詞即時線上翻譯為繁體中文對照時間軸。
 */
object GoogleTranslateLyricsProvider : PlainLyricsProvider {
    override val id: String = "google_translate"
    override val name: String = "Google Translate"
    override val continueOnFailure: Boolean = true

    override fun fetch(request: PlainLyricsSearchRequest): Result<LyricsProviderResult?> {
        return runCatching {
            request.cancellationToken?.throwIfCancellationRequested()
            val ctx = request.context ?: return@runCatching null
            if (!LyricsSettingsStore.isLocalGoogleTranslateEnabled(ctx)) return@runCatching null

            // 嘗試透過 LRCLIB 或 Netease 先取歌詞，再即時進行 Google 翻譯
            val baseResult = LrclibPlainLyricsProvider.fetch(request).getOrNull()
                ?: NeteasePlainLyricsProvider.fetch(request).getOrNull()
                ?: return@runCatching null

            request.cancellationToken?.throwIfCancellationRequested()
            val translated = GoogleTranslateService.translateLrcIfNeeded(
                context = ctx,
                plainLrc = baseResult.plainLrc,
                existingTranslatedLrc = baseResult.translatedLrc
            )

            baseResult.copy(
                plainProviderId = id,
                plainProviderName = name,
                translatedLrc = translated
            )
        }
    }
}
