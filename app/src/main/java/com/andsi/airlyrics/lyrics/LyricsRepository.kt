package com.andsi.airlyrics.lyrics

import android.content.Context
import android.util.Log
import com.andsi.airlyrics.BuildConfig
import com.andsi.airlyrics.core.model.LyricsSearchMode
import com.andsi.airlyrics.core.model.LyricsSettings
import com.andsi.airlyrics.core.model.PlainLyricsSearchSource
import com.andsi.airlyrics.core.model.SongIdentity
import com.andsi.airlyrics.logging.AppLogger
import com.andsi.airlyrics.lyrics.parser.LrcParser
import com.andsi.airlyrics.lyrics.providers.AzLyricsPlainLyricsProvider
import com.andsi.airlyrics.lyrics.providers.CustomJsonPluginStore
import com.andsi.airlyrics.lyrics.providers.GeniusPlainLyricsProvider
import com.andsi.airlyrics.lyrics.providers.GoogleTranslateService
import com.andsi.airlyrics.lyrics.providers.InternationalTitleResolver
import com.andsi.airlyrics.lyrics.providers.KugouPlainLyricsProvider
import com.andsi.airlyrics.lyrics.providers.LocalPlainLyricsProvider
import com.andsi.airlyrics.lyrics.providers.LrclibPlainLyricsProvider
import com.andsi.airlyrics.lyrics.providers.MusixmatchPlainLyricsProvider
import com.andsi.airlyrics.lyrics.providers.NeteasePlainLyricsProvider
import com.andsi.airlyrics.lyrics.providers.QqMusicPlainLyricsProvider
import com.andsi.airlyrics.lyrics.providers.SpicyLyricsProvider
import com.andsi.airlyrics.lyrics.storage.LyricsStorage
import com.andsi.airlyrics.lyrics.storage.ManualLyricsSelectionStore
import com.andsi.airlyrics.network.aether.AetherLyricApiClient
import com.andsi.airlyrics.settings.store.AetherLyricServerStore
import com.andsi.airlyrics.settings.store.LyricsSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CancellationException
import kotlin.math.abs

/**
 * 並行搜尋之單筆候選歌詞項目 (LyricsSearchCandidate)。
 */
data class LyricsSearchCandidate(
    val providerId: String,
    val sourceName: String,
    val priorityRank: Int,
    val hasSyncedLrc: Boolean,
    val hasWordTiming: Boolean,
    val durationMs: Long,
    val latestTimestampMs: Long,
    val matchedTitle: String,
    val matchedArtist: String,
    val previewSnippet: String,
    val result: LyricsProviderResult
)

/**
 * 自動搜尋時彈出候選清單之通知事件。
 */
data class AutoSearchPopupEvent(
    val song: SongIdentity,
    val candidates: List<LyricsSearchCandidate>
)

/**
 * Central lyrics lookup entry point.
 *
 * Lookup order:
 * 1. Local plain lyrics, so imported/saved files always win.
 * 2. Server engine (when enabled; if SERVER_ONLY mode is active, local online providers are skipped).
 * 3. Selected online providers & custom JSON plugins in priority order (or parallel search).
 * 4. Optional local Google Translate when translation is absent and enabled by user.
 * 5. Optional local plain cache save for successful online results.
 */
object LyricsRepository {
    private val onlinePlainLyricsProviders = mapOf(
        PlainLyricsSearchSource.NETEASE to NeteasePlainLyricsProvider,
        PlainLyricsSearchSource.QQ to QqMusicPlainLyricsProvider,
        PlainLyricsSearchSource.KUGOU to KugouPlainLyricsProvider,
        PlainLyricsSearchSource.MUSIXMATCH to MusixmatchPlainLyricsProvider,
        PlainLyricsSearchSource.GENIUS to GeniusPlainLyricsProvider,
        PlainLyricsSearchSource.AZLYRICS to AzLyricsPlainLyricsProvider,
        PlainLyricsSearchSource.LRCLIB to LrclibPlainLyricsProvider,
        PlainLyricsSearchSource.SPICY to SpicyLyricsProvider
    )

