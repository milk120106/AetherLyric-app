package com.andsi.airlyrics.community

import android.content.Context
import com.andsi.airlyrics.logging.AppLogger
import com.andsi.airlyrics.settings.store.AetherLyricServerStore
import com.andsi.airlyrics.settings.store.ServerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

/**
 * 伺服器五級信任狀態。
 * 嚴禁使用 Emoji，以標準文字與色彩標籤識別。
 */
enum class NodeTrustLevel(val key: String, val displayName: String) {
    OFFICIAL("official", "官方節點"),
    VERIFIED("verified", "已驗證節點"),
    UNVERIFIED("unverified", "未驗證節點"),
    SUSPICIOUS("suspicious", "可疑節點"),
    BANNED("banned", "封禁節點");

    companion object {
        fun fromKey(key: String?): NodeTrustLevel = when (key?.lowercase()?.trim()) {
            "official" -> OFFICIAL
            "verified" -> VERIFIED
            "suspicious" -> SUSPICIOUS
            "banned" -> BANNED
            else -> UNVERIFIED
        }
    }
}

/**
 * 公開社群伺服器資料結構。
 */
data class CommunityServer(
    val id: String,
    val name: String,
    val url: String,
    val regionCode: String,
    val trustLevel: NodeTrustLevel,
    val capabilities: List<String>,
    val description: String,
    val maintainer: String = "社群貢獻者",
    val latencyMs: Int = 50
)

/**
 * 公開伺服器探索與安全節點黑名單驗證器 (CommunityServerExplorer)。
 * 實作兩段式加入流程、五級信任標籤與惡意節點安全阻斷。
 */
object CommunityServerExplorer {

    // 惡意節點與危險 IP 黑名單列表
    private val DANGEROUS_HOST_BLACKLIST = setOf(
        "198.51.100.1",
        "203.0.113.5",
        "malicious.aetherlyric.xyz",
        "phishing-lyrics.tk",
        "185.220.101.5"
    )

    /**
     * 檢查指定 URL 是否命中危險節點黑名單。
     */
    fun isBlacklisted(url: String): Boolean {
        val cleanUrl = url.lowercase().trim()
        return DANGEROUS_HOST_BLACKLIST.any { cleanUrl.contains(it) }
    }

