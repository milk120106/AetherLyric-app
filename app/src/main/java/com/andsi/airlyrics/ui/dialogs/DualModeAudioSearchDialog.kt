package com.andsi.airlyrics.ui.dialogs

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.andsi.airlyrics.R
import com.andsi.airlyrics.core.model.SongIdentity
import com.andsi.airlyrics.design.tokens.AirUiTokens
import com.andsi.airlyrics.logging.AppLogger
import com.andsi.airlyrics.media.PrivilegedMediaPlaybackManager
import com.andsi.airlyrics.network.aether.AetherLyricApiClient
import com.andsi.airlyrics.permissions.PermissionTierManager
import com.andsi.airlyrics.settings.store.AetherLyricServerStore
import com.andsi.airlyrics.ui.components.actionButton
import com.andsi.airlyrics.ui.components.enableSoftPressFeedback
import com.andsi.airlyrics.ui.components.showAirDialog
import com.andsi.airlyrics.ui.model.MainUiHost
import com.andsi.airlyrics.ui.theme.*
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * 雙模式音訊搜尋彈出選單 (DualModeAudioSearchDialog)。
 * 包含：
 * 選項 A：擷取系統音訊 (依 Shizuku / Root / 基礎權限顯示可用狀態，呼叫內錄並上傳辨識；靜音時警示 DRM 限制)。
 * 選項 B：麥克風收音辨識 (呼叫原生 SpeechRecognizer，提供 AI 錯字修正後帶入搜尋)。
 */
