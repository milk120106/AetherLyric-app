package com.andsi.airlyrics.ui.pages.media

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadata
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.andsi.airlyrics.R
import com.andsi.airlyrics.core.model.SongIdentity
import com.andsi.airlyrics.design.tokens.AirUiTokens
import com.andsi.airlyrics.lyrics.storage.LyricsVersionStore
import com.andsi.airlyrics.settings.store.AppSettingsStore
import com.andsi.airlyrics.ui.components.*
import com.andsi.airlyrics.ui.model.MainUiHost
import com.andsi.airlyrics.ui.theme.*

/**
 * 媒體首頁歌詞切換區塊 (MediaLyricsSelectorCard)
 * 依據開關 isMediaLyricsSelectorEnabled 顯示目前播放歌曲的「可用歌詞版本」區塊，
 * 點選即可直接切換該版本歌詞，懸浮窗同步刷新。
 */
internal fun createMediaLyricsSelectorCard(activity: MainUiHost): View? = with(activity) {
    if (!AppSettingsStore.isMediaLyricsSelectorEnabled(activity)) return null

    val pageState = mediaPageState()
    val title = pageState.selectedTitle?.trim().orEmpty()
    val artist = pageState.selectedArtist?.trim().orEmpty()
    if (title.isBlank()) return null

    val durationMs = pageState.selectedController?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
    val songIdentity = SongIdentity(title = title, artist = artist, album = "", durationMs = durationMs)

    val versions = LyricsVersionStore.getVersions(activity, songIdentity)
    if (versions.isEmpty()) return null

    val activeVersionId = LyricsVersionStore.getActiveVersionId(activity, songIdentity.storageKey())
        ?: versions.firstOrNull()?.versionId

    return card(activity) {
        addView(bigText(activity, getString(R.string.ui_current_song_versions)))
        addView(smallHint(activity, "點選直接切換懸浮窗歌詞版本與時間軸"))

        val versionsContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, dp(AirUiTokens.Space.Sm), 0, 0)
        }

        fun populateVersions(currentActiveId: String?) {
            versionsContainer.removeAllViews()
            versions.forEach { version ->
                val isActive = version.versionId == currentActiveId
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(
                        dp(AirUiTokens.Space.CardH),
                        dp(AirUiTokens.Space.CardV),
                        dp(AirUiTokens.Space.CardH),
                        dp(AirUiTokens.Space.CardV)
                    )
                    background = GradientDrawable().apply {
                        cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
                        setColor(if (isActive) colorAccent else colorSurfaceLight)
                        setStroke(dp(AirUiTokens.Stroke.Hairline), if (isActive) colorAccent else colorStroke)
                    }
                    val params = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 0, 0, dp(AirUiTokens.Space.Sm))
                    }
                    layoutParams = params
                    isClickable = true
                    isFocusable = true
                    enableSoftPressFeedback(AirUiTokens.Motion.DefaultPressScale)

                    // 版本名稱與來源資訊
                    val textLayout = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

                        addView(TextView(activity).apply {
                            text = version.versionName
                            textSize = AirUiTokens.TextSize.Button
                            typeface = if (isActive) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                            setTextColor(if (isActive) colorOnAccent else colorTextStrong)
                        })

                        val offsetStr = if (version.offsetMs != 0L) {
                            " · 偏移 ${if (version.offsetMs > 0) "+" else ""}${version.offsetMs}ms"
                        } else ""
                        addView(TextView(activity).apply {
                            text = "來源: ${version.source}$offsetStr"
                            textSize = AirUiTokens.TextSize.Caption
                            setTextColor(if (isActive) colorOnAccent else colorTextMuted)
                        })
                    }
                    addView(textLayout)

                    if (isActive) {
                        addView(airIconView(R.drawable.ic_air_check, colorIconOnAccent).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                dp(AirUiTokens.Layout.IconSize),
                                dp(AirUiTokens.Layout.IconSize)
                            )
                        })
                    }

                    setOnClickListener {
                        if (!isActive) {
                            LyricsVersionStore.switchVersion(activity, songIdentity, version.versionId)
                            uiActions.reloadFloatingLyrics()
                            Toast.makeText(
                                activity,
                                activity.getString(R.string.ui_version_applied, version.versionName),
                                Toast.LENGTH_SHORT
                            ).show()
                            populateVersions(version.versionId)
                        }
                    }
                }
                versionsContainer.addView(row)
            }
        }

        populateVersions(activeVersionId)
        addView(versionsContainer)
    }
}
