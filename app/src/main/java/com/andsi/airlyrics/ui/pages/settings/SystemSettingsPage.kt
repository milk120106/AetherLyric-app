package com.andsi.airlyrics.ui.pages.settings

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import com.andsi.airlyrics.R
import com.andsi.airlyrics.design.tokens.AirUiTokens
import com.andsi.airlyrics.settings.store.AppSettingsStore
import com.andsi.airlyrics.settings.store.LyricsOffsetStore
import com.andsi.airlyrics.settings.store.ThemeSettingsStore
import com.andsi.airlyrics.ui.components.*
import com.andsi.airlyrics.ui.model.MainUiHost
import com.andsi.airlyrics.ui.navigation.SettingsSubPage
import com.andsi.airlyrics.ui.state.forgetAuxiliaryDialog
import com.andsi.airlyrics.ui.state.isAuxiliaryDialogShowing
import com.andsi.airlyrics.ui.state.rememberAuxiliaryDialog
import com.andsi.airlyrics.ui.theme.*

internal fun createSystemSettingsPage(activity: MainUiHost): View = with(activity) createSystemSettingsPage@ {
    val container = pageContainer(activity)
    container.addView(settingsBackHeader(getString(R.string.ui_system)))

    // 語言選擇
    container.addView(languageChoiceCard(activity))

    // 簡潔模式開關（自設定首頁移入）
    container.addView(compactModeCard(activity))

    // 狀態提示隱藏開關
    container.addView(statusPopupsMuteCard(activity))

    // 全域時間軸偏移量設定（自設定首頁移入）
    container.addView(globalOffsetCard(activity))

    // 權限模式卡片
    container.addView(permissionTiersCard(activity))

    // 原生系統權限卡片
    container.addView(systemPermissionCard())

    // 自訂主題顏色卡片 (Primary & Background HEX)
    container.addView(customThemeCard(activity))

    // 執行日誌入口卡片
    container.addView(executionLogsCard(activity))

    return scroll(activity, container)
}

private fun compactModeCard(activity: MainUiHost): View = with(activity) {
    val isCompact = AppSettingsStore.isCompactMode(activity)
    return card(activity) {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isClickable = true
        isFocusable = true
        enableSoftPressFeedback(AirUiTokens.Motion.DefaultPressScale)

        val infoLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, dp(AirUiTokens.Space.Xl), 0)
            }
            addView(bigText(activity, getString(R.string.ui_compact_mode)))
            addView(smallHint(activity, getString(R.string.ui_compact_mode_desc)))
        }
        addView(infoLayout)

        val compactSwitch = SwitchCompat(activity).apply {
            isChecked = isCompact
            applyAirThemeTint(activity)
            setOnCheckedChangeListener { _, checked ->
                if (checked != activity.isCompactMode()) {
                    activity.setCompactMode(checked)
                }
            }
        }
        addView(compactSwitch)

        setOnClickListener {
            compactSwitch.toggle()
        }
    }
}

