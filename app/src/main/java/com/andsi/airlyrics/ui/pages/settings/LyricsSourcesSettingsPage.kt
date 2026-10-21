package com.andsi.airlyrics.ui.pages.settings

import android.app.AlertDialog
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.andsi.airlyrics.R
import com.andsi.airlyrics.design.tokens.AirUiTokens
import com.andsi.airlyrics.logging.AppLogger
import com.andsi.airlyrics.lyrics.providers.CustomJsonPluginStore
import com.andsi.airlyrics.settings.store.LyricsSettingsStore
import com.andsi.airlyrics.ui.components.actionButton
import com.andsi.airlyrics.ui.components.airIconView
import com.andsi.airlyrics.ui.components.bigText
import com.andsi.airlyrics.ui.components.card
import com.andsi.airlyrics.ui.components.normalText
import com.andsi.airlyrics.ui.components.pageContainer
import com.andsi.airlyrics.ui.components.scroll
import com.andsi.airlyrics.ui.components.showAirDialog
import com.andsi.airlyrics.ui.components.smallHint
import com.andsi.airlyrics.ui.model.MainUiHost
import com.andsi.airlyrics.ui.theme.applyAirThemeTint
import com.andsi.airlyrics.ui.theme.colorAccentMint
import com.andsi.airlyrics.ui.theme.colorDanger
import com.andsi.airlyrics.ui.theme.colorStroke
import com.andsi.airlyrics.ui.theme.colorSurfaceLight
import com.andsi.airlyrics.ui.theme.colorTextMuted
import com.andsi.airlyrics.ui.theme.colorTextStrong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 設定子頁面 2：歌詞來源管理 (LyricsSourcesSettingsPage)
 * 支援來源優先順序拖曳排序、勾選啟用、BYOK 金鑰管理，以及「自訂 JSON 來源」外掛匯入與自動更新。
 */
