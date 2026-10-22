package com.andsi.airlyrics.ui.pages.settings

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.andsi.airlyrics.R
import com.andsi.airlyrics.design.tokens.AirUiTokens
import com.andsi.airlyrics.network.aether.AetherLyricApiClient
import com.andsi.airlyrics.network.aether.UserDeviceInfo
import com.andsi.airlyrics.settings.store.AetherLyricServerStore
import com.andsi.airlyrics.settings.store.AppSettingsStore
import com.andsi.airlyrics.settings.store.ServerProfile
import com.andsi.airlyrics.ui.components.*
import com.andsi.airlyrics.ui.model.MainUiHost
import com.andsi.airlyrics.ui.navigation.SettingsSubPage
import com.andsi.airlyrics.ui.theme.*
import kotlinx.coroutines.launch

/**
 * 設定子頁面 1：AetherLyric 伺服器 (ServerSettingsPage)。
 * 集中管理自架伺服器連線、多伺服器排序、登入認證、多裝置管理與公開伺服器探索。
 */
internal fun createServerSettingsPage(activity: MainUiHost): View = with(activity) {
    val container = pageContainer(activity, animateChanges = false)

    container.addView(
        settingsBackHeader(
            title = getString(R.string.ui_server_subpage_title),
            subtitle = getString(R.string.ui_server_subpage_desc)
        )
    )

    // 1. 公開伺服器探索入口卡片
    container.addView(
        settingsCategoryCard(
            title = "探索公開社群伺服器",
            subtitle = "瀏覽由社群維護的 AetherLyric 伺服器節點，檢視信任等級並快速加入",
            status = "探索伺服器",
            iconRes = R.drawable.ic_air_search
        ) {
            uiActions.openSettingsSubPage(SettingsSubPage.COMMUNITY_SERVERS)
        }
    )

    // 2. 伺服器連線、協同模式與認證主卡片
    container.addView(createAetherLyricServerCard(activity))

    // 3. 多伺服器設定檔清單與優先排序卡片
    container.addView(createServerProfilesListCard(activity))

    // 4. 多裝置管理卡片 (GET /api/v1/auth/devices & POST /api/v1/auth/revoke-others)
    container.addView(createDevicesManagementCard(activity))

    // 5. 伺服器支援能力與來源清單卡片
    container.addView(createServerCapabilitiesCard(activity))

    return scroll(activity, container, maxWidthDp = 1200)
}

/**
 * 多伺服器設定檔清單與排序卡片。
 * 支援顯示使用者暱稱、伺服器名稱、URL、健康狀態、向上/向下優先級排序與刪除。
 */
