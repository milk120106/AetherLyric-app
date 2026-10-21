package com.andsi.airlyrics.ui.pages.settings

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
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
import com.andsi.airlyrics.network.aether.AetherLyricApiClient
import com.andsi.airlyrics.settings.store.AetherLyricServerStore
import com.andsi.airlyrics.settings.store.AppSettingsStore
import com.andsi.airlyrics.settings.store.ServerEngineMode
import com.andsi.airlyrics.ui.components.*
import com.andsi.airlyrics.ui.model.MainUiHost
import com.andsi.airlyrics.ui.theme.*
import kotlinx.coroutines.launch

/**
 * AetherLyric 伺服器設定卡片 (AetherLyricServerCard)。
 * 支援：
 * 1. 多伺服器設定檔管理與快速切換
 * 2. 引擎運作模式切換（雙引擎模式 vs 僅伺服器模式）
 * 3. WebSocket 即時任務進度推播開關（含自動 HTTP 輪詢降級）
 * 4. 多層安全驗證（APP_ACCESS_TOKEN、PIN 碼、問答驗證答案，Android Keystore 加密儲存）
 * 5. HTTP(S) Proxy 代理連線設定
 */
internal fun createAetherLyricServerCard(activity: MainUiHost): View = with(activity) {
    return card(activity) {
        // 1. 卡片標題與描述
        addView(bigText(activity, getString(R.string.ui_aetherlyric_server_title)))

        val descView = TextView(activity).apply {
            text = getString(R.string.ui_aetherlyric_server_desc)
            textSize = AirUiTokens.TextSize.BodySmall
            setTextColor(colorTextMuted)
            setPadding(0, dp(AirUiTokens.Space.Sm), 0, 0)
            if (AppSettingsStore.isCompactMode(activity)) {
                visibility = View.GONE
            }
        }
        addView(descView)

        var activeProfile = AetherLyricServerStore.getActiveProfile(activity)

        // 2. 伺服器設定檔快速切換列
        val profileLabel = TextView(activity).apply {
            text = getString(R.string.ui_server_profiles)
            textSize = AirUiTokens.TextSize.BodySmall
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextStrong)
            setPadding(0, dp(AirUiTokens.Space.CardV), 0, dp(AirUiTokens.Space.Sm))
        }
        addView(profileLabel)

        val profileSelectorBox = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
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
            )
            isClickable = true
            isFocusable = true
            enableSoftPressFeedback(AirUiTokens.Motion.DefaultPressScale)
        }

        val profileInfoLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val profileNameView = TextView(activity).apply {
            text = activeProfile.name
            textSize = AirUiTokens.TextSize.Button
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextStrong)
        }
        val profileUrlSubView = TextView(activity).apply {
            text = activeProfile.url.ifBlank { "尚未設定伺服器位址" }
            textSize = AirUiTokens.TextSize.Caption
            setTextColor(colorTextMuted)
        }
        profileInfoLayout.addView(profileNameView)
        profileInfoLayout.addView(profileUrlSubView)
        profileSelectorBox.addView(profileInfoLayout)

        val switchIcon = airIconView(R.drawable.ic_air_sync_alt, colorAccent).apply {
            layoutParams = ViewGroup.LayoutParams(dp(AirUiTokens.Layout.IconSize), dp(AirUiTokens.Layout.IconSize))
        }
        profileSelectorBox.addView(switchIcon)
        addView(profileSelectorBox)

        // 3. 伺服器位址輸入框
        val urlLabel = TextView(activity).apply {
            text = getString(R.string.ui_server_url_label)
            textSize = AirUiTokens.TextSize.BodySmall
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextStrong)
            setPadding(0, dp(AirUiTokens.Space.CardV), 0, dp(AirUiTokens.Space.Sm))
        }
        addView(urlLabel)

        val urlInput = EditText(activity).apply {
            hint = getString(R.string.ui_server_url_hint)
            setText(activeProfile.url)
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
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val newUrl = s?.toString()?.trim().orEmpty()
                    activeProfile = activeProfile.copy(url = newUrl)
                    AetherLyricServerStore.updateProfile(activity, activeProfile)
                    profileUrlSubView.text = newUrl.ifBlank { "尚未設定伺服器位址" }
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        addView(urlInput)

        // 4. 多層安全驗證與 HTTP(S) Proxy 設定按鈕列
        val securityStatusSummary = TextView(activity).apply {
            textSize = AirUiTokens.TextSize.Caption
            setTextColor(colorTextMuted)
            setPadding(0, dp(AirUiTokens.Space.Sm), 0, dp(AirUiTokens.Space.Xs))
        }
        fun refreshSecuritySummary() {
            activeProfile = AetherLyricServerStore.getActiveProfile(activity)
            val hasAuth = AetherLyricServerStore.getAppAccessToken(activity).isNotBlank() ||
                AetherLyricServerStore.getAuthPin(activity).isNotBlank() ||
                AetherLyricServerStore.getAuthChallengeAnswer(activity).isNotBlank()
            val proxyEnabled = AetherLyricServerStore.isProxyEnabled(activity)
            val proxyHost = AetherLyricServerStore.getProxyHost(activity)
            val proxyPort = AetherLyricServerStore.getProxyPort(activity)
            val proxyStr = if (proxyEnabled && proxyHost.isNotBlank()) {
                "Proxy: $proxyHost:$proxyPort"
            } else {
                "Proxy: 未啟用"
            }
            val authStr = if (hasAuth) "安全驗證: 已設定 (Keystore 加密)" else "安全驗證: 免憑證公開連線"
            securityStatusSummary.text = "$authStr · $proxyStr"
        }
        refreshSecuritySummary()
        addView(securityStatusSummary)

        val authConfigBtn = actionButton(activity, "多層安全驗證設定（Token / PIN 碼 / 問答驗證）") {
            activeProfile = AetherLyricServerStore.getActiveProfile(activity)
            var tokenInput: EditText? = null
            var pinInput: EditText? = null
            var challengeInput: EditText? = null
            showAirDialog(
                title = "伺服器多層安全驗證",
                message = "對應伺服器端啟用的安全防護機制，所有憑證均透過 Android Keystore 加密儲存於本機",
                positiveText = "儲存憑證",
                negativeText = getString(R.string.ui_cancel),
                body = {
                    addView(smallHint(activity, "1. 存取權杖 (APP_ACCESS_TOKEN / Bearer Token)"))
                    tokenInput = EditText(activity).apply {
                        hint = "選填：輸入伺服器 APP_ACCESS_TOKEN"
                        setText(AetherLyricServerStore.getAppAccessToken(activity))
                        textSize = AirUiTokens.TextSize.BodySmall
                        setTextColor(colorTextStrong)
                        setHintTextColor(colorTextMuted)
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                        isSingleLine = true
                        setPadding(dp(AirUiTokens.Space.ButtonH), dp(AirUiTokens.Space.ControlV), dp(AirUiTokens.Space.ButtonH), dp(AirUiTokens.Space.ControlV))
                        background = GradientDrawable().apply {
                            cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
                            setColor(colorSurfaceLight)
                            setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
                        }
                    }
                    addView(tokenInput)

                    addView(smallHint(activity, "2. 存取 PIN 碼 (X-Auth-Pin)").apply {
                        setPadding(0, dp(AirUiTokens.Space.Sm), 0, 0)
                    })
                    pinInput = EditText(activity).apply {
                        hint = "選填：輸入 4~8 位數 PIN 碼"
                        setText(AetherLyricServerStore.getAuthPin(activity))
                        textSize = AirUiTokens.TextSize.BodySmall
                        setTextColor(colorTextStrong)
                        setHintTextColor(colorTextMuted)
                        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                        isSingleLine = true
                        setPadding(dp(AirUiTokens.Space.ButtonH), dp(AirUiTokens.Space.ControlV), dp(AirUiTokens.Space.ButtonH), dp(AirUiTokens.Space.ControlV))
                        background = GradientDrawable().apply {
                            cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
                            setColor(colorSurfaceLight)
                            setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
                        }
                    }
                    addView(pinInput)

                    addView(smallHint(activity, "3. 問答驗證答案 (X-Auth-Answer)").apply {
                        setPadding(0, dp(AirUiTokens.Space.Sm), 0, 0)
                    })
                    challengeInput = EditText(activity).apply {
                        hint = "選填：輸入伺服器設定的問答驗證答案"
                        setText(AetherLyricServerStore.getAuthChallengeAnswer(activity))
                        textSize = AirUiTokens.TextSize.BodySmall
                        setTextColor(colorTextStrong)
                        setHintTextColor(colorTextMuted)
                        inputType = InputType.TYPE_CLASS_TEXT
                        isSingleLine = true
                        setPadding(dp(AirUiTokens.Space.ButtonH), dp(AirUiTokens.Space.ControlV), dp(AirUiTokens.Space.ButtonH), dp(AirUiTokens.Space.ControlV))
                        background = GradientDrawable().apply {
                            cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
                            setColor(colorSurfaceLight)
                            setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
                        }
                    }
                    addView(challengeInput)
                },
                onPositive = {
                    AetherLyricServerStore.setAppAccessToken(activity, tokenInput?.text?.toString().orEmpty())
                    AetherLyricServerStore.setAuthPin(activity, pinInput?.text?.toString().orEmpty())
                    AetherLyricServerStore.setAuthChallengeAnswer(activity, challengeInput?.text?.toString().orEmpty())
                    refreshSecuritySummary()
                    Toast.makeText(activity, "安全驗證憑證已加密儲存", Toast.LENGTH_SHORT).show()
                }
            )
        }
        addView(authConfigBtn)

        val proxyConfigBtn = actionButton(activity, "HTTP(S) Proxy 代理連線設定") {
            activeProfile = AetherLyricServerStore.getActiveProfile(activity)
            var proxySwitch: SwitchCompat? = null
            var hostInput: EditText? = null
            var portInput: EditText? = null
            var userInput: EditText? = null
            var passInput: EditText? = null
            showAirDialog(
                title = "HTTP(S) Proxy 代理設定",
                message = "設定連線至自架伺服器時使用的 HTTP/HTTPS 代理伺服器與驗證資訊",
                positiveText = "儲存 Proxy 設定",
                negativeText = getString(R.string.ui_cancel),
                body = {
                    val switchRow = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, 0, 0, dp(AirUiTokens.Space.Sm))
                        addView(normalText(activity, "啟用 Proxy 代理連線").apply {
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        })
                        proxySwitch = SwitchCompat(activity).apply {
                            isChecked = AetherLyricServerStore.isProxyEnabled(activity)
                            applyAirThemeTint(activity)
                        }
                        addView(proxySwitch)
                    }
                    addView(switchRow)

                    addView(smallHint(activity, "Proxy 主機位址 (Host)"))
                    hostInput = EditText(activity).apply {
                        hint = "例如：192.168.1.1 或 proxy.example.com"
                        setText(AetherLyricServerStore.getProxyHost(activity))
                        textSize = AirUiTokens.TextSize.BodySmall
                        setTextColor(colorTextStrong)
                        setHintTextColor(colorTextMuted)
                        isSingleLine = true
                        setPadding(dp(AirUiTokens.Space.ButtonH), dp(AirUiTokens.Space.ControlV), dp(AirUiTokens.Space.ButtonH), dp(AirUiTokens.Space.ControlV))
                        background = GradientDrawable().apply {
                            cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
                            setColor(colorSurfaceLight)
                            setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
                        }
                    }
                    addView(hostInput)

                    addView(smallHint(activity, "Proxy 連接埠 (Port)").apply {
                        setPadding(0, dp(AirUiTokens.Space.Sm), 0, 0)
                    })
                    portInput = EditText(activity).apply {
                        hint = "預設：8080"
                        setText(AetherLyricServerStore.getProxyPort(activity).toString())
                        textSize = AirUiTokens.TextSize.BodySmall
                        setTextColor(colorTextStrong)
                        setHintTextColor(colorTextMuted)
                        inputType = InputType.TYPE_CLASS_NUMBER
                        isSingleLine = true
                        setPadding(dp(AirUiTokens.Space.ButtonH), dp(AirUiTokens.Space.ControlV), dp(AirUiTokens.Space.ButtonH), dp(AirUiTokens.Space.ControlV))
                        background = GradientDrawable().apply {
                            cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
                            setColor(colorSurfaceLight)
                            setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
                        }
                    }
                    addView(portInput)

                    addView(smallHint(activity, "Proxy 驗證帳號 (選填)").apply {
                        setPadding(0, dp(AirUiTokens.Space.Sm), 0, 0)
                    })
                    userInput = EditText(activity).apply {
                        hint = "選填：Proxy 使用者名稱"
                        setText(AetherLyricServerStore.getProxyUsername(activity))
                        textSize = AirUiTokens.TextSize.BodySmall
                        setTextColor(colorTextStrong)
                        setHintTextColor(colorTextMuted)
                        isSingleLine = true
                        setPadding(dp(AirUiTokens.Space.ButtonH), dp(AirUiTokens.Space.ControlV), dp(AirUiTokens.Space.ButtonH), dp(AirUiTokens.Space.ControlV))
                        background = GradientDrawable().apply {
                            cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
                            setColor(colorSurfaceLight)
                            setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
                        }
                    }
                    addView(userInput)

                    addView(smallHint(activity, "Proxy 驗證密碼 (選填，Keystore 加密)").apply {
                        setPadding(0, dp(AirUiTokens.Space.Sm), 0, 0)
                    })
                    passInput = EditText(activity).apply {
                        hint = "選填：Proxy 密碼"
                        setText(AetherLyricServerStore.getProxyPassword(activity))
                        textSize = AirUiTokens.TextSize.BodySmall
                        setTextColor(colorTextStrong)
                        setHintTextColor(colorTextMuted)
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                        isSingleLine = true
                        setPadding(dp(AirUiTokens.Space.ButtonH), dp(AirUiTokens.Space.ControlV), dp(AirUiTokens.Space.ButtonH), dp(AirUiTokens.Space.ControlV))
                        background = GradientDrawable().apply {
                            cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
                            setColor(colorSurfaceLight)
                            setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
                        }
                    }
                    addView(passInput)
                },
                onPositive = {
                    val portNum = portInput?.text?.toString()?.trim()?.toIntOrNull() ?: 8080
                    AetherLyricServerStore.setProxyEnabled(activity, proxySwitch?.isChecked == true)
                    AetherLyricServerStore.setProxyHost(activity, hostInput?.text?.toString().orEmpty())
                    AetherLyricServerStore.setProxyPort(activity, portNum)
                    AetherLyricServerStore.setProxyUsername(activity, userInput?.text?.toString().orEmpty())
                    AetherLyricServerStore.setProxyPassword(activity, passInput?.text?.toString().orEmpty())
                    refreshSecuritySummary()
                    Toast.makeText(activity, "Proxy 設定已儲存", Toast.LENGTH_SHORT).show()
                }
            )
        }
        addView(proxyConfigBtn)

        // 5. 測試連線按鈕
        val testButton = actionButton(activity, getString(R.string.ui_test_connection)) { }
        (testButton.layoutParams as? LinearLayout.LayoutParams)?.setMargins(
            0,
            dp(AirUiTokens.Space.CardV),
            0,
            0
        )
        addView(testButton)

        // 6. 伺服器公開狀態面板
        val publicStatusCard = LinearLayout(activity).apply {
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
                setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(AirUiTokens.Space.CardV), 0, 0)
            }
        }

        val statusHeaderRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val isVerifiedInitially = AetherLyricServerStore.isConnectionVerified(activity)
        val statusDot = View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply {
                setMargins(0, 0, dp(AirUiTokens.Space.Sm), 0)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (isVerifiedInitially) colorAccentMint else colorTextMuted)
            }
        }
        val statusText = TextView(activity).apply {
            textSize = AirUiTokens.TextSize.BodySmall
            typeface = Typeface.DEFAULT_BOLD
            text = if (isVerifiedInitially) "伺服器在線 (已驗證)" else "尚未連線或未驗證"
            setTextColor(if (isVerifiedInitially) colorAccentMint else colorTextMuted)
        }
        statusHeaderRow.addView(statusDot)
        statusHeaderRow.addView(statusText)
        publicStatusCard.addView(statusHeaderRow)

        val publicDetailText = TextView(activity).apply {
            textSize = AirUiTokens.TextSize.Caption
            setTextColor(colorTextMuted)
            setPadding(0, dp(AirUiTokens.Space.Xs), 0, 0)
            text = AetherLyricServerStore.getLastStatusMessage(activity).ifBlank { "點選「測試連線」以取得最新模型與佇列資訊" }
        }
        publicStatusCard.addView(publicDetailText)
        addView(publicStatusCard)

        // 7. 分隔線
        val divider = View(activity).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            alpha = 0.6f
            setBackgroundColor(colorStroke)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(AirUiTokens.Stroke.Hairline)
            ).apply {
                setMargins(0, dp(AirUiTokens.Space.CardV), 0, dp(AirUiTokens.Space.CardV))
            }
        }
        addView(divider)

        // 8. 啟用伺服器連線開關
        val isEnabledInitially = AetherLyricServerStore.isServerEnabled(activity)
        val engineModeDescription = TextView(activity).apply {
            textSize = AirUiTokens.TextSize.Caption
            setTextColor(colorTextMuted)
            setPadding(0, dp(AirUiTokens.Space.Xs), 0, 0)
            text = if (isEnabledInitially) {
                AetherLyricServerStore.getEngineMode(activity).description
            } else {
                getString(R.string.ui_server_engine_status_off)
            }
        }

        val serverSwitch = SwitchCompat(activity).apply {
            isChecked = isEnabledInitially
            applyAirThemeTint(activity)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        var isInternalSwitchUpdating = false

        val switchRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            enableSoftPressFeedback(AirUiTokens.Motion.DefaultPressScale)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            val textGroup = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    setMargins(0, 0, dp(AirUiTokens.Space.ButtonH), 0)
                }

                addView(TextView(activity).apply {
                    text = getString(R.string.ui_enable_server_engine)
                    textSize = AirUiTokens.TextSize.Button
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(colorTextStrong)
                })
                addView(engineModeDescription)
            }
            addView(textGroup)
            addView(serverSwitch)
        }
        addView(switchRow)

        // 9. 伺服器運作模式選擇（雙引擎模式 vs 僅伺服器模式）
        addView(TextView(activity).apply {
            text = "伺服器協同運作模式"
            textSize = AirUiTokens.TextSize.BodySmall
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextStrong)
            setPadding(0, dp(AirUiTokens.Space.CardV), 0, dp(AirUiTokens.Space.Xs))
        })

        var currentEngineMode = AetherLyricServerStore.getEngineMode(activity)
        val modeContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val modeOptionViews = mutableMapOf<ServerEngineMode, LinearLayout>()

        fun refreshEngineModeOptions() {
            modeOptionViews.forEach { (mode, box) ->
                val selected = mode == currentEngineMode
                box.background = GradientDrawable().apply {
                    cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
                    setColor(colorSurfaceLight)
                    setStroke(
                        dp(if (selected) AirUiTokens.Stroke.Selected else AirUiTokens.Stroke.Hairline),
                        if (selected) colorAccent else colorStroke
                    )
                }
                (box.getChildAt(0) as? TextView)?.setTextColor(if (selected) colorAccent else colorTextStrong)
            }
            if (serverSwitch.isChecked) {
                engineModeDescription.text = currentEngineMode.description
            }
        }

        ServerEngineMode.entries.forEach { mode ->
            val optionBox = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    dp(AirUiTokens.Space.CardH),
                    dp(AirUiTokens.Space.Sm),
                    dp(AirUiTokens.Space.CardH),
                    dp(AirUiTokens.Space.Sm)
                )
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, dp(AirUiTokens.Space.Sm))
                }
                isClickable = true
                isFocusable = true
                enableSoftPressFeedback(AirUiTokens.Motion.DefaultPressScale)

                addView(TextView(activity).apply {
                    text = mode.displayTitle
                    textSize = AirUiTokens.TextSize.BodySmall
                    typeface = Typeface.DEFAULT_BOLD
                })
                addView(TextView(activity).apply {
                    text = mode.description
                    textSize = AirUiTokens.TextSize.Caption
                    setTextColor(colorTextMuted)
                    setPadding(0, dp(2), 0, 0)
                })

                setOnClickListener {
                    currentEngineMode = mode
                    AetherLyricServerStore.setEngineMode(activity, mode)
                    refreshEngineModeOptions()
                }
            }
            modeOptionViews[mode] = optionBox
            modeContainer.addView(optionBox)
        }
        refreshEngineModeOptions()
        addView(modeContainer)

        // 10. 使用 WebSocket 傳輸進度開關
        val wsRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(AirUiTokens.Space.Sm), 0, 0)

            val wsTextCol = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(0, 0, dp(AirUiTokens.Space.Md), 0)
                }
                addView(normalText(activity, "使用 WebSocket 傳輸進度"))
                addView(smallHint(activity, "透過 /ws/tasks/{taskId} 即時推播 AI 轉錄與下載進度；若伺服器或網路不支援 WebSocket 將自動降級為 HTTP 輪詢"))
            }
            val wsSwitch = SwitchCompat(activity).apply {
                isChecked = AetherLyricServerStore.isWebSocketProgressEnabled(activity)
                applyAirThemeTint(activity)
                setOnCheckedChangeListener { _, checked ->
                    AetherLyricServerStore.setWebSocketProgressEnabled(activity, checked)
                }
            }
            addView(wsTextCol)
            addView(wsSwitch)
        }
        addView(wsRow)

        serverSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isInternalSwitchUpdating) return@setOnCheckedChangeListener

            if (isChecked) {
                val url = AetherLyricServerStore.getServerUrl(activity)
                val verified = AetherLyricServerStore.isConnectionVerified(activity)
                if (url.isEmpty() || !verified) {
                    isInternalSwitchUpdating = true
                    serverSwitch.isChecked = false
                    isInternalSwitchUpdating = false
                    Toast.makeText(
                        activity,
                        getString(R.string.ui_server_must_verify_first),
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnCheckedChangeListener
                }
                AetherLyricServerStore.setServerEnabled(activity, true)
                engineModeDescription.text = currentEngineMode.description
            } else {
                AetherLyricServerStore.setServerEnabled(activity, false)
                engineModeDescription.text = getString(R.string.ui_server_engine_status_off)
            }
        }

        switchRow.setOnClickListener {
            serverSwitch.isChecked = !serverSwitch.isChecked
        }

        // 連線測試函式
        fun performHealthCheck(targetUrl: String, onFinished: (() -> Unit)? = null) {
            val url = targetUrl.trim()
            if (url.isEmpty()) {
                statusText.text = "尚未輸入有效位址"
                statusText.setTextColor(colorTextMuted)
                (statusDot.background as? GradientDrawable)?.setColor(colorTextMuted)
                publicDetailText.text = "請輸入伺服器位址後再進行測試"
                onFinished?.invoke()
                return
            }

            statusText.text = getString(R.string.ui_testing_connection)
            statusText.setTextColor(colorAccent)
            (statusDot.background as? GradientDrawable)?.setColor(colorAccent)
            publicDetailText.text = "正在連線伺服器並查詢服務狀態..."

            activity.activity.lifecycleScope.launch {
                val result = AetherLyricApiClient.testHealth(serverUrl = url, context = activity)
                onFinished?.invoke()

                result.fold(
                    onSuccess = { health ->
                        AetherLyricServerStore.setConnectionVerified(activity, true)
                        (statusDot.background as? GradientDrawable)?.setColor(colorAccentMint)
                        statusText.text = "伺服器在線 (HTTP 200)"
                        statusText.setTextColor(colorAccentMint)

                        val modelStr = health.availableModels.takeIf { it.isNotEmpty() }?.joinToString(", ")
                            ?: health.defaultEngine
                        val ytdlpStr = if (health.ytdlpEnabled) "已支援" else "未開啟"
                        val detailMsg = "引擎/模型: $modelStr · 佇列: ${health.queueLength} 筆 · yt-dlp: $ytdlpStr"
                        publicDetailText.text = detailMsg
                        AetherLyricServerStore.setLastStatusMessage(activity, detailMsg)

                        Toast.makeText(
                            activity,
                            getString(R.string.ui_test_connection_success, health.queueLength, health.defaultEngine),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onFailure = { err ->
                        AetherLyricServerStore.setConnectionVerified(activity, false)
                        (statusDot.background as? GradientDrawable)?.setColor(colorDanger)
                        statusText.text = "連線失敗"
                        statusText.setTextColor(colorDanger)
                        val errMsg = "錯誤: ${err.message ?: "無法連接到伺服器"}"
                        publicDetailText.text = errMsg
                        AetherLyricServerStore.setLastStatusMessage(activity, errMsg)

                        if (serverSwitch.isChecked) {
                            isInternalSwitchUpdating = true
                            serverSwitch.isChecked = false
                            isInternalSwitchUpdating = false
                            AetherLyricServerStore.setServerEnabled(activity, false)
                            engineModeDescription.text = getString(R.string.ui_server_engine_status_off)
                        }

                        Toast.makeText(activity, "伺服器連線失敗：${err.message}", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }

        testButton.setOnClickListener {
            val rawUrl = urlInput.text.toString().trim()
            testButton.isEnabled = false
            testButton.text = getString(R.string.ui_testing_connection)
            performHealthCheck(rawUrl) {
                testButton.isEnabled = true
                testButton.text = getString(R.string.ui_test_connection)
            }
        }

        // 點擊展開「伺服器設定檔管理」彈窗
        profileSelectorBox.setOnClickListener {
            val profiles = AetherLyricServerStore.getProfiles(activity)
            showAirDialog(
                title = getString(R.string.ui_switch_server_profile),
                message = "選擇要連線的自架伺服器，或新增與管理設定檔",
                positiveText = getString(R.string.ui_add_server_profile),
                negativeText = getString(R.string.ui_cancel),
                body = {
                    val currentActiveId = AetherLyricServerStore.getActiveProfile(activity).id
                    profiles.forEach { profile ->
                        val isSelected = profile.id == currentActiveId
                        val row = LinearLayout(activity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(
                                dp(AirUiTokens.Space.CardH),
                                dp(AirUiTokens.Space.CardV),
                                dp(AirUiTokens.Space.CardH),
                                dp(AirUiTokens.Space.CardV)
                            )
                            background = GradientDrawable().apply {
                                cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
                                setColor(if (isSelected) colorAccent else colorSurfaceLight)
                                setStroke(dp(AirUiTokens.Stroke.Hairline), if (isSelected) colorAccent else colorStroke)
                            }
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply {
                                setMargins(0, 0, 0, dp(AirUiTokens.Space.Sm))
                            }
                            isClickable = true
                            isFocusable = true
                            enableSoftPressFeedback(AirUiTokens.Motion.DefaultPressScale)

                            val textCol = LinearLayout(activity).apply {
                                orientation = LinearLayout.VERTICAL
                                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                                addView(TextView(activity).apply {
                                    text = profile.name
                                    textSize = AirUiTokens.TextSize.Button
                                    typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                                    setTextColor(if (isSelected) colorOnAccent else colorTextStrong)
                                })
                                addView(TextView(activity).apply {
                                    text = profile.url
                                    textSize = AirUiTokens.TextSize.Caption
                                    setTextColor(if (isSelected) colorOnAccent else colorTextMuted)
                                })
                            }
                            addView(textCol)

                            if (profiles.size > 1) {
                                val deleteBtn = airIconView(R.drawable.ic_air_delete, if (isSelected) colorOnAccent else colorDanger).apply {
                                    layoutParams = ViewGroup.LayoutParams(dp(AirUiTokens.Layout.IconSize), dp(AirUiTokens.Layout.IconSize))
                                    setOnClickListener {
                                        AetherLyricServerStore.deleteProfile(activity, profile.id)
                                        Toast.makeText(activity, "已刪除設定檔：${profile.name}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                addView(deleteBtn)
                            }
                        }

                        row.setOnClickListener {
                            AetherLyricServerStore.setActiveProfileId(activity, profile.id)
                            activeProfile = AetherLyricServerStore.getActiveProfile(activity)
                            profileNameView.text = activeProfile.name
                            profileUrlSubView.text = activeProfile.url
                            urlInput.setText(activeProfile.url)
                            refreshSecuritySummary()

                            performHealthCheck(activeProfile.url)
                            Toast.makeText(activity, "已切換至「${activeProfile.name}」，正在測試連線...", Toast.LENGTH_SHORT).show()
                        }
                        addView(row)
                    }
                },
                onPositive = {
                    var nameInput: EditText? = null
                    var newUrlInput: EditText? = null
                    showAirDialog(
                        title = getString(R.string.ui_add_server_profile),
                        message = "輸入伺服器名稱與存取位址",
                        positiveText = "儲存並切換",
                        negativeText = getString(R.string.ui_cancel),
                        body = {
                            nameInput = EditText(activity).apply {
                                hint = getString(R.string.ui_profile_name_hint)
                                textSize = AirUiTokens.TextSize.BodySmall
                                setTextColor(colorTextStrong)
                                setHintTextColor(colorTextMuted)
                                setPadding(dp(AirUiTokens.Space.ButtonH), dp(AirUiTokens.Space.ControlV), dp(AirUiTokens.Space.ButtonH), dp(AirUiTokens.Space.ControlV))
                                background = GradientDrawable().apply {
                                    cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
                                    setColor(colorSurfaceLight)
                                    setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
                                }
                                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                            }
                            addView(nameInput)

                            newUrlInput = EditText(activity).apply {
                                hint = "http://192.168.x.x:8000"
                                textSize = AirUiTokens.TextSize.BodySmall
                                setTextColor(colorTextStrong)
                                setHintTextColor(colorTextMuted)
                                setPadding(dp(AirUiTokens.Space.ButtonH), dp(AirUiTokens.Space.ControlV), dp(AirUiTokens.Space.ButtonH), dp(AirUiTokens.Space.ControlV))
                                background = GradientDrawable().apply {
                                    cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
                                    setColor(colorSurfaceLight)
                                    setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
                                }
                                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                    setMargins(0, dp(AirUiTokens.Space.Sm), 0, 0)
                                }
                                layoutParams = lp
                            }
                            addView(newUrlInput)
                        },
                        onPositive = {
                            val name = nameInput?.text?.toString()?.trim().orEmpty().ifBlank { "自訂伺服器" }
                            val newUrl = newUrlInput?.text?.toString()?.trim().orEmpty()
                            if (newUrl.isNotBlank()) {
                                val added = AetherLyricServerStore.addProfile(activity, name, newUrl)
                                AetherLyricServerStore.setActiveProfileId(activity, added.id)
                                activeProfile = AetherLyricServerStore.getActiveProfile(activity)
                                profileNameView.text = activeProfile.name
                                profileUrlSubView.text = activeProfile.url
                                urlInput.setText(activeProfile.url)
                                refreshSecuritySummary()

                                performHealthCheck(added.url)
                                Toast.makeText(activity, "已新增並切換至「${added.name}」", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(activity, "伺服器位址不得為空", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            )
        }
    }
}
