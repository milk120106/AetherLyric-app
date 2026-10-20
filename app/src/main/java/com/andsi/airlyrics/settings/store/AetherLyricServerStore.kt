package com.andsi.airlyrics.settings.store

import android.content.Context
import com.andsi.airlyrics.core.prefs.prefs

/**
 * 伺服器雙引擎模式設定儲存庫 (AetherLyricServerStore)。
 * 負責持久化 AetherLyric-server 伺服器位址、連線驗證狀態、API 金鑰與雙引擎模式啟用開關。
 */
object AetherLyricServerStore {
    private const val PREFS_NAME = "aetherlyric_server_settings"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_SERVER_ENABLED = "server_enabled"
    private const val KEY_ADMIN_API_KEY = "admin_api_key"
    private const val KEY_CONNECTION_VERIFIED = "connection_verified"
    private const val KEY_LAST_STATUS_MESSAGE = "last_status_message"

    private fun store(context: Context) = prefs(context, PREFS_NAME)

    fun getServerUrl(context: Context): String {
        return store(context).getString(KEY_SERVER_URL, "").orEmpty()
    }

    fun setServerUrl(context: Context, url: String) {
        val trimmed = url.trim().removeSuffix("/")
        val oldUrl = getServerUrl(context)
        store(context).setString(KEY_SERVER_URL, trimmed)
        if (trimmed != oldUrl) {
            // 位址更更時自動重設驗證狀態
            setConnectionVerified(context, false)
            if (trimmed.isEmpty()) {
                setServerEnabled(context, false)
            }
        }
    }

    fun isServerEnabled(context: Context): Boolean {
        val enabled = store(context).getBoolean(KEY_SERVER_ENABLED, false)
        val url = getServerUrl(context)
        return enabled && url.isNotEmpty()
    }

    fun setServerEnabled(context: Context, enabled: Boolean) {
        store(context).setBoolean(KEY_SERVER_ENABLED, enabled)
    }

    fun getAdminApiKey(context: Context): String {
        return store(context).getString(KEY_ADMIN_API_KEY, "").orEmpty()
    }

    fun setAdminApiKey(context: Context, key: String) {
        store(context).setString(KEY_ADMIN_API_KEY, key.trim())
    }

    fun isConnectionVerified(context: Context): Boolean {
        return store(context).getBoolean(KEY_CONNECTION_VERIFIED, false)
    }

    fun setConnectionVerified(context: Context, verified: Boolean) {
        store(context).setBoolean(KEY_CONNECTION_VERIFIED, verified)
    }

    fun getLastStatusMessage(context: Context): String {
        return store(context).getString(KEY_LAST_STATUS_MESSAGE, "").orEmpty()
    }

    fun setLastStatusMessage(context: Context, message: String) {
        store(context).setString(KEY_LAST_STATUS_MESSAGE, message)
    }
}