    private val _autoSearchPopupEvents = MutableSharedFlow<AutoSearchPopupEvent>(extraBufferCapacity = 1)
    val autoSearchPopupEvents: SharedFlow<AutoSearchPopupEvent> = _autoSearchPopupEvents.asSharedFlow()

    /**
     * 並行查詢所有已啟用的歌詞來源（包含伺服器、內建音樂平台與自訂 JSON 外掛），
     * 並依據「1. 使用者設定之來源優先級 -> 2. 是否具備 LRC 時間軸 -> 3. 時長匹配度／時間戳完整度」排序回傳。
     */
    suspend fun searchAllSourcesParallel(
        context: Context,
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long = 0L,
        searchMode: LyricsSearchMode = LyricsSearchMode.Original
    ): List<LyricsSearchCandidate> = coroutineScope {
        val appContext = context.applicationContext
        val baseRequest = PlainLyricsSearchRequest(
            context = appContext,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs
        )
        val request = if (searchMode == LyricsSearchMode.International) {
            InternationalTitleResolver.resolve(baseRequest)
        } else {
            baseRequest
        }

        val serverEnabled = AetherLyricServerStore.isServerEnabled(appContext)
        val serverOnly = AetherLyricServerStore.isServerOnlyMode(appContext)
        val orderedSources = LyricsSettingsStore.getPlainLyricsSearchSources(appContext).distinct()
        val enabledPlugins = CustomJsonPluginStore.getPlugins(appContext).filter { it.enabled }

        val deferredTasks = buildList {
            // 1. 伺服器查詢
            if (serverEnabled) {
                add(async(Dispatchers.IO) {
                    val serverUrl = AetherLyricServerStore.getServerUrl(appContext)
                    val apiKey = AetherLyricServerStore.getAdminApiKey(appContext)
                    val durSec = if (request.durationMs > 0) request.durationMs / 1000f else null
                    val serverHit = AetherLyricApiClient.searchLyricsBlocking(
                        serverUrl = serverUrl,
                        title = request.title,
                        artist = request.artist,
                        duration = durSec,
                        apiKey = apiKey,
                        context = appContext
                    ).getOrNull()
                    if (serverHit != null && serverHit.hit && serverHit.lrcContent.isNotBlank()) {
                        val res = LyricsProviderResult(
                            plainProviderId = "server:${serverHit.source}",
                            plainProviderName = "伺服器 (${serverHit.source})",
                            plainLrc = serverHit.lrcContent,
                            matchedTitle = serverHit.trackTitle.ifBlank { request.title },
                            matchedArtist = serverHit.artist.ifBlank { request.artist },
                            matchedDurationMs = request.durationMs
                        )
                        toCandidate(res, priorityRank = 0, targetDurationMs = durationMs)
                    } else null
                })
            }

            // 2. 若非「僅伺服器模式」，並行查詢所有已啟用的內建來源與自訂 JSON 外掛
            if (!serverOnly) {
                orderedSources.forEachIndexed { index, source ->
                    val provider = onlinePlainLyricsProviders[source] ?: return@forEachIndexed
                    add(async(Dispatchers.IO) {
                        val res = runCatching { provider.fetch(request).getOrNull() }.getOrNull()
                        if (res != null && res.hasUsableLyrics()) {
                            toCandidate(res, priorityRank = index + 1, targetDurationMs = durationMs)
                        } else null
                    })
                }

                enabledPlugins.forEachIndexed { pIndex, plugin ->
                    add(async(Dispatchers.IO) {
                        val res = CustomJsonPluginStore.executePluginSearch(plugin, request).getOrNull()
                        if (res != null && res.hasUsableLyrics()) {
                            toCandidate(
                                res,
                                priorityRank = orderedSources.size + pIndex + 1,
                                targetDurationMs = durationMs
                            )
                        } else null
                    })
                }
            }
        }

        val rawCandidates = deferredTasks.awaitAll().filterNotNull()
        sortCandidatesByPriority(rawCandidates, durationMs)
    }

