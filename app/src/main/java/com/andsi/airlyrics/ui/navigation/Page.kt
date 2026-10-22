package com.andsi.airlyrics.ui.navigation

internal enum class Page { MEDIA, FLOATING, SETTINGS }

internal enum class SettingsSubPage {
    HOME,
    SERVER,             // 子頁面 1 - AetherLyric 伺服器
    SOURCES,            // 子頁面 2 - 歌詞來源管理
    LOCAL_LYRICS,       // 子頁面 3 - 本地歌詞管理
    EXTERNAL_CONTROL,   // 子頁面 4 - 擴充與外部控制
    SEARCH_STRATEGY,    // 子頁面 5 - 搜尋策略
    APPEARANCE,         // 子頁面 6 - 介面與外觀
    SYSTEM,             // 子頁面 7 - 系統設定
    ABOUT,              // 子頁面 8 - 關於
    PLUGIN_STORE,       // 擴充商店
    COMMUNITY_SERVERS,  // 公開伺服器探索
    LOGS,               // 執行日誌
    LYRICS,             // 相容既有導航
    SAVED_LYRICS        // 相容既有導航
}

internal fun SettingsSubPage.parentPage(): SettingsSubPage? {
    return when (this) {
        SettingsSubPage.HOME -> null
        SettingsSubPage.LOGS -> SettingsSubPage.SYSTEM
        SettingsSubPage.SAVED_LYRICS -> SettingsSubPage.LOCAL_LYRICS
        SettingsSubPage.PLUGIN_STORE -> SettingsSubPage.EXTERNAL_CONTROL
        SettingsSubPage.COMMUNITY_SERVERS -> SettingsSubPage.SERVER
        SettingsSubPage.SERVER,
        SettingsSubPage.SOURCES,
        SettingsSubPage.LOCAL_LYRICS,
        SettingsSubPage.EXTERNAL_CONTROL,
        SettingsSubPage.SEARCH_STRATEGY,
        SettingsSubPage.APPEARANCE,
        SettingsSubPage.SYSTEM,
        SettingsSubPage.ABOUT,
        SettingsSubPage.LYRICS -> SettingsSubPage.HOME
    }
}
