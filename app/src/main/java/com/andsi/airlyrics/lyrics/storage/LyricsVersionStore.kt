package com.andsi.airlyrics.lyrics.storage

import android.content.Context
import com.andsi.airlyrics.core.model.SongIdentity
import com.andsi.airlyrics.settings.store.LyricsOffsetStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * 歌詞版本實體。
 * @param versionId 唯一識別碼
 * @param songKey 歌曲對應之唯一識別鍵 (SongIdentity.storageKey)
 * @param versionName 使用者自訂版本名稱 (如「羅馬拼音版」、「日文翻譯版」、「AI校正版」)
 * @param lrcContent LRC 歌詞文字內容
 * @param offsetMs 此版本專屬的時間軸偏移量 (毫秒)
 * @param source 來源 (manual, ai, imported 等)
 * @param createdAt 建立時間戳記
 * @param updatedAt 更新時間戳記
 */
data class LyricsVersion(
    val versionId: String = UUID.randomUUID().toString(),
    val songKey: String,
    val versionName: String,
    val lrcContent: String,
    val offsetMs: Long = 0L,
    val source: String = "manual",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 多歌詞版本本地儲存庫。
 * 支援同一首歌曲 (songKey) 儲存多個不同版本歌詞，並在切換版本時自動套用該版本專屬的時間軸偏移量。
 */
object LyricsVersionStore {
    private const val VERSIONS_FILE_NAME = "lyrics_versions.json"
    private const val ACTIVE_PREFS_NAME = "lyrics_active_versions"

    private fun getStorageFile(context: Context): File {
        val dir = File(context.filesDir, "lyrics-versions")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, VERSIONS_FILE_NAME)
    }

    private fun getActivePrefs(context: Context) =
        context.getSharedPreferences(ACTIVE_PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    private fun readAll(context: Context): MutableList<LyricsVersion> {
        val file = getStorageFile(context)
        if (!file.exists()) return mutableListOf()
        val text = file.readText(Charsets.UTF_8)
        if (text.isBlank()) return mutableListOf()

        return runCatching {
            val array = JSONArray(text)
            val list = mutableListOf<LyricsVersion>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    LyricsVersion(
                        versionId = obj.optString("versionId", UUID.randomUUID().toString()),
                        songKey = obj.getString("songKey"),
                        versionName = obj.optString("versionName", "版本"),
                        lrcContent = obj.optString("lrcContent", ""),
                        offsetMs = obj.optLong("offsetMs", 0L),
                        source = obj.optString("source", "manual"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
            list
        }.getOrDefault(mutableListOf())
    }

    @Synchronized
    private fun writeAll(context: Context, versions: List<LyricsVersion>) {
        val array = JSONArray()
        versions.forEach { v ->
            array.put(JSONObject().apply {
                put("versionId", v.versionId)
                put("songKey", v.songKey)
                put("versionName", v.versionName)
                put("lrcContent", v.lrcContent)
                put("offsetMs", v.offsetMs)
                put("source", v.source)
                put("createdAt", v.createdAt)
                put("updatedAt", v.updatedAt)
            })
        }
        val file = getStorageFile(context)
        file.writeText(array.toString(2), Charsets.UTF_8)
    }

    /**
     * 取得指定歌曲的所有歌詞版本。
     * 若本機尚無該歌曲之多版本記錄，且存在預設歌詞，則自動建立一筆「預設版本」。
     */
    fun getVersions(context: Context, song: SongIdentity, defaultLrc: String? = null): List<LyricsVersion> {
        val key = song.storageKey()
        val all = readAll(context)
        val songVersions = all.filter { it.songKey == key }

        if (songVersions.isEmpty() && !defaultLrc.isNullOrBlank()) {
            val initial = LyricsVersion(
                songKey = key,
                versionName = "預設版本",
                lrcContent = defaultLrc,
                offsetMs = LyricsOffsetStore.getOffsetMs(context, song),
                source = "original"
            )
            all.add(initial)
            writeAll(context, all)
            setActiveVersionId(context, key, initial.versionId)
            return listOf(initial)
        }
        return songVersions
    }

    fun getActiveVersionId(context: Context, songKey: String): String? {
        return getActivePrefs(context).getString("active_$songKey", null)
    }

    fun setActiveVersionId(context: Context, songKey: String, versionId: String) {
        getActivePrefs(context).edit().putString("active_$songKey", versionId).apply()
    }

    /**
     * 切換當前生效之版本，並自動套用該版本專屬的時間軸偏移量。
     */
    fun switchVersion(context: Context, song: SongIdentity, versionId: String): LyricsVersion? {
        val versions = getVersions(context, song)
        val target = versions.firstOrNull { it.versionId == versionId } ?: return null

        setActiveVersionId(context, song.storageKey(), versionId)

        // 自動將該版本專屬的時間軸偏移量同步寫入 LyricsOffsetStore (單曲專屬)
        LyricsOffsetStore.savePerSongOffset(context, song, target.offsetMs)

        // 寫入當前本機歌詞內容
        PlainLyricsStorageOps.savePlainLyrics(
            context = context,
            title = song.title,
            artist = song.artist,
            duration = song.durationMs,
            plainLrc = target.lrcContent,
            plainSource = LyricsStorage.SOURCE_MANUAL_IMPORT,
            plainProvider = "version:${target.versionName}"
        )

        return target
    }

    /**
     * 新增歌詞版本。
     */
    /**
     * 重新命名指定版本。
     */
    fun renameVersion(context: Context, song: SongIdentity, versionId: String, newName: String): Boolean {
        val key = song.storageKey()
        val all = readAll(context)
        val index = all.indexOfFirst { it.songKey == key && it.versionId == versionId }
        if (index == -1) return false
        val current = all[index]
        all[index] = current.copy(versionName = newName.trim(), updatedAt = System.currentTimeMillis())
        writeAll(context, all)
        return true
    }

    fun addVersion(context: Context, version: LyricsVersion): LyricsVersion {
        val all = readAll(context)
        val newVersion = version.copy(updatedAt = System.currentTimeMillis())
        all.add(newVersion)
        writeAll(context, all)
        return newVersion
    }

    /**
     * 更新歌詞版本內容或專屬偏移量。
     */
    fun updateVersion(context: Context, version: LyricsVersion): Boolean {
        val all = readAll(context)
        val index = all.indexOfFirst { it.versionId == version.versionId }
        if (index == -1) return false
        all[index] = version.copy(updatedAt = System.currentTimeMillis())
        writeAll(context, all)
        return true
    }

    /**
     * 刪除指定版本 (若為目前啟動中之版本，則自動切換至其他版本)。
     */
    fun deleteVersion(context: Context, song: SongIdentity, versionId: String): Boolean {
        val key = song.storageKey()
        val all = readAll(context)
        val target = all.firstOrNull { it.songKey == key && it.versionId == versionId } ?: return false

        all.remove(target)
        writeAll(context, all)

        val remaining = all.filter { it.songKey == key }
        if (getActiveVersionId(context, key) == versionId && remaining.isNotEmpty()) {
            switchVersion(context, song, remaining.first().versionId)
        }
        return true
    }
}