    /**
     * 依據嚴格優先級排序候選歌詞：
     * 1. 是否具備 LRC 時間軸 (hasSyncedLrc: true 優先於純文字)
     * 2. 使用者設定之來源優先順序 (priorityRank 越小越優先)
     * 3. 歌曲時長相近度 / 最新時間戳完整度
     */
    fun sortCandidatesByPriority(
        candidates: List<LyricsSearchCandidate>,
        targetDurationMs: Long
    ): List<LyricsSearchCandidate> {
        return candidates.sortedWith(
            compareBy<LyricsSearchCandidate> { it.priorityRank }
                .thenByDescending { it.hasSyncedLrc }
                .thenByDescending { it.hasWordTiming }
                .thenBy { candidate ->
                    if (targetDurationMs > 0L && candidate.durationMs > 0L) {
                        abs(candidate.durationMs - targetDurationMs)
                    } else {
                        Long.MAX_VALUE / 2
                    }
                }
                .thenByDescending { it.latestTimestampMs }
        )
    }

    private fun toCandidate(
        result: LyricsProviderResult,
        priorityRank: Int,
        targetDurationMs: Long
    ): LyricsSearchCandidate {
        val parsedLines = LrcParser.parse(result.plainLrc).filter { !it.isMetadata }
        val timedLines = parsedLines.filter { it.timeMs > 0L || result.plainLrc.contains("[00:") }
        val hasSynced = timedLines.isNotEmpty() || result.wordByWordLines.isNotEmpty()
        val maxTimeMs = timedLines.maxOfOrNull { it.timeMs } ?: 0L
        val effectiveDurationMs = when {
            result.matchedDurationMs > 0L -> result.matchedDurationMs
            maxTimeMs > 0L -> maxTimeMs
            else -> targetDurationMs
        }
        val previewLines = if (parsedLines.isNotEmpty()) {
            parsedLines.map { it.text.trim() }.filter { it.isNotBlank() }.take(3)
        } else {
            result.plainLrc.lines().map { it.trim() }.filter { it.isNotBlank() }.take(3)
        }
        val snippet = previewLines.joinToString(" / ").ifBlank { "（無預覽內容）" }

        return LyricsSearchCandidate(
            providerId = result.plainProviderId,
            sourceName = result.plainProviderName,
            priorityRank = priorityRank,
            hasSyncedLrc = hasSynced,
            hasWordTiming = result.wordByWordLines.isNotEmpty(),
            durationMs = effectiveDurationMs,
            latestTimestampMs = maxTimeMs,
            matchedTitle = result.matchedTitle,
            matchedArtist = result.matchedArtist,
            previewSnippet = snippet,
            result = result
        )
    }

    /**
     * 將使用者於 BottomSheet 選取的候選歌詞套用並寫入本機快取。
     */
    fun applySelectedCandidate(
        context: Context,
        song: SongIdentity,
        candidate: LyricsSearchCandidate,
        searchMode: LyricsSearchMode = LyricsSearchMode.Original
    ): LyricsProviderResult {
        val appContext = context.applicationContext
        val enriched = enrichWithLocalTranslationIfNeeded(appContext, candidate.result)
        return ManualLyricsSelectionStore.commit(
            context = appContext,
            song = song,
            mode = searchMode,
            result = enriched.copy(selectedManually = true),
            manual = true,
            savePlain = true
        )
    }

    internal fun enrichWithLocalTranslationIfNeeded(
        context: Context,
        result: LyricsProviderResult
    ): LyricsProviderResult {
        if (!LyricsSettingsStore.isLocalGoogleTranslateEnabled(context)) return result
        if (GoogleTranslateService.hasExistingTranslation(result.plainLrc, result.translatedLrc)) return result
        val translated = GoogleTranslateService.translateLrcIfNeeded(
            context = context,
            plainLrc = result.plainLrc,
            existingTranslatedLrc = result.translatedLrc
        )
        return if (!translated.isNullOrBlank()) {
            result.copy(translatedLrc = translated)
        } else {
            result
        }
    }