private fun globalOffsetCard(activity: MainUiHost): View = with(activity) {
    return card(activity) {
        var currentGlobalOffset = LyricsOffsetStore.getGlobalOffsetMs(activity)
        var isGlobalEnabled = LyricsOffsetStore.isGlobalOffsetEnabled(activity)

        val headerRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val titleView = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(bigText(activity, getString(R.string.ui_apply_offset_to_all_songs)))
            addView(smallHint(activity, if (isGlobalEnabled) "目前套用全域時間軸" else "目前使用單曲專屬時間軸"))
        }

        val globalSwitch = SwitchCompat(activity).apply {
            isChecked = isGlobalEnabled
            applyAirThemeTint(activity)
            setOnCheckedChangeListener { _, checked ->
                isGlobalEnabled = checked
                LyricsOffsetStore.setGlobalOffsetEnabled(activity, checked)
                (titleView.getChildAt(1) as? TextView)?.text =
                    if (checked) "目前套用全域時間軸" else "目前使用單曲專屬時間軸"
                uiActions.reloadFloatingLyrics()
            }
        }
        headerRow.addView(titleView)
        headerRow.addView(globalSwitch)
        addView(headerRow)

        val offsetValueView = TextView(activity).apply {
            text = "${if (currentGlobalOffset > 0) "+" else ""}${currentGlobalOffset} ms"
            textSize = AirUiTokens.TextSize.PageTitle
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorAccent)
            gravity = Gravity.CENTER
            setPadding(0, dp(AirUiTokens.Space.Sm), 0, dp(AirUiTokens.Space.Sm))
        }
        addView(offsetValueView)

        val buttonsRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        fun updateOffset(delta: Long) {
            currentGlobalOffset = if (delta == 0L) 0L else (currentGlobalOffset + delta)
            LyricsOffsetStore.saveGlobalOffset(activity, currentGlobalOffset)
            globalSwitch.isChecked = true
            offsetValueView.text = "${if (currentGlobalOffset > 0) "+" else ""}${currentGlobalOffset} ms"
            uiActions.reloadFloatingLyrics()
        }

        val steps = listOf(-500L to "-500", -100L to "-100", 0L to "0", 100L to "+100", 500L to "+500")
        steps.forEach { (delta, label) ->
            val btn = TextView(activity).apply {
                text = label
                textSize = AirUiTokens.TextSize.BodySmall
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colorTextStrong)
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(AirUiTokens.Space.Sm), dp(4), dp(AirUiTokens.Space.Sm))
                background = GradientDrawable().apply {
                    cornerRadius = dp(AirUiTokens.Radius.Sm).toFloat()
                    setColor(colorSurfaceLight)
                    setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
                }
                val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                layoutParams = params
                isClickable = true
                isFocusable = true
                setOnClickListener { updateOffset(delta) }
            }
            buttonsRow.addView(btn)
        }
        addView(buttonsRow)
    }
}

private fun executionLogsCard(activity: MainUiHost): View = with(activity) {
    return settingsCategoryCard(
        title = getString(R.string.ui_execution_logs_title),
        subtitle = getString(R.string.ui_execution_logs_desc),
        status = "即時日誌",
        iconRes = R.drawable.ic_air_info
    ) {
        uiActions.openSettingsSubPage(SettingsSubPage.LOGS)
    }
}

