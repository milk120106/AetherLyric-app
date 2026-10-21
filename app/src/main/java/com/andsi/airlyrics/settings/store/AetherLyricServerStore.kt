package com.andsi.airlyrics.settings.store

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.andsi.airlyrics.core.prefs.prefs
import org.json.JSONArray
import org.json.JSONObject
import java.net.Authenticator
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.URL
import java.net.URLConnection
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

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
 * 伺服器引擎協同模式：
 * - DUAL_ENGINE: 雙引擎模式（本地多源 + 伺服器協同）
 * - SERVER_ONLY: 僅伺服器模式（停用本地搜尋與對齊，全交由伺服器執行）
 */
enum class ServerEngineMode(val key: String, val displayTitle: String, val description: String) {
    DUAL_ENGINE(
        key = "dual_engine",
        displayTitle = "雙引擎模式（本地 + 伺服器協同）",
        description = "優先向伺服器查詢與進行 AI 處理，若未命中則自動協同本地多源引擎"
    ),
    SERVER_ONLY(
        key = "server_only",
        displayTitle = "僅伺服器模式（全交由伺服器搜尋與辨識）",
        description = "完全停用本地各音樂平台搜尋，所有歌詞檢索、時間軸對齊與轉錄皆交由伺服器處理"
    );

    companion object {
        fun fromKey(key: String?): ServerEngineMode {
            return entries.firstOrNull { it.key == key } ?: DUAL_ENGINE
        }
    }
}

/**
 * 伺服器多設定檔、引擎模式、多層加密驗證與網路代理儲存庫 (AetherLyricServerStore)。
 * 敏感憑證（APP_ACCESS_TOKEN、PIN 碼、問答驗證答案、Proxy 密碼）採用 Android Keystore AES/GCM 硬體加密儲存。
 */
object AetherLyricServerStore {
    private const val PREFS_NAME = "aetherlyric_server_settings"
    private const val KEYSTORE_ALIAS = "aetherlyric_server_credential_key"
    private const val KEY_PROFILES_JSON = "server_profiles_json"
    private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
    private const val KEY_SERVER_ENABLED = "server_enabled"
    private const val KEY_ENGINE_MODE = "server_engine_mode"
    private const val KEY_WEBSOCKET_PROGRESS = "websocket_progress_enabled"
    private const val KEY_CONNECTION_VERIFIED = "connection_verified"
    private const val KEY_LAST_STATUS_MESSAGE = "last_status_message"

    // 加密儲存之多層驗證欄位
    private const val KEY_ENC_APP_ACCESS_TOKEN = "enc_app_access_token"
    private const val KEY_ENC_AUTH_PIN = "enc_auth_pin"
    private const val KEY_ENC_AUTH_CHALLENGE_ANSWER = "enc_auth_challenge_answer"

    // 代理伺服器 (Proxy) 設定欄位
    private const val KEY_PROXY_ENABLED = "proxy_enabled"
    private const val KEY_PROXY_HOST = "proxy_host"
    private const val KEY_PROXY_PORT = "proxy_port"
    private const val KEY_PROXY_USERNAME = "proxy_username"
    private const val KEY_ENC_PROXY_PASSWORD = "enc_proxy_password"

    private fun store(context: Context) = prefs(context, PREFS_NAME)

    // ==================== Android Keystore AES-GCM 加解密輔助 ====================