    fun findLyrics(
        context: Context,
        settings: LyricsSettings,
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long,
        bypassLocal: Boolean = false,
        forceSaveOnline: Boolean = false,
        ignoreAutoSearchSetting: Boolean = false,
        cancellationToken: LyricsLookupCancellationToken? = null,
        searchMode: LyricsSearchMode = LyricsSearchMode.Original,
        searchOrigin: LyricsSearchOrigin = LyricsSearchOrigin.Automatic,
        onInterimResult: ((LyricsProviderResult) -> Unit)? = null
    ): Result<LyricsProviderResult?> {
        val song = SongIdentity(title, artist, album, durationMs)
        val token = cancellationToken ?: LyricsLookupCancellationToken(song.storageKey(), 0L)
        val existing = LyricsSearchCoordinator.current(token)
        return runCatching {
            val lease = existing ?: LyricsSearchCoordinator.begin(song, token, searchOrigin, searchMode)
            try {
                LyricsRepositoryEngine(
                    localPlainLyricsProvider = LocalPlainLyricsProvider,
                    onlinePlainLyricsProviders = onlinePlainLyricsProviders,
                    settingsReader = { settings },
                    localPlainLyricsSaver = AndroidLocalPlainLyricsSaver,
                    wordByWordLyricsReader = AndroidWordByWordLyricsReader,
                    lookupLogger = AndroidLyricsLookupLogger,
                    commitOnlineResult = { request, rawResult ->
                        val enriched = enrichWithLocalTranslationIfNeeded(context.applicationContext, rawResult)
                        lease.commit {
                            ManualLyricsSelectionStore.commit(
                                context.applicationContext, song, request.searchMode, enriched,
                                manual = searchOrigin == LyricsSearchOrigin.Manual,
                                savePlain = settings.autoSaveLocal || forceSaveOnline
                            )
                        }
                    }
                ).findLyrics(
                    context = context,
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = durationMs,
                    bypassLocal = bypassLocal,
                    forceSaveOnline = forceSaveOnline,
                    ignoreAutoSearchSetting = ignoreAutoSearchSetting,
                    cancellationToken = token,
                    onInterimResult = onInterimResult,
                    searchMode = searchMode,
                    searchOrigin = searchOrigin
                ).getOrThrow().also { token.throwIfCancellationRequested() }
            } finally {
                if (existing == null) lease.close()
            }
        }
    }

    internal fun emitAutoSearchPopupEvent(song: SongIdentity, candidates: List<LyricsSearchCandidate>) {
        if (candidates.isNotEmpty()) {
            _autoSearchPopupEvents.tryEmit(AutoSearchPopupEvent(song, candidates))
        }
    }
}

