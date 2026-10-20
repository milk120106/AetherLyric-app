package com.andsi.airlyrics.ui.pages.settings

import android.view.View
import com.andsi.airlyrics.ui.model.MainUiHost
import com.andsi.airlyrics.ui.navigation.SettingsSubPage

internal fun createSettingsPage(activity: MainUiHost): View = with(activity) createSettingsPage@ {
    return when (settingsSubPage) {
        SettingsSubPage.HOME -> createSettingsHomePage(activity)
        SettingsSubPage.SERVER -> createServerSettingsPage(activity)
        SettingsSubPage.SOURCES -> createLyricsSourcesSettingsPage(activity)
        SettingsSubPage.LOCAL_LYRICS -> createLocalLyricsSettingsPage(activity)
        SettingsSubPage.EXTERNAL_CONTROL -> createExternalControlSettingsPage(activity)
        SettingsSubPage.SYSTEM -> createSystemSettingsPage(activity)
        SettingsSubPage.LOGS -> createExecutionLogsPage(activity)
        SettingsSubPage.LYRICS -> createLyricsSettingsPage(activity)
        SettingsSubPage.SAVED_LYRICS -> createSavedLyricsPage(activity)
        SettingsSubPage.ABOUT -> createAboutSettingsPage(activity)
    }
}
