package com.andsi.airlyrics.settings.store

import android.content.Context
import com.andsi.airlyrics.core.prefs.prefs
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 伺服器設定檔 (ServerProfile)。
 * @param id 唯一識別 ID
 * @param name 顯示名稱 (如「預設自架伺服器」、「家庭 NAS」)
 * @param url 伺服器基礎位址 (如 http://192.168.1.100:8000)
 */
data class ServerProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String
)

/**
 * 伺服器多設定檔與雙引擎模式儲存庫 (AetherLyricServerStore)。
 * 支援儲存多組伺服器設定檔，快速切換與管理，App 端無需綁定任何 ADMIN_API_KEY。
 */
object AetherLyricServerStore {
    private const val PREFS_NAME = "aetherlyric_server_settings"
    private const val KEY_PROFILES_JSON = "server_profiles_json"
    private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
    private const val KEY_SERVER_ENABLED = "server_enabled"
    private const val KEY_CONNECTION_VERIFIED = "connection_verified"
    private const val KEY_LAST_STATUS_MESSAGE = "last_status_message"

    private fun store(context: Context) = prefs(context, PREFS_NAME)

    /**
     * 取得所有儲存的伺服器設定檔清單。
     */
    fun getProfiles(context: Context): List<ServerProfile> {
        val raw = store(context).getString(KEY_PROFILES_JSON, "").orEmpty()
        if (raw.isBlank()) {
            val legacyUrl = store(context).getString("server_url", "http://192.168.1.100:8000").orEmpty().ifBlank { "http://192.168.1.100:8000" }
            val defaultProfile = ServerProfile(id = "default_profile", name = "預設伺服器", url = legacyUrl)
            saveProfiles(context, listOf(defaultProfile))
            return listOf(defaultProfile)
        }

        return runCatching {
            val array = JSONArray(raw)
            val list = mutableListOf<ServerProfile>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ServerProfile(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", "未命名伺服器"),
                        url = obj.optString("url", "")
                    )
                )
            }
            if (list.isEmpty()) {
                val fallback = ServerProfile(id = "default_profile", name = "預設伺服器", url = "http://192.168.1.100:8000")
                listOf(fallback)
            } else list
        }.getOrDefault(listOf(ServerProfile(id = "default_profile", name = "預設伺服器", url = "http://192.168.1.100:8000")))
    }

    /**
     * 保存伺服器設定檔清單。
     */
    fun saveProfiles(context: Context, profiles: List<ServerProfile>) {
        val array = JSONArray()
        profiles.forEach { p ->
            array.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("url", p.url.trim().removeSuffix("/"))
            })
        }
        store(context).setString(KEY_PROFILES_JSON, array.toString())
    }

    /**
     * 取得當前作用中之伺服器設定檔。
     */
    fun getActiveProfile(context: Context): ServerProfile {
        val profiles = getProfiles(context)
        val activeId = store(context).getString(KEY_ACTIVE_PROFILE_ID, "").orEmpty()
        return profiles.firstOrNull { it.id == activeId } ?: profiles.first()
    }

    /**
     * 切換當前作用中的伺服器設定檔。
     */
    fun setActiveProfileId(context: Context, id: String) {
        store(context).setString(KEY_ACTIVE_PROFILE_ID, id)
        setConnectionVerified(context, false)
    }

    /**
     * 新增一組伺服器設定檔。
     */
    fun addProfile(context: Context, name: String, url: String): ServerProfile {
        val cleanUrl = url.trim().removeSuffix("/")
        val cleanName = name.trim().ifBlank { cleanUrl }
        val newProfile = ServerProfile(name = cleanName, url = cleanUrl)
        val current = getProfiles(context).toMutableList()
        current.add(newProfile)
        saveProfiles(context, current)
        return newProfile
    }

    /**
     * 更新特定伺服器設定檔。
     */
    fun updateProfile(context: Context, updated: ServerProfile) {
        val current = getProfiles(context).toMutableList()
        val index = current.indexOfFirst { it.id == updated.id }
        if (index != -1) {
            current[index] = updated.copy(url = updated.url.trim().removeSuffix("/"))
            saveProfiles(context, current)
            if (getActiveProfile(context).id == updated.id) {
                setConnectionVerified(context, false)
            }
        }
    }

    /**
     * 刪除特定伺服器設定檔（至少保留一組）。
     */
    fun deleteProfile(context: Context, id: String): Boolean {
        val current = getProfiles(context).toMutableList()
        if (current.size <= 1) return false
        val removed = current.removeAll { it.id == id }
        if (removed) {
            saveProfiles(context, current)
            if (getActiveProfile(context).id == id) {
                setActiveProfileId(context, current.first().id)
            }
        }
        return removed
    }

    fun getServerUrl(context: Context): String {
        return getActiveProfile(context).url
    }

    fun setServerUrl(context: Context, url: String) {
        val active = getActiveProfile(context)
        updateProfile(context, active.copy(url = url))
    }

    fun isServerEnabled(context: Context): Boolean {
        val enabled = store(context).getBoolean(KEY_SERVER_ENABLED, false)
        val url = getServerUrl(context)
        return enabled && url.isNotEmpty()
    }

    fun setServerEnabled(context: Context, enabled: Boolean) {
        store(context).setBoolean(KEY_SERVER_ENABLED, enabled)
    }

    /**
     * App 端無需綁定管理員 API Key，固定回傳空字串以供相容。
     */
    fun getAdminApiKey(context: Context): String = ""

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
