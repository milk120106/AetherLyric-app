package com.andsi.airlyrics.core.model

/** User-facing lyrics lookup and display configuration. */
data class LyricsSettings(
    /** Selected online lookup sources in priority order. Empty disables online lookup. */
    val plainLyricsSearchSources: List<PlainLyricsSearchSource>,
    val autoSearchOnline: Boolean,
    val autoSaveLocal: Boolean,
    val contentDisplayMode: LyricsContentDisplayMode = LyricsContentDisplayMode.default,
    val lineDisplayMode: LyricsLineDisplayMode = LyricsLineDisplayMode.default,
    val switchAnimationMode: LyricsSwitchAnimationMode = LyricsSwitchAnimationMode.default,
    val wordByWordLyricsEnabled: Boolean = false
)

/** An online lookup source. Local lyrics are always checked first. */
enum class PlainLyricsSearchSource(val key: String, val supportsWordTiming: Boolean = false) {
    /** Persisted compatibility value. New UI models local-only behavior with [LyricsSettings.autoSearchOnline]. */
    LOCAL_ONLY("local_only"),
    LRCLIB("lrclib"),
    NETEASE("netease"),
    QQ("qq"),
    KUGOU("kugou"),
    MIGU("migu"),
    GENIUS("genius"),
    AZLYRICS("azlyrics"),
    LYRICS_OVH("lyrics_ovh"),
    MEGALOBIZ("megalobiz"),
    UTA_NET("uta_net"),
    PETIT_LYRICS("petit_lyrics"),
    BILIBILI("bilibili"),
    COMMUNITY("community"),
    GOOGLE_TRANSLATE("google_translate"),
    CUSTOM_JSON("custom_json"),
    MUSIXMATCH("musixmatch"),
    SPICY("spicy", supportsWordTiming = true);

    companion object {
        val default: PlainLyricsSearchSource = NETEASE
        val onlineSources: List<PlainLyricsSearchSource> = listOf(
            NETEASE,
            QQ,
            KUGOU,
            LRCLIB,
            MIGU,
            GENIUS,
            AZLYRICS,
            LYRICS_OVH,
            MEGALOBIZ,
            UTA_NET,
            PETIT_LYRICS,
            BILIBILI,
            COMMUNITY,
            GOOGLE_TRANSLATE,
            CUSTOM_JSON,
            MUSIXMATCH,
            SPICY
        )

        fun fromKeyOrNull(key: String?): PlainLyricsSearchSource? {
            return entries.firstOrNull { it.key == key }
        }
    }
}

/** Controls which lyric text should be rendered when translations are available. */
enum class LyricsContentDisplayMode(val key: String) {
    ORIGINAL_WITH_TRANSLATION("original_with_translation"),
    ORIGINAL_ONLY("original_only"),
    TRANSLATION_ONLY("translation_only");

    companion object {
        val default: LyricsContentDisplayMode = ORIGINAL_WITH_TRANSLATION

        fun fromKey(key: String?): LyricsContentDisplayMode {
            return entries.firstOrNull { it.key == key } ?: default
        }
    }
}

/** Controls how many neighboring lyric lines should be rendered around the current line. */
enum class LyricsLineDisplayMode(val key: String) {
    CURRENT_ONLY("current_only"),
    PREVIOUS_AND_CURRENT("previous_and_current"),
    CURRENT_AND_NEXT("current_and_next"),
    PREVIOUS_CURRENT_NEXT("previous_current_next");

    companion object {
        val default: LyricsLineDisplayMode = CURRENT_ONLY

        fun fromKey(key: String?): LyricsLineDisplayMode {
            return entries.firstOrNull { it.key == key } ?: default
        }
    }
}

/** Controls the animation used when the floating lyric text changes to a new line. */
enum class LyricsSwitchAnimationMode(val key: String) {
    NONE("none"),
    FADE("fade"),
    SLIDE_UP("slide_up"),
    SCALE_FADE("scale_fade");

    companion object {
        val default: LyricsSwitchAnimationMode = FADE

        fun fromKey(key: String?): LyricsSwitchAnimationMode {
            return entries.firstOrNull { it.key == key } ?: default
        }
    }
}
