package com.andsi.airlyrics.lyrics.providers

import android.content.Context
import com.andsi.airlyrics.core.prefs.prefs
import com.andsi.airlyrics.logging.AppLogger
import com.andsi.airlyrics.lyrics.LyricsProviderResult
import com.andsi.airlyrics.lyrics.PlainLyricsSearchRequest
import com.andsi.airlyrics.settings.store.AetherLyricServerStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * 自訂 JSON 歌詞來源外掛資料結構 (CustomJsonPlugin)。
 */
data class CustomJsonPlugin(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val version: String = "1.0.0",
    val author: String = "",
    val description: String = "",
    val enabled: Boolean = true,
    val updateUrl: String = "",
    val searchUrl: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val bodyTemplate: String = "",
    val lyricsPath: String = "lrc",
    val translationPath: String = "",
    val titlePath: String = "",
    val artistPath: String = "",
    val lastUpdatedAt: Long = System.currentTimeMillis()
)

/**
 * 自訂 JSON 歌詞來源外掛沙盒與管理中心 (CustomJsonPluginStore)。
 * 支援：
 * 1. 本機 .json 檔案匯入與遠端 URL 載入安裝
 * 2. 嚴格網路沙盒（僅限 HTTP/HTTPS、8 秒逾時上限、2MB 回應大小上限、無反射與檔案系統存取）
 * 3. 外掛自動更新開關與更新頻率設定（6 / 12 / 24 / 72 小時）
 */
object CustomJsonPluginStore {
    private const val PREFS_NAME = "custom_json_lyrics_plugins"
    private const val KEY_PLUGINS_JSON = "plugins_json"
    private const val KEY_AUTO_UPDATE_ENABLED = "auto_update_enabled"
    private const val KEY_AUTO_UPDATE_INTERVAL_HOURS = "auto_update_interval_hours"
    private const val KEY_LAST_AUTO_UPDATE_CHECK_MS = "last_auto_update_check_ms"

    private const val SANDBOX_TIMEOUT_MS = 8000
    private const val SANDBOX_MAX_BYTES = 2 * 1024 * 1024 // 2 MB

    val UPDATE_INTERVAL_OPTIONS_HOURS = listOf(6, 12, 24, 72)

    private fun store(context: Context) = prefs(context, PREFS_NAME)

    fun isAutoUpdateEnabled(context: Context): Boolean {
        return store(context).getBoolean(KEY_AUTO_UPDATE_ENABLED, true)
    }

    fun setAutoUpdateEnabled(context: Context, enabled: Boolean) {
        store(context).setBoolean(KEY_AUTO_UPDATE_ENABLED, enabled)
    }

    fun getAutoUpdateIntervalHours(context: Context): Int {
        return store(context).getInt(KEY_AUTO_UPDATE_INTERVAL_HOURS, 24)
            .takeIf { it in UPDATE_INTERVAL_OPTIONS_HOURS } ?: 24
    }

    fun setAutoUpdateIntervalHours(context: Context, hours: Int) {
        val valid = if (hours in UPDATE_INTERVAL_OPTIONS_HOURS) hours else 24
        store(context).setInt(KEY_AUTO_UPDATE_INTERVAL_HOURS, valid)
    }