private fun createServerProfilesListCard(activity: MainUiHost): View = with(activity) {
    return card(activity) {
        addView(bigText(activity, "多伺服器設定檔與優先排序"))
        addView(smallHint(activity, "依序作為同步檢索與 AI 轉錄之優先備援節點，支援個別健康檢查與排序切換"))

        val listLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        addView(listLayout)

        fun renderProfiles() {
            listLayout.removeAllViews()
            val profiles = AetherLyricServerStore.getProfiles(activity)
            val activeProfile = AetherLyricServerStore.getActiveProfile(activity)

            profiles.forEachIndexed { index, profile ->
                val isActive = profile.id == activeProfile.id
                val itemBox = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(
                        dp(AirUiTokens.Space.CardH),
                        dp(AirUiTokens.Space.CardV),
                        dp(AirUiTokens.Space.CardH),
                        dp(AirUiTokens.Space.CardV)
                    )
                    background = GradientDrawable().apply {
                        cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
                        setColor(colorSurfaceLight)
                        setStroke(
                            dp(if (isActive) AirUiTokens.Stroke.Selected else AirUiTokens.Stroke.Hairline),
                            if (isActive) colorAccent else colorStroke
                        )
                    }
                    val lp = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, dp(AirUiTokens.Space.Sm), 0, 0)
                    }
                    layoutParams = lp
                }

                // 標題列：使用者自訂暱稱 / 伺服器名稱 / 狀態徽章
                val headerRow = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val titleText = TextView(activity).apply {
                    val displayName = profile.userAlias.ifBlank { profile.serverDisplayName.ifBlank { profile.name } }
                    val secondary = if (profile.serverDisplayName.isNotBlank() && profile.serverDisplayName != displayName) {
                        " (${profile.serverDisplayName})"
                    } else ""
                    text = "$displayName$secondary"
                    textSize = AirUiTokens.TextSize.Body
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(if (isActive) colorAccent else colorTextStrong)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                headerRow.addView(titleText)

                // 使用中標籤
                if (isActive) {
                    val activeBadge = TextView(activity).apply {
                        text = "使用中"
                        textSize = AirUiTokens.TextSize.Caption
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(colorAccentMint)
                        setPadding(dp(6), dp(2), dp(6), dp(2))
                        background = GradientDrawable().apply {
                            cornerRadius = dp(AirUiTokens.Radius.Sm).toFloat()
                            setColor(colorAccentMint and 0x22FFFFFF)
                        }
                    }
                    headerRow.addView(activeBadge)
                }

                // 健康狀態標籤
                val isOnline = profile.healthStatus.contains("在線") || profile.healthStatus.contains("200")
                val isFail = profile.healthStatus.contains("失敗") || profile.healthStatus.contains("離線")
                val healthBadgeColor = if (isOnline) colorAccentMint else if (isFail) colorDanger else colorTextMuted
                val healthBadge = TextView(activity).apply {
                    text = profile.healthStatus.ifBlank { "未檢測" }
                    textSize = AirUiTokens.TextSize.Caption
                    setTextColor(healthBadgeColor)
                    setPadding(dp(AirUiTokens.Space.Sm), 0, 0, 0)
                }
                headerRow.addView(healthBadge)

                itemBox.addView(headerRow)

                // 第二列：URL 與連線設定摘要
                val urlText = TextView(activity).apply {
                    text = profile.url.ifBlank { "尚未設定伺服器位址" }
                    textSize = AirUiTokens.TextSize.Caption
                    setTextColor(colorTextMuted)
                    setPadding(0, dp(AirUiTokens.Space.Xxs), 0, dp(AirUiTokens.Space.Sm))
                }
                itemBox.addView(urlText)

                // 操作按鈕列：設為使用中、向上移動、向下移動、刪除
                val actionRow = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                if (!isActive) {
                    val setActiveBtn = TextView(activity).apply {
                        text = "切換使用"
                        textSize = AirUiTokens.TextSize.Caption
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(colorAccent)
                        setPadding(dp(AirUiTokens.Space.Sm), dp(4), dp(AirUiTokens.Space.Sm), dp(4))
                        background = GradientDrawable().apply {
                            cornerRadius = dp(AirUiTokens.Radius.Sm).toFloat()
                            setColor(colorSurfaceLight)
                            setStroke(dp(1), colorAccent)
                        }
                        isClickable = true
                        isFocusable = true
                        enableSoftPressFeedback(AirUiTokens.Motion.DefaultPressScale)
                        setOnClickListener {
                            AetherLyricServerStore.setActiveProfileId(activity, profile.id)
                            renderProfiles()
                            Toast.makeText(activity, "已切換至「${profile.name}」", Toast.LENGTH_SHORT).show()
                        }
                    }
                    actionRow.addView(setActiveBtn)
                }

                val spacer = View(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                }
                actionRow.addView(spacer)

                // 向上排序按鈕
                val upBtn = airIconView(R.drawable.ic_air_arrow_up, if (index > 0) colorTextStrong else colorStroke).apply {
                    layoutParams = ViewGroup.LayoutParams(dp(AirUiTokens.Layout.IconSize), dp(AirUiTokens.Layout.IconSize))
                    if (index > 0) {
                        isClickable = true
                        isFocusable = true
                        enableSoftPressFeedback(AirUiTokens.Motion.DefaultPressScale)
                        setOnClickListener {
                            AetherLyricServerStore.moveProfileUp(activity, profile.id)
                            renderProfiles()
                        }
                    }
                }
                actionRow.addView(upBtn)

                // 向下排序按鈕
                val downBtn = airIconView(R.drawable.ic_air_arrow_down, if (index < profiles.size - 1) colorTextStrong else colorStroke).apply {
                    layoutParams = ViewGroup.LayoutParams(dp(AirUiTokens.Layout.IconSize), dp(AirUiTokens.Layout.IconSize))
                    if (index < profiles.size - 1) {
                        isClickable = true
                        isFocusable = true
                        enableSoftPressFeedback(AirUiTokens.Motion.DefaultPressScale)
                        setOnClickListener {
                            AetherLyricServerStore.moveProfileDown(activity, profile.id)
                            renderProfiles()
                        }
                    }
                }
                actionRow.addView(downBtn)

                // 刪除按鈕
                if (profiles.size > 1) {
                    val deleteBtn = airIconView(R.drawable.ic_air_delete, colorDanger).apply {
                        layoutParams = ViewGroup.LayoutParams(dp(AirUiTokens.Layout.IconSize), dp(AirUiTokens.Layout.IconSize))
                        isClickable = true
                        isFocusable = true
                        enableSoftPressFeedback(AirUiTokens.Motion.DefaultPressScale)
                        setOnClickListener {
                            AetherLyricServerStore.deleteProfile(activity, profile.id)
                            renderProfiles()
                            Toast.makeText(activity, "已刪除設定檔：${profile.name}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    actionRow.addView(deleteBtn)
                }

                itemBox.addView(actionRow)
                listLayout.addView(itemBox)
            }
        }
        renderProfiles()

        // 新增伺服器設定檔按鈕
        val addProfileBtn = actionButton(activity, "新增自訂伺服器設定檔") {
            var aliasInput: EditText? = null
            var nameInput: EditText? = null
            var urlInput: EditText? = null

            showAirDialog(
                title = "新增伺服器設定檔",
                message = "自訂伺服器暱稱與連線位址",
                positiveText = "新增並儲存",
                negativeText = getString(R.string.ui_cancel),
                body = {
                    addView(smallHint(activity, "使用者暱稱 (例如：家庭伺服器 / 辦公室節點)"))
                    aliasInput = EditText(activity).apply {
                        hint = "輸入伺服器暱稱"
                        textSize = AirUiTokens.TextSize.BodySmall
                        setTextColor(colorTextStrong)
                        setHintTextColor(colorTextMuted)
                        setPadding(dp(AirUiTokens.Space.ButtonH), dp(AirUiTokens.Space.ControlV), dp(AirUiTokens.Space.ButtonH), dp(AirUiTokens.Space.ControlV))
                        background = GradientDrawable().apply {
                            cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
                            setColor(colorSurfaceLight)
                            setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
                        }
                    }
                    addView(aliasInput)

                    addView(smallHint(activity, "伺服器位址 (URL)").apply {
                        setPadding(0, dp(AirUiTokens.Space.Sm), 0, 0)
                    })
                    urlInput = EditText(activity).apply {
                        hint = "http://192.168.1.100:8000"
                        textSize = AirUiTokens.TextSize.BodySmall
                        setTextColor(colorTextStrong)
                        setHintTextColor(colorTextMuted)
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
                    val alias = aliasInput?.text?.toString()?.trim().orEmpty().ifBlank { "自訂節點" }
                    val u = urlInput?.text?.toString()?.trim().orEmpty()
                    if (u.isNotBlank()) {
                        AetherLyricServerStore.addProfile(activity, userAlias = alias, url = u)
                        renderProfiles()
                        Toast.makeText(activity, "已新增伺服器設定檔：$alias", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(activity, "伺服器位址不得為空", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
        (addProfileBtn.layoutParams as? LinearLayout.LayoutParams)?.setMargins(0, dp(AirUiTokens.Space.CardV), 0, 0)
        addView(addProfileBtn)
    }
}

/**
 * 多裝置授權管理卡片。
 * 呼叫 GET /api/v1/auth/devices 查詢並展示已授權之裝置，並支援 POST /api/v1/auth/revoke-others 遠端強制登出。
 */
private fun createDevicesManagementCard(activity: MainUiHost): View = with(activity) {
    return card(activity) {
        addView(bigText(activity, "已登入裝置管理"))
        addView(smallHint(activity, "查看目前使用此帳號登入授權的所有裝置清單，支援遠端撤銷其他裝置存取權限"))

        val contentLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(AirUiTokens.Space.Sm), 0, 0)
            }
        }
        addView(contentLayout)

        val statusHint = TextView(activity).apply {
            textSize = AirUiTokens.TextSize.BodySmall
            setTextColor(colorTextMuted)
            text = "點選下方按鈕查詢目前已授權之登入裝置"
        }
        contentLayout.addView(statusHint)

        fun loadDevices() {
            val serverUrl = AetherLyricServerStore.getServerUrl(activity)
            if (serverUrl.isBlank()) {
                statusHint.text = "尚未設定伺服器位址，無法查詢裝置清單"
                return
            }

            contentLayout.removeAllViews()
            val progress = ProgressBar(activity).apply {
                isIndeterminate = true
            }
            contentLayout.addView(progress)

            activity.activity.lifecycleScope.launch {
                val result = AetherLyricApiClient.fetchDevices(serverUrl, activity)
                contentLayout.removeAllViews()

                result.fold(
                    onSuccess = { devices ->
                        if (devices.isEmpty()) {
                            contentLayout.addView(TextView(activity).apply {
                                text = "目前無其他已記錄之登入裝置"
                                textSize = AirUiTokens.TextSize.BodySmall
                                setTextColor(colorTextMuted)
                            })
                            return@launch
                        }

                        devices.forEach { dev ->
                            val devBox = LinearLayout(activity).apply {
                                orientation = LinearLayout.VERTICAL
                                setPadding(
                                    dp(AirUiTokens.Space.CardH),
                                    dp(AirUiTokens.Space.Sm),
                                    dp(AirUiTokens.Space.CardH),
                                    dp(AirUiTokens.Space.Sm)
                                )
                                background = GradientDrawable().apply {
                                    cornerRadius = dp(AirUiTokens.Radius.Sm).toFloat()
                                    setColor(colorSurfaceLight)
                                    setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
                                }
                                val lp = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    setMargins(0, dp(AirUiTokens.Space.Xs), 0, 0)
                                }
                                layoutParams = lp
                            }

                            val row = LinearLayout(activity).apply {
                                orientation = LinearLayout.HORIZONTAL
                                gravity = Gravity.CENTER_VERTICAL
                            }
                            val nameView = TextView(activity).apply {
                                val dName = dev.deviceName.ifBlank { dev.deviceModel.ifBlank { "裝置 (${dev.id.take(8)})" } }
                                text = dName
                                textSize = AirUiTokens.TextSize.BodySmall
                                typeface = Typeface.DEFAULT_BOLD
                                setTextColor(colorTextStrong)
                                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                            }
                            row.addView(nameView)

                            if (dev.isCurrentDevice) {
                                val currentBadge = TextView(activity).apply {
                                    text = "本裝置"
                                    textSize = AirUiTokens.TextSize.Caption
                                    typeface = Typeface.DEFAULT_BOLD
                                    setTextColor(colorAccentMint)
                                    setPadding(dp(6), dp(2), dp(6), dp(2))
                                    background = GradientDrawable().apply {
                                        cornerRadius = dp(AirUiTokens.Radius.Sm).toFloat()
                                        setColor(colorAccentMint and 0x22FFFFFF)
                                    }
                                }
                                row.addView(currentBadge)
                            }
                            devBox.addView(row)

                            val detailView = TextView(activity).apply {
                                text = "型號: ${dev.deviceModel} · IP: ${dev.ipAddress} · 最後上線: ${dev.lastLoginTime}"
                                textSize = AirUiTokens.TextSize.Caption
                                setTextColor(colorTextMuted)
                                setPadding(0, dp(2), 0, 0)
                            }
                            devBox.addView(detailView)

                            contentLayout.addView(devBox)
                        }
                    },
                    onFailure = { err ->
                        contentLayout.addView(TextView(activity).apply {
                            text = "查詢裝置失敗：${err.message ?: "未登入或伺服器未啟用裝置端點"}"
                            textSize = AirUiTokens.TextSize.BodySmall
                            setTextColor(colorDanger)
                        })
                    }
                )
            }
        }

        val btnRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(AirUiTokens.Space.CardV), 0, 0)
            }
            layoutParams = lp
        }

        val refreshBtn = actionButton(activity, "查詢裝置清單") {
            loadDevices()
        }.apply {
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, dp(AirUiTokens.Space.Sm), 0)
            }
            layoutParams = lp
        }

        val revokeBtn = actionButton(activity, "登出其他裝置") {
            val serverUrl = AetherLyricServerStore.getServerUrl(activity)
            if (serverUrl.isBlank()) {
                Toast.makeText(activity, "尚未設定伺服器位址", Toast.LENGTH_SHORT).show()
                return@actionButton
            }

            showAirDialog(
                title = "撤銷其他裝置授權",
                message = "確定要遠端強制登出此帳號在其他所有裝置上的連線？本裝置將不受影響。",
                positiveText = "確認強制登出",
                negativeText = getString(R.string.ui_cancel),
                onPositive = {
                    activity.activity.lifecycleScope.launch {
                        val result = AetherLyricApiClient.revokeOtherDevices(serverUrl, activity)
                        result.fold(
                            onSuccess = { count ->
                                Toast.makeText(activity, "已成功登出 $count 台其他裝置", Toast.LENGTH_SHORT).show()
                                loadDevices()
                            },
                            onFailure = { err ->
                                Toast.makeText(activity, "登出失敗：${err.message}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            )
        }.apply {
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
        }

        btnRow.addView(refreshBtn)
        btnRow.addView(revokeBtn)
        addView(btnRow)
    }
}

/**
 * 伺服器支援能力與來源清單卡片。
 */
private fun createServerCapabilitiesCard(activity: MainUiHost): View = with(activity) {
    return card(activity) {
        addView(bigText(activity, "伺服器功能與能力支援"))
        addView(smallHint(activity, "AetherLyric-server 核心支援之 AI 轉錄引擎、校正模型與歌詞檢索來源清單"))

        val capabilities = listOf(
            "語音轉錄引擎" to "Faster-Whisper / Groq / OpenAI Compatible",
            "歌詞時間軸校準" to "difflib.SequenceMatcher 錨點對齊 (POST /api/v1/align)",
            "AI 錯字與格式校正" to "LiteLLM (支援 GPT / Claude / DeepSeek / 通義千問)",
            "整合線上來源" to "網易雲、QQ 音樂、酷狗音樂、LRCLIB、Musixmatch",
            "多格式匯出支援" to "LRC (標準同步)、SRT、ASS (特效字幕)、TXT"
        )

        capabilities.forEach { (title, desc) ->
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    dp(AirUiTokens.Space.CardH),
                    dp(AirUiTokens.Space.Sm),
                    dp(AirUiTokens.Space.CardH),
                    dp(AirUiTokens.Space.Sm)
                )
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(TextView(activity).apply {
                        text = title
                        textSize = AirUiTokens.TextSize.BodySmall
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(colorTextStrong)
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    addView(TextView(activity).apply {
                        text = "支援"
                        textSize = AirUiTokens.TextSize.Caption
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(colorAccentMint)
                    })
                }
                addView(row)
                if (!AppSettingsStore.isCompactMode(activity)) {
                    addView(TextView(activity).apply {
                        text = desc
                        textSize = AirUiTokens.TextSize.Caption
                        setTextColor(colorTextMuted)
                        setPadding(0, dp(AirUiTokens.Space.Xxs), 0, 0)
                    })
                }
            })
        }
    }
}
