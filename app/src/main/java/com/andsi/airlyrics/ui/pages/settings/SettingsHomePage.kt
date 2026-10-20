package com.andsi.airlyrics.ui.pages.settings

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import com.andsi.airlyrics.R
import com.andsi.airlyrics.design.tokens.AirUiTokens
import com.andsi.airlyrics.external.ExternalControlStore
import com.andsi.airlyrics.i18n.localizedPlainLyricsSourceList
import com.andsi.airlyrics.settings.store.AetherLyricServerStore
import com.andsi.airlyrics.settings.store.AppSettingsStore
import com.andsi.airlyrics.settings.store.LyricsOffsetStore
import com.andsi.airlyrics.ui.components.*
import com.andsi.airlyrics.ui.model.MainUiHost
import com.andsi.airlyrics.ui.navigation.SettingsSubPage
import com.andsi.airlyrics.ui.theme.*

internal fun createSettingsHomePage(activity: MainUiHost): View = with(activity) createSettingsHomePage@ {
    val container = pageContainer(activity, animateChanges = false)
    val lyricsSettings = lyricsSettingsState()
    val isCompact = AppSettingsStore.isCompactMode(activity)

    // 頁面頂端標題列與主題/夜間模式開關
    container.addView(settingsHomeHeader())

    // 1. 搜尋策略卡片
    container.addView(
        card(activity) {
            addView(bigText(activity, getString(R.string.ui_search_strategy)))

            val autoSearchButton = actionButton(
                activity,
                getString(if (lyricsSettings.autoSearchOnline) R.string.ui_online_fallback_on else R.string.ui_online_fallback_off)
            ) { }
            autoSearchButton.setOnClickListener {
                val enabled = uiActions.toggleLyricsAutoSearch()
                autoSearchButton.setText(if (enabled) R.string.ui_online_fallback_on else R.string.ui_online_fallback_off)
                playLocalRefreshFeedback(activity, autoSearchButton, null, getString(R.string.ui_updated))
            }
            addView(autoSearchButton)

            val autoSaveButton = actionButton(
                activity,
                getString(if (lyricsSettings.autoSaveLocal) R.string.ui_auto_save_on else R.string.ui_auto_save_off)
            ) { }
            autoSaveButton.setOnClickListener {
                val enabled = uiActions.toggleLyricsAutoSave()
                autoSaveButton.setText(if (enabled) R.string.ui_auto_save_on else R.string.ui_auto_save_off)
                playLocalRefreshFeedback(activity, autoSaveButton, null, getString(R.string.ui_updated))
            }
            addView(autoSaveButton)
        }
    )

    // 2. 全域時間軸偏移量卡片
    container.addView(
        card(activity) {
            var currentGlobalOffset = LyricsOffsetStore.getGlobalOffsetMs(activity)
            var isGlobalEnabled = LyricsOffsetStore.isGlobalOffsetEnabled(activity)

            val headerRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val titleView = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(bigText(activity, getString(R.string.ui_apply_offset_to_all_songs)))
                addView(smallHint(activity, if (isGlobalEnabled) "目前套用全域時間軸" else "目前使用單曲專屬時間軸"))
            }

            val globalSwitch = SwitchCompat(activity).apply {
                isChecked = isGlobalEnabled
                applyAirThemeTint(activity)
                setOnCheckedChangeListener { _, checked ->
                    isGlobalEnabled = checked
                    LyricsOffsetStore.setGlobalOffsetEnabled(activity, checked)
                    (titleView.getChildAt(1) as? TextView)?.text =
                        if (checked) "目前套用全域時間軸" else "目前使用單曲專屬時間軸"
                }
            }
            headerRow.addView(titleView)
            headerRow.addView(globalSwitch)
            addView(headerRow)

            // 數值顯示
            val offsetValueView = TextView(activity).apply {
                text = "${if (currentGlobalOffset > 0) "+" else ""}${currentGlobalOffset} ms"
                textSize = AirUiTokens.TextSize.PageTitle
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colorAccent)
                gravity = Gravity.CENTER
                setPadding(0, dp(AirUiTokens.Space.Sm), 0, dp(AirUiTokens.Space.Sm))
            }
            addView(offsetValueView)

            // 微調按鈕列
            val buttonsRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            fun updateOffset(delta: Long) {
                currentGlobalOffset = if (delta == 0L) 0L else (currentGlobalOffset + delta)
                LyricsOffsetStore.saveGlobalOffset(activity, currentGlobalOffset)
                globalSwitch.isChecked = true
                offsetValueView.text = "${if (currentGlobalOffset > 0) "+" else ""}${currentGlobalOffset} ms"
            }

            val steps = listOf(-500L to "-500", -100L to "-100", 0L to "0", 100L to "+100", 500L to "+500")
            steps.forEach { (delta, label) ->
                val btn = TextView(activity).apply {
                    text = label
                    textSize = AirUiTokens.TextSize.BodySmall
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(colorTextStrong)
                    gravity = Gravity.CENTER
                    setPadding(dp(4), dp(AirUiTokens.Space.Sm), dp(4), dp(AirUiTokens.Space.Sm))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(AirUiTokens.Radius.Sm).toFloat()
                        setColor(colorSurfaceLight)
                        setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
                    }
                    val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        setMargins(dp(2), 0, dp(2), 0)
                    }
                    layoutParams = params
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { updateOffset(delta) }
                }
                buttonsRow.addView(btn)
            }
            addView(buttonsRow)
        }
    )

    // 3. 簡潔模式開關卡片
    container.addView(
        card(activity) {
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
                addView(bigText(activity, getString(R.string.ui_compact_mode)))
                addView(smallHint(activity, getString(R.string.ui_compact_mode_desc)))
            }
            addView(infoLayout)

            val compactSwitch = SwitchCompat(activity).apply {
                isChecked = isCompact
                applyAirThemeTint(activity)
                setOnCheckedChangeListener { _, checked ->
                    AppSettingsStore.setCompactMode(activity, checked)
                }
            }
            addView(compactSwitch)

            setOnClickListener {
                compactSwitch.isChecked = !compactSwitch.isChecked
            }
        }
    )

    // 4. 子頁面入口卡片清單 (透過 AdaptiveGridLayout 網格佈局)
    val subpageGrid = AdaptiveGridLayout(
        activity,
        2,
        dp(24),
        dp(12),
        columnPolicy = GridColumnPolicy.Width(360)
    )

    // 子頁面 1: AetherLyric 伺服器
    subpageGrid.addView(
        settingsCategoryCard(
            title = getString(R.string.ui_server_subpage_title),
            subtitle = if (isCompact) "" else getString(R.string.ui_server_subpage_desc),
            status = if (AetherLyricServerStore.isServerEnabled(activity)) "雙引擎啟用" else "未啟用",
            iconRes = R.drawable.ic_air_sync_alt
        ) {
            uiActions.openSettingsSubPage(SettingsSubPage.SERVER)
        }
    )

    // 子頁面 2: 歌詞來源管理
    subpageGrid.addView(
        settingsCategoryCard(
            title = getString(R.string.ui_sources_subpage_title),
            subtitle = if (isCompact) "" else getString(R.string.ui_sources_subpage_desc),
            status = localizedPlainLyricsSourceList(lyricsSettings.selectedPlainLyricsSources),
            iconRes = R.drawable.ic_air_music_note
        ) {
            uiActions.openSettingsSubPage(SettingsSubPage.SOURCES)
        }
    )

    // 子頁面 3: 本地歌詞管理
    subpageGrid.addView(
        settingsCategoryCard(
            title = getString(R.string.ui_local_lyrics_subpage_title),
            subtitle = if (isCompact) "" else getString(R.string.ui_local_lyrics_subpage_desc),
            status = if (lyricsSettings.autoSaveLocal) getString(R.string.ui_auto_save) else getString(R.string.ui_no_auto_save),
            iconRes = R.drawable.ic_air_lyrics
        ) {
            uiActions.openSettingsSubPage(SettingsSubPage.LOCAL_LYRICS)
        }
    )

    // 子頁面 4: 擴充與外部控制
    subpageGrid.addView(
        settingsCategoryCard(
            title = getString(R.string.ui_external_control_subpage_title),
            subtitle = if (isCompact) "" else getString(R.string.ui_external_control_subpage_desc),
            status = if (ExternalControlStore.isIntentApiEnabled(activity)) "Intent 啟用" else "已停用",
            iconRes = R.drawable.ic_air_shield
        ) {
            uiActions.openSettingsSubPage(SettingsSubPage.EXTERNAL_CONTROL)
        }
    )

    // 系統設定
    subpageGrid.addView(
        settingsCategoryCard(
            title = getString(R.string.ui_system),
            subtitle = if (isCompact) "" else getString(R.string.ui_overlay_and_notification_permissions),
            status = permissionSummary(),
            iconRes = R.drawable.ic_air_shield
        ) {
            uiActions.openSettingsSubPage(SettingsSubPage.SYSTEM)
        }
    )

    // 關於
    subpageGrid.addView(
        settingsCategoryCard(
            title = getString(R.string.ui_about),
            subtitle = if (isCompact) "" else getString(R.string.ui_version_project_link_and_changelog),
            status = "AetherLyric ${getAppVersionName()}",
            iconRes = R.drawable.ic_air_info
        ) {
            uiActions.openSettingsSubPage(SettingsSubPage.ABOUT)
        }
    )

    container.addView(subpageGrid)
    return scroll(activity, container, maxWidthDp = 1200)
}
