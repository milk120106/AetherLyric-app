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
    private const val KEY_AUTO_PAUSE_ON_GENERATION = "auto_pause_on_generation"
    private const val KEY_ANIMATION_EFFECTS_ENABLED = "animation_effects_enabled"
    private const val KEY_ANIMATION_DURATION_MS = "animation_duration_ms"

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

    fun isAutoPauseOnGenerationEnabled(context: Context): Boolean {
        return store(context).getBoolean(KEY_AUTO_PAUSE_ON_GENERATION, true)
    }

    fun setAutoPauseOnGenerationEnabled(context: Context, enabled: Boolean) {
        store(context).setBoolean(KEY_AUTO_PAUSE_ON_GENERATION, enabled)
    }

    fun isAnimationEffectsEnabled(context: Context): Boolean {
        return store(context).getBoolean(KEY_ANIMATION_EFFECTS_ENABLED, true)
    }

    fun setAnimationEffectsEnabled(context: Context, enabled: Boolean) {
        store(context).setBoolean(KEY_ANIMATION_EFFECTS_ENABLED, enabled)
    }

    fun getAnimationDurationMs(context: Context): Int {
        return store(context).getInt(KEY_ANIMATION_DURATION_MS, 300).coerceIn(0, 1000)
    }

    fun setAnimationDurationMs(context: Context, durationMs: Int) {
        store(context).setInt(KEY_ANIMATION_DURATION_MS, durationMs.coerceIn(0, 1000))
    }
}