internal fun createLyricsSourcesSettingsPage(activity: MainUiHost): View = with(activity) {
    val container = pageContainer(activity, animateChanges = false)
    val settings = lyricsSettingsState()

    container.addView(
        settingsBackHeader(
            title = getString(R.string.ui_sources_subpage_title),
            subtitle = getString(R.string.ui_sources_subpage_desc)
        )
    )

    // 1. 來源排序與勾選卡片
    container.addView(createLyricsSourceOrderCard(activity, settings, maxDisplayed = 4))

    // 2. 自訂 JSON 歌詞來源外掛卡片
    container.addView(createCustomJsonPluginsCard(activity))

    // 3. 自訂 API Key (BYOK) 卡片
    container.addView(card(activity) {
        addView(bigText(activity, getString(R.string.ui_byok_title)))
        addView(smallHint(activity, getString(R.string.ui_byok_hint)))

        // Genius Token
        addView(TextView(activity).apply {
            text = getString(R.string.ui_genius_api_key)
            textSize = AirUiTokens.TextSize.BodySmall
            setTextColor(colorTextStrong)
            setPadding(0, dp(AirUiTokens.Space.CardV), 0, dp(AirUiTokens.Space.Sm))
        })

        val geniusInput = EditText(activity).apply {
            hint = "例如：Bearer Access Token"
            setText(LyricsSettingsStore.getGeniusApiKey(activity).orEmpty())
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
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        addView(geniusInput)

        // Musixmatch Token
        addView(TextView(activity).apply {
            text = getString(R.string.ui_musixmatch_api_key)
            textSize = AirUiTokens.TextSize.BodySmall
            setTextColor(colorTextStrong)
            setPadding(0, dp(AirUiTokens.Space.CardV), 0, dp(AirUiTokens.Space.Sm))
        })

        val musixmatchInput = EditText(activity).apply {
            hint = "例如：User Token"
            setText(LyricsSettingsStore.getMusixmatchApiKey(activity).orEmpty())
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
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        addView(musixmatchInput)

        // 儲存金鑰按鈕
        val saveButton = actionButton(activity, "儲存 API 金鑰") { }
        (saveButton.layoutParams as? LinearLayout.LayoutParams)?.setMargins(
            0,
            dp(AirUiTokens.Space.CardV),
            0,
            0
        )
        saveButton.setOnClickListener {
            val geniusKey = geniusInput.text.toString().trim()
            val musixKey = musixmatchInput.text.toString().trim()
            LyricsSettingsStore.setGeniusApiKey(activity, geniusKey)
            LyricsSettingsStore.setMusixmatchApiKey(activity, musixKey)
            AppLogger.i("LyricsSources", "BYOK 金鑰設定已更新")
            Toast.makeText(activity, getString(R.string.ui_byok_saved), Toast.LENGTH_SHORT).show()
        }
        addView(saveButton)
    })

    return scroll(activity, container, maxWidthDp = 1200)
}

/**
 * 自訂 JSON 歌詞來源外掛管理卡片：
 * 支援 URL 安裝、JSON 規則直接貼上匯入、外掛自動更新開關與更新間隔設定（6 / 12 / 24 / 72 小時）。
 */
private fun createCustomJsonPluginsCard(activity: MainUiHost): View = with(activity) {
    val pluginsListContainer = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    fun renderPluginList() {
        pluginsListContainer.removeAllViews()
        val plugins = CustomJsonPluginStore.getPlugins(activity)
        if (plugins.isEmpty()) {
            pluginsListContainer.addView(smallHint(activity, "目前尚未安裝任何自訂 JSON 歌詞來源外掛").apply {
                setPadding(0, dp(AirUiTokens.Space.Sm), 0, dp(AirUiTokens.Space.Sm))
            })
            return
        }

        plugins.forEach { plugin ->
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    dp(AirUiTokens.Space.CardH),
                    dp(AirUiTokens.Space.Sm),
                    dp(AirUiTokens.Space.CardH),
                    dp(AirUiTokens.Space.Sm)
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
                    setMargins(0, dp(AirUiTokens.Space.Xs), 0, dp(AirUiTokens.Space.Xs))
                }
            }

            val infoCol = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(0, 0, dp(AirUiTokens.Space.Sm), 0)
                }
                addView(TextView(activity).apply {
                    text = "${plugin.name} (v${plugin.version})"
                    textSize = AirUiTokens.TextSize.BodySmall
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(colorTextStrong)
                })
                addView(TextView(activity).apply {
                    text = "作者: ${plugin.author.ifBlank { "未知" }} · 方法: ${plugin.method}"
                    textSize = AirUiTokens.TextSize.Caption
                    setTextColor(colorAccentMint)
                })
                addView(TextView(activity).apply {
                    text = plugin.searchUrl
                    textSize = AirUiTokens.TextSize.Tiny
                    setTextColor(colorTextMuted)
                    maxLines = 1
                })
            }
            row.addView(infoCol)

            val enableSwitch = SwitchCompat(activity).apply {
                isChecked = plugin.enabled
                applyAirThemeTint(activity)
                setOnCheckedChangeListener { _, checked ->
                    CustomJsonPluginStore.setPluginEnabled(activity, plugin.id, checked)
                }
            }
            row.addView(enableSwitch)

            val deleteBtn = airIconView(
                iconRes = R.drawable.ic_air_delete,
                tint = colorDanger,
                contentDescription = "刪除外掛"
            ).apply {
                layoutParams = LinearLayout.LayoutParams(
                    dp(AirUiTokens.Layout.CompactIconButtonSize),
                    dp(AirUiTokens.Layout.CompactIconButtonSize)
                ).apply {
                    setMargins(dp(AirUiTokens.Space.Xs), 0, 0, 0)
                }
                setOnClickListener {
                    CustomJsonPluginStore.deletePlugin(activity, plugin.id)
                    renderPluginList()
                    Toast.makeText(activity, "已移除外掛：${plugin.name}", Toast.LENGTH_SHORT).show()
                }
            }
            row.addView(deleteBtn)
            pluginsListContainer.addView(row)
        }
    }

    renderPluginList()

    return card(activity) {
        addView(bigText(activity, "自訂 JSON 來源"))
        addView(smallHint(activity, "支援透過網址或 JSON 規則檔安裝第三方歌詞來源（沙盒安全限制：僅限 HTTP/HTTPS、8 秒逾時與 2MB 回應上限）"))

        // 安裝按鈕：網址安裝
        addView(actionButton(activity, "透過網址 (URL) 安裝 JSON 外掛") {
            var urlInput: EditText? = null
            showAirDialog(
                title = "網址安裝 JSON 歌詞外掛",
                message = "輸入外掛 JSON 設定檔的 HTTP/HTTPS 網址，支援後續自動檢查更新",
                positiveText = "下載並安裝",
                negativeText = getString(R.string.ui_cancel),
                body = {
                    urlInput = EditText(activity).apply {
                        hint = "https://example.com/lyrics-plugin.json"
                        textSize = AirUiTokens.TextSize.BodySmall
                        setTextColor(colorTextStrong)
                        setHintTextColor(colorTextMuted)
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                        isSingleLine = true
                        setPadding(dp(AirUiTokens.Space.ButtonH), dp(AirUiTokens.Space.ControlV), dp(AirUiTokens.Space.ButtonH), dp(AirUiTokens.Space.ControlV))
                        background = GradientDrawable().apply {
                            cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
                            setColor(colorSurfaceLight)
                            setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
                        }
                    }
                    addView(urlInput)
                },
                onPositive = {
                    val rawUrl = urlInput?.text?.toString()?.trim().orEmpty()
                    if (rawUrl.isBlank()) {
                        Toast.makeText(activity, "請輸入有效網址", Toast.LENGTH_SHORT).show()
                        return@showAirDialog
                    }
                    activity.activity.lifecycleScope.launch {
                        val res = withContext(Dispatchers.IO) {
                            CustomJsonPluginStore.installFromUrlBlocking(activity, rawUrl)
                        }
                        res.fold(
                            onSuccess = { list ->
                                renderPluginList()
                                Toast.makeText(activity, "成功安裝 ${list.size} 個外掛", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { err ->
                                Toast.makeText(activity, "安裝失敗：${err.message}", Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                }
            )
        })

        // 安裝按鈕：貼上 JSON 規則內容匯入
        addView(actionButton(activity, "貼上或匯入 JSON 外掛規則") {
            var jsonInput: EditText? = null
            showAirDialog(
                title = "匯入 JSON 外掛規則",
                message = "貼上符合 AetherLyric 規範之 JSON 來源定義（需包含 name、searchUrl、lyricsPath）",
                positiveText = "匯入安裝",
                negativeText = getString(R.string.ui_cancel),
                body = {
                    jsonInput = EditText(activity).apply {
                        hint = """{"name":"範例來源","searchUrl":"https://api.example.com/lrc?title={title}&artist={artist}","lyricsPath":"data.lrc"}"""
                        textSize = AirUiTokens.TextSize.Caption
                        setTextColor(colorTextStrong)
                        setHintTextColor(colorTextMuted)
                        minLines = 5
                        maxLines = 10
                        gravity = Gravity.TOP or Gravity.START
                        setPadding(dp(AirUiTokens.Space.ButtonH), dp(AirUiTokens.Space.ControlV), dp(AirUiTokens.Space.ButtonH), dp(AirUiTokens.Space.ControlV))
                        background = GradientDrawable().apply {
                            cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
                            setColor(colorSurfaceLight)
                            setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
                        }
                    }
                    addView(jsonInput)
                },
                onPositive = {
                    val rawJson = jsonInput?.text?.toString()?.trim().orEmpty()
                    val res = CustomJsonPluginStore.importPluginJson(activity, rawJson)
                    res.fold(
                        onSuccess = { list ->
                            renderPluginList()
                            Toast.makeText(activity, "已匯入 ${list.size} 個外掛", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { err ->
                            Toast.makeText(activity, "JSON 格式錯誤：${err.message}", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            )
        })

        // 外掛自動更新開關與更新間隔
        val autoUpdateRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(AirUiTokens.Space.Sm), 0, dp(AirUiTokens.Space.Xs))

            val textCol = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(0, 0, dp(AirUiTokens.Space.Md), 0)
                }
                addView(normalText(activity, "外掛自動更新"))
                addView(smallHint(activity, "定期向來源網址 (`updateUrl`) 檢查並更新外掛規則"))
            }
            val autoUpdateSwitch = SwitchCompat(activity).apply {
                isChecked = CustomJsonPluginStore.isAutoUpdateEnabled(activity)
                applyAirThemeTint(activity)
                setOnCheckedChangeListener { _, checked ->
                    CustomJsonPluginStore.setAutoUpdateEnabled(activity, checked)
                }
            }
            addView(textCol)
            addView(autoUpdateSwitch)
        }
        addView(autoUpdateRow)

        val intervalBtn = actionButton(
            activity,
            "自動更新間隔：每 ${CustomJsonPluginStore.getAutoUpdateIntervalHours(activity)} 小時"
        ) { }
        intervalBtn.setOnClickListener {
            val intervals = CustomJsonPluginStore.UPDATE_INTERVAL_OPTIONS_HOURS
            val labels = intervals.map { "每 $it 小時" }.toTypedArray()
            AlertDialog.Builder(activity)
                .setTitle("選擇外掛自動更新間隔")
                .setItems(labels) { _, which ->
                    val chosen = intervals[which]
                    CustomJsonPluginStore.setAutoUpdateIntervalHours(activity, chosen)
                    intervalBtn.text = "自動更新間隔：每 $chosen 小時"
                }
                .setNegativeButton(getString(R.string.ui_cancel), null)
                .show()
        }
        addView(intervalBtn)

        addView(actionButton(activity, "立即檢查所有外掛更新") {
            activity.activity.lifecycleScope.launch {
                val updatedCount = withContext(Dispatchers.IO) {
                    CustomJsonPluginStore.forceUpdateAllPluginsBlocking(activity)
                }
                renderPluginList()
                Toast.makeText(activity, "外掛檢查完成，共更新 $updatedCount 個來源", Toast.LENGTH_SHORT).show()
            }
        })

        addView(pluginsListContainer)
    }
}
