package com.andsi.airlyrics.settings.store

import android.content.Context
import com.andsi.airlyrics.core.prefs.prefs

/** Stores app-wide preferences that do not belong to a single feature area. */
object AppSettingsStore {
    private const val PREFS_NAME = "app_settings"
    // Keep the original key so upgrades preserve the user's existing preference.
    private const val KEY_STATUS_POPUPS_MUTED = "toaster_muted"
    private const val KEY_COMPACT_MODE = "compact_mode"
    private const val KEY_MEDIA_LYRICS_SELECTOR = "media_lyrics_selector"
    private const val KEY_AUDIO_AI_TRANSCRIPTION = "audio_ai_transcription_enabled"

    private fun store(context: Context) = prefs(context, PREFS_NAME)

    fun areStatusPopupsMuted(context: Context): Boolean {
        return store(context).getBoolean(KEY_STATUS_POPUPS_MUTED, false)
    }

    fun setStatusPopupsMuted(context: Context, muted: Boolean) {
        store(context).setBoolean(KEY_STATUS_POPUPS_MUTED, muted)
    }

    fun isCompactMode(context: Context): Boolean {
        return store(context).getBoolean(KEY_COMPACT_MODE, false)
    }

    fun setCompactMode(context: Context, enabled: Boolean) {
        store(context).setBoolean(KEY_COMPACT_MODE, enabled)
    }

    fun isMediaLyricsSelectorEnabled(context: Context): Boolean {
        return store(context).getBoolean(KEY_MEDIA_LYRICS_SELECTOR, true)
    }

    fun setMediaLyricsSelectorEnabled(context: Context, enabled: Boolean) {
        store(context).setBoolean(KEY_MEDIA_LYRICS_SELECTOR, enabled)
    }

    fun isAudioAiTranscriptionEnabled(context: Context): Boolean {
        return store(context).getBoolean(KEY_AUDIO_AI_TRANSCRIPTION, false)
    }

    fun setAudioAiTranscriptionEnabled(context: Context, enabled: Boolean) {
        store(context).setBoolean(KEY_AUDIO_AI_TRANSCRIPTION, enabled)
    }
}
