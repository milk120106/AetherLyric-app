package com.andsi.airlyrics.app.interaction

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors
import java.util.concurrent.Future
import org.json.JSONObject

/** Ordered writes ensure that a delayed save cannot recreate a discarded draft. */
internal class LyricsDraftStore(private val directory: File) {
    fun write(id: String, text: String): Future<*> = io.submit {
        directory.mkdirs()
        val file = targetFile(id)
        val temp = File.createTempFile(id, ".tmp", directory)
        val bytes = JSONObject().put("version", 1).put("text", text).toString().toByteArray(Charsets.UTF_8)
        temp.writeBytes(bytes)
        try {
            Files.move(
                temp.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(
                temp.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    fun read(id: String): Future<String?> = io.submit<String?> {
        runCatching {
            val file = targetFile(id)
            if (!file.exists()) return@runCatching null
            val raw = String(Files.readAllBytes(file.toPath()), Charsets.UTF_8)
            val json = JSONObject(raw)
            if (json.getInt("version") == 1) json.getString("text") else null
        }.getOrNull()
    }

    fun delete(id: String): Future<*> = io.submit { targetFile(id).delete() }
    fun prune(keep: String?): Future<*> = io.submit {
        directory.listFiles()?.filter { it.name.substringBefore('.') != keep }?.forEach(File::delete)
    }

    private fun targetFile(id: String): File {
        require(id.matches(Regex("[a-zA-Z0-9-]+")))
        return File(directory, "$id.json")
    }

    companion object {
        private val liveOwners = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

        fun forSession(context: android.content.Context, state: com.andsi.airlyrics.ui.state.MainInteractionState, kind: String): LyricsDraftStore {
            val owner = state.fileOwner
            liveOwners.add(owner)
            val base = File(context.filesDir, kind)
            val directory = File(base, owner)
            val legacyId = if (kind == "editor-drafts") state.read("editor")?.getString("id") else null
            io.submit {
                directory.mkdirs()
                directory.setLastModified(System.currentTimeMillis())
                // Old flat editor drafts remain readable across an application upgrade.
                if (legacyId != null && legacyId.matches(Regex("[a-zA-Z0-9-]+"))) {
                    listOf(".json", ".json.bak").forEach { suffix ->
                        val old = File(base, legacyId + suffix)
                        val target = File(directory, legacyId + suffix)
                        if (old.isFile && !target.exists()) old.renameTo(target)
                    }
                }
                val expiry = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
                base.listFiles()?.filter { it.lastModified() < expiry && it.name !in liveOwners }?.forEach { it.deleteRecursively() }
            }
            return LyricsDraftStore(directory)
        }

        private val io = Executors.newSingleThreadExecutor { task -> Thread(task, "AirLyrics-Drafts").apply { isDaemon = true } }
    }
}
