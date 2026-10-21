package com.andsi.airlyrics.network.aether

import android.content.Context
import android.util.Base64
import com.andsi.airlyrics.logging.AppLogger
import com.andsi.airlyrics.settings.store.AetherLyricServerStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.net.ssl.SSLSocketFactory

/**
 * 伺服器健康檢查回應資料結構。
 */
data class ServerHealthResponse(
    val status: String,
    val service: String,
    val version: String,
    val defaultEngine: String,
    val queueLength: Int,
    val availableModels: List<String>,
    val ytdlpEnabled: Boolean = false
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
 * 支援 Proxy 代理、多層加密驗證標頭、WebSocket 即時進度傳輸（自動降級至 HTTP 輪詢）以及端到端任務管線。
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

    private fun openHttpConnection(
        context: Context?,
        urlStr: String,
        method: String,
        connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
        apiKey: String? = null
    ): HttpURLConnection {
        val conn = (AetherLyricServerStore.openConnectionWithProxy(context, urlStr) as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Accept", "application/json")
        }
        AetherLyricServerStore.applyAuthHeaders(context, conn, apiKey)
        return conn
    }

    /**
     * 測試伺服器連線狀態 (GET /api/v1/health)。
     */
    suspend fun testHealth(
        serverUrl: String,
        apiKey: String? = null,
        context: Context? = null
    ): Result<ServerHealthResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val base = normalizeUrl(serverUrl)
            if (base.isEmpty()) {
                throw IllegalArgumentException("伺服器位址不得為空白")
            }
            val targetUrl = "$base/api/v1/health"
            val conn = openHttpConnection(
                context = context,
                urlStr = targetUrl,
                method = "GET",
                connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MS,
                readTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MS,
                apiKey = apiKey
            )

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
                    availableModels = modelsList,
                    ytdlpEnabled = json.optBoolean("ytdlp_enabled", false)
                )
            } else if (code == HttpURLConnection.HTTP_UNAUTHORIZED || code == HttpURLConnection.HTTP_FORBIDDEN) {
                throw IllegalStateException("驗證失敗 (HTTP $code)：請檢查 APP_ACCESS_TOKEN、PIN 碼或問答驗證設定")
            } else {
                val errText = conn.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: ""
                throw IllegalStateException("伺服器回應錯誤 (HTTP $code) $errText")
            }
        }
    }

    /**
     * 快查外部或快取歌詞（阻塞式，供 Repository 呼叫）。
     */
    fun searchLyricsBlocking(
        serverUrl: String,
        title: String,
        artist: String = "",
        duration: Float? = null,
        apiKey: String? = null,
        context: Context? = null
    ): Result<LyricsSearchResult?> = runCatching {
        val base = normalizeUrl(serverUrl)
        if (base.isEmpty()) return@runCatching null
        val encTitle = URLEncoder.encode(title, "UTF-8")
        val encArtist = URLEncoder.encode(artist, "UTF-8")
        var urlStr = "$base/api/v1/lyrics/search?title=$encTitle&artist=$encArtist"
        if (duration != null && duration > 0) {
            urlStr += "&duration=${duration.toInt()}"
        }

        val conn = openHttpConnection(
            context = context,
            urlStr = urlStr,
            method = "GET",
            connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MS,
            readTimeoutMs = DEFAULT_READ_TIMEOUT_MS,
            apiKey = apiKey
        )

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

    /**
     * 快查外部或快取歌詞 (GET /api/v1/lyrics/search)。
     */
    suspend fun searchLyrics(
        serverUrl: String,
        title: String,
        artist: String = "",
        duration: Float? = null,
        apiKey: String? = null,
        context: Context? = null
    ): Result<LyricsSearchResult?> = withContext(Dispatchers.IO) {
        searchLyricsBlocking(serverUrl, title, artist, duration, apiKey, context)
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
        referenceLrc: String? = null,
        apiKey: String? = null,
        context: Context? = null
    ): Result<TaskCreateResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val base = normalizeUrl(serverUrl)
            val targetUrl = "$base/api/v1/tasks"

            if (audioFile != null && audioFile.exists()) {
                uploadTaskWithAudio(
                    context = context,
                    targetUrl = targetUrl,
                    audioFile = audioFile,
                    title = title,
                    artist = artist,
                    preferredEngine = preferredEngine,
                    preferredSource = preferredSource,
                    enableAiCorrection = enableAiCorrection,
                    referenceLrc = referenceLrc,
                    apiKey = apiKey
                )
            } else {
                createTaskJson(
                    context = context,
                    targetUrl = targetUrl,
                    title = title,
                    artist = artist,
                    preferredEngine = preferredEngine,
                    preferredSource = preferredSource,
                    enableAiCorrection = enableAiCorrection,
                    referenceLrc = referenceLrc,
                    apiKey = apiKey
                )
            }
        }
    }

    private fun createTaskJson(
        context: Context?,
        targetUrl: String,
        title: String?,
        artist: String?,
        preferredEngine: String,
        preferredSource: String,
        enableAiCorrection: Boolean,
        referenceLrc: String?,
        apiKey: String?
    ): TaskCreateResponse {
        val conn = openHttpConnection(
            context = context,
            urlStr = targetUrl,
            method = "POST",
            connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MS,
            readTimeoutMs = DEFAULT_READ_TIMEOUT_MS,
            apiKey = apiKey
        ).apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }

        val json = JSONObject().apply {
            put("track_title", title ?: "")
            put("artist", artist ?: "")
            put("preferred_engine", preferredEngine)
            put("preferred_lyrics_source", preferredSource)
            put("enable_ai_correction", enableAiCorrection)
            if (!referenceLrc.isNullOrBlank()) {
                put("reference_lrc", referenceLrc)
            }
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
        context: Context?,
        targetUrl: String,
        audioFile: File,
        title: String?,
        artist: String?,
        preferredEngine: String,
        preferredSource: String,
        enableAiCorrection: Boolean,
        referenceLrc: String?,
        apiKey: String?
    ): TaskCreateResponse {
        val boundary = "===AetherLyricBoundary" + System.currentTimeMillis() + "==="
        val conn = openHttpConnection(
            context = context,
            urlStr = targetUrl,
            method = "POST",
            connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MS,
            readTimeoutMs = 60000,
            apiKey = apiKey
        ).apply {
            doOutput = true
            doInput = true
            useCaches = false
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        conn.outputStream.use { outputStream ->
            PrintWriter(OutputStreamWriter(outputStream, StandardCharsets.UTF_8), true).use { writer ->
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
                if (!referenceLrc.isNullOrEmpty()) writeFormField("reference_lrc", referenceLrc)

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
        apiKey: String? = null,
        context: Context? = null
    ): Result<TaskStatusResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val base = normalizeUrl(serverUrl)
            val targetUrl = "$base/api/v1/tasks/$taskId"
            val conn = openHttpConnection(
                context = context,
                urlStr = targetUrl,
                method = "GET",
                connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MS,
                readTimeoutMs = DEFAULT_READ_TIMEOUT_MS,
                apiKey = apiKey
            )

            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_OK) {
                val resText = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                parseTaskStatusJson(taskId, JSONObject(resText))
            } else {
                throw IllegalStateException("查詢任務進度失敗 (HTTP $code)")
            }
        }
    }

    private fun parseTaskStatusJson(defaultTaskId: String, json: JSONObject): TaskStatusResponse {
        return TaskStatusResponse(
            taskId = json.optString("task_id", defaultTaskId),
            status = json.optString("status", "unknown"),
            progress = json.optInt("progress", 0),
            stage = json.optString("stage", ""),
            resultLrc = json.optString("result").takeIf { it.isNotEmpty() }
                ?: json.optString("result_lrc").takeIf { it.isNotEmpty() },
            errorMessage = json.optString("error").takeIf { it.isNotEmpty() }
        )
    }

    /**
     * 監聽任務即時進度：
     * 若使用者開啟「使用 WebSocket 傳輸進度」，優先建立 WebSocket (`/ws/tasks/{taskId}`) 接收即時推播；
     * 若 WebSocket 連線失敗、中斷或未開啟，則無縫降級為 HTTP 輪詢 (`getTaskStatus`)。
     */
    suspend fun observeTaskUntilDone(
        context: Context,
        serverUrl: String,
        taskId: String,
        apiKey: String? = null,
        maxWaitSeconds: Int = 120,
        onProgress: suspend (TaskStatusResponse) -> Unit
    ): TaskStatusResponse? = withContext(Dispatchers.IO) {
        val preferWs = AetherLyricServerStore.isWebSocketProgressEnabled(context)
        if (preferWs) {
            val wsTerminalResult = runCatching {
                listenTaskWebSocket(context, serverUrl, taskId, apiKey, onProgress)
            }.onFailure { err ->
                AppLogger.w("AetherLyricApiClient", "WebSocket 進度連線中斷或失敗 (${err.message})，自動降級為 HTTP 輪詢")
            }.getOrNull()

            if (wsTerminalResult != null && (wsTerminalResult.status == "completed" || wsTerminalResult.status == "failed")) {
                return@withContext wsTerminalResult
            }
        }

        // HTTP 輪詢降級機制
        val maxRetries = (maxWaitSeconds / 2).coerceAtLeast(15)
        var lastResponse: TaskStatusResponse? = null
        for (i in 0 until maxRetries) {
            val statusRes = getTaskStatus(serverUrl, taskId, apiKey, context)
            val info = statusRes.getOrNull()
            if (info != null) {
                lastResponse = info
                withContext(Dispatchers.Main) {
                    onProgress(info)
                }
                if (info.status == "completed" || info.status == "failed") {
                    return@withContext info
                }
            }
            delay(2000)
        }
        lastResponse
    }

    /**
     * 輕量級 RFC 6455 WebSocket 客戶端連線至 /ws/tasks/{taskId}。
     */
    private suspend fun listenTaskWebSocket(
        context: Context,
        serverUrl: String,
        taskId: String,
        apiKey: String?,
        onProgress: suspend (TaskStatusResponse) -> Unit
    ): TaskStatusResponse? {
        val base = normalizeUrl(serverUrl)
        val uri = URI(base)
        val isSecure = uri.scheme.equals("https", ignoreCase = true)
        val host = uri.host ?: throw IllegalArgumentException("無效的伺服器主機名稱")
        val port = if (uri.port != -1) uri.port else if (isSecure) 443 else 80
        val basePath = uri.path?.removeSuffix("/") ?: ""
        val wsPath = "$basePath/ws/tasks/$taskId"

        val rawSocket = Socket()
        rawSocket.connect(InetSocketAddress(host, port), DEFAULT_CONNECT_TIMEOUT_MS)
        rawSocket.soTimeout = 30000

        val socket: Socket = if (isSecure) {
            val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
            sslFactory.createSocket(rawSocket, host, port, true)
        } else {
            rawSocket
        }

        socket.use { activeSocket ->
            val keyBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val secKey = Base64.encodeToString(keyBytes, Base64.NO_WRAP)
            val token = apiKey?.takeIf { it.isNotBlank() } ?: AetherLyricServerStore.getAppAccessToken(context)
            val pin = AetherLyricServerStore.getAuthPin(context)
            val answer = AetherLyricServerStore.getAuthChallengeAnswer(context)

            val requestBuilder = StringBuilder().apply {
                append("GET $wsPath HTTP/1.1\r\n")
                append("Host: $host:$port\r\n")
                append("Upgrade: websocket\r\n")
                append("Connection: Upgrade\r\n")
                append("Sec-WebSocket-Key: $secKey\r\n")
                append("Sec-WebSocket-Version: 13\r\n")
                if (token.isNotBlank()) {
                    append("Authorization: Bearer ${token.trim()}\r\n")
                    append("X-App-Access-Token: ${token.trim()}\r\n")
                }
                if (pin.isNotBlank()) {
                    append("X-Auth-Pin: $pin\r\n")
                }
                if (answer.isNotBlank()) {
                    val encAns = Base64.encodeToString(answer.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
                    append("X-Auth-Answer: $encAns\r\n")
                }
                append("\r\n")
            }

            val out = BufferedOutputStream(activeSocket.getOutputStream())
            out.write(requestBuilder.toString().toByteArray(StandardCharsets.UTF_8))
            out.flush()

            val input = BufferedInputStream(activeSocket.getInputStream())
            val statusLine = readHttpHeaderLine(input)
            if (!statusLine.contains("101")) {
                throw IllegalStateException("WebSocket 交握未返回 101: $statusLine")
            }
            // 略過剩餘 HTTP 回應標頭
            while (true) {
                val line = readHttpHeaderLine(input)
                if (line.isEmpty()) break
            }

            AppLogger.i("AetherLyricApiClient", "已建立 WebSocket 任務進度連線: $wsPath")

            var terminal: TaskStatusResponse? = null
            while (terminal == null) {
                val frameText = readWebSocketTextFrame(input, out) ?: break
                if (frameText.isBlank()) continue
                val json = runCatching { JSONObject(frameText) }.getOrNull() ?: continue
                val statusInfo = parseTaskStatusJson(taskId, json)
                withContext(Dispatchers.Main) {
                    onProgress(statusInfo)
                }
                if (statusInfo.status == "completed" || statusInfo.status == "failed") {
                    terminal = statusInfo
                }
            }
            return terminal
        }
    }

    private fun readHttpHeaderLine(input: InputStream): String {
        val sb = StringBuilder()
        var prev = -1
        while (true) {
            val b = input.read()
            if (b == -1) break
            if (prev == '\r'.code && b == '\n'.code) {
                if (sb.isNotEmpty()) sb.setLength(sb.length - 1)
                return sb.toString()
            }
            sb.append(b.toChar())
            prev = b
        }
        return sb.toString()
    }

    private fun readWebSocketTextFrame(input: InputStream, out: BufferedOutputStream): String? {
        val b0 = input.read()
        if (b0 == -1) return null
        val opcode = b0 and 0x0F
        val b1 = input.read()
        if (b1 == -1) return null
        val masked = (b1 and 0x80) != 0
        var payloadLen = (b1 and 0x7F).toLong()

        if (payloadLen == 126L) {
            val hi = input.read()
            val lo = input.read()
            if (hi == -1 || lo == -1) return null
            payloadLen = ((hi and 0xFF) shl 8 or (lo and 0xFF)).toLong()
        } else if (payloadLen == 127L) {
            var len = 0L
            for (i in 0 until 8) {
                val b = input.read()
                if (b == -1) return null
                len = (len shl 8) or (b.toLong() and 0xFFL)
            }
            payloadLen = len
        }

        if (payloadLen > 2 * 1024 * 1024) {
            throw IllegalStateException("WebSocket 單一訊息超過 2MB 安全上限")
        }

        val maskKey = if (masked) {
            ByteArray(4).also { key ->
                var read = 0
                while (read < 4) {
                    val r = input.read(key, read, 4 - read)
                    if (r == -1) return null
                    read += r
                }
            }
        } else null

        val payload = ByteArray(payloadLen.toInt())
        var totalRead = 0
        while (totalRead < payload.size) {
            val r = input.read(payload, totalRead, payload.size - totalRead)
            if (r == -1) return null
            totalRead += r
        }

        if (maskKey != null) {
            for (i in payload.indices) {
                payload[i] = (payload[i].toInt() xor maskKey[i % 4].toInt()).toByte()
            }
        }

        return when (opcode) {
            0x1 -> String(payload, StandardCharsets.UTF_8) // Text frame
            0x8 -> null // Close frame
            0x9 -> {
                // Ping -> 回應 Pong (opcode 0xA, Masked by client)
                runCatching {
                    val pongMask = ByteArray(4).also { SecureRandom().nextBytes(it) }
                    out.write(0x8A)
                    out.write(0x80 or (payload.size and 0x7F))
                    out.write(pongMask)
                    for (i in payload.indices) {
                        out.write(payload[i].toInt() xor pongMask[i % 4].toInt())
                    }
                    out.flush()
                }
                ""
            }
            else -> ""
        }
    }

    /**
     * 匯出多格式字幕或純文字歌詞 (GET /api/v1/tasks/{taskId}/export?format=srt|ass|txt)。
     */
    suspend fun exportLyrics(
        serverUrl: String,
        taskId: String,
        format: String = "srt",
        apiKey: String? = null,
        context: Context? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val base = normalizeUrl(serverUrl)
            val targetUrl = "$base/api/v1/tasks/$taskId/export?format=$format"
            val conn = openHttpConnection(
                context = context,
                urlStr = targetUrl,
                method = "GET",
                connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MS,
                readTimeoutMs = DEFAULT_READ_TIMEOUT_MS,
                apiKey = apiKey
            )

            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_OK) {
                conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            } else {
                throw IllegalStateException("匯出字幕失敗 (HTTP $code)")
            }
        }
    }

    /**
     * 呼叫時間軸錨點對齊 API (POST /api/v1/align)。
     */
    suspend fun alignLyrics(
        serverUrl: String,
        referenceLrc: String,
        asrSegments: List<Map<String, Any>> = emptyList(),
        apiKey: String? = null,
        context: Context? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val base = normalizeUrl(serverUrl)
            if (base.isEmpty()) throw IllegalArgumentException("伺服器位址不得為空白")
            val targetUrl = "$base/api/v1/align"
            val conn = openHttpConnection(
                context = context,
                urlStr = targetUrl,
                method = "POST",
                connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MS,
                readTimeoutMs = DEFAULT_READ_TIMEOUT_MS,
                apiKey = apiKey
            ).apply {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }

            val bodyJson = JSONObject().apply {
                put("reference_lrc", referenceLrc)
                val segmentsArray = JSONArray()
                asrSegments.forEach { seg ->
                    segmentsArray.put(JSONObject(seg))
                }
                put("asr_segments", segmentsArray)
                put("min_anchors", 1)
            }

            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use {
                it.write(bodyJson.toString())
                it.flush()
            }

            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_OK) {
                val respText = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                val json = JSONObject(respText)
                val output = json.optString("lrc_output")
                if (output.isNotBlank()) {
                    AppLogger.i("AetherLyricApiClient", "歌詞時間軸對齊成功 (offset: ${json.optDouble("offset")}s, confidence: ${json.optDouble("confidence")})")
                    output
                } else {
                    throw IllegalStateException("對齊結果為空")
                }
            } else {
                throw IllegalStateException("對齊請求失敗 (HTTP $code)")
            }
        }
    }

    /**
     * 發起影片網址解析與歌詞生成任務 (POST /api/v1/tasks/url)。
     * 支援 YouTube、Bilibili 等影音網址。
     */
    suspend fun createTaskFromUrl(
        serverUrl: String,
        videoUrl: String,
        title: String? = null,
        artist: String? = null,
        apiKey: String? = null,
        context: Context? = null
    ): Result<TaskCreateResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val base = normalizeUrl(serverUrl)
            if (base.isEmpty()) throw IllegalArgumentException("伺服器位址不得為空白")

            val targetUrl = "$base/api/v1/tasks/url"
            val json = JSONObject().apply {
                put("url", videoUrl.trim())
                if (!title.isNullOrBlank()) put("track_title", title.trim())
                if (!artist.isNullOrBlank()) put("artist", artist.trim())
            }

            fun postJson(urlStr: String): Pair<Int, String> {
                val conn = openHttpConnection(
                    context = context,
                    urlStr = urlStr,
                    method = "POST",
                    connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MS,
                    readTimeoutMs = DEFAULT_READ_TIMEOUT_MS,
                    apiKey = apiKey
                ).apply {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                }
                conn.outputStream.use { os ->
                    os.write(json.toString().toByteArray(StandardCharsets.UTF_8))
                    os.flush()
                }
                val code = conn.responseCode
                val resp = if (code in 200..299) {
                    conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                } else {
                    conn.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: ""
                }
                return code to resp
            }

            var (code, respText) = postJson(targetUrl)
            if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                val fallbackUrl = "$base/api/v1/tasks"
                val fallbackRes = postJson(fallbackUrl)
                code = fallbackRes.first
                respText = fallbackRes.second
            }

            if (code == HttpURLConnection.HTTP_ACCEPTED || code == HttpURLConnection.HTTP_OK) {
                val resJson = JSONObject(respText)
                TaskCreateResponse(
                    taskId = resJson.getString("task_id"),
                    status = resJson.optString("status", "queued"),
                    message = resJson.optString("message", "Task queued")
                )
            } else {
                throw IllegalStateException("影片解析任務發起失敗 (HTTP $code): $respText")
            }
        }
    }

    /**
     * 本地簡易時間軸對齊 (備用方案)：
     * 當伺服器未連線時，根據音訊總時長將純文字歌詞平均分配時間戳。
     */
    fun alignPlainLyricsLocally(plainText: String, durationMs: Long): String {
        val rawLines = plainText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (rawLines.isEmpty()) return ""

        val totalSec = if (durationMs > 1000L) durationMs / 1000.0 else 180.0
        val startSec = (totalSec * 0.05).coerceAtMost(5.0)
        val endSec = (totalSec * 0.95).coerceAtLeast(startSec + 1.0)
        val span = endSec - startSec
        val step = if (rawLines.size > 1) span / (rawLines.size - 1) else 0.0

        val sb = StringBuilder()
        rawLines.forEachIndexed { index, line ->
            val curTimeSec = startSec + index * step
            val m = (curTimeSec / 60).toInt()
            val s = curTimeSec % 60
            val timeTag = String.format(java.util.Locale.US, "[%02d:%05.2f]", m, s)
            sb.append(timeTag).append(line).append("\n")
        }
        return sb.toString().trimEnd()
    }
}
