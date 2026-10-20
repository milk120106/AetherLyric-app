package com.andsi.airlyrics.ui.pages.media

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.OpenableColumns
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.andsi.airlyrics.R
import com.andsi.airlyrics.core.model.SongIdentity
import com.andsi.airlyrics.design.tokens.AirUiTokens
import com.andsi.airlyrics.logging.AppLogger
import com.andsi.airlyrics.lyrics.storage.LyricsStorage
import com.andsi.airlyrics.lyrics.storage.LyricsVersion
import com.andsi.airlyrics.lyrics.storage.LyricsVersionStore
import com.andsi.airlyrics.lyrics.storage.PlainLyricsStorageOps
import com.andsi.airlyrics.network.aether.AetherLyricApiClient
import com.andsi.airlyrics.settings.store.AetherLyricServerStore
import com.andsi.airlyrics.ui.components.actionButton
import com.andsi.airlyrics.ui.components.bigText
import com.andsi.airlyrics.ui.components.card
import com.andsi.airlyrics.ui.components.smallHint
import com.andsi.airlyrics.ui.model.MainUiHost
import com.andsi.airlyrics.ui.theme.colorAccent
import com.andsi.airlyrics.ui.theme.colorAccentMint
import com.andsi.airlyrics.ui.theme.colorDanger
import com.andsi.airlyrics.ui.theme.colorStroke
import com.andsi.airlyrics.ui.theme.colorSurfaceLight
import com.andsi.airlyrics.ui.theme.colorTextMuted
import com.andsi.airlyrics.ui.theme.colorTextStrong
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * 本地音訊 AI 歌詞辨識卡片。
 * 提供選取本機 MP3/FLAC 等音檔、輸入或微調歌曲資訊，
 * 一鍵上傳至自架 AetherLyric-server 進行 AI 語音轉錄並即時產生同步 LRC 歌詞，附帶進度條與錯誤回饋。
 */
