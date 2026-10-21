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
import com.andsi.airlyrics.settings.store.LyricsSettingsStore
import com.andsi.airlyrics.ui.components.*
import com.andsi.airlyrics.ui.model.MainUiHost
import com.andsi.airlyrics.ui.navigation.SettingsSubPage
import com.andsi.airlyrics.ui.theme.*

internal fun createSettingsHomePage(activity: MainUiHost): View = with(activity) createSettingsHomePage@ {
    val container = pageContainer(activity, animateChanges = false)
    val lyricsSettings = lyricsSettingsState()

    // 頁面頂端標題列與主題/夜間模式開關
    container.addView(settingsHomeHeader())

    // 1. 搜尋策略與歌詞顯示設定卡片
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

            // 自動搜尋時彈出結果清單開關 (預設關閉)
            val autoPopupRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(AirUiTokens.Space.Sm), 0, dp(AirUiTokens.Space.Xs))
                val textCol = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        setMargins(0, 0, dp(AirUiTokens.Space.Md), 0)
                    }
                    addView(normalText(activity, "自動搜尋時彈出結果清單"))
                    addView(smallHint(activity, "關閉時依優先級自動套用最佳結果；開啟時於自動搜尋完成後彈出多來源候選清單"))
                }
                val popupSwitch = SwitchCompat(activity).apply {
                    isChecked = LyricsSettingsStore.isAutoSearchPopupEnabled(activity)
                    applyAirThemeTint(activity)
                    setOnCheckedChangeListener { _, checked ->
                        LyricsSettingsStore.setAutoSearchPopupEnabled(activity, checked)
                    }
                }
                addView(textCol)
                addView(popupSwitch)
            }
            addView(autoPopupRow)

            // 啟用本地 Google 翻譯開關
            val googleTranslateRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(AirUiTokens.Space.Xs), 0, dp(AirUiTokens.Space.Sm))
                val textCol = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        setMargins(0, 0, dp(AirUiTokens.Space.Md), 0)
                    }
                    addView(normalText(activity, "啟用本地 Google 翻譯"))
                    addView(smallHint(activity, "當外語歌曲無現成翻譯時，自動於背景呼叫 Google 翻譯補齊繁體中文對照並保留時間軸"))
                }
                val translateSwitch = SwitchCompat(activity).apply {
                    isChecked = LyricsSettingsStore.isLocalGoogleTranslateEnabled(activity)
                    applyAirThemeTint(activity)
                    setOnCheckedChangeListener { _, checked ->
                        LyricsSettingsStore.setLocalGoogleTranslateEnabled(activity, checked)
                    }
                }
                addView(textCol)
                addView(translateSwitch)
            }
            addView(googleTranslateRow)
        }
    )

    // 2. 子頁面入口卡片清單 (透過 AdaptiveGridLayout 網格佈局)
    val subpageGrid = AdaptiveGridLayout(
        activity,
        2,
        dp(24),
        dp(12),
        columnPolicy = GridColumnPolicy.Width(360)
    )

    // 子頁面 1: 伺服器
    subpageGrid.addView(
        settingsCategoryCard(
            title = getString(R.string.ui_server_subpage_title),
            subtitle = getString(R.string.ui_server_subpage_desc),
            status = if (AetherLyricServerStore.isServerEnabled(activity)) {
                AetherLyricServerStore.getEngineMode(activity).displayTitle
            } else {
                "未啟用"
            },
            iconRes = R.drawable.ic_air_sync_alt
        ) {
            uiActions.openSettingsSubPage(SettingsSubPage.SERVER)
        }
    )

    // 子頁面 2: 歌詞源管理
    subpageGrid.addView(
        settingsCategoryCard(
            title = getString(R.string.ui_sources_subpage_title),
            subtitle = getString(R.string.ui_sources_subpage_desc),
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
            subtitle = getString(R.string.ui_local_lyrics_subpage_desc),
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
            subtitle = getString(R.string.ui_external_control_subpage_desc),
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
            subtitle = getString(R.string.ui_overlay_and_notification_permissions),
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
            subtitle = getString(R.string.ui_version_project_link_and_changelog),
            status = "AetherLyric ${getAppVersionName()}",
            iconRes = R.drawable.ic_air_info
        ) {
            uiActions.openSettingsSubPage(SettingsSubPage.ABOUT)
        }
    )

    container.addView(subpageGrid)
    return scroll(activity, container, maxWidthDp = 1200)
}