private fun customThemeCard(activity: MainUiHost): View = with(activity) {
    val currentPrimaryHex = ThemeSettingsStore.getCustomPrimaryHex(activity)
        ?: String.format("#%06X", (0xFFFFFF and colorAccent))
    val currentBgHex = ThemeSettingsStore.getCustomBackgroundHex(activity)
        ?: String.format("#%06X", (0xFFFFFF and colorBackground))

    return card(activity) {
        addView(bigText(activity, getString(R.string.ui_custom_theme_color)))
        addView(smallHint(activity, getString(R.string.ui_custom_theme_desc)))

        // 主要強調色輸入與預覽色塊
        addView(normalText(activity, getString(R.string.ui_primary_color_hex)))
        val primaryRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(AirUiTokens.Space.Xs), 0, dp(AirUiTokens.Space.Sm))
        }
        val primarySwatch = View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                setMargins(0, 0, dp(AirUiTokens.Space.Sm), 0)
            }
            background = GradientDrawable().apply {
                cornerRadius = dp(AirUiTokens.Radius.Sm).toFloat()
                setColor(colorAccent)
                setStroke(dp(1), colorStroke)
            }
        }
        val primaryInput = EditText(activity).apply {
            setText(currentPrimaryHex)
            textSize = AirUiTokens.TextSize.Body
            setTextColor(colorTextStrong)
            background = GradientDrawable().apply {
                cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
                setColor(colorSurfaceLight)
                setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
            }
            setPadding(dp(AirUiTokens.Space.CardH), dp(AirUiTokens.Space.CardV), dp(AirUiTokens.Space.CardH), dp(AirUiTokens.Space.CardV))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val hex = s?.toString()?.trim() ?: ""
                    try {
                        val parsed = Color.parseColor(hex)
                        (primarySwatch.background as? GradientDrawable)?.setColor(parsed)
                    } catch (_: Exception) {}
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        primaryRow.addView(primarySwatch)
        primaryRow.addView(primaryInput)
        addView(primaryRow)

        // 背景底色輸入與預覽色塊
        addView(normalText(activity, getString(R.string.ui_background_color_hex)))
        val bgRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(AirUiTokens.Space.Xs), 0, dp(AirUiTokens.Space.Sm))
        }
        val bgSwatch = View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                setMargins(0, 0, dp(AirUiTokens.Space.Sm), 0)
            }
            background = GradientDrawable().apply {
                cornerRadius = dp(AirUiTokens.Radius.Sm).toFloat()
                setColor(colorBackground)
                setStroke(dp(1), colorStroke)
            }
        }
        val bgInput = EditText(activity).apply {
            setText(currentBgHex)
            textSize = AirUiTokens.TextSize.Body
            setTextColor(colorTextStrong)
            background = GradientDrawable().apply {
                cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
                setColor(colorSurfaceLight)
                setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
            }
            setPadding(dp(AirUiTokens.Space.CardH), dp(AirUiTokens.Space.CardV), dp(AirUiTokens.Space.CardH), dp(AirUiTokens.Space.CardV))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val hex = s?.toString()?.trim() ?: ""
                    try {
                        val parsed = Color.parseColor(hex)
                        (bgSwatch.background as? GradientDrawable)?.setColor(parsed)
                    } catch (_: Exception) {}
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        bgRow.addView(bgSwatch)
        bgRow.addView(bgInput)
        addView(bgRow)

        // 按鈕列 (套用 / 重設)
        val buttonsRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, dp(AirUiTokens.Space.Sm), 0, 0)
        }
        val applyBtn = actionButton(activity, getString(R.string.ui_apply_custom_colors)) {
            val p = primaryInput.text?.toString()?.trim().orEmpty()
            val b = bgInput.text?.toString()?.trim().orEmpty()
            var valid = true
            if (p.isNotBlank()) {
                try {
                    Color.parseColor(p)
                    ThemeSettingsStore.setCustomPrimaryHex(activity, p)
                } catch (e: Exception) {
                    valid = false
                    Toast.makeText(activity, "主要強調色 HEX 格式錯誤", Toast.LENGTH_SHORT).show()
                }
            }
            if (b.isNotBlank() && valid) {
                try {
                    Color.parseColor(b)
                    ThemeSettingsStore.setCustomBackgroundHex(activity, b)
                } catch (e: Exception) {
                    valid = false
                    Toast.makeText(activity, "背景底色 HEX 格式錯誤", Toast.LENGTH_SHORT).show()
                }
            }
            if (valid) {
                Toast.makeText(activity, R.string.ui_custom_colors_applied, Toast.LENGTH_SHORT).show()
                activity.activity.recreate()
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, dp(2), 0)
            }
        }
        val resetBtn = actionButton(activity, getString(R.string.ui_reset_default_colors)) {
            ThemeSettingsStore.clearCustomColors(activity)
            Toast.makeText(activity, R.string.ui_custom_colors_reset, Toast.LENGTH_SHORT).show()
            activity.activity.recreate()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(2), 0, 0, 0)
            }
        }
        buttonsRow.addView(applyBtn)
        buttonsRow.addView(resetBtn)
        addView(buttonsRow)
    }
}

private fun statusPopupsMuteCard(activity: MainUiHost): View = with(activity) statusPopupsMuteCard@ {
    val statusPopupsSwitch = SwitchCompat(activity).apply {
        isChecked = areStatusPopupsMuted()
        applyAirThemeTint(activity)
        contentDescription = getString(R.string.ui_status_popups_mute)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    return card(activity) {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isClickable = true
        isFocusable = true
        enableSoftPressFeedback(AirUiTokens.Motion.DefaultPressScale)

        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, dp(AirUiTokens.Space.Xl + AirUiTokens.Space.Lg), 0)
            }
            addView(bigText(activity, getString(R.string.ui_status_popups_mute)))
            addView(smallHint(activity, "開啟時隱藏設定模塊狀態提示與懸浮窗更新提示"))
        })

        addView(statusPopupsSwitch)

        statusPopupsSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked != areStatusPopupsMuted()) {
                setStatusPopupsMuted(checked)
            }
        }
        setOnClickListener {
            statusPopupsSwitch.toggle()
        }
    }
}