    private fun getOrCreateSecretKey(): SecretKey? {
        return runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val existing = keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (existing != null) return@runCatching existing.secretKey

            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val spec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGen.init(spec)
            keyGen.generateKey()
        }.getOrNull()
    }

    private fun encryptSecret(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return runCatching {
            val secretKey = getOrCreateSecretKey() ?: return@runCatching Base64.encodeToString(
                plainText.toByteArray(StandardCharsets.UTF_8),
                Base64.NO_WRAP
            )
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val cipherBase64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
            "gcm:$ivBase64:$cipherBase64"
        }.getOrDefault("")
    }

    private fun decryptSecret(stored: String?): String {
        if (stored.isNullOrBlank()) return ""
        return runCatching {
            if (!stored.startsWith("gcm:")) {
                return@runCatching String(Base64.decode(stored, Base64.NO_WRAP), StandardCharsets.UTF_8)
            }
            val parts = stored.split(":")
            if (parts.size != 3) return@runCatching ""
            val iv = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipherBytes = Base64.decode(parts[2], Base64.NO_WRAP)
            val secretKey = getOrCreateSecretKey() ?: return@runCatching ""
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
        }.getOrDefault("")
    }

    // ==================== 伺服器設定檔管理 ====================

    fun getProfiles(context: Context): List<ServerProfile> {
        val raw = store(context).getString(KEY_PROFILES_JSON, "").orEmpty()
        if (raw.isBlank()) {
            val legacyUrl = store(context).getString("server_url", "http://192.168.1.100:8000")
                .orEmpty()
                .ifBlank { "http://192.168.1.100:8000" }
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

    fun getActiveProfile(context: Context): ServerProfile {
        val profiles = getProfiles(context)
        val activeId = store(context).getString(KEY_ACTIVE_PROFILE_ID, "").orEmpty()
        return profiles.firstOrNull { it.id == activeId } ?: profiles.first()
    }

    fun setActiveProfileId(context: Context, id: String) {
        store(context).setString(KEY_ACTIVE_PROFILE_ID, id)
        setConnectionVerified(context, false)
    }

    fun addProfile(context: Context, name: String, url: String): ServerProfile {
        val cleanUrl = url.trim().removeSuffix("/")
        val cleanName = name.trim().ifBlank { cleanUrl }
        val newProfile = ServerProfile(name = cleanName, url = cleanUrl)
        val current = getProfiles(context).toMutableList()
        current.add(newProfile)
        saveProfiles(context, current)
        return newProfile
    }

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

    // ==================== 引擎模式與 WebSocket 進度開關 ====================

    fun getEngineMode(context: Context): ServerEngineMode {
        val raw = store(context).getString(KEY_ENGINE_MODE, ServerEngineMode.DUAL_ENGINE.key)
        return ServerEngineMode.fromKey(raw)
    }

    fun setEngineMode(context: Context, mode: ServerEngineMode) {
        store(context).setString(KEY_ENGINE_MODE, mode.key)
    }

    fun isServerOnlyMode(context: Context): Boolean {
        return isServerEnabled(context) && getEngineMode(context) == ServerEngineMode.SERVER_ONLY
    }

    fun isWebSocketProgressEnabled(context: Context): Boolean {
        return store(context).getBoolean(KEY_WEBSOCKET_PROGRESS, true)
    }

    fun setWebSocketProgressEnabled(context: Context, enabled: Boolean) {
        store(context).setBoolean(KEY_WEBSOCKET_PROGRESS, enabled)
    }

    // ==================== 多層安全驗證（App Access Token / PIN / 問答驗證） ====================

    fun getAppAccessToken(context: Context): String {
        return decryptSecret(store(context).getString(KEY_ENC_APP_ACCESS_TOKEN, ""))
    }

    fun setAppAccessToken(context: Context, token: String) {
        store(context).setString(KEY_ENC_APP_ACCESS_TOKEN, encryptSecret(token.trim()))
    }

    fun getAuthPin(context: Context): String {
        return decryptSecret(store(context).getString(KEY_ENC_AUTH_PIN, ""))
    }

    fun setAuthPin(context: Context, pin: String) {
        store(context).setString(KEY_ENC_AUTH_PIN, encryptSecret(pin.trim()))
    }

    fun getAuthChallengeAnswer(context: Context): String {
        return decryptSecret(store(context).getString(KEY_ENC_AUTH_CHALLENGE_ANSWER, ""))
    }

    fun setAuthChallengeAnswer(context: Context, answer: String) {
        store(context).setString(KEY_ENC_AUTH_CHALLENGE_ANSWER, encryptSecret(answer.trim()))
    }

    /**
     * 相容舊版呼叫：回傳加密儲存之 APP_ACCESS_TOKEN。
     */
    fun getAdminApiKey(context: Context): String = getAppAccessToken(context)

    // ==================== 網路代理伺服器 (Proxy) 設定 ====================

    fun isProxyEnabled(context: Context): Boolean {
        return store(context).getBoolean(KEY_PROXY_ENABLED, false)
    }

    fun setProxyEnabled(context: Context, enabled: Boolean) {
        store(context).setBoolean(KEY_PROXY_ENABLED, enabled)
    }

    fun getProxyHost(context: Context): String {
        return store(context).getString(KEY_PROXY_HOST, "").orEmpty().trim()
    }

    fun setProxyHost(context: Context, host: String) {
        store(context).setString(KEY_PROXY_HOST, host.trim())
    }

    fun getProxyPort(context: Context): Int {
        return store(context).getInt(KEY_PROXY_PORT, 8080).coerceIn(1, 65535)
    }

    fun setProxyPort(context: Context, port: Int) {
        store(context).setInt(KEY_PROXY_PORT, port.coerceIn(1, 65535))
    }

    fun getProxyUsername(context: Context): String {
        return store(context).getString(KEY_PROXY_USERNAME, "").orEmpty().trim()
    }

    fun setProxyUsername(context: Context, username: String) {
        store(context).setString(KEY_PROXY_USERNAME, username.trim())
    }

    fun getProxyPassword(context: Context): String {
        return decryptSecret(store(context).getString(KEY_ENC_PROXY_PASSWORD, ""))
    }

    fun setProxyPassword(context: Context, password: String) {
        store(context).setString(KEY_ENC_PROXY_PASSWORD, encryptSecret(password))
    }

    fun resolveJavaProxy(context: Context?): Proxy {
        if (context == null || !isProxyEnabled(context)) return Proxy.NO_PROXY
        val host = getProxyHost(context)
        val port = getProxyPort(context)
        if (host.isBlank()) return Proxy.NO_PROXY
        val user = getProxyUsername(context)
        val pass = getProxyPassword(context)
        if (user.isNotBlank()) {
            runCatching {
                Authenticator.setDefault(object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(user, pass.toCharArray())
                    }
                })
            }
        }
        return runCatching {
            Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port))
        }.getOrDefault(Proxy.NO_PROXY)
    }

    fun openConnectionWithProxy(context: Context?, urlStr: String): URLConnection {
        val url = URL(urlStr)
        val proxy = resolveJavaProxy(context)
        val conn = if (proxy != Proxy.NO_PROXY) url.openConnection(proxy) else url.openConnection()
        if (context != null && isProxyEnabled(context)) {
            val user = getProxyUsername(context)
            val pass = getProxyPassword(context)
            if (user.isNotBlank()) {
                val credentials = Base64.encodeToString(
                    "$user:$pass".toByteArray(StandardCharsets.UTF_8),
                    Base64.NO_WRAP
                )
                conn.setRequestProperty("Proxy-Authorization", "Basic $credentials")
            }
        }
        return conn
    }

    fun applyAuthHeaders(context: Context?, conn: HttpURLConnection, explicitApiKey: String? = null) {
        val token = explicitApiKey?.takeIf { it.isNotBlank() }
            ?: context?.let { getAppAccessToken(it) }.orEmpty()
        if (token.isNotBlank()) {
            conn.setRequestProperty("Authorization", "Bearer ${token.trim()}")
            conn.setRequestProperty("X-App-Access-Token", token.trim())
        }
        if (context != null) {
            val pin = getAuthPin(context)
            if (pin.isNotBlank()) {
                conn.setRequestProperty("X-Auth-Pin", pin)
            }
            val answer = getAuthChallengeAnswer(context)
            if (answer.isNotBlank()) {
                val encodedAnswer = Base64.encodeToString(
                    answer.toByteArray(StandardCharsets.UTF_8),
                    Base64.NO_WRAP
                )
                conn.setRequestProperty("X-Auth-Answer", encodedAnswer)
            }
        }
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
