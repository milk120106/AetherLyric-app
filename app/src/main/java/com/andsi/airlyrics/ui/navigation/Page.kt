package com.andsi.airlyrics.ui.navigation

internal enum class Page { MEDIA, FLOATING, SETTINGS }

internal enum class SettingsSubPage {
    HOME,
    SERVER,             // 子頁面 1 - AetherLyric 伺服器
    SOURCES,            // 子頁面 2 - 歌詞來源管理
    LOCAL_LYRICS,       // 子頁面 3 - 本地歌詞管理
    EXTERNAL_CONTROL,   // 子頁面 4 - 擴充與外部控制
    SYSTEM,             // 系統設定
    LOGS,               // 執行日誌
    ABOUT,              // 關於
    LYRICS,             // 相容既有導航
    SAVED_LYRICS        // 相容既有導航
}

internal fun SettingsSubPage.parentPage(): SettingsSubPage? {
    return when (this) {
        SettingsSubPage.HOME -> null
        SettingsSubPage.LOGS -> SettingsSubPage.SYSTEM
        SettingsSubPage.SAVED_LYRICS -> SettingsSubPage.LOCAL_LYRICS
        SettingsSubPage.SERVER,
        SettingsSubPage.SOURCES,
        SettingsSubPage.LOCAL_LYRICS,
        SettingsSubPage.EXTERNAL_CONTROL,
        SettingsSubPage.SYSTEM,
        SettingsSubPage.ABOUT,
        SettingsSubPage.LYRICS -> SettingsSubPage.HOME
    }
}