    fun getPlugins(context: Context): List<CustomJsonPlugin> {
        val raw = store(context).getString(KEY_PLUGINS_JSON, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val list = mutableListOf<CustomJsonPlugin>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                parsePluginObject(obj)?.let { list.add(it) }
            }
            list
        }.getOrDefault(emptyList())
    }

    fun savePlugins(context: Context, plugins: List<CustomJsonPlugin>) {
        val arr = JSONArray()
        plugins.forEach { plugin ->
            arr.put(serializePluginObject(plugin))
        }
        store(context).setString(KEY_PLUGINS_JSON, arr.toString())
    }

    /**
     * 解析單一 JSON 物件或包含陣列的 JSON 字串並匯入外掛。
     */
    fun importPluginJson(context: Context, jsonText: String, sourceUrl: String = ""): Result<List<CustomJsonPlugin>> {
        return runCatching {
            val trimmed = jsonText.trim()
            if (trimmed.isEmpty()) throw IllegalArgumentException("JSON 內容為空")

            val imported = mutableListOf<CustomJsonPlugin>()
            if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val p = parsePluginObject(obj, sourceUrl)
                        ?: throw IllegalArgumentException("第 ${i + 1} 個外掛缺少必要欄位 (name 或 searchUrl)")
                    validateSandboxUrl(p.searchUrl)
                    imported.add(p)
                }
            } else {
                val obj = JSONObject(trimmed)
                val p = parsePluginObject(obj, sourceUrl)
                    ?: throw IllegalArgumentException("外掛 JSON 缺少必要欄位 (name 或 searchUrl)")
                validateSandboxUrl(p.searchUrl)
                imported.add(p)
            }

            if (imported.isEmpty()) throw IllegalArgumentException("未解析到有效的外掛定義")

            val current = getPlugins(context).toMutableList()
            imported.forEach { newPlugin ->
                val idx = current.indexOfFirst { it.id == newPlugin.id || it.name.equals(newPlugin.name, ignoreCase = true) }
                if (idx != -1) {
                    current[idx] = newPlugin.copy(id = current[idx].id, enabled = current[idx].enabled)
                } else {
                    current.add(newPlugin)
                }
            }
            savePlugins(context, current)
            AppLogger.i("JsonPluginSandbox", "已成功匯入 ${imported.size} 個自訂 JSON 歌詞外掛")
            imported
        }
    }

    /**
     * 透過遠端網址下載並安裝 JSON 外掛。
     */
    fun installFromUrlBlocking(context: Context, urlStr: String): Result<List<CustomJsonPlugin>> {
        return runCatching {
            val cleanUrl = urlStr.trim()
            validateSandboxUrl(cleanUrl)
            val jsonText = executeSandboxedHttpText(context, cleanUrl, "GET", emptyMap(), null)
            importPluginJson(context, jsonText, sourceUrl = cleanUrl).getOrThrow()
        }
    }

    fun setPluginEnabled(context: Context, pluginId: String, enabled: Boolean) {
        val updated = getPlugins(context).map {
            if (it.id == pluginId) it.copy(enabled = enabled) else it
        }
        savePlugins(context, updated)
    }

    fun deletePlugin(context: Context, pluginId: String) {
        val updated = getPlugins(context).filterNot { it.id == pluginId }
        savePlugins(context, updated)
    }

    /**
     * 檢查是否到達自動更新週期，若到期則在背景更新具備 updateUrl 的外掛。
     */
    fun checkAndAutoUpdatePluginsIfNeeded(context: Context) {
        if (!isAutoUpdateEnabled(context)) return
        val intervalMs = getAutoUpdateIntervalHours(context) * 3600_000L
        val lastCheck = store(context).getLong(KEY_LAST_AUTO_UPDATE_CHECK_MS, 0L)
        val now = System.currentTimeMillis()
        if (now - lastCheck < intervalMs) return

        store(context).setLong(KEY_LAST_AUTO_UPDATE_CHECK_MS, now)
        runCatching {
            forceUpdateAllPluginsBlocking(context)
        }.onFailure { err ->
            AppLogger.w("JsonPluginSandbox", "外掛自動更新檢查失敗: ${err.message}")
        }
    }

    /**
     * 立即強制更新所有設定了 updateUrl 的外掛。
     */
    fun forceUpdateAllPluginsBlocking(context: Context): Int {
        val current = getPlugins(context).toMutableList()
        var updatedCount = 0
        for (i in current.indices) {
            val plugin = current[i]
            val updateUrl = plugin.updateUrl.trim()
            if (updateUrl.isEmpty()) continue
            runCatching {
                validateSandboxUrl(updateUrl)
                val remoteJson = executeSandboxedHttpText(context, updateUrl, "GET", emptyMap(), null)
                val parsed = parsePluginObject(JSONObject(remoteJson), updateUrl)
                if (parsed != null) {
                    validateSandboxUrl(parsed.searchUrl)
                    current[i] = parsed.copy(
                        id = plugin.id,
                        enabled = plugin.enabled,
                        lastUpdatedAt = System.currentTimeMillis()
                    )
                    updatedCount++
                }
            }.onFailure { err ->
                AppLogger.w("JsonPluginSandbox", "外掛 [${plugin.name}] 更新失敗: ${err.message}")
            }
        }
        if (updatedCount > 0) {
            savePlugins(context, current)
            AppLogger.i("JsonPluginSandbox", "已完成 $updatedCount 個 JSON 外掛更新")
        }
        return updatedCount
    }

    /**
     * 在安全沙盒內執行單一自訂 JSON 外掛查詢。
     */
    fun executePluginSearch(
        plugin: CustomJsonPlugin,
        request: PlainLyricsSearchRequest
    ): Result<LyricsProviderResult?> = runCatching {
        if (!plugin.enabled) return@runCatching null
        request.cancellationToken?.throwIfCancellationRequested()

        val encTitle = URLEncoder.encode(request.title, "UTF-8")
        val encArtist = URLEncoder.encode(request.artist, "UTF-8")
        val encQuery = URLEncoder.encode("${request.title} ${request.artist}".trim(), "UTF-8")
        val durationSec = (request.durationMs / 1000L).coerceAtLeast(0L).toString()

        fun replacePlaceholders(template: String, urlEncodeValues: Boolean): String {
            return template
                .replace("{title}", if (urlEncodeValues) encTitle else request.title)
                .replace("{artist}", if (urlEncodeValues) encArtist else request.artist)
                .replace("{query}", if (urlEncodeValues) encQuery else "${request.title} ${request.artist}".trim())
                .replace("{duration}", durationSec)
                .replace("{durationMs}", request.durationMs.toString())
        }

        val resolvedUrl = replacePlaceholders(plugin.searchUrl.trim(), urlEncodeValues = true)
        validateSandboxUrl(resolvedUrl)

        val resolvedBody = plugin.bodyTemplate.takeIf { it.isNotBlank() }?.let {
            replacePlaceholders(it, urlEncodeValues = false)
        }

        val responseText = executeSandboxedHttpText(
            context = request.context,
            urlStr = resolvedUrl,
            method = plugin.method.uppercase().ifBlank { "GET" },
            headers = plugin.headers,
            body = resolvedBody
        )

        if (responseText.isBlank()) return@runCatching null

        val trimmed = responseText.trim()
        val (lrcText, transText, matchedTitle, matchedArtist) = if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val root: Any = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed)
            val lrc = extractJsonPath(root, plugin.lyricsPath).orEmpty().trim()
            val trans = plugin.translationPath.takeIf { it.isNotBlank() }?.let { extractJsonPath(root, it) }?.trim()
            val mTitle = plugin.titlePath.takeIf { it.isNotBlank() }?.let { extractJsonPath(root, it) }?.trim().orEmpty()
            val mArtist = plugin.artistPath.takeIf { it.isNotBlank() }?.let { extractJsonPath(root, it) }?.trim().orEmpty()
            Quadruple(lrc, trans, mTitle, mArtist)
        } else {
            Quadruple(trimmed, null, request.title, request.artist)
        }

        if (lrcText.isBlank()) return@runCatching null

        LyricsProviderResult(
            plainProviderId = "json_plugin:${plugin.id}",
            plainProviderName = plugin.name,
            plainLrc = lrcText,
            translatedLrc = transText?.takeIf { it.isNotBlank() },
            matchedTitle = matchedTitle.ifBlank { request.title },
            matchedArtist = matchedArtist.ifBlank { request.artist },
            matchedDurationMs = request.durationMs
        )
    }.onFailure { err ->
        AppLogger.w("JsonPluginSandbox", "沙盒執行外掛 [${plugin.name}] 失敗: ${err.message}")
    }

    private data class Quadruple(
        val lrc: String,
        val trans: String?,
        val title: String,
        val artist: String
    )

    /**
     * 依序嘗試所有已啟用的自訂 JSON 外掛。
     */
    fun findFirstFromEnabledPlugins(request: PlainLyricsSearchRequest): LyricsProviderResult? {
        checkAndAutoUpdatePluginsIfNeeded(request.context)
        val enabledPlugins = getPlugins(request.context).filter { it.enabled }
        for (plugin in enabledPlugins) {
            request.cancellationToken?.throwIfCancellationRequested()
            val res = executePluginSearch(plugin, request).getOrNull()
            if (res != null && res.plainLrc.isNotBlank()) {
                return res
            }
        }
        return null
    }

    // ==================== 沙盒驗證與 JSON Path 擷取 ====================

    private fun validateSandboxUrl(urlStr: String) {
        val uri = runCatching { URI(urlStr) }.getOrNull()
            ?: throw SecurityException("沙盒阻擋：網址格式無效 ($urlStr)")
        val scheme = uri.scheme?.lowercase() ?: ""
        if (scheme != "http" && scheme != "https") {
            throw SecurityException("沙盒阻擋：僅允許 HTTP/HTTPS 協定 ($scheme)")
        }
        val host = uri.host?.lowercase() ?: throw SecurityException("沙盒阻擋：缺少有效主機名稱")
        if (host == "0.0.0.0") {
            throw SecurityException("沙盒阻擋：不允許存取本地敏感位址")
        }
    }

    private fun executeSandboxedHttpText(
        context: Context,
        urlStr: String,
        method: String,
        headers: Map<String, String>,
        body: String?
    ): String {
        val conn = (AetherLyricServerStore.openConnectionWithProxy(context, urlStr) as HttpURLConnection).apply {
            requestMethod = if (method == "POST") "POST" else "GET"
            connectTimeout = SANDBOX_TIMEOUT_MS
            readTimeout = SANDBOX_TIMEOUT_MS
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("User-Agent", "AetherLyric-PluginSandbox/0.1")
            headers.forEach { (k, v) ->
                val cleanKey = k.trim()
                if (cleanKey.isNotEmpty() && !cleanKey.contains("\r") && !cleanKey.contains("\n")) {
                    setRequestProperty(cleanKey, v.replace("\r", "").replace("\n", ""))
                }
            }
        }

        if (method == "POST" && !body.isNullOrEmpty()) {
            conn.doOutput = true
            if (conn.getRequestProperty("Content-Type") == null) {
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            conn.outputStream.use { os ->
                os.write(body.toByteArray(StandardCharsets.UTF_8))
                os.flush()
            }
        }

        val code = conn.responseCode
        if (code !in 200..299) {
            throw IllegalStateException("HTTP $code")
        }

        return readBoundedStream(conn.inputStream, SANDBOX_MAX_BYTES)
    }

    private fun readBoundedStream(input: InputStream, maxBytes: Int): String {
        input.use { stream ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            var total = 0
            while (true) {
                val r = stream.read(buf)
                if (r == -1) break
                total += r
                if (total > maxBytes) {
                    throw SecurityException("沙盒阻擋：回應內容超過 2MB 安全上限")
                }
                out.write(buf, 0, r)
            }
            return out.toString(StandardCharsets.UTF_8.name())
        }
    }

    /**
     * 支援點號與陣列索引的輕量級 JSON Path 解析（例如 "data.songs[0].lyric"）。
     */
    internal fun extractJsonPath(root: Any?, path: String): String? {
        if (root == null) return null
        val cleanPath = path.trim()
        if (cleanPath.isEmpty() || cleanPath == "$") return root.toString()

        val tokens = cleanPath
            .removePrefix("$.")
            .removePrefix("$")
            .split(".")
            .filter { it.isNotEmpty() }

        var current: Any? = root
        for (token in tokens) {
            if (current == null || current == JSONObject.NULL) return null
            val bracketIdx = token.indexOf('[')
            if (bracketIdx != -1 && token.endsWith("]")) {
                val propName = token.substring(0, bracketIdx)
                val indexStr = token.substring(bracketIdx + 1, token.length - 1)
                val arrIndex = indexStr.toIntOrNull() ?: return null

                if (propName.isNotEmpty()) {
                    current = (current as? JSONObject)?.opt(propName)
                }
                current = (current as? JSONArray)?.opt(arrIndex)
            } else {
                current = when (current) {
                    is JSONObject -> current.opt(token)
                    is JSONArray -> token.toIntOrNull()?.let { current.opt(it) }
                    else -> null
                }
            }
        }

        return when (current) {
            null, JSONObject.NULL -> null
            is String -> current
            else -> current.toString()
        }
    }

    private fun parsePluginObject(obj: JSONObject, fallbackUpdateUrl: String = ""): CustomJsonPlugin? {
        val name = obj.optString("name", "").trim()
        val searchUrl = obj.optString("searchUrl", obj.optString("search_url", "")).trim()
        if (name.isEmpty() || searchUrl.isEmpty()) return null

        val headersMap = mutableMapOf<String, String>()
        val headersObj = obj.optJSONObject("headers")
        if (headersObj != null) {
            val keys = headersObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                headersMap[k] = headersObj.optString(k, "")
            }
        }

        return CustomJsonPlugin(
            id = obj.optString("id", UUID.randomUUID().toString()).ifBlank { UUID.randomUUID().toString() },
            name = name,
            version = obj.optString("version", "1.0.0").ifBlank { "1.0.0" },
            author = obj.optString("author", ""),
            description = obj.optString("description", ""),
            enabled = obj.optBoolean("enabled", true),
            updateUrl = obj.optString("updateUrl", obj.optString("update_url", fallbackUpdateUrl)).trim(),
            searchUrl = searchUrl,
            method = obj.optString("method", "GET").uppercase().ifBlank { "GET" },
            headers = headersMap,
            bodyTemplate = obj.optString("bodyTemplate", obj.optString("body_template", "")),
            lyricsPath = obj.optString("lyricsPath", obj.optString("lyrics_path", "lrc")).ifBlank { "lrc" },
            translationPath = obj.optString("translationPath", obj.optString("translation_path", "")),
            titlePath = obj.optString("titlePath", obj.optString("title_path", "")),
            artistPath = obj.optString("artistPath", obj.optString("artist_path", "")),
            lastUpdatedAt = obj.optLong("lastUpdatedAt", System.currentTimeMillis())
        )
    }

    private fun serializePluginObject(plugin: CustomJsonPlugin): JSONObject {
        return JSONObject().apply {
            put("id", plugin.id)
            put("name", plugin.name)
            put("version", plugin.version)
            put("author", plugin.author)
            put("description", plugin.description)
            put("enabled", plugin.enabled)
            put("updateUrl", plugin.updateUrl)
            put("searchUrl", plugin.searchUrl)
            put("method", plugin.method)
            val headersObj = JSONObject()
            plugin.headers.forEach { (k, v) -> headersObj.put(k, v) }
            put("headers", headersObj)
            put("bodyTemplate", plugin.bodyTemplate)
            put("lyricsPath", plugin.lyricsPath)
            put("translationPath", plugin.translationPath)
            put("titlePath", plugin.titlePath)
            put("artistPath", plugin.artistPath)
            put("lastUpdatedAt", plugin.lastUpdatedAt)
        }
    }
}
