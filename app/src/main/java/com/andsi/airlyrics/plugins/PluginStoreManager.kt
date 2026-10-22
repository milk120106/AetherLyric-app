package com.andsi.airlyrics.plugins

import android.content.Context
import com.andsi.airlyrics.community.NodeTrustLevel
import com.andsi.airlyrics.core.prefs.prefs
import com.andsi.airlyrics.lyrics.providers.CustomJsonPluginStore
import com.andsi.airlyrics.lyrics.providers.CustomJsonPlugin
import com.andsi.airlyrics.settings.store.AetherLyricServerStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 外掛分類類別。
 */
enum class PluginCategory(val key: String, val displayName: String) {
    LYRICS_SOURCE("lyrics_source", "歌詞源"),
    TRANSLATOR("translator", "翻譯"),
    SYSTEM_TOOL("system_tool", "系統工具");

    companion object {
        fun fromKey(key: String?): PluginCategory = when (key?.lowercase()?.trim()) {
            "translator" -> TRANSLATOR
            "system_tool" -> SYSTEM_TOOL
            else -> LYRICS_SOURCE
        }
    }
}

/**
 * 擴充商店外掛項目結構。
 */
data class StorePluginItem(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val maintainerBadge: String, // 官方維護、認證維護者、社群貢獻者
    val category: PluginCategory,
    val trustLevel: NodeTrustLevel,
    val description: String,
    val downloadUrl: String,
    val isInstalled: Boolean = false,
    val isEnabled: Boolean = false,
    val banReason: String? = null
)

/**
 * 擴充商店管理中心 (PluginStoreManager)。
 * 負責外掛市集目錄管理、違規外掛自動禁用比對與啟動單一合併彈窗提示。
 */
object PluginStoreManager {
    const val COMMUNITY_PLUGINS_URL = "https://raw.githubusercontent.com/milk120106/AetherLyric-community/main/data/plugins.json"
    private const val PREFS_NAME = "plugin_store_prefs"
    private const val KEY_PENDING_DISABLED_ALERTS = "pending_disabled_alerts"
    private const val KEY_CACHED_REMOTE_PLUGINS = "cached_remote_plugins_json"

    private fun store(context: Context) = prefs(context, PREFS_NAME)

    /**
     * 從遠端社群倉庫非同步同步 plugins.json。
     */
    suspend fun syncRemoteStorePlugins(context: Context): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val conn = AetherLyricServerStore.openConnectionWithProxy(context, COMMUNITY_PLUGINS_URL)
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            val jsonText = conn.getInputStream().bufferedReader().use { it.readText() }
            if (jsonText.isNotBlank()) {
                store(context).setString(KEY_CACHED_REMOTE_PLUGINS, jsonText)
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    /**
     * 內建與社群同步外掛庫目錄。
     */
    fun getStorePlugins(context: Context): List<StorePluginItem> {
        val installedPlugins = CustomJsonPluginStore.getPlugins(context)
        val installedMap = installedPlugins.associateBy { it.id }

        val remoteList = mutableListOf<StorePluginItem>()
        val cachedJson = store(context).getString(KEY_CACHED_REMOTE_PLUGINS, "").orEmpty()
        if (cachedJson.isNotBlank()) {
            runCatching {
                val arr = if (cachedJson.trimStart().startsWith("[")) {
                    JSONArray(cachedJson)
                } else {
                    JSONObject(cachedJson).optJSONArray("plugins")
                }
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.optString("id", "")
                        val name = obj.optString("name", "")
                        if (id.isNotBlank() && name.isNotBlank()) {
                            remoteList.add(
                                StorePluginItem(
                                    id = id,
                                    name = name,
                                    version = obj.optString("version", "1.0.0"),
                                    author = obj.optString("author", "社群貢獻者"),
                                    maintainerBadge = when (obj.optString("maintainer_level", "contributor")) {
                                        "official" -> "官方維護"
                                        "maintainer" -> "認證維護者"
                                        else -> "社群貢獻者"
                                    },
                                    category = PluginCategory.fromKey(obj.optString("category")),
                                    trustLevel = NodeTrustLevel.fromKey(obj.optString("trust_status")),
                                    description = obj.optString("description", ""),
                                    downloadUrl = obj.optString("source_url", obj.optString("download_url", ""))
                                )
                            )
                        }
                    }
                }
            }
        }

