package com.andsi.airlyrics.ui.pages.media

import com.andsi.airlyrics.R

import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.widget.SwitchCompat
import com.andsi.airlyrics.core.model.LyricsContentDisplayMode
import com.andsi.airlyrics.core.model.SongIdentity
import com.andsi.airlyrics.design.tokens.AirUiTokens
import com.andsi.airlyrics.ui.dialogs.showManualLyricsSearchBottomSheet
import com.andsi.airlyrics.ui.model.MainUiHost
import com.andsi.airlyrics.ui.model.OptionItem
import com.andsi.airlyrics.ui.components.*
import com.andsi.airlyrics.ui.theme.*

internal fun createMediaPage(activity: MainUiHost, animateContent: Boolean = true): View = with(activity) createMediaPage@ {
    val container = pageContainer(activity, animateChanges = animateContent)
    val pageState = mediaPageState()
    val controllers = pageState.controllers
    val selectedPackage = pageState.selectedPackage
    val selectedController = pageState.selectedController
    val notificationAccessGranted = hasNotificationListenerAccess()

    val heading = sectionTitle(
        activity, 
        getString(R.string.ui_media),
        getString(R.string.ui_choose_the_player_airlyrics_follows)
    ).apply {
        setTag(R.id.interaction_anchor, "media.heading")
    }
    container.addView(heading)

    val currentMediaCard = card(activity) {
        setTag(R.id.interaction_anchor, "media.current")
        addView(label(activity, getString(R.string.ui_current_media), colorTextMuted))
        if (selectedController != null) {
            val title = pageState.selectedTitle
                .orEmpty()
                .ifBlank { getString(R.string.ui_unknown_song) }
            val artist = pageState.selectedArtist
                ?: getString(R.string.ui_unknown_artist)
            val appName = getAppName(selectedController.packageName)
            val state = getPlaybackStateText(selectedController.playbackState?.state)

            addView(expandableText(bigText(activity, title)))
            addView(expandableText(normalText(activity, "$artist · $appName")))
            addView(statusPill(activity, state, selectedController.playbackState?.state == PlaybackState.STATE_PLAYING))

            val durationMs = selectedController.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            addView(actionButton(activity, "手動搜尋歌詞（多源候選清單）") {
                val songIdent = SongIdentity(
                    title = pageState.selectedTitle.orEmpty().ifBlank { title },
                    artist = pageState.selectedArtist.orEmpty(),
                    album = "",
                    durationMs = durationMs
                )
                showManualLyricsSearchBottomSheet(activity, songIdent) {
                    rebuildCurrentPage(animateContent = false, animateTabs = false)
                }
            })
        } else {
            addView(bigText(activity, getString(R.string.ui_no_media_detected_yet)))
            val hintText = getString(
                if (notificationAccessGranted) {
                    R.string.ui_media_streams_empty_hint
                } else {
                    R.string.ui_enable_notif_access_hint
                }
            )
            addView(normalText(activity, hintText))
        }
    }
    container.addView(currentMediaCard)

    // 可用歌詞版本切換區塊
    createMediaLyricsSelectorCard(activity)?.let { selectorCard ->
        container.addView(spacer(activity, 12))
        container.addView(selectorCard)
    }

    // 歌詞顯示格式快速切換（僅原文 / 僅翻譯 / 雙語對照）
    container.addView(spacer(activity, 12))
    container.addView(createLyricsDisplayModeQuickCard(activity))

    val playersHeading = label(activity, getString(R.string.ui_active_players), colorTextMuted).apply {
        setTag(R.id.interaction_anchor, "media.players.heading")
    }
    container.addView(playersHeading)

    if (controllers.isEmpty()) {
        container.addView(
            card(activity) {
                addView(bigText(activity, getString(R.string.ui_waiting_for_music_signal)))
                addView(normalText(activity, getString(R.string.ui_media_streams_empty_hint)))
                if (!notificationAccessGranted) {
                    addView(smallHint(activity, getString(R.string.ui_media_empty_permission_hint)))
                }
            }
        )
    } else {
        controllers.forEach { controller ->
            container.addView(mediaSourceCard(controller, controller.packageName == selectedPackage).apply {
                setTag(R.id.interaction_anchor, "media.player.${controller.packageName}")
            })
        }
    }

    container.addView(spacer(activity, 18))
    val refreshBtn = refreshMediaButton().apply {
        setTag(R.id.interaction_anchor, "media.refresh")
    }
    container.addView(refreshBtn)

    // 媒體頁 AI 音訊歌詞辨識開關與功能區塊（自設定首頁移入媒體頁）
    container.addView(spacer(activity, 18))
    container.addView(createAiTranscriptionToggleCard(activity))

    if (com.andsi.airlyrics.settings.store.AppSettingsStore.isAudioAiTranscriptionEnabled(activity)) {
        container.addView(spacer(activity, 12))
        container.addView(createAudioAiTranscriptionCard(activity))
    }

    return com.andsi.airlyrics.ui.layout.ResponsivePage(activity, container, setOf(0, 1), "scroll.MEDIA.root")
}

