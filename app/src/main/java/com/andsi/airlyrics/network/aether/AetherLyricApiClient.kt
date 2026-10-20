package com.andsi.airlyrics.network.aether

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 伺服器健康檢查回應資料結構。
 */
data class ServerHealthResponse(
    val status: String,
    val service: String,
    val version: String,
    val defaultEngine: String,
    val queueLength: Int,
    val availableModels: List<String>
)

/**
 * 伺服器線上歌詞查詢結果。
 */
data class LyricsSearchResult(
    val hit: Boolean,
    val trackTitle: String,
    val artist: String,
    val lrcContent: String,
    val source: String
)

/**
 * 建立任務回應。
 */
data class TaskCreateResponse(
    val taskId: String,
    val status: String,
    val message: String
)

/**
 * 任務狀態與即時進度回應。
 */
data class TaskStatusResponse(
    val taskId: String,
    val status: String,
    val progress: Int,
    val stage: String,
    val resultLrc: String?,
    val errorMessage: String?
)

/**
 * AetherLyric 伺服器通訊客戶端 (AetherLyricApiClient)。
 * 使用協程與原生連線串接 AetherLyric-server 之健康檢查、歌詞快查與端到端任務管線。
 */
object AetherLyricApiClient {
    private const val DEFAULT_CONNECT_TIMEOUT_MS = 5000
    private const val DEFAULT_READ_TIMEOUT_MS = 15000

    /**
     * 標準化 URL，確保通訊協定開頭並去除末尾斜線。
     */
    fun normalizeUrl(baseUrl: String): String {
        var trimmed = baseUrl.trim().removeSuffix("/")
        if (trimmed.isNotEmpty() && !trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "http://$trimmed"
        }
        return trimmed
    }

