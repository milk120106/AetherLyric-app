package com.andsi.airlyrics.media

import android.content.Context
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import com.andsi.airlyrics.logging.AppLogger
import com.andsi.airlyrics.permissions.PermissionTierManager
import com.andsi.airlyrics.settings.store.AppSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Shizuku 特權播放器深度整合與播放狀態調度器 (PrivilegedMediaPlaybackManager)。
 * 1. 存取系統 MediaSession 精準獲取毫秒進度。
 * 2. 實作「生成歌詞時自動暫停」與 60 秒超時自動恢復機制。
 */
object PrivilegedMediaPlaybackManager {

    private var pausedController: MediaController? = null
    private var wasPlayingBeforePause = false
    private var autoResumeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    /**
     * 精準抓取播放毫秒進度。
     */
    fun getAccuratePlaybackPositionMs(context: Context, controller: MediaController): Long {
        val state = controller.playbackState ?: return 0L
        val basePos = state.position
        if (state.state == PlaybackState.STATE_PLAYING) {
            val delta = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
            val speed = state.playbackSpeed.coerceAtLeast(0f)
            return (basePos + (delta * speed).toLong()).coerceAtLeast(0L)
        }
        return basePos.coerceAtLeast(0L)
    }

    /**
     * 當啟動 AI 辨識或伺服器生成任務時呼叫。
     * 若使用者啟用此功能，自動暫停目前播放中之音樂，並啟動 60 秒超時保護計時器。
     */
    fun onGenerationTaskStarted(context: Context) {
        if (!AppSettingsStore.isAutoPauseOnGenerationEnabled(context)) return

        val controllers = CurrentMediaReader.getActiveControllers(context)
        val playing = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        if (playing != null) {
            AppLogger.i("PrivilegedMedia", "生成歌詞任務啟動，自動發送暫停指令給播放器：${playing.packageName}")
            pausedController = playing
            wasPlayingBeforePause = true
            try {
                playing.transportControls.pause()
            } catch (e: Exception) {
                AppLogger.e("PrivilegedMedia", "發送暫停指令失敗：${e.message}")
            }

            // 啟動 60 秒超時看門狗計時器
            autoResumeJob?.cancel()
            autoResumeJob = scope.launch {
                delay(60000L) // 60 秒超時保護
                AppLogger.w("PrivilegedMedia", "生成歌詞任務超過 60 秒，自動觸發超時恢復播放")
                resumePlaybackInternal()
            }
        }
    }

    /**
     * 當 AI 辨識或生成任務完成、失敗或取消時呼叫。
     * 立即取消超時計時器並恢復播放。
     */
    fun onGenerationTaskEnded(context: Context) {
        autoResumeJob?.cancel()
        autoResumeJob = null
        if (wasPlayingBeforePause) {
            resumePlaybackInternal()
        }
    }

    private fun resumePlaybackInternal() {
        val controller = pausedController
        if (controller != null && wasPlayingBeforePause) {
            AppLogger.i("PrivilegedMedia", "任務結束或超時，自動恢復播放器播放：${controller.packageName}")
            try {
                controller.transportControls.play()
            } catch (e: Exception) {
                AppLogger.e("PrivilegedMedia", "發送恢復播放指令失敗：${e.message}")
            }
        }
        pausedController = null
        wasPlayingBeforePause = false
    }
}
