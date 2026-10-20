package com.andsi.airlyrics.external

import android.content.Context
import com.andsi.airlyrics.core.prefs.prefs
import org.json.JSONArray
import org.json.JSONObject

/**
 * 外部擴充控制與 App 權限記錄管理。
 */
object ExternalControlStore {
    private const val PREFS_NAME = "external_control_prefs"
    private const val KEY_INTENT_API_ENABLED = "intent_api_enabled"
    private const val KEY_UNKNOWN_APP_BEHAVIOR = "unknown_app_behavior"
    private const val KEY_APP_PERMISSIONS_JSON = "app_permissions_json"

    // 未知 App 行為常數
    const val BEHAVIOR_DENY = 0
    const val BEHAVIOR_ASK = 1
    const val BEHAVIOR_ALLOW = 2

    private fun store(context: Context) = prefs(context, PREFS_NAME)

    data class AppPermissionRecord(
        val packageName: String,
        val appName: String,
        val canControlOverlay: Boolean = true,
        val canSendLyrics: Boolean = true,
        val behavior: Int = BEHAVIOR_ALLOW
    )

    fun isIntentApiEnabled(context: Context): Boolean {
        return store(context).getBoolean(KEY_INTENT_API_ENABLED, false)
    }

    fun setIntentApiEnabled(context: Context, enabled: Boolean) {
        store(context).setBoolean(KEY_INTENT_API_ENABLED, enabled)
    }

    fun getUnknownAppBehavior(context: Context): Int {
        return store(context).getInt(KEY_UNKNOWN_APP_BEHAVIOR, BEHAVIOR_ASK)
    }

    fun setUnknownAppBehavior(context: Context, behavior: Int) {
        store(context).edit {
            putInt(KEY_UNKNOWN_APP_BEHAVIOR, behavior)
        }
    }

    fun getAllPermissions(context: Context): List<AppPermissionRecord> {
        val raw = store(context).getString(KEY_APP_PERMISSIONS_JSON, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val list = mutableListOf<AppPermissionRecord>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    AppPermissionRecord(
                        packageName = obj.getString("package"),
                        appName = obj.optString("name", obj.getString("package")),
                        canControlOverlay = obj.optBoolean("overlay", true),
                        canSendLyrics = obj.optBoolean("lyrics", true),
                        behavior = obj.optInt("behavior", BEHAVIOR_ALLOW)
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    fun getAppPermission(context: Context, packageName: String): AppPermissionRecord? {
        return getAllPermissions(context).firstOrNull { it.packageName == packageName }
    }

    fun saveAppPermission(context: Context, record: AppPermissionRecord) {
        val list = getAllPermissions(context).toMutableList()
        val index = list.indexOfFirst { it.packageName == record.packageName }
        if (index != -1) {
            list[index] = record
        } else {
            list.add(record)
        }
        saveAll(context, list)
    }

    fun removeAppPermission(context: Context, packageName: String) {
        val list = getAllPermissions(context).toMutableList()
        list.removeAll { it.packageName == packageName }
        saveAll(context, list)
    }

    private fun saveAll(context: Context, list: List<AppPermissionRecord>) {
        val array = JSONArray()
        list.forEach { rec ->
            val obj = JSONObject().apply {
                put("package", rec.packageName)
                put("name", rec.appName)
                put("overlay", rec.canControlOverlay)
                put("lyrics", rec.canSendLyrics)
                put("behavior", rec.behavior)
            }
            array.put(obj)
        }
        store(context).setString(KEY_APP_PERMISSIONS_JSON, array.toString())
    }
}