        val catalog = listOf(
            // 歌詞源 (lyrics_source)
            StorePluginItem(
                id = "plugin_lrclib_pro",
                name = "LRCLIB 增強擴充包",
                version = "1.2.0",
                author = "AetherLyric Team",
                maintainerBadge = "官方維護",
                category = PluginCategory.LYRICS_SOURCE,
                trustLevel = NodeTrustLevel.OFFICIAL,
                description = "具備自動重試與繁體自動對照的 LRCLIB 社群來源適配器",
                downloadUrl = "https://cdn.aetherlyric.org/plugins/lrclib_pro.json"
            ),
            StorePluginItem(
                id = "plugin_bilibili_audio",
                name = "Bilibili 音訊歌詞解析器",
                version = "1.0.5",
                author = "Community Contributor",
                maintainerBadge = "認證維護者",
                category = PluginCategory.LYRICS_SOURCE,
                trustLevel = NodeTrustLevel.VERIFIED,
                description = "抓取 B 站投稿音訊與字幕，自動轉換為標準 LRC",
                downloadUrl = "https://cdn.aetherlyric.org/plugins/bilibili_audio.json"
            ),
            StorePluginItem(
                id = "plugin_migu_hq",
                name = "咪咕音樂高音質歌詞",
                version = "1.1.0",
                author = "Migu OpenAPI",
                maintainerBadge = "認證維護者",
                category = PluginCategory.LYRICS_SOURCE,
                trustLevel = NodeTrustLevel.VERIFIED,
                description = "咪咕音樂社群公開端點，收錄華語正版專輯歌詞",
                downloadUrl = "https://cdn.aetherlyric.org/plugins/migu_hq.json"
            ),
            StorePluginItem(
                id = "plugin_uta_net_jp",
                name = "Uta-Net 日本歌詞適配器",
                version = "0.9.8",
                author = "J-Lyric Dev",
                maintainerBadge = "社群貢獻者",
                category = PluginCategory.LYRICS_SOURCE,
                trustLevel = NodeTrustLevel.UNVERIFIED,
                description = "解析 Uta-Net 網站歌詞與動漫 OST 完整文本",
                downloadUrl = "https://cdn.aetherlyric.org/plugins/uta_net.json"
            ),
            StorePluginItem(
                id = "plugin_suspicious_source",
                name = "第三方聚合爬蟲外掛",
                version = "0.2.1",
                author = "Unknown Hacker",
                maintainerBadge = "社群貢獻者",
                category = PluginCategory.LYRICS_SOURCE,
                trustLevel = NodeTrustLevel.SUSPICIOUS,
                description = "頻繁觸發逾時與非標準重導向，存在資料外洩隱患",
                downloadUrl = "http://bad-proxy.xyz/bad_lyrics.json",
                banReason = "行為可疑：頻繁向不明伺服器回傳請求記錄"
            ),
            StorePluginItem(
                id = "plugin_banned_malware",
                name = "破解版 VIP 歌詞解密器",
                version = "3.0.0",
                author = "BlackHat",
                maintainerBadge = "已封禁",
                category = PluginCategory.LYRICS_SOURCE,
                trustLevel = NodeTrustLevel.BANNED,
                description = "含有惡意程式碼注入風險，已被官方安全庫永久封禁",
                downloadUrl = "http://malicious.aetherlyric.xyz/bad.json",
                banReason = "惡意程式碼：注入未授權廣告腳本"
            ),

            // 翻譯 (translator)
            StorePluginItem(
                id = "plugin_google_translate_zh",
                name = "Google 翻譯繁體本地引擎",
                version = "2.0.0",
                author = "AetherLyric Team",
                maintainerBadge = "官方維護",
                category = PluginCategory.TRANSLATOR,
                trustLevel = NodeTrustLevel.OFFICIAL,
                description = "免 API 金鑰之高相容 Google Translate 繁體中文雙語對照引擎",
                downloadUrl = "https://cdn.aetherlyric.org/plugins/google_trans.json"
            ),
            StorePluginItem(
                id = "plugin_opencc_converter",
                name = "OpenCC 簡繁詞彙精確對照器",
                version = "1.3.2",
                author = "OpenCC Team",
                maintainerBadge = "認證維護者",
                category = PluginCategory.TRANSLATOR,
                trustLevel = NodeTrustLevel.VERIFIED,
                description = "依照台灣與香港用語慣例精確轉換術語與歌詞文字",
                downloadUrl = "https://cdn.aetherlyric.org/plugins/opencc.json"
            ),
            StorePluginItem(
                id = "plugin_deepl_unofficial",
                name = "DeepL 自由翻譯通道",
                version = "1.0.1",
                author = "AI Translators",
                maintainerBadge = "社群貢獻者",
                category = PluginCategory.TRANSLATOR,
                trustLevel = NodeTrustLevel.UNVERIFIED,
                description = "呼叫 DeepL 語意翻譯引擎進行多語言逐行翻譯",
                downloadUrl = "https://cdn.aetherlyric.org/plugins/deepl.json"
            ),

            // 系統工具 (system_tool)
            StorePluginItem(
                id = "plugin_shizuku_enhancer",
                name = "Shizuku 特權播放器增強模組",
                version = "1.1.0",
                author = "AetherLyric Team",
                maintainerBadge = "官方維護",
                category = PluginCategory.SYSTEM_TOOL,
                trustLevel = NodeTrustLevel.OFFICIAL,
                description = "深度掛載 MediaSession 內部進度輪詢，實現 1ms 精確歌詞滾動",
                downloadUrl = "https://cdn.aetherlyric.org/plugins/shizuku_enhancer.json"
            ),
            StorePluginItem(
                id = "plugin_status_bar_ticker",
                name = "狀態列歌詞滾動器",
                version = "1.0.4",
                author = "Android Modders",
                maintainerBadge = "認證維護者",
                category = PluginCategory.SYSTEM_TOOL,
                trustLevel = NodeTrustLevel.VERIFIED,
                description = "在 Android 系統狀態列即時顯示當前同步動態歌詞",
                downloadUrl = "https://cdn.aetherlyric.org/plugins/status_bar.json"
            )
        )

