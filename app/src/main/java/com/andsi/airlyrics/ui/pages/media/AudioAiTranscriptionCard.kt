package com.andsi.airlyrics.ui.pages.media

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
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
import com.andsi.airlyrics.ui.components.showAirDialog
import com.andsi.airlyrics.ui.components.smallHint
import com.andsi.airlyrics.ui.model.MainUiHost
import com.andsi.airlyrics.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * AI 音訊辨識與影音解析卡片 (AudioAiTranscriptionCard)。
 * 包含：
 * 1. 分頁 1 (本地音檔辨識)：選取本機音檔上傳至 Server 進行 AI 轉錄，並提供「手動輸入參考歌詞進行對齊」功能。
 * 2. 分頁 2 (輸入影片網址)：偵測 Server 之 yt-dlp 狀態，支援輸入 YouTube / Bilibili 網址非同步解析並生成 LRC。
 */
internal fun createAudioAiTranscriptionCard(activity: MainUiHost): View = with(activity) {
    var selectedAudioFile: File? = null
    var selectedTrackTitle: String = ""
    var selectedTrackArtist: String = ""

    return card(activity) {
        val isAiEnabled = isAudioAiTranscriptionEnabled()

        val expandedContent = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            visibility = if (isAiEnabled) View.VISIBLE else View.GONE
        }

        val headerRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            val infoLayout = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(0, 0, dp(AirUiTokens.Space.Sm), 0)
                }
                addView(bigText(activity, getString(R.string.ui_ai_audio_transcription_title)))
                addView(smallHint(activity, getString(R.string.ui_ai_audio_transcription_desc)))
            }
            addView(infoLayout)

            val aiSwitch = SwitchCompat(activity).apply {
                isChecked = isAiEnabled
                applyAirThemeTint(activity)
                setOnCheckedChangeListener { _, checked ->
                    if (checked != isAudioAiTranscriptionEnabled()) {
                        setAudioAiTranscriptionEnabled(checked)
                    }
                    expandedContent.visibility = if (checked) View.VISIBLE else View.GONE
                }
            }
            addView(aiSwitch)
        }
        addView(headerRow)

        // 分頁切換列：[本地音檔辨識] / [輸入影片網址]
        var currentTab = 0 // 0: 本地音檔, 1: 影片網址
        val tabContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(AirUiTokens.Space.CardV), 0, dp(AirUiTokens.Space.Sm))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val tabLocalButton = TextView(activity).apply {
            setText(R.string.ui_tab_local_audio)
            textSize = AirUiTokens.TextSize.Button
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorOnAccent)
            gravity = Gravity.CENTER
            setPadding(dp(AirUiTokens.Space.ButtonH), dp(AirUiTokens.Space.ControlV), dp(AirUiTokens.Space.ButtonH), dp(AirUiTokens.Space.ControlV))
            background = GradientDrawable().apply {
                cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
                setColor(colorAccent)
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, dp(AirUiTokens.Space.Xs), 0)
            }
            isClickable = true
            isFocusable = true
        }

        val tabUrlButton = TextView(activity).apply {
            setText(R.string.ui_tab_video_url)
            textSize = AirUiTokens.TextSize.Button
            typeface = Typeface.DEFAULT
            setTextColor(colorTextStrong)
            gravity = Gravity.CENTER
            setPadding(dp(AirUiTokens.Space.ButtonH), dp(AirUiTokens.Space.ControlV), dp(AirUiTokens.Space.ButtonH), dp(AirUiTokens.Space.ControlV))
            background = GradientDrawable().apply {
                cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
                setColor(colorSurfaceLight)
                setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(AirUiTokens.Space.Xs), 0, 0, 0)
            }
            isClickable = true
            isFocusable = true
        }

        tabContainer.addView(tabLocalButton)
        tabContainer.addView(tabUrlButton)
        expandedContent.addView(tabContainer)

        // ==================== 進度條與狀態提示文字 (共用) ====================
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
        val statusText = TextView(activity).apply {
            text = ""
            textSize = AirUiTokens.TextSize.Caption
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorAccent)
            visibility = View.GONE
            setPadding(0, dp(AirUiTokens.Space.Sm), 0, 0)
        }

        // ==================== 面板 1：本地音檔辨識 ====================
        val localPanel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

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
                setMargins(0, dp(AirUiTokens.Space.Sm), 0, 0)
            }
        }
        localPanel.addView(fileInfoBox)

        val localTitleInput = EditText(activity).apply {
            hint = "歌曲標題 (選填，可輔助 AI 校正)"
            textSize = AirUiTokens.TextSize.BodySmall
            setTextColor(colorTextStrong)
            setHintTextColor(colorTextMuted)
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
        localPanel.addView(localTitleInput)

        val localArtistInput = EditText(activity).apply {
            hint = "演唱歌手 (選填，可輔助 AI 校正)"
            textSize = AirUiTokens.TextSize.BodySmall
            setTextColor(colorTextStrong)
            setHintTextColor(colorTextMuted)
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
        localPanel.addView(localArtistInput)

        // SAF 檔案選取器
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
        val selectButton = actionButton(activity, getString(R.string.ui_select_audio_file)) {
            audioPickerLauncher.launch("audio/*")
        }
        (selectButton.layoutParams as? LinearLayout.LayoutParams)?.setMargins(0, dp(AirUiTokens.Space.CardV), 0, 0)
        localPanel.addView(selectButton)

        // 按鈕：上傳並辨識
        val uploadButton = actionButton(activity, getString(R.string.ui_upload_and_transcribe)) { }
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

            selectedTrackTitle = localTitleInput.text.toString().trim()
            selectedTrackArtist = localArtistInput.text.toString().trim()

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
                        apiKey = apiKey,
                        context = activity
                    )

                    val taskId = createResult.getOrThrow().taskId
                    statusText.text = "任務已發起 (ID: ${taskId.take(8)}...)，AI 辨識中..."
                    progressBar.isIndeterminate = false
                    progressBar.progress = 5

                    var isDone = false
                    val finalInfo = AetherLyricApiClient.observeTaskUntilDone(
                        context = activity,
                        serverUrl = serverUrl,
                        taskId = taskId,
                        apiKey = apiKey,
                        maxWaitSeconds = 120
                    ) { taskInfo ->
                        val curProgress = taskInfo.progress
                        progressBar.progress = curProgress.coerceIn(5, 95)
                        val stageLabel = taskInfo.stage.takeIf { it.isNotBlank() }?.let { " [$it]" } ?: ""
                        statusText.text = "AI 辨識中$stageLabel... (進度: $curProgress%)"
                    }

                    when (finalInfo?.status) {
                        "completed" -> {
                            isDone = true
                            progressBar.progress = 100
                            val lrcContent = finalInfo.resultLrc?.takeIf { it.isNotBlank() }
                                ?: AetherLyricApiClient.exportLyrics(serverUrl, taskId, apiKey = apiKey, context = activity).getOrThrow()

                            val title = selectedTrackTitle.ifBlank { file.nameWithoutExtension }
                            val artist = selectedTrackArtist.ifBlank { "Unknown" }

                            PlainLyricsStorageOps.savePlainLyrics(
                                context = activity,
                                title = title,
                                artist = artist,
                                duration = 0L,
                                plainLrc = lrcContent,
                                plainSource = LyricsStorage.SOURCE_MANUAL_IMPORT,
                                plainProvider = "AetherLyric AI"
                            )

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
                            val err = finalInfo.errorMessage ?: "辨識失敗"
                            statusText.text = getString(R.string.ui_transcription_failed, err)
                            statusText.setTextColor(colorDanger)
                            AppLogger.e("AudioAiTranscription", "AI 辨識任務失敗: $err")
                            Toast.makeText(activity, "AI 辨識失敗: $err", Toast.LENGTH_LONG).show()
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
        (uploadButton.layoutParams as? LinearLayout.LayoutParams)?.setMargins(0, dp(AirUiTokens.Space.Sm), 0, 0)
        localPanel.addView(uploadButton)

        // 按鈕：輸入參考歌詞進行對齊 (追加入口)
        val alignReferenceButton = actionButton(activity, getString(R.string.ui_input_reference_lyrics_align)) { }
        alignReferenceButton.setOnClickListener {
            // 彈出輸入對話框
            var dialogInput: EditText? = null
            showAirDialog(
                title = getString(R.string.ui_reference_lyrics_dialog_title),
                message = getString(R.string.ui_reference_lyrics_hint),
                positiveText = getString(R.string.ui_start_align),
                negativeText = getString(R.string.ui_cancel),
                body = {
                    val filePrompt = TextView(activity).apply {
                        text = if (selectedAudioFile != null) "已選音檔: ${selectedAudioFile?.name}" else "請先在主卡片選取音訊檔案"
                        textSize = AirUiTokens.TextSize.Caption
                        setTextColor(if (selectedAudioFile != null) colorAccentMint else colorDanger)
                        setPadding(0, 0, 0, dp(AirUiTokens.Space.Sm))
                    }
                    addView(filePrompt)

                    dialogInput = EditText(activity).apply {
                        hint = "請貼上無時間戳的純文字歌詞（一行一句）..."
                        textSize = AirUiTokens.TextSize.BodySmall
                        setTextColor(colorTextStrong)
                        setHintTextColor(colorTextMuted)
                        minLines = 6
                        maxLines = 10
                        gravity = Gravity.TOP or Gravity.START
                        setPadding(dp(AirUiTokens.Space.CardH), dp(AirUiTokens.Space.CardV), dp(AirUiTokens.Space.CardH), dp(AirUiTokens.Space.CardV))
                        background = GradientDrawable().apply {
                            cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
                            setColor(colorSurfaceLight)
                            setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
                        }
                    }
                    addView(dialogInput)
                },
                onPositive = {
                    val plainLyrics = dialogInput?.text?.toString()?.trim().orEmpty()
                    if (plainLyrics.isBlank()) {
                        Toast.makeText(activity, "歌詞文字不得為空", Toast.LENGTH_SHORT).show()
                        return@showAirDialog
                    }

                    val audioFile = selectedAudioFile
                    if (audioFile == null || !audioFile.exists()) {
                        Toast.makeText(activity, "請先選取對應音訊檔案", Toast.LENGTH_SHORT).show()
                        return@showAirDialog
                    }

                    val serverUrl = AetherLyricServerStore.getServerUrl(activity)
                    val apiKey = AetherLyricServerStore.getAdminApiKey(activity)
                    val serverOnline = AetherLyricServerStore.isServerEnabled(activity) && serverUrl.isNotBlank()

                    val title = localTitleInput.text.toString().trim().ifBlank { audioFile.nameWithoutExtension }
                    val artist = localArtistInput.text.toString().trim().ifBlank { "Unknown" }

                    statusText.visibility = View.VISIBLE
                    statusText.text = "正在進行歌詞時間軸對齊..."
                    statusText.setTextColor(colorAccent)
                    progressBar.visibility = View.VISIBLE
                    progressBar.isIndeterminate = true

                    activity.activity.lifecycleScope.launch {
                        try {
                            var alignedLrc: String? = null

                            // 1. 若連線 Server，發起參考對齊任務
                            if (serverOnline) {
                                AppLogger.i("ReferenceAlign", "發送音訊與參考歌詞至伺服器對齊: ${audioFile.name}")
                                val taskRes = AetherLyricApiClient.createTask(
                                    serverUrl = serverUrl,
                                    audioFile = audioFile,
                                    title = title,
                                    artist = artist,
                                    referenceLrc = plainLyrics,
                                    apiKey = apiKey,
                                    context = activity
                                )

                                val taskId = taskRes.getOrThrow().taskId
                                val finalAlign = AetherLyricApiClient.observeTaskUntilDone(
                                    context = activity,
                                    serverUrl = serverUrl,
                                    taskId = taskId,
                                    apiKey = apiKey,
                                    maxWaitSeconds = 90
                                ) { info ->
                                    statusText.text = "正在進行伺服器時間軸對齊... (${info.progress}%)"
                                }
                                if (finalAlign?.status == "completed") {
                                    alignedLrc = finalAlign.resultLrc?.takeIf { it.isNotBlank() }
                                        ?: AetherLyricApiClient.exportLyrics(serverUrl, taskId, apiKey = apiKey, context = activity).getOrNull()
                                }
                            }

                            // 2. 備用方案：若未連線或伺服器失敗，且非「僅伺服器模式」，使用本地簡易時間軸對齊
                            if (alignedLrc.isNullOrBlank()) {
                                if (AetherLyricServerStore.isServerOnlyMode(activity)) {
                                    throw IllegalStateException("目前為「僅伺服器模式」，伺服器對齊未成功且已停用本地簡易對齊")
                                }
                                AppLogger.w("ReferenceAlign", "伺服器未連線或對齊失敗，採用本地簡易時間軸對齊")
                                val retriever = MediaMetadataRetriever()
                                val durationMs = try {
                                    retriever.setDataSource(audioFile.absolutePath)
                                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 180000L
                                } catch (_: Exception) {
                                    180000L
                                } finally {
                                    retriever.release()
                                }

                                alignedLrc = AetherLyricApiClient.alignPlainLyricsLocally(plainLyrics, durationMs)
                                Toast.makeText(activity, R.string.ui_local_align_hint, Toast.LENGTH_LONG).show()
                            }

                            // 3. 儲存至本地歌詞庫並建立新版本
                            PlainLyricsStorageOps.savePlainLyrics(
                                context = activity,
                                title = title,
                                artist = artist,
                                duration = 0L,
                                plainLrc = alignedLrc,
                                plainSource = LyricsStorage.SOURCE_MANUAL_IMPORT,
                                plainProvider = "Reference Align"
                            )

                            val songIdent = SongIdentity(title, artist, "", 0L)
                            val newVer = LyricsVersion(
                                songKey = songIdent.storageKey(),
                                versionName = activity.getString(R.string.ui_align_version_name),
                                lrcContent = alignedLrc,
                                source = "reference_align"
                            )
                            LyricsVersionStore.addVersion(activity, newVer)
                            LyricsVersionStore.switchVersion(activity, songIdent, newVer.versionId)
                            activity.uiActions.reloadFloatingLyrics()

                            progressBar.visibility = View.GONE
                            statusText.text = "對齊完成！已套用「${activity.getString(R.string.ui_align_version_name)}」"
                            statusText.setTextColor(colorAccentMint)
                            AppLogger.i("ReferenceAlign", "參考歌詞對齊完成: $title - $artist")
                            Toast.makeText(activity, "對齊完成，懸浮視窗已同步更新", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            progressBar.visibility = View.GONE
                            statusText.text = "對齊失敗：${e.message}"
                            statusText.setTextColor(colorDanger)
                            AppLogger.e("ReferenceAlign", "對齊流程異常", e)
                            Toast.makeText(activity, "對齊失敗：${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
        (alignReferenceButton.layoutParams as? LinearLayout.LayoutParams)?.setMargins(0, dp(AirUiTokens.Space.Sm), 0, 0)
        localPanel.addView(alignReferenceButton)

        expandedContent.addView(localPanel)

        // ==================== 面板 2：輸入影片網址 ====================
        val urlPanel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val urlWarningHint = TextView(activity).apply {
            text = ""
            textSize = AirUiTokens.TextSize.Caption
            setTextColor(colorDanger)
            visibility = View.GONE
            setPadding(0, 0, 0, dp(AirUiTokens.Space.Sm))
        }
        urlPanel.addView(urlWarningHint)

        val videoUrlInput = EditText(activity).apply {
            hint = getString(R.string.ui_video_url_hint)
            textSize = AirUiTokens.TextSize.BodySmall
            setTextColor(colorTextStrong)
            setHintTextColor(colorTextMuted)
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
        urlPanel.addView(videoUrlInput)

        val urlTitleInput = EditText(activity).apply {
            hint = "歌曲標題 (選填)"
            textSize = AirUiTokens.TextSize.BodySmall
            setTextColor(colorTextStrong)
            setHintTextColor(colorTextMuted)
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
        urlPanel.addView(urlTitleInput)

        val urlArtistInput = EditText(activity).apply {
            hint = "歌手名稱 (選填)"
            textSize = AirUiTokens.TextSize.BodySmall
            setTextColor(colorTextStrong)
            setHintTextColor(colorTextMuted)
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
        urlPanel.addView(urlArtistInput)

        val downloadUrlButton = actionButton(activity, getString(R.string.ui_download_and_transcribe)) { }
        downloadUrlButton.setOnClickListener {
            val url = videoUrlInput.text.toString().trim()
            if (url.isBlank() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
                Toast.makeText(activity, "請輸入有效的 HTTP/HTTPS 影片網址", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val serverUrl = AetherLyricServerStore.getServerUrl(activity)
            val apiKey = AetherLyricServerStore.getAdminApiKey(activity)

            if (serverUrl.isBlank()) {
                Toast.makeText(activity, "請先至設定頁面設定並驗證 AetherLyric 伺服器", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val title = urlTitleInput.text.toString().trim().ifBlank { "Video_Audio" }
            val artist = urlArtistInput.text.toString().trim().ifBlank { "WebMedia" }

            progressBar.visibility = View.VISIBLE
            progressBar.isIndeterminate = true
            statusText.visibility = View.VISIBLE
            statusText.text = getString(R.string.ui_video_parsing)
            statusText.setTextColor(colorAccent)

            activity.activity.lifecycleScope.launch {
                try {
                    AppLogger.i("VideoTask", "發起影片網址解析任務: $url")
                    val createRes = AetherLyricApiClient.createTaskFromUrl(
                        serverUrl = serverUrl,
                        videoUrl = url,
                        title = title,
                        artist = artist,
                        apiKey = apiKey,
                        context = activity
                    )

                    val taskId = createRes.getOrThrow().taskId
                    statusText.text = "影片任務已發起 (ID: ${taskId.take(8)}...)，下載與轉錄中..."
                    progressBar.isIndeterminate = false
                    progressBar.progress = 10

                    var isDone = false
                    val finalVideoInfo = AetherLyricApiClient.observeTaskUntilDone(
                        context = activity,
                        serverUrl = serverUrl,
                        taskId = taskId,
                        apiKey = apiKey,
                        maxWaitSeconds = 150
                    ) { taskInfo ->
                        val curProgress = taskInfo.progress
                        progressBar.progress = curProgress.coerceIn(10, 95)
                        statusText.text = "影音下載辨識中... (進度: $curProgress%)"
                    }

                    when (finalVideoInfo?.status) {
                        "completed" -> {
                            isDone = true
                            progressBar.progress = 100
                            val lrcContent = finalVideoInfo.resultLrc?.takeIf { it.isNotBlank() }
                                ?: AetherLyricApiClient.exportLyrics(serverUrl, taskId, apiKey = apiKey, context = activity).getOrThrow()

                            PlainLyricsStorageOps.savePlainLyrics(
                                context = activity,
                                title = title,
                                artist = artist,
                                duration = 0L,
                                plainLrc = lrcContent,
                                plainSource = LyricsStorage.SOURCE_MANUAL_IMPORT,
                                plainProvider = "Video URL"
                            )

                            val songIdent = SongIdentity(title, artist, "", 0L)
                            val newVer = LyricsVersion(
                                songKey = songIdent.storageKey(),
                                versionName = activity.getString(R.string.ui_video_version_name),
                                lrcContent = lrcContent,
                                source = "video_url"
                            )
                            LyricsVersionStore.addVersion(activity, newVer)
                            LyricsVersionStore.switchVersion(activity, songIdent, newVer.versionId)
                            activity.uiActions.reloadFloatingLyrics()

                            statusText.text = getString(R.string.ui_transcription_success)
                            statusText.setTextColor(colorAccentMint)
                            progressBar.visibility = View.GONE
                            AppLogger.i("VideoTask", "影片歌詞轉錄成功: $title - $artist")
                            Toast.makeText(activity, "影片歌詞轉錄成功並已套用！", Toast.LENGTH_LONG).show()
                        }
                        "failed" -> {
                            isDone = true
                            progressBar.visibility = View.GONE
                            val err = finalVideoInfo.errorMessage ?: "轉錄失敗"
                            statusText.text = "影片轉錄失敗: $err"
                            statusText.setTextColor(colorDanger)
                            AppLogger.e("VideoTask", "影片解析失敗: $err")
                            Toast.makeText(activity, "影片轉錄失敗: $err", Toast.LENGTH_LONG).show()
                        }
                    }

                    if (!isDone) {
                        progressBar.visibility = View.GONE
                        statusText.text = "影片處理逾時，請檢查伺服器狀態"
                        statusText.setTextColor(colorDanger)
                        AppLogger.w("VideoTask", "影片解析任務超時未完成 (taskId: $taskId)")
                    }
                } catch (e: Exception) {
                    progressBar.visibility = View.GONE
                    statusText.text = "任務發起失敗: ${e.message}"
                    statusText.setTextColor(colorDanger)
                    AppLogger.e("VideoTask", "影片解析發起失敗", e)
                    Toast.makeText(activity, "影片處理失敗: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        (downloadUrlButton.layoutParams as? LinearLayout.LayoutParams)?.setMargins(0, dp(AirUiTokens.Space.CardV), 0, 0)
        urlPanel.addView(downloadUrlButton)

        // 按鈕：使用 YTDLnis 本地下載音訊
        val ytdlnisButton = actionButton(activity, getString(R.string.ui_ytdlnis_download)) { }
        ytdlnisButton.setOnClickListener {
            if (!isYtdlnisInstalled(activity)) {
                showAirDialog(
                    title = getString(R.string.ui_ytdlnis_not_installed),
                    message = getString(R.string.ui_ytdlnis_not_installed_desc),
                    positiveText = getString(R.string.ui_go_download),
                    negativeText = getString(R.string.ui_cancel),
                    onPositive = {
                        activity.openUrl("https://github.com/deniscerri/ytdlnis/releases")
                    }
                )
                return@setOnClickListener
            }

            val url = videoUrlInput.text.toString().trim()
            if (url.isNotBlank()) {
                try {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, url)
                        setPackage("com.deniscerri.ytdlnis")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    activity.startActivity(sendIntent)
                } catch (e: Exception) {
                    AppLogger.e("YTDLnis", "調用 YTDLnis 失敗", e)
                    Toast.makeText(activity, "調用 YTDLnis 失敗: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                val launchIntent = activity.packageManager.getLaunchIntentForPackage("com.deniscerri.ytdlnis")
                if (launchIntent != null) {
                    activity.startActivity(launchIntent)
                } else {
                    Toast.makeText(activity, "請先輸入影片網址", Toast.LENGTH_SHORT).show()
                }
            }
        }
        (ytdlnisButton.layoutParams as? LinearLayout.LayoutParams)?.setMargins(0, dp(AirUiTokens.Space.Sm), 0, 0)
        urlPanel.addView(ytdlnisButton)

        expandedContent.addView(urlPanel)

        // 插入進度條與提示文字在最底端
        expandedContent.addView(progressBar)
        expandedContent.addView(statusText)

        addView(expandedContent)

        // 分頁切換函式與狀態檢測
        fun checkYtdlpSupport() {
            val serverUrl = AetherLyricServerStore.getServerUrl(activity)
            val apiKey = AetherLyricServerStore.getAdminApiKey(activity)
            if (serverUrl.isBlank()) {
                downloadUrlButton.isEnabled = false
                urlWarningHint.visibility = View.VISIBLE
                urlWarningHint.text = "尚未連線伺服器。若需下載影片音訊，可使用下方 YTDLnis 本地下載"
                return
            }

            activity.activity.lifecycleScope.launch {
                val healthRes = AetherLyricApiClient.testHealth(serverUrl, apiKey)
                val isYtdlpSupported = healthRes.getOrNull()?.ytdlpEnabled ?: false
                if (!isYtdlpSupported) {
                    downloadUrlButton.isEnabled = false
                    urlWarningHint.visibility = View.VISIBLE
                    urlWarningHint.text = activity.getString(R.string.ui_video_url_disabled_hint)
                } else {
                    downloadUrlButton.isEnabled = true
                    urlWarningHint.visibility = View.GONE
                }
            }
        }

        tabLocalButton.setOnClickListener {
            if (currentTab != 0) {
                currentTab = 0
                tabLocalButton.setTextColor(colorOnAccent)
                tabLocalButton.background = GradientDrawable().apply {
                    cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
                    setColor(colorAccent)
                }
                tabUrlButton.setTextColor(colorTextStrong)
                tabUrlButton.background = GradientDrawable().apply {
                    cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
                    setColor(colorSurfaceLight)
                    setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
                }
                localPanel.visibility = View.VISIBLE
                urlPanel.visibility = View.GONE
            }
        }

        tabUrlButton.setOnClickListener {
            if (currentTab != 1) {
                currentTab = 1
                tabUrlButton.setTextColor(colorOnAccent)
                tabUrlButton.background = GradientDrawable().apply {
                    cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
                    setColor(colorAccent)
                }
                tabLocalButton.setTextColor(colorTextStrong)
                tabLocalButton.background = GradientDrawable().apply {
                    cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
                    setColor(colorSurfaceLight)
                    setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
                }
                localPanel.visibility = View.GONE
                urlPanel.visibility = View.VISIBLE
                checkYtdlpSupport()
            }
        }
    }
}

/**
 * 檢查系統是否已安裝 YTDLnis 下載器應用程式。
 */
private fun isYtdlnisInstalled(context: Context): Boolean {
    return try {
        context.packageManager.getPackageInfo("com.deniscerri.ytdlnis", 0)
        true
    } catch (_: Exception) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage("com.deniscerri.ytdlnis")
        }
        context.packageManager.queryIntentActivities(sendIntent, 0).isNotEmpty()
    }
}