    /**
     * 讀取公開社群伺服器清單（支援遠端 servers.json 與本網段探索預設節點）。
     */
    suspend fun fetchCommunityServers(context: Context): List<CommunityServer> = withContext(Dispatchers.IO) {
        val list = mutableListOf<CommunityServer>()

        // 預設內建社群節點庫
        val defaultNodes = listOf(
            CommunityServer(
                id = "official_global_1",
                name = "AetherLyric 官方亞太節點",
                url = "https://ap-east.aetherlyric.org",
                regionCode = "TW",
                trustLevel = NodeTrustLevel.OFFICIAL,
                capabilities = listOf("AI辨識", "TTML", "yt-dlp", "雙引擎協同"),
                description = "官方營運高可用性亞太節點，支援完整 Whisper 與 TTML 逐字歌詞解析",
                maintainer = "官方維護",
                latencyMs = 28
            ),
            CommunityServer(
                id = "official_us_1",
                name = "AetherLyric 官方北美節點",
                url = "https://us-west.aetherlyric.org",
                regionCode = "US",
                trustLevel = NodeTrustLevel.OFFICIAL,
                capabilities = listOf("AI辨識", "TTML", "yt-dlp"),
                description = "官方北美加速伺服器，具備充足 GPU 轉錄佇列",
                maintainer = "官方維護",
                latencyMs = 120
            ),
            CommunityServer(
                id = "verified_jp_1",
                name = "東京社群鏡像 (Tokyo Mirror)",
                url = "https://jp-mirror.lyrics-hub.net",
                regionCode = "JP",
                trustLevel = NodeTrustLevel.VERIFIED,
                capabilities = listOf("TTML", "雙引擎協同"),
                description = "由認證開源愛好者維護，提供高速日文歌詞查詢與錨點時間軸對齊",
                maintainer = "認證維護者",
                latencyMs = 45
            ),
            CommunityServer(
                id = "unverified_eu_1",
                name = "歐洲自由節點 (Frankfurt Hub)",
                url = "https://eu-node.openlyrics.io",
                regionCode = "DE",
                trustLevel = NodeTrustLevel.UNVERIFIED,
                capabilities = listOf("TTML"),
                description = "社群自建節點，未完成官方簽名驗證",
                maintainer = "社群貢獻者",
                latencyMs = 210
            ),
            CommunityServer(
                id = "suspicious_node_1",
                name = "第三方匿名轉發節點",
                url = "http://anon-proxy.lyrics-node.cc",
                regionCode = "RU",
                trustLevel = NodeTrustLevel.SUSPICIOUS,
                capabilities = listOf("AI辨識"),
                description = "近期回傳憑證異常，存在流量審查或不穩定風險",
                maintainer = "匿名維護者",
                latencyMs = 380
            ),
            CommunityServer(
                id = "banned_node_1",
                name = "已知惡意注入節點",
                url = "http://malicious.aetherlyric.xyz",
                regionCode = "XX",
                trustLevel = NodeTrustLevel.BANNED,
                capabilities = emptyList(),
                description = "曾回傳偽造歌詞與惡意導向指令，官方安全中心已永久封禁",
                maintainer = "已封禁",
                latencyMs = 999
            )
        )

        list.addAll(defaultNodes)

        // 嘗試同步遠端社群 servers.json
        runCatching {
            val endpoint = "https://raw.githubusercontent.com/milk120106/AetherLyric-community/main/data/servers.json"
            val conn = AetherLyricServerStore.openConnectionWithProxy(context, endpoint)
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            val jsonText = conn.getInputStream().bufferedReader().use { it.readText() }
            val arr = if (jsonText.trimStart().startsWith("[")) {
                JSONArray(jsonText)
            } else {
                JSONObject(jsonText).optJSONArray("servers")
            }
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val capArr = obj.optJSONArray("capabilities")
                    val caps = mutableListOf<String>()
                    if (capArr != null) {
                        for (c in 0 until capArr.length()) caps.add(capArr.getString(c))
                    }
                    val trustKey = obj.optString("trust_status", obj.optString("trust_level", "unverified"))
                    val maintainerStr = obj.optString("owner", obj.optString("maintainer", "社群貢獻者"))
                    val node = CommunityServer(
                        id = obj.optString("id", "remote_$i"),
                        name = obj.optString("name", "社群節點"),
                        url = obj.optString("url", ""),
                        regionCode = obj.optString("region", obj.optString("country", "UN")),
                        trustLevel = NodeTrustLevel.fromKey(trustKey),
                        capabilities = caps,
                        description = obj.optString("description", ""),
                        maintainer = maintainerStr,
                        latencyMs = obj.optInt("latency_ms", 100)
                    )
                    if (node.url.isNotBlank() && list.none { it.url == node.url }) {
                        list.add(node)
                    }
                }
            }
        }.onFailure {
            AppLogger.i("CommunityServerExplorer", "遠端社群伺服器列表取得略過或失敗：${it.message}")
        }

        list
    }

    /**
     * 加入社群伺服器至本地列表。
     * 若為 banned 或命中黑名單，拒絕加入並拋出安全例外。
     */
    fun addCommunityServerToLocal(context: Context, server: CommunityServer): ServerProfile {
        if (server.trustLevel == NodeTrustLevel.BANNED) {
            throw SecurityException("此節點已被封禁，嚴禁加入！")
        }
        if (isBlacklisted(server.url)) {
            throw SecurityException("此伺服器命中安全中心黑名單，已阻斷連線！")
        }

        return AetherLyricServerStore.addProfile(
            context = context,
            userAlias = server.name,
            url = server.url,
            serverDisplayName = server.name
        )
    }
}