        val mergedCatalog = (catalog + remoteList).distinctBy { it.id }

        return mergedCatalog.map { item ->
            val installed = installedMap[item.id]
            if (installed != null) {
                item.copy(isInstalled = true, isEnabled = installed.enabled)
            } else {
                item
            }
        }
    }

    /**
     * 違規外掛自動禁用機制：
     * 當同步社群外掛庫偵測到已安裝的外掛被列入 banned 或 suspicious 時，
     * 自動關閉該外掛並記錄停用警告資訊。
     */
    fun performAutoDisableCheck(context: Context): List<Pair<String, String>> {
        val storePlugins = getStorePlugins(context)
        val installedPlugins = CustomJsonPluginStore.getPlugins(context)
        val newlyDisabled = mutableListOf<Pair<String, String>>()

        installedPlugins.forEach { plugin ->
            val matchedStoreItem = storePlugins.firstOrNull { it.id == plugin.id || it.name == plugin.name }
            if (matchedStoreItem != null) {
                val isProblematic = matchedStoreItem.trustLevel == NodeTrustLevel.BANNED ||
                    matchedStoreItem.trustLevel == NodeTrustLevel.SUSPICIOUS
                if (isProblematic && plugin.enabled) {
                    // 自動停用
                    CustomJsonPluginStore.setPluginEnabled(context, plugin.id, false)
                    val reason = matchedStoreItem.banReason ?: if (matchedStoreItem.trustLevel == NodeTrustLevel.BANNED) {
                        "此來源已被官方安全庫列為封禁節點"
                    } else {
                        "此來源近期行為異常，已被標記為可疑外掛"
                    }
                    newlyDisabled.add(plugin.name to reason)
                }
            }
        }

        if (newlyDisabled.isNotEmpty()) {
            val existingAlerts = getPendingDisabledAlerts(context).toMutableList()
            existingAlerts.addAll(newlyDisabled)
            savePendingDisabledAlerts(context, existingAlerts)
        }

        return newlyDisabled
    }

    fun getPendingDisabledAlerts(context: Context): List<Pair<String, String>> {
        val raw = store(context).getString(KEY_PENDING_DISABLED_ALERTS, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val list = mutableListOf<Pair<String, String>>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(obj.optString("name", "") to obj.optString("reason", ""))
            }
            list
        }.getOrDefault(emptyList())
    }

    private fun savePendingDisabledAlerts(context: Context, alerts: List<Pair<String, String>>) {
        val array = JSONArray()
        alerts.forEach { (name, reason) ->
            array.put(JSONObject().apply {
                put("name", name)
                put("reason", reason)
            })
        }
        store(context).setString(KEY_PENDING_DISABLED_ALERTS, array.toString())
    }

    fun clearPendingDisabledAlerts(context: Context) {
        store(context).setString(KEY_PENDING_DISABLED_ALERTS, null)
    }

    /**
     * 安裝商店外掛。
     */
    fun installStorePlugin(context: Context, item: StorePluginItem): Boolean {
        if (item.trustLevel == NodeTrustLevel.BANNED) {
            throw SecurityException("此外掛已被官方安全中心封禁，禁止安裝！")
        }
        // 建立 CustomJsonPlugin 物件
        val plugin = CustomJsonPlugin(
            id = item.id,
            name = item.name,
            version = item.version,
            author = item.author,
            description = item.description,
            searchUrl = item.downloadUrl,
            method = "GET",
            enabled = true
        )
        val current = CustomJsonPluginStore.getPlugins(context).toMutableList()
        current.removeAll { it.id == item.id }
        current.add(plugin)
        CustomJsonPluginStore.savePlugins(context, current)
        return true
    }
}
