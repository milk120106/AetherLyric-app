package com.andsi.airlyrics.ui.pages.settings

import android.view.View
import com.andsi.airlyrics.R
import com.andsi.airlyrics.design.tokens.AirUiTokens
import com.andsi.airlyrics.external.ExternalControlStore
import com.andsi.airlyrics.i18n.localizedPlainLyricsSourceList
import com.andsi.airlyrics.settings.store.AetherLyricServerStore
import com.andsi.airlyrics.ui.components.*
import com.andsi.airlyrics.ui.model.MainUiHost
import com.andsi.airlyrics.ui.navigation.SettingsSubPage

/**
 * 設定首頁 (SettingsHomePage)。
 * 提供 8 個主要設定子頁面入口卡片：
 * 1. 伺服器 (SERVER)
 * 2. 歌詞源管理 (SOURCES)
 * 3. 本地歌詞管理 (LOCAL_LYRICS)
 * 4. 擴充與外部控制 (EXTERNAL_CONTROL)
 * 5. 搜尋策略 (SEARCH_STRATEGY)
 * 6. 介面與外觀 (APPEARANCE)
 * 7. 系統 (SYSTEM)
 * 8. 關於 (ABOUT)
 */
internal fun createSettingsHomePage(activity: MainUiHost): View = with(activity) createSettingsHomePage@ {
    val container = pageContainer(activity, animateChanges = false)
    val lyricsSettings = lyricsSettingsState()

    // 頁面頂端標題列與主題/夜間模式開關
    container.addView(settingsHomeHeader())

    // 8 個主要子頁面入口卡片清單 (透過 AdaptiveGridLayout 網格佈局自適應 1~2 欄)
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

    // 子頁面 5: 搜尋策略
    subpageGrid.addView(
        settingsCategoryCard(
            title = "搜尋策略",
            subtitle = "自訂多來源檢索、自動備援、翻譯與全域時間軸對齊",
            status = if (lyricsSettings.autoSearchOnline) "線上自動檢索" else "手動搜尋優先",
            iconRes = R.drawable.ic_air_search
        ) {
            uiActions.openSettingsSubPage(SettingsSubPage.SEARCH_STRATEGY)
        }
    )

    // 子頁面 6: 介面與外觀
    subpageGrid.addView(
        settingsCategoryCard(
            title = "介面與外觀",
            subtitle = "主題色彩、HEX 調色盤、動畫開關與轉場效果",
            status = "主題與動畫設定",
            iconRes = R.drawable.ic_air_style
        ) {
            uiActions.openSettingsSubPage(SettingsSubPage.APPEARANCE)
        }
    )

    // 子頁面 7: 系統
    subpageGrid.addView(
        settingsCategoryCard(
            title = getString(R.string.ui_system),
            subtitle = "懸浮窗、通知存取、麥克風與特權模式",
            status = permissionSummary(),
            iconRes = R.drawable.ic_air_shield
        ) {
            uiActions.openSettingsSubPage(SettingsSubPage.SYSTEM)
        }
    )

    // 子頁面 8: 關於
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
