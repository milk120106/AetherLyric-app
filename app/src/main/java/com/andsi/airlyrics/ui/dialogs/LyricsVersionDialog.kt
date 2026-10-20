package com.andsi.airlyrics.ui.dialogs

import android.app.Dialog
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.andsi.airlyrics.R
import com.andsi.airlyrics.core.model.SongIdentity
import com.andsi.airlyrics.design.tokens.AirUiTokens
import com.andsi.airlyrics.i18n.localizedOffsetDescription
import com.andsi.airlyrics.lyrics.storage.LyricsStorage
import com.andsi.airlyrics.lyrics.storage.LyricsVersion
import com.andsi.airlyrics.lyrics.storage.LyricsVersionStore
import com.andsi.airlyrics.lyrics.storage.PlainLyricsStorageOps
import com.andsi.airlyrics.ui.components.actionButton
import com.andsi.airlyrics.ui.components.airIconView
import com.andsi.airlyrics.ui.components.enableSoftPressFeedback
import com.andsi.airlyrics.ui.components.showAirDialog
import com.andsi.airlyrics.ui.model.MainUiHost
import com.andsi.airlyrics.ui.theme.colorAccent
import com.andsi.airlyrics.ui.theme.colorAccentMint
import com.andsi.airlyrics.ui.theme.colorCard
import com.andsi.airlyrics.ui.theme.colorDanger
import com.andsi.airlyrics.ui.theme.colorStroke
import com.andsi.airlyrics.ui.theme.colorSurfaceLight
import com.andsi.airlyrics.ui.theme.colorTextMuted
import com.andsi.airlyrics.ui.theme.colorTextStrong

/**
 * 歌詞多版本切換與管理對話框。
 * 支援查看、新增、重新命名、刪除特定版本，並在切換時即時通知懸浮窗更新。
 */
internal fun showLyricsVersionDialog(
    activity: MainUiHost,
    song: SongIdentity,
    onVersionChanged: () -> Unit
) = with(activity) {
    lateinit var dialog: Dialog

    val defaultLrc = PlainLyricsStorageOps.readPlainLyrics(
        context = activity,
        title = song.title,
        artist = song.artist,
        duration = song.durationMs
    ).orEmpty()

    val versionListContainer = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    fun refreshVersionList() {
        versionListContainer.removeAllViews()
        val versions = LyricsVersionStore.getVersions(activity, song, defaultLrc)
        val activeId = LyricsVersionStore.getActiveVersionId(activity, song.storageKey())
            ?: versions.firstOrNull()?.versionId

        versions.forEach { version ->
            val isActive = version.versionId == activeId
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                enableSoftPressFeedback(AirUiTokens.Motion.DefaultPressScale)
                setPadding(
                    dp(AirUiTokens.Space.CardH),
                    dp(AirUiTokens.Space.Lg * 2),
                    dp(AirUiTokens.Space.CardH),
                    dp(AirUiTokens.Space.Lg * 2)
                )
                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, dp(AirUiTokens.Space.Sm + AirUiTokens.Space.Xs))
                }
                layoutParams = params
                background = GradientDrawable().apply {
                    cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
                    setColor(if (isActive) colorSurfaceLight else colorCard)
                    setStroke(
                        dp(if (isActive) AirUiTokens.Stroke.Selected else AirUiTokens.Stroke.Hairline),
                        if (isActive) colorAccent else colorStroke
                    )
                }

                // 左側版本資訊
                val textGroup = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

                    val titleRow = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL

                        addView(TextView(activity).apply {
                            text = version.versionName
                            textSize = AirUiTokens.TextSize.Body
                            typeface = Typeface.DEFAULT_BOLD
                            setTextColor(if (isActive) colorAccent else colorTextStrong)
                        })

                        if (isActive) {
                            addView(TextView(activity).apply {
                                text = "使用中"
                                textSize = AirUiTokens.TextSize.Tiny
                                typeface = Typeface.DEFAULT_BOLD
                                setTextColor(colorAccentMint)
                                setPadding(dp(AirUiTokens.Space.Sm), 0, 0, 0)
                            })
                        }
                    }
                    addView(titleRow)

                    val offsetText = localizedOffsetDescription(version.offsetMs)
                    val subText = "偏移量: $offsetText · 來源: ${version.source}"
                    addView(TextView(activity).apply {
                        text = subText
                        textSize = AirUiTokens.TextSize.Caption
                        setTextColor(colorTextMuted)
                        setPadding(0, dp(AirUiTokens.Space.Xxs), 0, 0)
                    })
                }
                addView(textGroup)

                // 重新命名按鈕
                val renameIcon = airIconView(R.drawable.ic_air_edit, colorTextMuted, "重新命名版本").apply {
                    layoutParams = LinearLayout.LayoutParams(
                        dp(AirUiTokens.Layout.CompactIconButtonSize),
                        dp(AirUiTokens.Layout.CompactIconButtonSize)
                    ).apply {
                        setMargins(0, 0, dp(AirUiTokens.Space.Sm), 0)
                    }
                    setOnClickListener {
                        showRenameVersionDialog(activity, song, version) {
                            refreshVersionList()
                            onVersionChanged()
                        }
                    }
                }
                addView(renameIcon)

                // 刪除按鈕 (保留最後一個版本不可刪除)
                if (versions.size > 1) {
                    val deleteIcon = airIconView(R.drawable.ic_air_delete, colorDanger, "刪除版本").apply {
                        layoutParams = LinearLayout.LayoutParams(
                            dp(AirUiTokens.Layout.CompactIconButtonSize),
                            dp(AirUiTokens.Layout.CompactIconButtonSize)
                        )
                        setOnClickListener {
                            showAirDialog(
                                title = "刪除歌詞版本",
                                message = "確定要刪除版本「${version.versionName}」嗎？此動作無法復原。",
                                positiveText = "刪除",
                                negativeText = getString(R.string.ui_cancel),
                                onPositive = {
                                    LyricsVersionStore.deleteVersion(activity, song, version.versionId)
                                    Toast.makeText(activity, "已刪除版本：${version.versionName}", Toast.LENGTH_SHORT).show()
                                    activity.uiActions.reloadFloatingLyrics()
                                    refreshVersionList()
                                    onVersionChanged()
                                }
                            )
                        }
                    }
                    addView(deleteIcon)
                }

                setOnClickListener {
                    if (!isActive) {
                        LyricsVersionStore.switchVersion(activity, song, version.versionId)
                        activity.uiActions.reloadFloatingLyrics()
                        Toast.makeText(activity, "已切換至：${version.versionName}", Toast.LENGTH_SHORT).show()
                        refreshVersionList()
                        onVersionChanged()
                    }
                }
            }
            versionListContainer.addView(row)
        }
    }

    refreshVersionList()

    dialog = showAirDialog(
        title = getString(R.string.ui_lyrics_versions),
        message = "${song.title} - ${song.artist}",
        positiveText = getString(R.string.ui_close),
        body = {
            addView(versionListContainer)

            // 新增版本按鈕
            val addBtn = actionButton(activity, getString(R.string.ui_add_new_version)) { }
            addBtn.setOnClickListener {
                showAddVersionDialog(activity, song, defaultLrc) {
                    refreshVersionList()
                    onVersionChanged()
                }
            }
            addView(addBtn)
        }
    )
}

