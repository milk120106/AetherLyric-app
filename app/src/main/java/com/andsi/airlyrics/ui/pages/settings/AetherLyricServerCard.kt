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
import com.andsi.airlyrics.ui.components.actionButton
import com.andsi.airlyrics.ui.components.bigText
import com.andsi.airlyrics.ui.components.card
import com.andsi.airlyrics.ui.components.enableSoftPressFeedback
import com.andsi.airlyrics.ui.model.MainUiHost
import com.andsi.airlyrics.ui.theme.applyAirThemeTint
import com.andsi.airlyrics.ui.theme.colorAccent
import com.andsi.airlyrics.ui.theme.colorAccentLight
import com.andsi.airlyrics.ui.theme.colorAccentMint
import com.andsi.airlyrics.ui.theme.colorDanger
import com.andsi.airlyrics.ui.theme.colorStroke
import com.andsi.airlyrics.ui.theme.colorSurfaceLight
import com.andsi.airlyrics.ui.theme.colorTextMuted
import com.andsi.airlyrics.ui.theme.colorTextStrong
import kotlinx.coroutines.launch

/**
 * AetherLyric 伺服器雙引擎設定卡片 (AetherLyricServerCard)。
 * 採用 AirLyrics 原生設計語言與 Token 系統重構，支援深淺色主題無縫適配：
 * - 大圓角卡片 (Radius.Card)
 * - 粗體標題 (Title) 與暗色次要描述 (BodySmall, colorTextMuted)
 * - 原生風格輸入框 (colorSurfaceLight 背景、Hairline colorStroke 細邊框、Radius.Md 圓角)
 * - 原版尺寸規格之測試按鈕 (actionButton)
 * - 柔和非鮮豔之輔助提示文字 (Caption, colorTextMuted / colorAccentMint / colorDanger)
 * - 主題色開關 (SwitchCompat 搭配 applyAirThemeTint，拇指與軌道套用 colorAccent / colorAccentLight)
 */