private fun permissionTiersCard(activity: MainUiHost): View = with(activity) {
    return card(activity) {
        addView(bigText(activity, getString(R.string.ui_permission_tier_title)))
        addView(smallHint(activity, "依播放器相容性提供三種偵測方式：標準通知監聽、進階無障礙輔助、以及狀態列文字擷取"))

        // 1. 基礎模式 (最上)
        val isBasicOk = hasNotificationListenerAccess() && overlayPermissionGranted
        val basicStatus = if (isBasicOk) "正常運作" else "未授權"

        val basicRow = settingRow(
            activity,
            name = getString(R.string.ui_tier_basic_status),
            value = basicStatus
        )
        addView(basicRow)

        if (!isBasicOk) {
            if (!overlayPermissionGranted) {
                addView(actionButton(activity, "授權懸浮窗權限") {
                    uiActions.requestOverlayPermission()
                })
            }
            if (!hasNotificationListenerAccess()) {
                addView(actionButton(activity, "開啟通知存取設定") {
                    uiActions.openNotificationListenerSettings()
                })
            }
        }

        addView(tierDivider(activity))

        // 2. 無障礙模式 (中間)
        val isA11yOk = com.andsi.airlyrics.permissions.PermissionTierManager.isAccessibilityEnabled(activity)
        val isRestricted = com.andsi.airlyrics.permissions.PermissionTierManager.isRestrictedSettingsBlocked(activity)
        val a11yStatus = if (isA11yOk) "正常運作" else if (isRestricted) "已受系統限制" else "未開啟"

        val a11yRow = settingRow(
            activity,
            name = getString(R.string.ui_tier_accessibility_status),
            value = a11yStatus
        )
        addView(a11yRow)

        if (isRestricted) {
            if (!com.andsi.airlyrics.settings.store.AppSettingsStore.isCompactMode(activity)) {
                addView(TextView(activity).apply {
                    text = "Android 13+ 偵測到受限制的設定，請點擊下方按鈕前往「應用程式資訊」，在右上角選單點擊「允許受限制的設定」。"
                    textSize = AirUiTokens.TextSize.Caption
                    setTextColor(colorDanger)
                    setPadding(0, dp(AirUiTokens.Space.Xxs), 0, dp(AirUiTokens.Space.Xxs))
                })
            }
            addView(actionButton(activity, getString(R.string.ui_unlock_restricted_settings)) {
                com.andsi.airlyrics.permissions.PermissionTierManager.openAppDetailsSettings(activity)
            })
        } else if (!isA11yOk) {
            addView(actionButton(activity, getString(R.string.ui_open_accessibility_settings)) {
                com.andsi.airlyrics.permissions.PermissionTierManager.openAccessibilitySettings(activity)
            })
        }

        addView(tierDivider(activity))

        // 3. Shizuku (最下)
        val isShizukuOk = com.andsi.airlyrics.permissions.PermissionTierManager.isShizukuAvailableAndGranted(activity)
        val isShizukuInstalled = com.andsi.airlyrics.permissions.PermissionTierManager.isShizukuInstalled(activity)
        val shizukuStatus = if (isShizukuOk) "正常運作" else if (isShizukuInstalled) "未授權" else "未安裝"

        val shizukuRow = settingRow(
            activity,
            name = getString(R.string.ui_tier_shizuku_status),
            value = shizukuStatus
        )
        addView(shizukuRow)

        if (!isShizukuOk) {
            if (isShizukuInstalled) {
                addView(actionButton(activity, getString(R.string.ui_open_dev_settings)) {
                    com.andsi.airlyrics.permissions.PermissionTierManager.openDevelopmentSettings(activity)
                })
            } else {
                addView(actionButton(activity, "前往取得 Shizuku") {
                    activity.openUrl("https://shizuku.rikka.app")
                })
            }
        }
    }
}

private fun tierDivider(activity: MainUiHost): View = with(activity) {
    return View(this).apply {
        setBackgroundColor(colorStroke)
        alpha = 0.5f
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(AirUiTokens.Stroke.Hairline)
        ).apply {
            setMargins(0, dp(AirUiTokens.Space.Lg), 0, dp(AirUiTokens.Space.Sm))
        }
    }
}

