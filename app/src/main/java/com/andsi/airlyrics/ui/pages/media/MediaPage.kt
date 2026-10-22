package com.andsi.airlyrics.ui.pages.media

import com.andsi.airlyrics.R

import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.widget.SwitchCompat
import com.andsi.airlyrics.core.model.SongIdentity
import com.andsi.airlyrics.design.tokens.AirUiTokens
import com.andsi.airlyrics.ui.dialogs.showDualModeAudioSearchDialog
import com.andsi.airlyrics.ui.dialogs.showManualLyricsSearchBottomSheet
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

            val durationMs = selectedController.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

            val buttonRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, dp(AirUiTokens.Space.Sm), 0, 0)
                }
            }

            val searchManualBtn = actionButton(activity, "手動搜尋歌詞") {
                val songIdent = SongIdentity(
                    title = pageState.selectedTitle.orEmpty().ifBlank { title },
                    artist = pageState.selectedArtist.orEmpty(),
                    album = "",
                    durationMs = durationMs
                )
                showManualLyricsSearchBottomSheet(activity, songIdent) {
                    rebuildCurrentPage(animateContent = false, animateTabs = false)
                }
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(0, 0, dp(4), 0)
                }
            }

            val searchAudioBtn = actionButton(activity, "語音/內錄搜尋") {
                showDualModeAudioSearchDialog(activity)
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(4), 0, 0, 0)
                }
            }

            buttonRow.addView(searchManualBtn)
            buttonRow.addView(searchAudioBtn)
            addView(buttonRow)
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

            val buttonRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, dp(AirUiTokens.Space.Sm), 0, 0)
                }
            }

            val searchManualBtn = actionButton(activity, "手動搜尋歌詞") {
                var titleInput: android.widget.EditText? = null
                var artistInput: android.widget.EditText? = null
                showAirDialog(
                    title = "手動搜尋歌詞",
                    message = "輸入歌曲名稱與歌手進行多源歌詞搜尋",
                    positiveText = "搜尋",
                    negativeText = getString(R.string.ui_cancel),
                    body = {
                        titleInput = android.widget.EditText(activity).apply {
                            hint = "歌曲名稱（必填）"
                            textSize = AirUiTokens.TextSize.BodySmall
                            setTextColor(colorTextStrong)
                            setHintTextColor(colorTextMuted)
                            isSingleLine = true
                            background = android.graphics.drawable.GradientDrawable().apply {
                                cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
                                setColor(colorSurfaceLight)
                                setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
                            }
                            setPadding(dp(12), dp(10), dp(12), dp(10))
                        }
                        artistInput = android.widget.EditText(activity).apply {
                            hint = "歌手 / 演出者（選填）"
                            textSize = AirUiTokens.TextSize.BodySmall
                            setTextColor(colorTextStrong)
                            setHintTextColor(colorTextMuted)
                            isSingleLine = true
                            background = android.graphics.drawable.GradientDrawable().apply {
                                cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
                                setColor(colorSurfaceLight)
                                setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
                            }
                            setPadding(dp(12), dp(10), dp(12), dp(10))
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply {
                                setMargins(0, dp(AirUiTokens.Space.Sm), 0, 0)
                            }
                        }
                        addView(titleInput)
                        addView(artistInput)
                    },
                    onPositive = {
                        val inputTitle = titleInput?.text?.toString()?.trim().orEmpty()
                        val inputArtist = artistInput?.text?.toString()?.trim().orEmpty()
                        if (inputTitle.isBlank()) {
                            android.widget.Toast.makeText(activity, "請輸入歌曲名稱", android.widget.Toast.LENGTH_SHORT).show()
                            return@showAirDialog
                        }
                        val songIdent = SongIdentity(
                            title = inputTitle,
                            artist = inputArtist,
                            album = "",
                            durationMs = 0L
                        )
                        showManualLyricsSearchBottomSheet(activity, songIdent) {
                            rebuildCurrentPage(animateContent = false, animateTabs = false)
                        }
                    }
                )
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(0, 0, dp(4), 0)
                }
            }

            val searchAudioBtn = actionButton(activity, "語音/內錄搜尋") {
                showDualModeAudioSearchDialog(activity)
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(4), 0, 0, 0)
                }
            }

            buttonRow.addView(searchManualBtn)
            buttonRow.addView(searchAudioBtn)
            addView(buttonRow)
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

    // AI 音檔辨識卡片（內部整合開關）
    container.addView(spacer(activity, 18))
    container.addView(createAudioAiTranscriptionCard(activity))

    return com.andsi.airlyrics.ui.layout.ResponsivePage(activity, container, setOf(0, 1), "scroll.MEDIA.root")
}