private fun createLyricsDisplayModeQuickCard(activity: MainUiHost): View = with(activity) {
    val currentMode = lyricsContentDisplayMode()
    return card(activity) {
        addView(bigText(activity, "歌詞顯示格式"))
        addView(smallHint(activity, "快速切換懸浮窗與歌詞預覽的原文／翻譯／雙語顯示模式"))
        val options = listOf(
            OptionItem(
                title = getString(R.string.ui_original_only),
                selected = currentMode == LyricsContentDisplayMode.ORIGINAL_ONLY
            ) {
                setLyricsContentDisplayMode(LyricsContentDisplayMode.ORIGINAL_ONLY)
                notifyFloatingStyleChanged()
                uiActions.reloadFloatingLyrics()
                rebuildCurrentPage(animateContent = false, animateTabs = false)
            },
            OptionItem(
                title = getString(R.string.ui_translation_only),
                selected = currentMode == LyricsContentDisplayMode.TRANSLATION_ONLY
            ) {
                setLyricsContentDisplayMode(LyricsContentDisplayMode.TRANSLATION_ONLY)
                notifyFloatingStyleChanged()
                uiActions.reloadFloatingLyrics()
                rebuildCurrentPage(animateContent = false, animateTabs = false)
            },
            OptionItem(
                title = "雙語對照",
                selected = currentMode == LyricsContentDisplayMode.ORIGINAL_WITH_TRANSLATION
            ) {
                setLyricsContentDisplayMode(LyricsContentDisplayMode.ORIGINAL_WITH_TRANSLATION)
                notifyFloatingStyleChanged()
                uiActions.reloadFloatingLyrics()
                rebuildCurrentPage(animateContent = false, animateTabs = false)
            }
        )
        addView(optionGrid(options))
    }
}

private fun createAiTranscriptionToggleCard(activity: MainUiHost): View = with(activity) {
    val isAiTranscriptionEnabled = isAudioAiTranscriptionEnabled()
    return card(activity) {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isClickable = true
        isFocusable = true
        enableSoftPressFeedback(AirUiTokens.Motion.DefaultPressScale)

        val infoLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, dp(AirUiTokens.Space.Xl), 0)
            }
            addView(bigText(activity, "媒體頁 AI 歌詞辨識"))
            addView(smallHint(activity, "展開音訊上傳、參考歌詞強制對齊與影片網址 AI 辨識區塊"))
        }
        addView(infoLayout)

        val aiSwitch = SwitchCompat(activity).apply {
            isChecked = isAiTranscriptionEnabled
            applyAirThemeTint(activity)
            setOnCheckedChangeListener { _, checked ->
                if (checked != isAudioAiTranscriptionEnabled()) {
                    setAudioAiTranscriptionEnabled(checked)
                }
            }
        }
        addView(aiSwitch)

        setOnClickListener {
            aiSwitch.toggle()
        }
    }
}
