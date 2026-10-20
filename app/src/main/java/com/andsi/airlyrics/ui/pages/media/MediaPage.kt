package com.andsi.airlyrics.ui.pages.media

import com.andsi.airlyrics.R

import android.media.session.PlaybackState
import android.view.View
import com.andsi.airlyrics.ui.model.MainUiHost
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

    // AI 音訊歌詞辨識區塊：移至「目前媒體」與「使用中的播放器」下方，且預設隱藏，需在設定頁手動開啟才顯示
    if (com.andsi.airlyrics.settings.store.AppSettingsStore.isAudioAiTranscriptionEnabled(activity)) {
        container.addView(spacer(activity, 18))
        container.addView(createAudioAiTranscriptionCard(activity))
    }

    return com.andsi.airlyrics.ui.layout.ResponsivePage(activity, container, setOf(0, 1), "scroll.MEDIA.root")
}
