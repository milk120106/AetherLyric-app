package com.andsi.airlyrics.ui.pages.settings

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.andsi.airlyrics.ui.state.confirmOperation
import com.andsi.airlyrics.ui.model.ConfirmationAction
import com.andsi.airlyrics.R
import com.andsi.airlyrics.core.model.PlainLyricsSearchSource
import com.andsi.airlyrics.i18n.localizedPlainLyricsSourceCompactTitle
import com.andsi.airlyrics.i18n.localizedPlainLyricsSourceTitle
import com.andsi.airlyrics.ui.components.*
import com.andsi.airlyrics.ui.model.LyricsSettingsUiState
import com.andsi.airlyrics.ui.model.MainUiHost
import com.andsi.airlyrics.design.tokens.AirUiTokens
import com.andsi.airlyrics.ui.theme.colorAccent
import com.andsi.airlyrics.ui.theme.colorOnAccent
import com.andsi.airlyrics.ui.theme.colorStroke
import com.andsi.airlyrics.ui.theme.colorSurfaceLight
import com.andsi.airlyrics.ui.theme.colorTextMuted
import com.andsi.airlyrics.ui.theme.colorTextStrong

internal fun createLyricsSettingsPage(activity: MainUiHost): View  = with(activity) createLyricsSettingsPage@ {
    val container = pageContainer(activity, animateChanges = false)
    val settings = lyricsSettingsState()

    container.addView(settingsBackHeader(getString(R.string.ui_lyrics)))

    val currentLyricsCard = createCurrentLyricsCard(activity)
    container.addView(currentLyricsCard.view)

    container.addView(
        card(activity) {
            addView(bigText(activity, getString(R.string.ui_search_strategy)))

            val autoSearchButton = actionButton(activity, getString(if (settings.autoSearchOnline) R.string.ui_online_fallback_on else R.string.ui_online_fallback_off)) { }
            autoSearchButton.setOnClickListener {
                val enabled = uiActions.toggleLyricsAutoSearch()
                autoSearchButton.setText(if (enabled) R.string.ui_online_fallback_on else R.string.ui_online_fallback_off)
                playLocalRefreshFeedback(activity, autoSearchButton, null, getString(R.string.ui_updated))
            }
            addView(autoSearchButton)

            val autoSaveButton = actionButton(activity, getString(if (settings.autoSaveLocal) R.string.ui_auto_save_on else R.string.ui_auto_save_off)) { }
            autoSaveButton.setOnClickListener {
                val enabled = uiActions.toggleLyricsAutoSave()
                autoSaveButton.setText(if (enabled) R.string.ui_auto_save_on else R.string.ui_auto_save_off)
                playLocalRefreshFeedback(activity, autoSaveButton, null, getString(R.string.ui_updated))
            }
            addView(autoSaveButton)
        }
    )

    // AetherLyric 自架伺服器雙引擎模式設定卡片
    container.addView(createAetherLyricServerCard(activity))

    container.addView(createLyricsSourceOrderCard(activity, settings))

    container.addView(
        card(activity) {
            addView(bigText(activity, getString(R.string.ui_local_lyrics_folder)))
            addView(normalText(activity, getString(R.string.ui_save_folder) + settings.lyricsDirectoryPath))
            addView(smallHint(activity, getString(R.string.ui_lyrics_folder_scope_hint)))
            addView(actionButton(activity, getString(R.string.ui_choose_lyrics_save_folder)) {
                uiActions.selectLyricsDirectory()
            })
            addView(actionButton(activity, getString(R.string.ui_copy_lyrics_save_folder)) {
                uiActions.copyLyricsDirectory()
            })
        }
    )

    val recentLyricsCard = createRecentLyricsCard(activity)
    container.addView(recentLyricsCard.view)

    container.addView(
        card(activity) {
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL

                addView(bigText(activity, getString(R.string.ui_delete_all_saved_lyrics)).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                })
                addView(dangerActionButton(activity, getString(R.string.ui_delete)) {
                    activity.confirmOperation(
                        action = ConfirmationAction.DeleteAll,
                        title = getString(R.string.ui_delete_all_saved_lyrics_confirm),
                        message = getString(R.string.ui_delete_all_saved_lyrics_message),
                        positiveText = getString(R.string.ui_delete)
                    )
                }.apply {
                    (layoutParams as LinearLayout.LayoutParams).setMargins(
                        dp(AirUiTokens.Space.Xxl),
                        0,
                        0,
                        0
                    )
                })
            })
        }
    )

    lyricsSettingsContentRefresh = {
        currentLyricsCard.refreshContent()
        recentLyricsCard.refreshContent()
    }

    listOf("heading", "current", "search", "server", "sources", "directory", "recent", "delete").forEachIndexed { index, name ->
        container.getChildAt(index)?.setTag(R.id.interaction_anchor, "lyrics.settings.$name")
    }
    return com.andsi.airlyrics.ui.layout.ResponsivePage(activity, container, setOf(0, 1, 6), "scroll.SETTINGS.LYRICS")
}