internal fun createAetherLyricServerCard(activity: MainUiHost): View = with(activity) {
    return card(activity) {
        // 1. 卡片標題與描述 (貼合原版字體層級與暗色描述文字)
        addView(bigText(activity, getString(R.string.ui_aetherlyric_server_title)))

        val descView = TextView(activity).apply {
            text = getString(R.string.ui_aetherlyric_server_desc)
            textSize = AirUiTokens.TextSize.BodySmall
            setTextColor(colorTextMuted)
            setPadding(0, dp(AirUiTokens.Space.Sm), 0, 0)
        }
        addView(descView)

        // 2. 伺服器位址標籤與輸入框
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
            setText(AetherLyricServerStore.getServerUrl(activity))
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
        }
        addView(urlInput)

        // 3. API 金鑰標籤與輸入框 (選填)
        val apiKeyLabel = TextView(activity).apply {
            text = getString(R.string.ui_server_api_key_label)
            textSize = AirUiTokens.TextSize.BodySmall
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextStrong)
            setPadding(0, dp(AirUiTokens.Space.CardV), 0, dp(AirUiTokens.Space.Sm))
        }
        addView(apiKeyLabel)

        val apiKeyInput = EditText(activity).apply {
            hint = getString(R.string.ui_server_api_key_hint)
            setText(AetherLyricServerStore.getAdminApiKey(activity))
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
        addView(apiKeyInput)

        // 4. 測試連線按鈕 (原版按鈕樣式與規格)
        val testButton = actionButton(activity, getString(R.string.ui_test_connection)) { }
        (testButton.layoutParams as? LinearLayout.LayoutParams)?.setMargins(
            0,
            dp(AirUiTokens.Space.CardV),
            0,
            0
        )
        addView(testButton)

        // 5. 連線狀態指示燈與輔助文字 (使用原生主題色系，不使用高對比鮮豔色)
        val isVerifiedInitially = AetherLyricServerStore.isConnectionVerified(activity)
        val isEnabledInitially = AetherLyricServerStore.isServerEnabled(activity)

        val initialDotColor = when {
            isVerifiedInitially && isEnabledInitially -> colorAccentMint
            isVerifiedInitially -> colorAccentLight
            else -> colorTextMuted
        }
        val initialTextColor = when {
            isVerifiedInitially && isEnabledInitially -> colorAccentMint
            else -> colorTextMuted
        }

        val statusContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(AirUiTokens.Space.Xl), 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val statusDot = View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply {
                setMargins(0, 0, dp(AirUiTokens.Space.Sm + AirUiTokens.Space.Xs), 0)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(initialDotColor)
            }
        }
        statusContainer.addView(statusDot)

        val statusText = TextView(activity).apply {
            textSize = AirUiTokens.TextSize.Caption
            val lastMsg = AetherLyricServerStore.getLastStatusMessage(activity)
            text = if (lastMsg.isNotEmpty()) {
                lastMsg
            } else if (isVerifiedInitially) {
                getString(R.string.ui_test_connection_success, 0, "online")
            } else {
                getString(R.string.ui_server_must_verify_first)
            }
            setTextColor(initialTextColor)
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        statusContainer.addView(statusText)
        addView(statusContainer)

        // 6. 細線分隔線 (Hairline Divider)
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

        // 7. 雙引擎切換區塊 (Switch Row，包含標題、狀態描述與主題色 Switch)
        val engineModeDescription = TextView(activity).apply {
            textSize = AirUiTokens.TextSize.Caption
            setTextColor(colorTextMuted)
            setPadding(0, dp(AirUiTokens.Space.Xs), 0, 0)
            text = if (isEnabledInitially) {
                getString(R.string.ui_server_engine_status_on)
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
            setPadding(0, 0, 0, 0)
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

        // 開關變更邏輯
        serverSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isInternalSwitchUpdating) return@setOnCheckedChangeListener

            if (isChecked) {
                val url = AetherLyricServerStore.getServerUrl(activity)
                val verified = AetherLyricServerStore.isConnectionVerified(activity)
                if (url.isEmpty() || !verified) {
                    isInternalSwitchUpdating = true
                    serverSwitch.isChecked = false
                    isInternalSwitchUpdating = false
                    statusText.text = getString(R.string.ui_server_must_verify_first)
                    statusText.setTextColor(colorTextMuted)
                    (statusDot.background as? GradientDrawable)?.setColor(colorTextMuted)
                    Toast.makeText(
                        activity,
                        getString(R.string.ui_server_must_verify_first),
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnCheckedChangeListener
                }
                AetherLyricServerStore.setServerEnabled(activity, true)
                engineModeDescription.text = getString(R.string.ui_server_engine_status_on)
                (statusDot.background as? GradientDrawable)?.setColor(colorAccentMint)
                statusText.setTextColor(colorAccentMint)
            } else {
                AetherLyricServerStore.setServerEnabled(activity, false)
                engineModeDescription.text = getString(R.string.ui_server_engine_status_off)
                val verified = AetherLyricServerStore.isConnectionVerified(activity)
                val dotColor = if (verified) colorAccentLight else colorTextMuted
                (statusDot.background as? GradientDrawable)?.setColor(dotColor)
                statusText.setTextColor(colorTextMuted)
            }
        }

        switchRow.setOnClickListener {
            serverSwitch.isChecked = !serverSwitch.isChecked
        }

        // 8. 測試連線按鈕點擊處理
        testButton.setOnClickListener {
            val rawUrl = urlInput.text.toString().trim()
            val rawKey = apiKeyInput.text.toString().trim()

            if (rawUrl.isEmpty()) {
                statusText.text = getString(R.string.ui_server_must_verify_first)
                statusText.setTextColor(colorTextMuted)
                Toast.makeText(
                    activity,
                    getString(R.string.ui_server_must_verify_first),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            testButton.isEnabled = false
            testButton.text = getString(R.string.ui_testing_connection)
            statusText.text = getString(R.string.ui_testing_connection)
            statusText.setTextColor(colorTextMuted)
            (statusDot.background as? GradientDrawable)?.setColor(colorAccent)

            activity.activity.lifecycleScope.launch {
                val result = AetherLyricApiClient.testHealth(rawUrl, rawKey)
                testButton.isEnabled = true
                testButton.text = getString(R.string.ui_test_connection)

                result.onSuccess { health ->
                    AetherLyricServerStore.setServerUrl(activity, rawUrl)
                    AetherLyricServerStore.setAdminApiKey(activity, rawKey)
                    AetherLyricServerStore.setConnectionVerified(activity, true)

                    val successMsg = getString(
                        R.string.ui_test_connection_success,
                        health.queueLength,
                        health.defaultEngine
                    )
                    AetherLyricServerStore.setLastStatusMessage(activity, successMsg)

                    (statusDot.background as? GradientDrawable)?.setColor(colorAccentMint)
                    statusText.text = successMsg
                    statusText.setTextColor(colorAccentMint)

                    Toast.makeText(activity, "伺服器連線成功！", Toast.LENGTH_SHORT).show()
                }.onFailure { exc ->
                    AetherLyricServerStore.setConnectionVerified(activity, false)
                    AetherLyricServerStore.setServerEnabled(activity, false)

                    val errorMsg = getString(
                        R.string.ui_test_connection_failed,
                        exc.message ?: "連線逾時"
                    )
                    AetherLyricServerStore.setLastStatusMessage(activity, errorMsg)

                    (statusDot.background as? GradientDrawable)?.setColor(colorDanger)
                    statusText.text = errorMsg
                    statusText.setTextColor(colorDanger)

                    isInternalSwitchUpdating = true
                    serverSwitch.isChecked = false
                    isInternalSwitchUpdating = false
                    engineModeDescription.text = getString(R.string.ui_server_engine_status_off)

                    Toast.makeText(activity, errorMsg, Toast.LENGTH_LONG).show()
                }
            }
        }

        // 9. 監聽輸入框異動，位址變更時重設驗證狀態
        urlInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val newUrl = s?.toString()?.trim().orEmpty()
                if (newUrl != AetherLyricServerStore.getServerUrl(activity)) {
                    AetherLyricServerStore.setConnectionVerified(activity, false)
                    (statusDot.background as? GradientDrawable)?.setColor(colorTextMuted)
                    statusText.text = getString(R.string.ui_server_must_verify_first)
                    statusText.setTextColor(colorTextMuted)
                    if (serverSwitch.isChecked) {
                        isInternalSwitchUpdating = true
                        serverSwitch.isChecked = false
                        isInternalSwitchUpdating = false
                        AetherLyricServerStore.setServerEnabled(activity, false)
                        engineModeDescription.text = getString(R.string.ui_server_engine_status_off)
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        apiKeyInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val newKey = s?.toString()?.trim().orEmpty()
                if (newKey != AetherLyricServerStore.getAdminApiKey(activity)) {
                    AetherLyricServerStore.setConnectionVerified(activity, false)
                    (statusDot.background as? GradientDrawable)?.setColor(colorTextMuted)
                    statusText.text = getString(R.string.ui_server_must_verify_first)
                    statusText.setTextColor(colorTextMuted)
                    if (serverSwitch.isChecked) {
                        isInternalSwitchUpdating = true
                        serverSwitch.isChecked = false
                        isInternalSwitchUpdating = false
                        AetherLyricServerStore.setServerEnabled(activity, false)
                        engineModeDescription.text = getString(R.string.ui_server_engine_status_off)
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }
}