/**
 * 重新命名版本子彈窗
 */
private fun showRenameVersionDialog(
    activity: MainUiHost,
    song: SongIdentity,
    version: LyricsVersion,
    onRenamed: () -> Unit
) = with(activity) {
    lateinit var renameDialog: Dialog

    val nameInput = EditText(activity).apply {
        setText(version.versionName)
        setSelection(text.length)
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
        isSingleLine = true
    }

    renameDialog = showAirDialog(
        title = "重新命名版本",
        message = "請輸入新版本名稱：",
        positiveText = "儲存",
        negativeText = getString(R.string.ui_cancel),
        body = {
            addView(nameInput)
        },
        onPositive = {
            val newName = nameInput.text.toString().trim()
            if (newName.isNotBlank()) {
                LyricsVersionStore.renameVersion(activity, song, version.versionId, newName)
                Toast.makeText(activity, "已重新命名為：$newName", Toast.LENGTH_SHORT).show()
                onRenamed()
            }
            renameDialog.dismiss()
        }
    )
}

/**
 * 新增版本子彈窗
 */
private fun showAddVersionDialog(
    activity: MainUiHost,
    song: SongIdentity,
    templateLrc: String,
    onAdded: () -> Unit
) = with(activity) {
    lateinit var addDialog: Dialog

    val nameInput = EditText(activity).apply {
        hint = getString(R.string.ui_version_name_hint)
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
        isSingleLine = true
    }

    addDialog = showAirDialog(
        title = getString(R.string.ui_add_new_version),
        message = "請輸入新版本名稱（將複製目前歌詞作為初始內容）：",
        positiveText = "建立並切換",
        negativeText = getString(R.string.ui_cancel),
        body = {
            addView(nameInput)
        },
        onPositive = {
            val name = nameInput.text.toString().trim().ifBlank { "新版本" }
            val newVer = LyricsVersion(
                songKey = song.storageKey(),
                versionName = name,
                lrcContent = templateLrc,
                offsetMs = 0L,
                source = "custom"
            )
            LyricsVersionStore.addVersion(activity, newVer)
            LyricsVersionStore.switchVersion(activity, song, newVer.versionId)
            activity.uiActions.reloadFloatingLyrics()
            Toast.makeText(activity, "已建立並切換至：$name", Toast.LENGTH_SHORT).show()
            onAdded()
            addDialog.dismiss()
        }
    )
}