internal fun createLyricsSourceOrderCard(
    activity: MainUiHost,
    settings: LyricsSettingsUiState,
    maxDisplayed: Int? = null
): View = with(activity) {
    card(activity) {
        var selectedSources = settings.selectedPlainLyricsSources.distinct()

        addView(bigText(activity, getString(R.string.ui_plain_lyrics_source)))
        if (maxDisplayed != null) {
            addView(smallHint(activity, "顯示常用來源，點擊「更多來源…」可彈出選單調整完整排序與啟用狀態"))
        }

        lateinit var refreshSourceOptions: (Boolean) -> Unit
        val sourceButtons = settings.plainLyricsSourceOptions.associateWith { source ->
            LyricsSourceOptionButton(
                host = activity,
                source = source,
                compactTitle = localizedPlainLyricsSourceCompactTitle(source)
            ).apply {
                setOnClickListener {
                    val updatedSources = toggleOrderedPlainLyricsSource(
                        selectedSources = selectedSources,
                        source = source
                    )
                    selectedSources = updatedSources
                    uiActions.setPlainLyricsSources(selectedSources)
                    refreshSourceOptions(true)
                    playTinyPulse(this)
                }
            }
        }
        val sourceGrid = LyricsSourceOrderRow(activity)
        refreshSourceOptions = { animateOrder ->
            sourceButtons.forEach { (source, button) ->
                val selectedIndex = selectedSources.indexOf(source)
                button.render(
                    selectedPriority = (selectedIndex + 1).takeIf { selectedIndex >= 0 },
                    fullTitle = localizedPlainLyricsSourceTitle(source)
                )
            }
            val orderedSources = orderedLyricsSourceOptions(
                selectedSources = selectedSources,
                sourceOptions = settings.plainLyricsSourceOptions
            )
            val displayedSources = if (maxDisplayed != null) orderedSources.take(maxDisplayed) else orderedSources
            sourceGrid.setSourceOrder(
                orderedButtons = displayedSources.map(sourceButtons::getValue),
                animate = animateOrder
            )
        }

        addView(sourceGrid)
        refreshSourceOptions(false)

        if (maxDisplayed != null) {
            // 更多來源按鈕：點擊後彈出選單 Dialog
            addView(actionButton(activity, "更多來源…") {
                showAllLyricsSourcesDialog(activity, settings.plainLyricsSourceOptions) { updated ->
                    selectedSources = updated
                    refreshSourceOptions(true)
                }
            })
        }
    }
}

/**
 * 彈窗選單 (Dialog) 管理所有歌詞來源與啟用排序
 */
internal fun showAllLyricsSourcesDialog(
    activity: MainUiHost,
    allOptions: List<PlainLyricsSearchSource>,
    onChanged: (List<PlainLyricsSearchSource>) -> Unit
) = with(activity) {
    showAirDialog(
        title = "歌詞來源管理與排序",
        message = "點擊來源以勾選或停用，已啟用的來源將按優先順序進行檢索",
        positiveText = getString(R.string.ui_ok),
        body = {
            var currentSelected = lyricsSettingsState().selectedPlainLyricsSources.distinct()
            val listContainer = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
            }
            addView(listContainer)

            fun renderItems() {
                listContainer.removeAllViews()
                val ordered = orderedLyricsSourceOptions(currentSelected, allOptions)
                ordered.forEach { source ->
                    val isEnabled = source in currentSelected
                    val priority = if (isEnabled) currentSelected.indexOf(source) + 1 else null

                    val row = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        isClickable = true
                        isFocusable = true
                        enableSoftPressFeedback(AirUiTokens.Motion.DefaultPressScale)
                        setPadding(
                            dp(AirUiTokens.Space.CardH),
                            dp(AirUiTokens.Space.CardV),
                            dp(AirUiTokens.Space.CardH),
                            dp(AirUiTokens.Space.CardV)
                        )
                        background = GradientDrawable().apply {
                            cornerRadius = dp(AirUiTokens.Radius.Sm).toFloat()
                            setColor(if (isEnabled) colorSurfaceLight else android.graphics.Color.TRANSPARENT)
                            setStroke(dp(AirUiTokens.Stroke.Hairline), if (isEnabled) colorAccent else colorStroke)
                        }
                        val params = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, dp(AirUiTokens.Space.Xs), 0, dp(AirUiTokens.Space.Xs))
                        }
                        layoutParams = params

                        addView(TextView(activity).apply {
                            text = if (priority != null) "#$priority" else "-"
                            textSize = AirUiTokens.TextSize.BodySmall
                            typeface = Typeface.DEFAULT_BOLD
                            setTextColor(if (isEnabled) colorAccent else colorTextMuted)
                            layoutParams = LinearLayout.LayoutParams(dp(36), ViewGroup.LayoutParams.WRAP_CONTENT)
                        })

                        addView(TextView(activity).apply {
                            text = localizedPlainLyricsSourceTitle(source)
                            textSize = AirUiTokens.TextSize.Body
                            typeface = if (isEnabled) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                            setTextColor(if (isEnabled) colorTextStrong else colorTextMuted)
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        })

                        addView(TextView(activity).apply {
                            text = if (isEnabled) "已啟用" else "未啟用"
                            textSize = AirUiTokens.TextSize.Caption
                            typeface = Typeface.DEFAULT_BOLD
                            setTextColor(if (isEnabled) colorOnAccent else colorTextMuted)
                            setPadding(dp(AirUiTokens.Space.Sm), dp(AirUiTokens.Space.Xxs), dp(AirUiTokens.Space.Sm), dp(AirUiTokens.Space.Xxs))
                            background = GradientDrawable().apply {
                                cornerRadius = dp(AirUiTokens.Radius.Pill).toFloat()
                                setColor(if (isEnabled) colorAccent else colorSurfaceLight)
                            }
                        })

                        setOnClickListener {
                            currentSelected = toggleOrderedPlainLyricsSource(currentSelected, source)
                            uiActions.setPlainLyricsSources(currentSelected)
                            renderItems()
                            onChanged(currentSelected)
                        }
                    }
                    listContainer.addView(row)
                }
            }

            renderItems()
        }
    )
}

internal fun toggleOrderedPlainLyricsSource(
    selectedSources: List<PlainLyricsSearchSource>,
    source: PlainLyricsSearchSource
): List<PlainLyricsSearchSource> {
    val normalizedSources = selectedSources.distinct()
    return if (source in normalizedSources) {
        normalizedSources - source
    } else {
        normalizedSources + source
    }
}