private fun languageChoiceCard(activity: MainUiHost): View = with(activity) languageChoiceCard@ {
    return card(activity) {
        setPadding(dp(AirUiTokens.Space.CardH), dp(AirUiTokens.Space.CardH), dp(AirUiTokens.Space.CardH), dp(AirUiTokens.Space.CardH))
        isClickable = true
        isFocusable = true
        enableSoftPressFeedback(AirUiTokens.Motion.DefaultPressScale)
        setOnClickListener { showLanguageDialog(activity) }

        val labelView = TextView(activity).apply {
            setText(R.string.ui_language)
            textSize = AirUiTokens.TextSize.PageTitle - 4f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextStrong)
        }

        val valueView = TextView(activity).apply {
            text = languageSettingsState().displayName
            textSize = AirUiTokens.TextSize.Button
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorAccent)
            gravity = Gravity.START
        }

        val chevron = airIconView(R.drawable.ic_air_chevron_right, colorAccent).apply {
            layoutParams = ViewGroup.LayoutParams(
                dp(AirUiTokens.Layout.IconSize),
                dp(AirUiTokens.Layout.IconSize)
            )
        }

        addView(AdaptiveLabelValueLayout(
            context = activity,
            labelView = labelView,
            valueView = valueView,
            trailingView = chevron,
            horizontalGapPx = dp(AirUiTokens.Space.Xxl + AirUiTokens.Space.Xxs),
            verticalGapPx = dp(AirUiTokens.Space.Sm),
            trailingGapPx = dp(AirUiTokens.Space.Lg)
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        })
    }
}

internal fun showLanguageDialog(activity: MainUiHost) = with(activity) showLanguageDialog@ {
    if (isAuxiliaryDialogShowing("language")) return@showLanguageDialog
    val languageState = languageSettingsState()
    rememberAuxiliaryDialog("language")
    var selectedMode: String? = null
    lateinit var dialog: android.app.Dialog
    dialog = showAirDialog(
        title = getString(R.string.ui_language),
        message = null,
        positiveText = null,
        onUserDismiss = { forgetAuxiliaryDialog("language") },
        scrollStateKey = "aux.language.scroll",
        afterUserDismiss = {
            selectedMode?.let { mode ->
                setLanguageMode(mode)
                activity.reloadFloatingLyricsAfterLanguageChanged()
            }
        },
        body = {
            val selectMode: (String) -> Unit = { mode ->
                if (mode == languageState.currentMode) {
                    dialog.dismiss()
                } else {
                    selectedMode = mode
                    dialog.dismiss()
                }
            }
            languageState.options.forEach { option ->
                addLanguageOption(activity, option.title, option.mode, languageState.currentMode, selectMode)
            }
        }
    )
    auxiliaryDialogs["language"] = dialog
}

private fun LinearLayout.addLanguageOption(
    activity: MainUiHost,
    title: String,
    mode: String,
    currentMode: String,
    onSelect: (String) -> Unit
) {
    val selected = mode == currentMode
    addView(TextView(activity).apply {
        text = title
        textSize = AirUiTokens.TextSize.Button
        typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setTextColor(if (selected) activity.colorOnAccent else activity.colorTextStrong)
        setAirStartIcon(
            host = activity,
            iconRes = R.drawable.ic_air_check.takeIf { selected },
            tint = if (selected) activity.colorIconOnAccent else activity.colorTextStrong
        )
        gravity = Gravity.CENTER_VERTICAL
        setPadding(activity.dp(AirUiTokens.Space.Xl + AirUiTokens.Space.Lg), activity.dp(AirUiTokens.Space.ControlV), activity.dp(AirUiTokens.Space.Xl + AirUiTokens.Space.Lg), activity.dp(AirUiTokens.Space.ControlV))
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, activity.dp(AirUiTokens.Space.Xxl), 0, 0)
        layoutParams = params
        background = GradientDrawable().apply {
            cornerRadius = activity.dp(AirUiTokens.Radius.Md).toFloat()
            setColor(if (selected) activity.colorAccent else activity.colorSurfaceLight)
            setStroke(activity.dp(AirUiTokens.Stroke.Hairline), if (selected) activity.colorAccent else activity.colorStroke)
        }
        enableSoftPressFeedback(AirUiTokens.Motion.DefaultPressScale)
        setOnClickListener {
            onSelect(mode)
        }
    })
}
