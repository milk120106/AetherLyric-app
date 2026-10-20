package com.andsi.airlyrics.network.aether

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AetherLyricApiClientTest {

    @Test
    fun testNormalizeUrl() {
        assertEquals("http://192.168.1.100:8000", AetherLyricApiClient.normalizeUrl("http://192.168.1.100:8000/"))
        assertEquals("http://192.168.1.100:8000", AetherLyricApiClient.normalizeUrl("192.168.1.100:8000/"))
        assertEquals("https://api.aetherlyric.internal", AetherLyricApiClient.normalizeUrl("https://api.aetherlyric.internal/"))
        assertEquals("", AetherLyricApiClient.normalizeUrl("   "))
    }

    @Test
    fun testEmptyUrlHealthCheckFails() = runBlocking {
        val result = AetherLyricApiClient.testHealth("")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun testDataModelProperties() {
        val health = ServerHealthResponse(
            status = "ok",
            service = "AetherLyric-server",
            version = "1.0.0",
            defaultEngine = "groq",
            queueLength = 3,
            availableModels = listOf("whisper-large-v3-turbo", "openai/gpt-oss-120b")
        )
        assertEquals("ok", health.status)
        assertEquals(3, health.queueLength)
        assertEquals(2, health.availableModels.size)

        val searchResult = LyricsSearchResult(
            hit = true,
            trackTitle = "夜曲",
            artist = "周杰倫",
            lrcContent = "[00:10.00] 一群嗜血的螞蟻",
            source = "lrclib"
        )
        assertTrue(searchResult.hit)
        assertEquals("夜曲", searchResult.trackTitle)
        assertEquals("lrclib", searchResult.source)

        val taskCreate = TaskCreateResponse(
            taskId = "task-uuid-123",
            status = "queued",
            message = "Task queued"
        )
        assertEquals("task-uuid-123", taskCreate.taskId)

        val taskStatus = TaskStatusResponse(
            taskId = "task-uuid-123",
            status = "completed",
            progress = 100,
            stage = "completed",
            resultLrc = "[00:10.00] Aligned lyric",
            errorMessage = null
        )
        assertEquals("completed", taskStatus.status)
        assertEquals(100, taskStatus.progress)
        assertNotNull(taskStatus.resultLrc)
    }
}