internal class LyricsRepositoryEngine(
    private val localPlainLyricsProvider: PlainLyricsProvider,
    private val onlinePlainLyricsProviders: Map<PlainLyricsSearchSource, PlainLyricsProvider>,
    private val settingsReader: (Context) -> LyricsSettings,
    private val localPlainLyricsSaver: LocalPlainLyricsSaver,
    private val wordByWordLyricsReader: WordByWordLyricsReader,
    private val cachedOnlineLyricsReader: (LyricsProvider, PlainLyricsSearchRequest) -> LyricsProviderResult? = { provider, request -> provider.cached(request) },
    private val lookupLogger: LyricsLookupLogger = LyricsLookupLogger { _, _, _, _, _, _ -> },
    private val commitOnlineResult: ((PlainLyricsSearchRequest, LyricsProviderResult) -> LyricsProviderResult)? = null
) {
    fun findLyrics(
        context: Context,
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long,
        bypassLocal: Boolean = false,
        forceSaveOnline: Boolean = false,
        ignoreAutoSearchSetting: Boolean = false,
        cancellationToken: LyricsLookupCancellationToken? = null,
        searchMode: LyricsSearchMode = LyricsSearchMode.Original,
        searchOrigin: LyricsSearchOrigin = LyricsSearchOrigin.Automatic,
        onInterimResult: ((LyricsProviderResult) -> Unit)? = null
    ): Result<LyricsProviderResult?> {
        val appContext = context.applicationContext
        var request = PlainLyricsSearchRequest(
            context = appContext,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            cancellationToken = cancellationToken
        )

        return runCatching {
            cancellationToken?.throwIfCancellationRequested()
            val settings = settingsReader(context)
            fun commit(result: LyricsProviderResult): LyricsProviderResult {
                cancellationToken?.throwIfCancellationRequested()
                return commitOnlineResult?.invoke(request, result) ?: result.also {
                    if (it.access == null && (settings.autoSaveLocal || forceSaveOnline)) {
                        localPlainLyricsSaver.save(appContext, title, artist, album, durationMs, it)
                    }
                }
            }
            val wordByWordLyricsEnabled = settings.wordByWordLyricsEnabled

            val local = if (!bypassLocal) localPlainLyricsProvider.fetch(request).getOrThrow()?.let {
                attachLocalWordByWordLyricsIfAvailable(appContext, it, title, artist, durationMs, wordByWordLyricsEnabled)
            } else null

            val onlineAllowed = ignoreAutoSearchSetting || settings.autoSearchOnline
            val serverEnabled = AetherLyricServerStore.isServerEnabled(appContext)
            val serverOnly = AetherLyricServerStore.isServerOnlyMode(appContext)

            val wordSources = if (wordByWordLyricsEnabled && !serverOnly) {
                settings.plainLyricsSearchSources.distinct().filter {
                    onlinePlainLyricsProviders[it]?.supportsWordTiming == true
                }
            } else emptyList()
            val seekWords = wordSources.isNotEmpty()
            if (local != null && (local.manualLocal || !seekWords || local.wordByWordLines.isNotEmpty())) {
                return@runCatching LyricsRepository.enrichWithLocalTranslationIfNeeded(appContext, local)
            }
            if (!bypassLocal && seekWords) {
                for (source in wordSources) {
                    val cached = cachedOnlineLyricsReader(onlinePlainLyricsProviders.getValue(source), request)
                    cancellationToken?.throwIfCancellationRequested()
                    if (cached?.wordByWordLines?.isNotEmpty() == true) return@runCatching cached
                }
            }
            if (!onlineAllowed) return@runCatching local
            if (searchMode == LyricsSearchMode.International) {
                request = InternationalTitleResolver.resolve(request)
            }
            cancellationToken?.reportSearchMode(request.searchMode)
            if (local != null) {
                cancellationToken?.throwIfCancellationRequested()
                onInterimResult?.invoke(local)
            }

            // 若使用者開啟「自動搜尋時彈出結果清單」，並行取得所有候選結果並發送彈窗通知
            if (searchOrigin == LyricsSearchOrigin.Automatic && LyricsSettingsStore.isAutoSearchPopupEnabled(appContext)) {
                val parallelCandidates = runBlocking(Dispatchers.IO) {
                    LyricsRepository.searchAllSourcesParallel(
                        context = appContext,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = durationMs,
                        searchMode = searchMode
                    )
                }
                if (parallelCandidates.isNotEmpty()) {
                    val song = SongIdentity(title, artist, album, durationMs)
                    LyricsRepository.emitAutoSearchPopupEvent(song, parallelCandidates)
                    val best = parallelCandidates.first().result
                    return@runCatching commit(best)
                }
            }

            // 1. 若啟用伺服器，優先向伺服器檢索歌詞
            if (serverEnabled) {
                val serverUrl = AetherLyricServerStore.getServerUrl(appContext)
                val apiKey = AetherLyricServerStore.getAdminApiKey(appContext)
                val durSec = if (request.durationMs > 0) request.durationMs / 1000f else null
                val serverHit = AetherLyricApiClient.searchLyricsBlocking(
                    serverUrl = serverUrl,
                    title = request.title,
                    artist = request.artist,
                    duration = durSec,
                    apiKey = apiKey,
                    context = appContext
                ).getOrNull()
                cancellationToken?.throwIfCancellationRequested()
                if (serverHit != null && serverHit.hit && serverHit.lrcContent.isNotBlank()) {
                    val serverResult = LyricsProviderResult(
                        plainProviderId = "server:${serverHit.source}",
                        plainProviderName = "伺服器 (${serverHit.source})",
                        plainLrc = serverHit.lrcContent,
                        matchedTitle = serverHit.trackTitle.ifBlank { request.title },
                        matchedArtist = serverHit.artist.ifBlank { request.artist },
                        matchedDurationMs = request.durationMs
                    )
                    return@runCatching commit(serverResult)
                }
                // 若為「僅伺服器模式」，直接結束而不執行本地多源搜尋
                if (serverOnly) {
                    AppLogger.i("LyricsRepository", "僅伺服器模式：伺服器未命中歌詞，已略過本地多源搜尋")
                    return@runCatching local
                }
            }

            val prefetched = mutableMapOf<PlainLyricsSearchSource, LyricsProviderResult?>()
            var wordLookupError: Throwable? = null
            for (source in wordSources) {
                val result = try {
                    onlinePlainLyricsProviders.getValue(source).fetch(request).getOrThrow()
                } catch (e: Exception) {
                    cancellationToken?.throwIfCancellationRequested()
                    if (e is CancellationException) throw e
                    wordLookupError = e
                    prefetched[source] = null
                    continue
                }
                cancellationToken?.throwIfCancellationRequested()
                prefetched[source] = result
                if (result?.wordByWordLines?.isNotEmpty() == true) return@runCatching commit(result)
            }
            if (seekWords && local != null) return@runCatching local
            val lineFallback = prefetched.values.firstOrNull { it != null }
            val sources = settings.plainLyricsSearchSources
            val onlinePlainLyricsResult = try {
                findOnlinePlainLyrics(sources, request, prefetched)
                    ?: CustomJsonPluginStore.findFirstFromEnabledPlugins(request)
                    ?: lineFallback
            } catch (error: Exception) {
                cancellationToken?.throwIfCancellationRequested()
                val pluginFallback = runCatching { CustomJsonPluginStore.findFirstFromEnabledPlugins(request) }.getOrNull()
                if (pluginFallback != null) {
                    pluginFallback
                } else {
                    if (error is CancellationException || lineFallback == null) throw error
                    lineFallback
                }
            }
            if (onlinePlainLyricsResult == null && sources.all { it in wordSources } && wordLookupError != null) throw wordLookupError
            cancellationToken?.throwIfCancellationRequested()
            onlinePlainLyricsResult?.let { result ->
                val committed = commit(result)
                if (forceSaveOnline) committed else
                    attachLocalWordByWordLyricsIfAvailable(appContext, committed, title, artist, durationMs, wordByWordLyricsEnabled)
            }
        }
    }

    private fun findOnlinePlainLyrics(
        sources: List<PlainLyricsSearchSource>,
        request: PlainLyricsSearchRequest,
        preloaded: Map<PlainLyricsSearchSource, LyricsProviderResult?> = emptyMap()
    ): LyricsProviderResult? {
        val orderedSources = sources.distinct()
        var plainTextFallback: LyricsProviderResult? = null

        for ((index, source) in orderedSources.withIndex()) {
            request.cancellationToken?.throwIfCancellationRequested()
            val provider = checkNotNull(onlinePlainLyricsProviders[source]) {
                "No online lyrics provider registered for source: ${source.key}"
            }
            lookupLogger.logProviderProgress(
                provider = provider,
                priority = index + 1,
                total = orderedSources.size,
                stage = LyricsLookupProviderStage.ATTEMPT,
                debug = BuildConfig.DEBUG
            )

            val result = try {
                if (source in preloaded) preloaded[source] else provider.fetch(request).getOrThrow()
            } catch (error: Throwable) {
                request.cancellationToken?.throwIfCancellationRequested()
                if (error is CancellationException) {
                    throw error
                }
                Log.e("LyricsRepository", "來源 ${source.key} (${provider.name}) 查詢失敗: ${error.message}", error)
                AppLogger.e("LyricsRepository", "來源 ${source.key} (${provider.name}) 查詢失敗: ${error.message}", error)
                lookupLogger.logProviderFailure(
                    provider = provider,
                    title = request.title,
                    artist = request.artist,
                    durationMs = request.durationMs,
                    error = error,
                    debug = BuildConfig.DEBUG
                )
                if (provider.continueOnFailure && index < orderedSources.lastIndex) continue
                throw error
            }

            request.cancellationToken?.throwIfCancellationRequested()
            if (result == null || !result.hasUsableLyrics()) {
                lookupLogger.logProviderProgress(
                    provider = provider,
                    priority = index + 1,
                    total = orderedSources.size,
                    stage = LyricsLookupProviderStage.MISS,
                    debug = BuildConfig.DEBUG
                )
                continue
            }

            // 檢查是否具備 LRC 時間軸標籤；若具備則立即命中，若僅為純文字則暫存為備案並繼續尋找具備時間軸之來源
            val hasSyncedTimers = LrcParser.parse(result.plainLrc).any { !it.isMetadata && it.timeMs > 0L } ||
                result.plainLrc.contains("[00:")
            if (hasSyncedTimers) {
                lookupLogger.logProviderProgress(
                    provider = provider,
                    priority = index + 1,
                    total = orderedSources.size,
                    stage = LyricsLookupProviderStage.MATCH,
                    debug = BuildConfig.DEBUG
                )
                return result
            } else if (plainTextFallback == null) {
                plainTextFallback = result
            }
        }
        return plainTextFallback
    }

    private fun attachLocalWordByWordLyricsIfAvailable(
        context: Context,
        result: LyricsProviderResult,
        title: String,
        artist: String,
        durationMs: Long,
        enabled: Boolean
    ): LyricsProviderResult {
        if (!enabled || result.access != null || result.selectedManually) return result

        val cachedWordByWordLines = wordByWordLyricsReader.read(
            context = context,
            title = title,
            artist = artist,
            durationMs = durationMs
        )
        if (cachedWordByWordLines.isEmpty()) return result

        return result.copy(wordByWordLines = cachedWordByWordLines)
    }
}

