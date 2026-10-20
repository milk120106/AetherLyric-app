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

    // 狀態提示隱藏開關
    container.addView(statusPopupsMuteCard(activity))

    // 原生系統權限卡片
    container.addView(systemPermissionCard())

    // 自訂主題顏色卡片 (Primary & Background HEX)
    container.addView(customThemeCard(activity))

    // 執行日誌入口卡片
    container.addView(executionLogsCard(activity))

    return scroll(activity, container)
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
        })

        addView(statusPopupsSwitch)

        statusPopupsSwitch.setOnCheckedChangeListener { _, checked ->
            setStatusPopupsMuted(checked)
        }
        setOnClickListener {
            statusPopupsSwitch.isChecked = !statusPopupsSwitch.isChecked
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
