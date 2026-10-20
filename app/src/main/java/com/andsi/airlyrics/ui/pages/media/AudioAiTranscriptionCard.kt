package com.andsi.airlyrics.ui.pages.media

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.andsi.airlyrics.R
import com.andsi.airlyrics.core.model.SongIdentity
import com.andsi.airlyrics.design.tokens.AirUiTokens
import com.andsi.airlyrics.lyrics.storage.LyricsStorage
import com.andsi.airlyrics.lyrics.storage.LyricsVersion
import com.andsi.airlyrics.lyrics.storage.LyricsVersionStore
import com.andsi.airlyrics.lyrics.storage.PlainLyricsStorageOps
import com.andsi.airlyrics.network.aether.AetherLyricApiClient
import com.andsi.airlyrics.settings.store.AetherLyricServerStore
import com.andsi.airlyrics.ui.components.actionButton
import com.andsi.airlyrics.ui.components.bigText
import com.andsi.airlyrics.ui.components.card
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
 * 手動音訊 AI 生成歌詞卡片 (AudioAiTranscriptionCard)。
 * 允許使用者選取本地 MP3/FLAC 音檔，呼叫 AetherLyric-server 進行 AI 語音轉錄，並儲存覆蓋本地 LRC。
 */
internal fun createAudioAiTranscriptionCard(activity: MainUiHost): View = with(activity) {
    return card(activity) {
        addView(bigText(activity, getString(R.string.ui_ai_audio_transcription_title)))

        val descView = TextView(activity).apply {
            text = getString(R.string.ui_ai_audio_transcription_desc)
            textSize = AirUiTokens.TextSize.BodySmall
            setTextColor(colorTextMuted)
            setPadding(0, dp(AirUiTokens.Space.Sm), 0, 0)
        }
        addView(descView)

        var selectedAudioFile: File? = null
        var selectedTrackTitle: String = ""
        var selectedTrackArtist: String = ""

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
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(AirUiTokens.Space.CardV), 0, 0)
            }
            layoutParams = lp
        }
        addView(fileInfoBox)

        val statusText = TextView(activity).apply {
            textSize = AirUiTokens.TextSize.Caption
            setTextColor(colorTextMuted)
            setPadding(0, dp(AirUiTokens.Space.Sm), 0, 0)
            visibility = View.GONE
        }
        addView(statusText)

        // 註冊音訊檔案選擇器
        val audioPickerLauncher = activity.activity.activityResultRegistry.register(
            "aether_pick_audio",
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                activity.activity.lifecycleScope.launch {
                    try {
                        var fileName = "audio.mp3"
                        activity.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1 && cursor.moveToFirst()) {
                                fileName = cursor.getString(nameIndex) ?: "audio.mp3"
                            }
                        }

                        // 解析初步標題與藝人
                        val baseName = fileName.substringBeforeLast(".")
                        val parts = baseName.split(" - ", "-", "–")
                        if (parts.size >= 2) {
                            selectedTrackArtist = parts[0].trim()
                            selectedTrackTitle = parts.subList(1, parts.size).joinToString("-").trim()
                        } else {
                            selectedTrackTitle = baseName.trim()
                            selectedTrackArtist = ""
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
                    } catch (e: Exception) {
                        Toast.makeText(activity, "讀取檔案失敗: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
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

            uploadButton.isEnabled = false
            selectButton.isEnabled = false
            statusText.visibility = View.VISIBLE
            statusText.text = getString(R.string.ui_transcribing)
            statusText.setTextColor(colorAccent)

            activity.activity.lifecycleScope.launch {
                try {
                    val createResult = AetherLyricApiClient.createTask(
                        serverUrl = serverUrl,
                        audioFile = file,
                        title = selectedTrackTitle.ifBlank { "Unknown" },
                        artist = selectedTrackArtist.ifBlank { "Unknown" },
                        apiKey = apiKey
                    )

                    val taskId = createResult.getOrThrow().taskId
                    statusText.text = "任務已發起 (ID: ${taskId.take(8)}...)，AI 辨識中..."

                    // 輪詢任務狀態
                    var isDone = false
                    var retryCount = 0
                    while (!isDone && retryCount < 60) {
                        delay(2000)
                        retryCount++
                        val statusRes = AetherLyricApiClient.getTaskStatus(serverUrl, taskId, apiKey)
                        val status = statusRes.getOrNull()?.status

                        when (status) {
                            "completed" -> {
                                isDone = true
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

                                statusText.text = getString(R.string.ui_transcription_success)
                                statusText.setTextColor(colorAccentMint)
                                Toast.makeText(activity, getString(R.string.ui_transcription_success), Toast.LENGTH_LONG).show()
                            }
                            "failed" -> {
                                isDone = true
                                val err = statusRes.getOrNull()?.errorMessage ?: "辨識失敗"
                                statusText.text = getString(R.string.ui_transcription_failed, err)
                                statusText.setTextColor(colorDanger)
                                Toast.makeText(activity, "AI 辨識失敗: $err", Toast.LENGTH_LONG).show()
                            }
                            else -> {
                                statusText.text = "AI 辨識中... (進度: ${statusRes.getOrNull()?.progress ?: 0}%)"
                            }
                        }
                    }

                    if (!isDone) {
                        statusText.text = "辨識逾時，請至伺服器查看任務進度"
                        statusText.setTextColor(colorDanger)
                    }
                } catch (e: Exception) {
                    statusText.text = getString(R.string.ui_transcription_failed, e.message ?: "連線錯誤")
                    statusText.setTextColor(colorDanger)
                    Toast.makeText(activity, "錯誤: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    uploadButton.isEnabled = true
                    selectButton.isEnabled = true
                }
            }
        }
        addView(uploadButton)
    }
}