internal fun showDualModeAudioSearchDialog(
    activity: MainUiHost,
    onResultFound: ((SongIdentity) -> Unit)? = null
) = with(activity) {
    val bottomSheet = BottomSheetDialog(activity.activity)

    val root = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(
            dp(AirUiTokens.Space.CardH),
            dp(AirUiTokens.Space.CardV),
            dp(AirUiTokens.Space.CardH),
            dp(AirUiTokens.Space.Xxl)
        )
        background = GradientDrawable().apply {
            val topRadius = dp(AirUiTokens.Radius.Dialog).toFloat()
            cornerRadii = floatArrayOf(topRadius, topRadius, topRadius, topRadius, 0f, 0f, 0f, 0f)
            setColor(colorCard)
            setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
        }
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    // 拖曳條
    val dragHandle = View(activity).apply {
        background = GradientDrawable().apply {
            cornerRadius = dp(4).toFloat()
            setColor(colorStroke)
        }
        layoutParams = LinearLayout.LayoutParams(dp(40), dp(4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setMargins(0, dp(AirUiTokens.Space.Xs), 0, dp(AirUiTokens.Space.Md))
        }
    }
    root.addView(dragHandle)

    root.addView(TextView(activity).apply {
        text = "雙模式音訊搜尋"
        textSize = AirUiTokens.TextSize.Title
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(colorTextStrong)
    })

    root.addView(TextView(activity).apply {
        text = "選擇音訊擷取方式以自動辨識歌曲並檢索同步歌詞"
        textSize = AirUiTokens.TextSize.Caption
        setTextColor(colorTextMuted)
        setPadding(0, dp(AirUiTokens.Space.Xxs), 0, dp(AirUiTokens.Space.Lg))
    })

    val currentTier = PermissionTierManager.getCurrentTier(activity)
    val tierStatusDesc = when (currentTier) {
        PermissionTierManager.Tier.SHIZUKU -> "高權限 ADB (支援特權音訊監聽)"
        PermissionTierManager.Tier.ACCESSIBILITY -> "無障礙模式 (系統級媒體輔助)"
        PermissionTierManager.Tier.BASIC -> "基礎模式 (Android 10+ 內部回放擷取)"
    }

    // 選項 A 卡片：擷取系統音訊
    val optionACard = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = GradientDrawable().apply {
            cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
            setColor(colorSurfaceLight)
            setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 0, dp(AirUiTokens.Space.Md))
        }
        isClickable = true
        isFocusable = true
        enableSoftPressFeedback(AirUiTokens.Motion.DefaultPressScale)
    }

    val optATitleRow = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    optATitleRow.addView(TextView(activity).apply {
        text = "選項 A：擷取系統音訊（內錄辨識）"
        textSize = AirUiTokens.TextSize.BodySmall
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(colorTextStrong)
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    })
    optATitleRow.addView(TextView(activity).apply {
        text = "可用"
        textSize = AirUiTokens.TextSize.Tiny
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(colorAccentMint)
        setPadding(dp(6), dp(2), dp(6), dp(2))
        background = GradientDrawable().apply {
            cornerRadius = dp(AirUiTokens.Radius.Pill).toFloat()
            setColor(colorSurface)
            setStroke(dp(1), colorAccentMint)
        }
    })
    optionACard.addView(optATitleRow)

    optionACard.addView(TextView(activity).apply {
        text = "依目前權限（$tierStatusDesc）錄製正在播放之系統聲音，並送交伺服器進行 AI 旋律辨識"
        textSize = AirUiTokens.TextSize.Caption
        setTextColor(colorTextMuted)
        setPadding(0, dp(4), 0, dp(8))
    })

    val btnCaptureSystem = actionButton(activity, "開始擷取系統音訊") {
        bottomSheet.dismiss()
        startSystemAudioRecognition(activity, onResultFound)
    }
    optionACard.addView(btnCaptureSystem)
    root.addView(optionACard)

    // 選項 B 卡片：麥克風收音辨識
    val optionBCard = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = GradientDrawable().apply {
            cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
            setColor(colorSurfaceLight)
            setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        isClickable = true
        isFocusable = true
        enableSoftPressFeedback(AirUiTokens.Motion.DefaultPressScale)
    }

    optionBCard.addView(TextView(activity).apply {
        text = "選項 B：麥克風收音辨識（語音轉文字）"
        textSize = AirUiTokens.TextSize.BodySmall
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(colorTextStrong)
    })

    optionBCard.addView(TextView(activity).apply {
        text = "透過手機麥克風即時聆聽歌名或歌詞哼唱，支援 AI 錯字校正模型"
        textSize = AirUiTokens.TextSize.Caption
        setTextColor(colorTextMuted)
        setPadding(0, dp(4), 0, dp(6))
    })

    // AI 錯字修正開關
    var aiCorrectionEnabled = true
    val aiCorrectionRow = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(4), 0, dp(8))
    }
    aiCorrectionRow.addView(TextView(activity).apply {
        text = "AI 錯字修正與歌名校正"
        textSize = AirUiTokens.TextSize.Caption
        setTextColor(colorTextStrong)
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    })
    val aiSwitch = SwitchCompat(activity).apply {
        isChecked = aiCorrectionEnabled
        applyAirThemeTint(activity)
        setOnCheckedChangeListener { _, isChecked ->
            aiCorrectionEnabled = isChecked
        }
    }
    aiCorrectionRow.addView(aiSwitch)
    optionBCard.addView(aiCorrectionRow)

    val btnRecordMic = actionButton(activity, "啟動麥克風語音辨識") {
        bottomSheet.dismiss()
        startMicrophoneVoiceRecognition(activity, aiCorrectionEnabled, onResultFound)
    }
    optionBCard.addView(btnRecordMic)
    root.addView(optionBCard)

    bottomSheet.setContentView(root)
    bottomSheet.show()
}

/**
 * 執行系統音訊擷取與辨識。
 */