internal fun createAudioAiTranscriptionCard(activity: MainUiHost): View = with(activity) {
    var selectedAudioFile: File? = null
    var selectedTrackTitle: String = ""
    var selectedTrackArtist: String = ""

    return card(activity) {
        addView(bigText(activity, getString(R.string.ui_ai_audio_transcription_title)))
        addView(smallHint(activity, getString(R.string.ui_ai_audio_transcription_desc)))

        // 檔案資訊顯示區
        val fileInfoBox = TextView(activity).apply {
            text = "尚未選取音訊檔案"
            textSize = AirUiTokens.TextSize.BodySmall
            setTextColor(colorTextMuted)
            setPadding(
                dp(AirUiTokens.Space.ButtonH),
                dp(AirUiTokens.Space.ControlV),
                dp(AirUiTokens.Space.ButtonH),
                dp(AirUiTokens.Space.ControlV)
            )
            background = GradientDrawable().apply {
                cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
                setColor(colorSurfaceLight)
                setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(AirUiTokens.Space.CardV), 0, 0)
            }
        }
        addView(fileInfoBox)

        // 歌曲標題輸入框 (選填，輔助校正)
        val titleInput = EditText(activity).apply {
            hint = "歌曲標題 (選填，可輔助 AI 校正)"
            textSize = AirUiTokens.TextSize.BodySmall
            setTextColor(colorTextStrong)
            setHintTextColor(colorTextMuted)
            minimumHeight = dp(AirUiTokens.Layout.IconTouchSize)
            setPadding(
                dp(AirUiTokens.Space.ButtonH),
                dp(AirUiTokens.Space.ControlV),
                dp(AirUiTokens.Space.ButtonH),
                dp(AirUiTokens.Space.ControlV)
            )
            background = GradientDrawable().apply {
                cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
                setColor(colorSurfaceLight)
                setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(AirUiTokens.Space.Sm), 0, 0)
            }
            isSingleLine = true
        }
        addView(titleInput)

        // 歌手名稱輸入框 (選填，輔助校正)
        val artistInput = EditText(activity).apply {
            hint = "演唱歌手 (選填，可輔助 AI 校正)"
            textSize = AirUiTokens.TextSize.BodySmall
            setTextColor(colorTextStrong)
            setHintTextColor(colorTextMuted)
            minimumHeight = dp(AirUiTokens.Layout.IconTouchSize)
            setPadding(
                dp(AirUiTokens.Space.ButtonH),
                dp(AirUiTokens.Space.ControlV),
                dp(AirUiTokens.Space.ButtonH),
                dp(AirUiTokens.Space.ControlV)
            )
            background = GradientDrawable().apply {
                cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
                setColor(colorSurfaceLight)
                setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(AirUiTokens.Space.Sm), 0, 0)
            }
            isSingleLine = true
        }
        addView(artistInput)

        // 進度條與狀態提示文字
        val progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            progress = 0
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(AirUiTokens.Space.Sm)
            ).apply {
                setMargins(0, dp(AirUiTokens.Space.CardV), 0, 0)
            }
        }
        addView(progressBar)

        val statusText = TextView(activity).apply {
            text = ""
            textSize = AirUiTokens.TextSize.Caption
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorAccent)
            visibility = View.GONE
            setPadding(0, dp(AirUiTokens.Space.Sm), 0, 0)
        }
        addView(statusText)

        // SAF 檔案選取回呼
        val audioPickerLauncher = activity.activity.activityResultRegistry.register(
            "audio_picker_launcher",
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                try {
                    val cursor = activity.contentResolver.query(uri, null, null, null, null)
                    var fileName = "audio_${System.currentTimeMillis()}.mp3"
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1) {
                                fileName = it.getString(nameIndex)
                            }
                        }
                    }

                    // 複製到快取目錄以供上傳
                    val cacheFile = File(activity.cacheDir, "audio_upload_${System.currentTimeMillis()}_$fileName")
                    activity.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(cacheFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    selectedAudioFile = cacheFile
                    val sizeMb = cacheFile.length() / (1024.0 * 1024.0)
                    fileInfoBox.text = "已選取: $fileName (%.1f MB)".format(sizeMb)
                    fileInfoBox.setTextColor(colorTextStrong)
                    statusText.visibility = View.GONE
                    progressBar.visibility = View.GONE
                } catch (e: Exception) {
                    Toast.makeText(activity, "讀取檔案失敗: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 按鈕：選取音訊檔案
        val selectButton = actionButton(activity, getString(R.string.ui_select_audio_file)) { }
        (selectButton.layoutParams as? LinearLayout.LayoutParams)?.setMargins(
            0,
            dp(AirUiTokens.Space.CardV),
            0,
            0
        )
        selectButton.setOnClickListener {
            audioPickerLauncher.launch("audio/*")
        }
        addView(selectButton)

        // 按鈕：上傳並辨識
        val uploadButton = actionButton(activity, getString(R.string.ui_upload_and_transcribe)) { }
        (uploadButton.layoutParams as? LinearLayout.LayoutParams)?.setMargins(
            0,
            dp(AirUiTokens.Space.Sm),
            0,
            0
        )
        uploadButton.setOnClickListener {
            val file = selectedAudioFile
            if (file == null || !file.exists()) {
                Toast.makeText(activity, "請先選取本機音訊檔案", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val serverUrl = AetherLyricServerStore.getServerUrl(activity)
            val apiKey = AetherLyricServerStore.getAdminApiKey(activity)

            if (serverUrl.isBlank()) {
                Toast.makeText(activity, "請先至設定頁面設定並驗證 AetherLyric 伺服器", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            selectedTrackTitle = titleInput.text.toString().trim()
            selectedTrackArtist = artistInput.text.toString().trim()

            uploadButton.isEnabled = false
            selectButton.isEnabled = false
            statusText.visibility = View.VISIBLE
            statusText.text = getString(R.string.ui_transcribing)
            statusText.setTextColor(colorAccent)
            progressBar.visibility = View.VISIBLE
            progressBar.isIndeterminate = true

            AppLogger.i("AudioAiTranscription", "發起音訊 AI 辨識任務: ${file.name}")

            activity.activity.lifecycleScope.launch {
                try {
                    val createResult = AetherLyricApiClient.createTask(
                        serverUrl = serverUrl,
                        audioFile = file,
                        title = selectedTrackTitle.ifBlank { file.nameWithoutExtension },
                        artist = selectedTrackArtist.ifBlank { "Unknown" },
                        apiKey = apiKey
                    )

                    val taskId = createResult.getOrThrow().taskId
                    statusText.text = "任務已發起 (ID: ${taskId.take(8)}...)，AI 辨識中..."
                    progressBar.isIndeterminate = false
                    progressBar.progress = 5

                    // 輪詢任務狀態
                    var isDone = false
                    var retryCount = 0
                    while (!isDone && retryCount < 60) {
                        delay(2000)
                        retryCount++
                        val statusRes = AetherLyricApiClient.getTaskStatus(serverUrl, taskId, apiKey)
                        val taskInfo = statusRes.getOrNull()
                        val status = taskInfo?.status
                        val curProgress = taskInfo?.progress ?: 0
                        progressBar.progress = curProgress.coerceIn(5, 95)

                        when (status) {
                            "completed" -> {
                                isDone = true
                                progressBar.progress = 100
                                // 導出生成的 LRC 歌詞
                                val exportRes = AetherLyricApiClient.exportLyrics(serverUrl, taskId, apiKey = apiKey)
                                val lrcContent = exportRes.getOrThrow()

                                val title = selectedTrackTitle.ifBlank { file.nameWithoutExtension }
                                val artist = selectedTrackArtist.ifBlank { "Unknown" }

                                // 儲存至本地歌詞庫
                                PlainLyricsStorageOps.savePlainLyrics(
                                    context = activity,
                                    title = title,
                                    artist = artist,
                                    duration = 0L,
                                    plainLrc = lrcContent,
                                    plainSource = LyricsStorage.SOURCE_MANUAL_IMPORT,
                                    plainProvider = "AetherLyric AI"
                                )

                                // 自動同步建立並切換為多版本之一
                                val songIdent = SongIdentity(title, artist, "", 0L)
                                val newVer = LyricsVersion(
                                    songKey = songIdent.storageKey(),
                                    versionName = "AI辨識版",
                                    lrcContent = lrcContent,
                                    source = "server_ai"
                                )
                                LyricsVersionStore.addVersion(activity, newVer)
                                LyricsVersionStore.switchVersion(activity, songIdent, newVer.versionId)
                                activity.uiActions.reloadFloatingLyrics()

                                statusText.text = getString(R.string.ui_transcription_success)
                                statusText.setTextColor(colorAccentMint)
                                progressBar.visibility = View.GONE
                                AppLogger.i("AudioAiTranscription", "音訊辨識成功: $title - $artist")
                                Toast.makeText(activity, getString(R.string.ui_transcription_success), Toast.LENGTH_LONG).show()
                            }
                            "failed" -> {
                                isDone = true
                                progressBar.visibility = View.GONE
                                val err = taskInfo?.errorMessage ?: "辨識失敗"
                                statusText.text = getString(R.string.ui_transcription_failed, err)
                                statusText.setTextColor(colorDanger)
                                AppLogger.e("AudioAiTranscription", "AI 辨識任務失敗: $err")
                                Toast.makeText(activity, "AI 辨識失敗: $err", Toast.LENGTH_LONG).show()
                            }
                            else -> {
                                statusText.text = "AI 辨識中... (進度: $curProgress%)"
                            }
                        }
                    }

                    if (!isDone) {
                        progressBar.visibility = View.GONE
                        statusText.text = "辨識逾時，請至伺服器查看任務進度"
                        statusText.setTextColor(colorDanger)
                        AppLogger.w("AudioAiTranscription", "AI 辨識超時未完成 (taskId: $taskId)")
                        Toast.makeText(activity, "AI 辨識超時，請檢查伺服器狀態", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    progressBar.visibility = View.GONE
                    statusText.text = getString(R.string.ui_transcription_failed, e.message ?: "連線錯誤")
                    statusText.setTextColor(colorDanger)
                    AppLogger.e("AudioAiTranscription", "上傳辨識異常", e)
                    Toast.makeText(activity, "上傳或辨識失敗: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    uploadButton.isEnabled = true
                    selectButton.isEnabled = true
                }
            }
        }
        addView(uploadButton)
    }
}