internal fun interface LocalPlainLyricsSaver {
    fun save(
        context: Context,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        plainLyricsResult: LyricsProviderResult
    )
}

internal fun interface WordByWordLyricsReader {
    fun read(context: Context, title: String, artist: String, durationMs: Long): List<WordByWordLine>
}

internal fun interface LyricsLookupLogger {
    fun logProviderFailure(
        provider: PlainLyricsProvider,
        title: String,
        artist: String,
        durationMs: Long,
        error: Throwable,
        debug: Boolean
    )

    fun logProviderProgress(
        provider: PlainLyricsProvider,
        priority: Int,
        total: Int,
        stage: LyricsLookupProviderStage,
        debug: Boolean
    ) = Unit
}

internal enum class LyricsLookupProviderStage(val logValue: String) {
    ATTEMPT("provider_attempt"),
    MISS("provider_miss"),
    MATCH("provider_match")
}

private object AndroidLocalPlainLyricsSaver : LocalPlainLyricsSaver {
    override fun save(
        context: Context,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        plainLyricsResult: LyricsProviderResult
    ) {
        if (plainLyricsResult.access != null || LyricsStorage.hasWordByWordLyrics(context, title, artist, durationMs)) {
            return
        }

        LyricsStorage.savePlainLyrics(
            context = context,
            title = title,
            artist = artist,
            duration = durationMs,
            plainLrc = LrcParser.mergeOriginalAndTranslationForStorage(
                plainLrc = plainLyricsResult.plainLrc,
                translatedLrc = plainLyricsResult.translatedLrc
            ),
            album = album,
            plainSource = LyricsStorage.SOURCE_DOWNLOADED,
            plainProvider = plainLyricsResult.plainProviderId,
            overwrite = true
        )
    }
}

private object AndroidWordByWordLyricsReader : WordByWordLyricsReader {
    override fun read(context: Context, title: String, artist: String, durationMs: Long): List<WordByWordLine> {
        return LyricsStorage.readWordByWordLyrics(
            context = context,
            title = title,
            artist = artist,
            duration = durationMs
        )
    }
}

private object AndroidLyricsLookupLogger : LyricsLookupLogger {
    override fun logProviderFailure(
        provider: PlainLyricsProvider,
        title: String,
        artist: String,
        durationMs: Long,
        error: Throwable,
        debug: Boolean
    ) {
        if (debug) {
            Log.w(
                "AirLyricsLyrics",
                "${provider.name} lookup failed: title=$title artist=$artist durationMs=$durationMs",
                error
            )
        } else {
            Log.w("AirLyricsLyrics", "${provider.name} lookup failed", error)
        }
    }

    override fun logProviderProgress(
        provider: PlainLyricsProvider,
        priority: Int,
        total: Int,
        stage: LyricsLookupProviderStage,
        debug: Boolean
    ) {
        if (!debug) return
        Log.d(
            "AirLyricsLyrics",
            "stage=${stage.logValue} source=${provider.id} priority=$priority/$total"
        )
    }
}