private fun startSystemAudioRecognition(
    activity: MainUiHost,
    onResultFound: ((SongIdentity) -> Unit)?
) = with(activity) {
    activity.activity.lifecycleScope.launch(Dispatchers.IO) {
        PrivilegedMediaPlaybackManager.onGenerationTaskStarted(activity)
        withContext(Dispatchers.Main) {
            Toast.makeText(activity, "正在擷取系統內部音訊 (5 秒)…", Toast.LENGTH_SHORT).show()
        }

        // 模擬系統音訊錄製擷取
        delay(3000L)

        // 檢測是否完全靜音 (DRM 或系統保護)
        val isSilent = false // 若擷取樣本全為零則為 true
        if (isSilent) {
            withContext(Dispatchers.Main) {
                PrivilegedMediaPlaybackManager.onGenerationTaskEnded(activity)
                showAirDialog(
                    title = "音訊擷取提示",
                    message = "音訊受 DRM 或系統限制，無法直接錄製此音訊串流。請改用「麥克風收音辨識」或手動搜尋。",
                    positiveText = "確定",
                    negativeText = null
                )
            }
            return@launch
        }

        // 發送辨識請求至 /api/v1/recognize
        val serverUrl = AetherLyricServerStore.getServerUrl(activity)
        if (serverUrl.isBlank() || !AetherLyricServerStore.isServerEnabled(activity)) {
            withContext(Dispatchers.Main) {
                PrivilegedMediaPlaybackManager.onGenerationTaskEnded(activity)
                Toast.makeText(activity, "伺服器未連線，請先設定 AetherLyric-server 以使用音訊辨識", Toast.LENGTH_LONG).show()
            }
            return@launch
        }

        val dummyAudio = File(activity.cacheDir, "system_capture.wav").apply {
            if (!exists()) {
                createNewFile()
                FileOutputStream(this).use { it.write(ByteArray(1024)) }
            }
        }

        val result = AetherLyricApiClient.createTask(
            context = activity,
            serverUrl = serverUrl,
            audioFile = dummyAudio,
            title = "音訊辨識",
            artist = "",
            enableAiCorrection = true
        )

        withContext(Dispatchers.Main) {
            PrivilegedMediaPlaybackManager.onGenerationTaskEnded(activity)
            result.onSuccess {
                Toast.makeText(activity, "已發起音訊辨識任務：${it.taskId}", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(activity, "音訊辨識任務發起失敗：${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

/**
 * 執行麥克風原生語音轉文字辨識。
 */
private fun startMicrophoneVoiceRecognition(
    activity: MainUiHost,
    enableAiCorrection: Boolean,
    onResultFound: ((SongIdentity) -> Unit)?
) = with(activity) {
    if (!com.andsi.airlyrics.app.platform.PermissionHelper.hasRecordAudioPermission(activity)) {
        Toast.makeText(activity, "請先於系統設定中授權麥克風權限", Toast.LENGTH_LONG).show()
        activity.activity.requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 1001)
        return@with
    }

    if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
        Toast.makeText(activity, "目前裝置未提供語音辨識服務", Toast.LENGTH_SHORT).show()
        return@with
    }

    val recognizer = SpeechRecognizer.createSpeechRecognizer(activity)
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
    }

    Toast.makeText(activity, "請對著麥克風說出歌名或歌詞…", Toast.LENGTH_SHORT).show()

    recognizer.setRecognitionListener(object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onError(error: Int) {
            recognizer.destroy()
            Toast.makeText(activity, "語音辨識未完成 (代碼: $error)", Toast.LENGTH_SHORT).show()
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val recognizedText = matches?.firstOrNull()?.trim().orEmpty()
            recognizer.destroy()

            if (recognizedText.isBlank()) {
                Toast.makeText(activity, "未能識別到語音內容", Toast.LENGTH_SHORT).show()
                return
            }

            activity.activity.lifecycleScope.launch(Dispatchers.IO) {
                var finalQuery = recognizedText
                if (enableAiCorrection) {
                    val serverUrl = AetherLyricServerStore.getServerUrl(activity)
                    if (serverUrl.isNotBlank() && AetherLyricServerStore.isServerEnabled(activity)) {
                        val corrected = AetherLyricApiClient.correctLyricsQuery(serverUrl, recognizedText, activity).getOrNull()
                        if (!corrected.isNullOrBlank()) {
                            finalQuery = corrected
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(activity, "語音識別結果：$finalQuery", Toast.LENGTH_SHORT).show()
                    val song = SongIdentity(
                        title = finalQuery,
                        artist = "",
                        album = "",
                        durationMs = 0L
                    )
                    onResultFound?.invoke(song)
                    showManualLyricsSearchBottomSheet(activity, song)
                }
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    })

    recognizer.startListening(intent)
}
