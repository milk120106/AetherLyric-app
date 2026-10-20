package com.andsi.airlyrics.lyrics.storage

import android.content.Context
import com.andsi.airlyrics.lyrics.LyricsProviderResult
import com.andsi.airlyrics.lyrics.parser.LrcParser
import com.andsi.airlyrics.lyrics.parser.WordByWordLrcParser
import com.andsi.airlyrics.lyrics.providers.SpicyLyricsParser
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Private, non-backed-up, bounded-lifetime documents; never use the user's lyrics directory. */
internal class SpicyLyricsCache(private val directory: File, private val clock: () -> Long = System::currentTimeMillis) {
    companion object {
        const val PREFIX = "spicy:"
        private val lock = Any()
        fun forContext(context: Context) = SpicyLyricsCache(File(context.noBackupFilesDir, "spicy-lyrics"))
        fun key(title: String, artist: String, duration: Long): String = MessageDigest.getInstance("SHA-256")
            .digest(org.json.JSONArray(listOf(title.trim(), artist.trim(), duration)).toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
    private fun file(key: String): File {
        require(Regex("[0-9a-f]{64}").matches(key))
        return File(directory, "$key.json")
    }
    private fun document(key: String): JSONObject? {
        val f = file(key)
        if (!f.exists()) return null
        return runCatching {
            val doc = JSONObject(String(Files.readAllBytes(f.toPath()), Charsets.UTF_8))
            val at = doc.getLong("fetchedAt")
            if (clock() < at || clock() - at >= SpicyLyricsParser.RETENTION_MS) {
                f.delete(); LyricsStorage.markContentChanged(); null
            } else doc
        }.getOrElse {
            if (f.exists()) { f.delete(); LyricsStorage.markContentChanged() }
            null
        }
    }
    private fun result(key: String, doc: JSONObject): LyricsProviderResult? {
        val original = SpicyLyricsParser.parse(doc.getString("raw"), key, doc.getLong("fetchedAt")) ?: return null
        val edit = doc.optString("edit")
        if (edit.isBlank()) return original
        return if (original.wordByWordLines.isNotEmpty()) {
            val words = doc.optString("wordEdit").takeIf { it.isNotEmpty() }
                ?.let(WordByWordLyricsJsonCodec::parseWordByWordLinesJson)
                ?: WordByWordLrcParser.parseImport(edit).wordByWordLines
            original.copy(plainLrc = WordByWordLrcParser.wordByWordLinesToPlainLrc(words.filterNot { it.isBackground }), translatedLrc = null, wordByWordLines = words)
        } else original.copy(plainLrc = edit, translatedLrc = null)
    }
    fun read(key: String): LyricsProviderResult? = synchronized(lock) {
        runCatching { document(key)?.let { result(key, it) } }.getOrNull()
    }
    /** Lookup caches are mode-specific; the saved document remains bound to the original song. */
    fun readForSearch(key: String, searchKey: String): LyricsProviderResult? = synchronized(lock) {
        runCatching {
            document(key)?.takeIf { it.optString("searchKey", key) == searchKey }?.let { result(key, it) }
        }.getOrNull()
    }
    fun save(key: String, title: String, artist: String, album: String, duration: Long, raw: String, searchKey: String = key, fetchedAtMs: Long = clock()): LyricsProviderResult? = synchronized(lock) {
        val parsed = SpicyLyricsParser.parse(raw, key, fetchedAtMs) ?: return null
        val doc = JSONObject().put("raw", raw).put("fetchedAt", fetchedAtMs).put("updatedAt", fetchedAtMs)
            .put("title", title).put("artist", artist).put("album", album).put("duration", duration).put("searchKey", searchKey)
        write(key, doc)
        parsed
    }
    internal fun snapshot(key: String): ByteArray? = synchronized(lock) {
        val f = file(key)
        if (f.exists()) Files.readAllBytes(f.toPath()) else null
    }

    internal fun restore(key: String, bytes: ByteArray?) = synchronized(lock) {
        if (bytes == null) file(key).delete()
        else write(key, JSONObject(String(bytes, Charsets.UTF_8)))
    }

    private fun write(key: String, document: JSONObject) {
        check(directory.isDirectory || directory.mkdirs())
        val f = file(key)
        val temp = File.createTempFile(key.take(16), ".tmp", directory)
        temp.writeBytes(document.toString().toByteArray(Charsets.UTF_8))
        try {
            Files.move(temp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        LyricsStorage.markContentChanged()
    }
    fun edit(key: String, text: String, word: Boolean): LyricsStorage.LocalLyricsUpdateResult = synchronized(lock) {
        val doc = document(key) ?: return@synchronized LyricsStorage.LocalLyricsUpdateResult(false)
        val payload = result(key, doc) ?: return@synchronized LyricsStorage.LocalLyricsUpdateResult(false)
        if (word != payload.wordByWordLines.isNotEmpty()) return@synchronized LyricsStorage.LocalLyricsUpdateResult(false)
        val validation = if (word) {
            val v = WordByWordLrcParser.validateForStorage(text)
            LyricsStorage.LocalLyricsUpdateResult(v.isValid, v.invalidLineNumbers)
        } else {
            val v = LrcParser.validateForStorage(text)
            LyricsStorage.LocalLyricsUpdateResult(v.isValid, v.invalidLineNumbers)
        }
        if (!validation.saved) return@synchronized validation
        val currentText = if (word) WordByWordLrcParser.wordByWordLinesToWordByWordLrc(payload.wordByWordLines) else payload.plainLrc
        if (text == currentText) return@synchronized validation
        if (word) {
            val baseline = WordByWordLrcParser.parse(currentText)
            val edited = WordByWordLrcParser.parse(text).mapIndexed { i, line ->
                val before = baseline.getOrNull(i)
                val precise = payload.wordByWordLines.getOrNull(i)
                if (before == null || precise == null || before.segments.size != line.segments.size) line
                else line.copy(
                    isBackground = precise.isBackground,
                    startMs = if (line.startMs == before.startMs) precise.startMs else line.startMs,
                    endMs = if (line.segments.map { it.startMs } == before.segments.map { it.startMs }) precise.endMs else line.endMs,
                    segments = line.segments.mapIndexed { j, segment ->
                        if (segment.startMs == before.segments[j].startMs) precise.segments[j].copy(text = segment.text) else segment
                    }
                )
            }
            doc.put("wordEdit", WordByWordLyricsJsonCodec.wordByWordLinesToJson(edited))
        }
        // fetchedAt is immutable, even for edits. The original response (including marks) survives intact.
        write(key, doc.put("edit", text).put("updatedAt", clock()))
        validation
    }
    fun delete(key: String): Boolean = synchronized(lock) {
        val existed = file(key).exists()
        file(key).delete()
        if (existed) LyricsStorage.markContentChanged()
        existed
    }
    fun item(key: String): LyricsStorage.LocalLyricsItem? = synchronized(lock) {
        runCatching {
            val doc = document(key) ?: return@synchronized null
            val payload = result(key, doc) ?: return@synchronized null
            LyricsStorage.LocalLyricsItem(PREFIX + key, doc.getLong("updatedAt"), file(key).length(), doc.getString("title"),
                doc.getString("artist"), doc.optString("album"), doc.getLong("duration"), PREFIX + key,
                LyricsStorage.SOURCE_PROVIDER_CACHE, "spicy", true, payload.wordByWordLines.isNotEmpty())
        }.getOrNull()
    }
    fun list(): List<LyricsStorage.LocalLyricsItem> = synchronized(lock) {
        directory.listFiles()?.map { it.name.removeSuffix(".bak").removeSuffix(".new") }
            ?.filter { it.endsWith(".json") && Regex("[0-9a-f]{64}").matches(it.removeSuffix(".json")) }
            ?.distinct()?.mapNotNull { item(it.removeSuffix(".json")) }.orEmpty()
    }
    fun prune() { list() }
    fun clear(): Boolean = synchronized(lock) {
        val hadFiles = directory.listFiles()?.isNotEmpty() == true
        val success = !directory.exists() || directory.deleteRecursively()
        if (success && hadFiles) LyricsStorage.markContentChanged()
        success
    }
}