    /**
     * 測試伺服器連線狀態 (GET /api/v1/health)。
     */
    suspend fun testHealth(
        serverUrl: String,
        apiKey: String? = null
    ): Result<ServerHealthResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val base = normalizeUrl(serverUrl)
            if (base.isEmpty()) {
                throw IllegalArgumentException("伺服器位址不得為空白")
            }
            val targetUrl = "$base/api/v1/health"
            val conn = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = DEFAULT_CONNECT_TIMEOUT_MS
                readTimeout = DEFAULT_CONNECT_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                if (!apiKey.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                }
            }

            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_OK) {
                val responseText = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                val json = JSONObject(responseText)
                val modelsList = mutableListOf<String>()
                val modelsArr = json.optJSONArray("available_models")
                if (modelsArr != null) {
                    for (i in 0 until modelsArr.length()) {
                        modelsList.add(modelsArr.getString(i))
                    }
                }
                ServerHealthResponse(
                    status = json.optString("status", "ok"),
                    service = json.optString("service", "AetherLyric-server"),
                    version = json.optString("version", "1.0.0"),
                    defaultEngine = json.optString("default_engine", "local"),
                    queueLength = json.optInt("queue_length", 0),
                    availableModels = modelsList
                )
            } else if (code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                throw IllegalStateException("未授權 (HTTP 401)：請檢查 ADMIN_API_KEY 是否正確")
            } else {
                val errText = conn.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: ""
                throw IllegalStateException("伺服器回應錯誤 (HTTP $code) $errText")
            }
        }
    }

    /**
     * 快查外部或快取歌詞 (GET /api/v1/lyrics/search)。
     */
    suspend fun searchLyrics(
        serverUrl: String,
        title: String,
        artist: String = "",
        duration: Float? = null,
        apiKey: String? = null
    ): Result<LyricsSearchResult?> = withContext(Dispatchers.IO) {
        runCatching {
            val base = normalizeUrl(serverUrl)
            val encTitle = URLEncoder.encode(title, "UTF-8")
            val encArtist = URLEncoder.encode(artist, "UTF-8")
            var urlStr = "$base/api/v1/lyrics/search?title=$encTitle&artist=$encArtist"
            if (duration != null && duration > 0) {
                urlStr += "&duration=${duration.toInt()}"
            }

            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = DEFAULT_CONNECT_TIMEOUT_MS
                readTimeout = DEFAULT_READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                if (!apiKey.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                }
            }

            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_OK) {
                val responseText = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                val json = JSONObject(responseText)
                val hit = json.optBoolean("hit", false)
                val lrcContent = json.optString("lrc_content", "").trim()
                if (hit && lrcContent.isNotEmpty()) {
                    LyricsSearchResult(
                        hit = true,
                        trackTitle = json.optString("title", title),
                        artist = json.optString("artist", artist),
                        lrcContent = lrcContent,
                        source = json.optString("source", "server")
                    )
                } else {
                    null
                }
            } else {
                null
            }
        }
    }

    /**
     * 建立非同步音訊轉錄與時間軸校正任務 (POST /api/v1/tasks)。
     */
    suspend fun createTask(
        serverUrl: String,
        title: String? = null,
        artist: String? = null,
        audioFile: File? = null,
        preferredEngine: String = "groq",
        preferredSource: String = "lrclib",
        enableAiCorrection: Boolean = true,
        apiKey: String? = null
    ): Result<TaskCreateResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val base = normalizeUrl(serverUrl)
            val targetUrl = "$base/api/v1/tasks"

            if (audioFile != null && audioFile.exists()) {
                // 使用 multipart/form-data 上傳音訊檔案
                uploadTaskWithAudio(
                    targetUrl = targetUrl,
                    audioFile = audioFile,
                    title = title,
                    artist = artist,
                    preferredEngine = preferredEngine,
                    preferredSource = preferredSource,
                    enableAiCorrection = enableAiCorrection,
                    apiKey = apiKey
                )
            } else {
                // 使用 application/json 偏好設定發起任務
                createTaskJson(
                    targetUrl = targetUrl,
                    title = title,
                    artist = artist,
                    preferredEngine = preferredEngine,
                    preferredSource = preferredSource,
                    enableAiCorrection = enableAiCorrection,
                    apiKey = apiKey
                )
            }
        }
    }

    private fun createTaskJson(
        targetUrl: String,
        title: String?,
        artist: String?,
        preferredEngine: String,
        preferredSource: String,
        enableAiCorrection: Boolean,
        apiKey: String?
    ): TaskCreateResponse {
        val conn = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = DEFAULT_CONNECT_TIMEOUT_MS
            readTimeout = DEFAULT_READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
            if (!apiKey.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            }
        }

        val json = JSONObject().apply {
            put("track_title", title ?: "")
            put("artist", artist ?: "")
            put("preferred_engine", preferredEngine)
            put("preferred_lyrics_source", preferredSource)
            put("enable_ai_correction", enableAiCorrection)
        }

        conn.outputStream.use { os ->
            os.write(json.toString().toByteArray(StandardCharsets.UTF_8))
            os.flush()
        }

        val code = conn.responseCode
        if (code == HttpURLConnection.HTTP_ACCEPTED || code == HttpURLConnection.HTTP_OK) {
            val resText = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val resJson = JSONObject(resText)
            return TaskCreateResponse(
                taskId = resJson.getString("task_id"),
                status = resJson.optString("status", "queued"),
                message = resJson.optString("message", "Task queued")
            )
        } else {
            val err = conn.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: ""
            throw IllegalStateException("建立任務失敗 (HTTP $code): $err")
        }
    }

    private fun uploadTaskWithAudio(
        targetUrl: String,
        audioFile: File,
        title: String?,
        artist: String?,
        preferredEngine: String,
        preferredSource: String,
        enableAiCorrection: Boolean,
        apiKey: String?
    ): TaskCreateResponse {
        val boundary = "===AetherLyricBoundary" + System.currentTimeMillis() + "==="
        val conn = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = DEFAULT_CONNECT_TIMEOUT_MS
            readTimeout = 60000 // 上傳給予較長逾時時間
            doOutput = true
            doInput = true
            useCaches = false
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json")
            if (!apiKey.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            }
        }

        conn.outputStream.use { outputStream ->
            PrintWriter(OutputStreamWriter(outputStream, StandardCharsets.UTF_8), true).use { writer ->
                // 表單欄位
                fun writeFormField(name: String, value: String) {
                    writer.append("--$boundary\r\n")
                    writer.append("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
                    writer.append(value).append("\r\n")
                    writer.flush()
                }

                if (!title.isNullOrEmpty()) writeFormField("track_title", title)
                if (!artist.isNullOrEmpty()) writeFormField("artist", artist)
                writeFormField("preferred_engine", preferredEngine)
                writeFormField("preferred_lyrics_source", preferredSource)
                writeFormField("enable_ai_correction", enableAiCorrection.toString())

                // 檔案區塊
                writer.append("--$boundary\r\n")
                writer.append("Content-Disposition: form-data; name=\"audio\"; filename=\"${audioFile.name}\"\r\n")
                writer.append("Content-Type: application/octet-stream\r\n\r\n")
                writer.flush()

                FileInputStream(audioFile).use { inputStream ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    outputStream.flush()
                }

                writer.append("\r\n")
                writer.append("--$boundary--\r\n")
                writer.flush()
            }
        }

        val code = conn.responseCode
        if (code == HttpURLConnection.HTTP_ACCEPTED || code == HttpURLConnection.HTTP_OK) {
            val resText = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val resJson = JSONObject(resText)
            return TaskCreateResponse(
                taskId = resJson.getString("task_id"),
                status = resJson.optString("status", "queued"),
                message = resJson.optString("message", "Task queued")
            )
        } else {
            val err = conn.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: ""
            throw IllegalStateException("音訊上傳失敗 (HTTP $code): $err")
        }
    }

    /**
     * 查詢任務處理進度與結果 (GET /api/v1/tasks/{taskId})。
     */
    suspend fun getTaskStatus(
        serverUrl: String,
        taskId: String,
        apiKey: String? = null
    ): Result<TaskStatusResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val base = normalizeUrl(serverUrl)
            val targetUrl = "$base/api/v1/tasks/$taskId"
            val conn = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = DEFAULT_CONNECT_TIMEOUT_MS
                readTimeout = DEFAULT_READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                if (!apiKey.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                }
            }

            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_OK) {
                val resText = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                val json = JSONObject(resText)
                TaskStatusResponse(
                    taskId = json.optString("task_id", taskId),
                    status = json.optString("status", "unknown"),
                    progress = json.optInt("progress", 0),
                    stage = json.optString("stage", ""),
                    resultLrc = json.optString("result").takeIf { it.isNotEmpty() }
                        ?: json.optString("result_lrc").takeIf { it.isNotEmpty() },
                    errorMessage = json.optString("error").takeIf { it.isNotEmpty() }
                )
            } else {
                throw IllegalStateException("查詢任務進度失敗 (HTTP $code)")
            }
        }
    }

    /**
     * 匯出多格式字幕或純文字歌詞 (GET /api/v1/tasks/{taskId}/export?format=srt|ass|txt)。
     */
    suspend fun exportLyrics(
        serverUrl: String,
        taskId: String,
        format: String = "srt",
        apiKey: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val base = normalizeUrl(serverUrl)
            val targetUrl = "$base/api/v1/tasks/$taskId/export?format=$format"
            val conn = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = DEFAULT_CONNECT_TIMEOUT_MS
                readTimeout = DEFAULT_READ_TIMEOUT_MS
                if (!apiKey.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                }
            }

            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_OK) {
                conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            } else {
                throw IllegalStateException("匯出字幕失敗 (HTTP $code)")
            }
        }
    }
}
