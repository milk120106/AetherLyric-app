package com.andsi.airlyrics.ui.pages.settings

import android.animation.ObjectAnimator
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
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import com.andsi.airlyrics.R
import com.andsi.airlyrics.core.model.ThemeAccent
import com.andsi.airlyrics.design.tokens.AirUiTokens
import com.andsi.airlyrics.settings.store.AppSettingsStore
import com.andsi.airlyrics.settings.store.ThemeSettingsStore
import com.andsi.airlyrics.ui.components.*
import com.andsi.airlyrics.ui.model.MainUiHost
import com.andsi.airlyrics.ui.theme.*

/**
 * 設定子頁面 6：介面與外觀 (AppearanceSettingsPage)。
 * 包含：
 * 1. 5 種預設主題色彩選擇。
 * 2. 自訂調色盤（HEX 輸入與色塊即時預覽，切換預設主題時自動清除自訂色）。
 * 3. 動畫效果開關與自訂動畫時長滑桿（0 至 1000ms，支援即時動畫預覽與儲存）。
 */
internal fun createAppearanceSettingsPage(activity: MainUiHost): View = with(activity) {
    val container = pageContainer(activity, animateChanges = false)

    container.addView(
        settingsBackHeader(
            title = "介面與外觀",
            subtitle = "自訂應用程式主題色彩、調色盤與動畫過渡時長"
        )
    )

    // 1. 預設 5 種主題色彩選擇卡片
    container.addView(presetThemesCard(activity))

    // 2. 自訂調色盤卡片 (Primary & Background HEX 即時預覽)
    container.addView(customPaletteCard(activity))

    // 3. 動畫效果與動畫時長滑桿卡片
    container.addView(animationSettingsCard(activity))

    return scroll(activity, container, maxWidthDp = 1200)
}

/**
 * 5 種預設主題色彩卡片。
 */
private fun presetThemesCard(activity: MainUiHost): View = with(activity) {
    val currentAccent = ThemeSettingsStore.getAccent(activity)
    val hasCustomColor = ThemeSettingsStore.getCustomPrimaryHex(activity) != null

    return card(activity) {
        addView(bigText(activity, "預設主題色彩"))
        addView(smallHint(activity, "選擇 5 款精心配色方案（點擊將自動清除自訂色並套用主題）"))

        val presets = listOf(
            ThemeAccent.PINK to ("粉紅 (預設)" to Color.parseColor("#E91E63")),
            ThemeAccent.ORANGE to ("活力橙" to Color.parseColor("#FF9800")),
            ThemeAccent.GREEN to ("清新綠" to Color.parseColor("#4CAF50")),
            ThemeAccent.BLUE to ("經典藍" to Color.parseColor("#2196F3")),
            ThemeAccent.PURPLE to ("優雅紫" to Color.parseColor("#9C27B0"))
        )

        val swatchesGrid = AdaptiveGridLayout(
            activity,
            maxColumns = 5,
            horizontalGap = dp(8),
            verticalGap = dp(8),
            fillCells = true,
            columnPolicy = GridColumnPolicy.Width(90)
        ).apply {
            setPadding(0, dp(AirUiTokens.Space.Sm), 0, dp(AirUiTokens.Space.Sm))
        }

        presets.forEach { (accent, info) ->
            val (name, colorValue) = info
            val isSelected = !hasCustomColor && currentAccent == accent

            val swatchBtn = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(12), dp(8), dp(12))
                background = GradientDrawable().apply {
                    cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
                    setColor(if (isSelected) colorSurfaceLight else colorSurface)
                    setStroke(if (isSelected) dp(2) else dp(AirUiTokens.Stroke.Hairline), if (isSelected) colorValue else colorStroke)
                }
                isClickable = true
                isFocusable = true
                enableSoftPressFeedback(AirUiTokens.Motion.DefaultPressScale)
                setOnClickListener {
                    ThemeSettingsStore.clearCustomColors(activity)
                    ThemeSettingsStore.setAccent(activity, accent)
                    Toast.makeText(activity, "已套用主題：$name", Toast.LENGTH_SHORT).show()
                    activity.activity.recreate()
                }
            }

            val dot = View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(colorValue)
                }
            }
            swatchBtn.addView(dot)

            val label = TextView(activity).apply {
                text = name
                textSize = AirUiTokens.TextSize.Tiny
                typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (isSelected) colorAccent else colorTextStrong)
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            }
            swatchBtn.addView(label)
            swatchesGrid.addView(swatchBtn)
        }

        addView(swatchesGrid)
    }
}

/**
 * 自訂調色盤卡片 (支援 HEX 輸入與色塊即時預覽)。
 */
private fun customPaletteCard(activity: MainUiHost): View = with(activity) {
    val currentPrimaryHex = ThemeSettingsStore.getCustomPrimaryHex(activity)
        ?: String.format("#%06X", (0xFFFFFF and colorAccent))
    val currentBgHex = ThemeSettingsStore.getCustomBackgroundHex(activity)
        ?: String.format("#%06X", (0xFFFFFF and colorBackground))

    return card(activity) {
        addView(bigText(activity, "自訂調色盤 (HEX / 色彩微調)"))
        addView(smallHint(activity, "直接輸入 16 進位色碼進行即時預覽，支援設定強調色與自訂背景底色"))

        // 主要強調色
        addView(normalText(activity, "主要強調色 (Primary Accent)"))
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
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val hex = s?.toString()?.trim().orEmpty()
                    runCatching {
                        val parsed = Color.parseColor(hex)
                        (primarySwatch.background as? GradientDrawable)?.setColor(parsed)
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        primaryRow.addView(primarySwatch)
        primaryRow.addView(primaryInput)
        addView(primaryRow)

        // 背景底色
        addView(normalText(activity, "背景底色 (Background)"))
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
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val hex = s?.toString()?.trim().orEmpty()
                    runCatching {
                        val parsed = Color.parseColor(hex)
                        (bgSwatch.background as? GradientDrawable)?.setColor(parsed)
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        bgRow.addView(bgSwatch)
        bgRow.addView(bgInput)
        addView(bgRow)

        val buttonsRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(AirUiTokens.Space.Sm), 0, 0)
            }
        }

        val applyBtn = actionButton(activity, "套用自訂色彩") {
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
                Toast.makeText(activity, "已儲存並套用自訂色彩", Toast.LENGTH_SHORT).show()
                activity.activity.recreate()
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, dp(4), 0)
            }
        }

        val resetBtn = actionButton(activity, "重設為預設色彩") {
            ThemeSettingsStore.clearCustomColors(activity)
            Toast.makeText(activity, "已清除自訂色彩，恢復預設主題", Toast.LENGTH_SHORT).show()
            activity.activity.recreate()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(4), 0, 0, 0)
            }
        }

        buttonsRow.addView(applyBtn)
        buttonsRow.addView(resetBtn)
        addView(buttonsRow)
    }
}

/**
 * 動畫效果開關與自訂時長滑桿卡片。
 */
private fun animationSettingsCard(activity: MainUiHost): View = with(activity) {
    var isAnimEnabled = AppSettingsStore.isAnimationEffectsEnabled(activity)
    var currentDuration = AppSettingsStore.getAnimationDurationMs(activity)

    return card(activity) {
        addView(bigText(activity, "動畫與過渡效果"))
        addView(smallHint(activity, "調整視窗彈出、切換與歌詞滾動之動畫過渡速度"))

        // 動畫效果開關
        val switchRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(AirUiTokens.Space.Sm), 0, dp(AirUiTokens.Space.Sm))
        }
        val textCol = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(normalText(activity, "啟用介面動態過渡動畫"))
            addView(smallHint(activity, "關閉時停用所有視窗彈出與切換動畫以提升極致反應速度"))
        }
        val animSwitch = SwitchCompat(activity).apply {
            isChecked = isAnimEnabled
            applyAirThemeTint(activity)
            setOnCheckedChangeListener { _, checked ->
                isAnimEnabled = checked
                AppSettingsStore.setAnimationEffectsEnabled(activity, checked)
            }
        }
        switchRow.addView(textCol)
        switchRow.addView(animSwitch)
        addView(switchRow)

        // 動畫時長滑桿與數值顯示
        val durationLabel = TextView(activity).apply {
            text = "動畫過渡時長：${currentDuration} ms"
            textSize = AirUiTokens.TextSize.BodySmall
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorAccent)
            setPadding(0, dp(AirUiTokens.Space.Sm), 0, dp(AirUiTokens.Space.Xs))
        }
        addView(durationLabel)

        // 即時預覽色塊方盒
        val previewBox = View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                setMargins(0, dp(AirUiTokens.Space.Sm), 0, dp(AirUiTokens.Space.Sm))
            }
            background = GradientDrawable().apply {
                cornerRadius = dp(AirUiTokens.Radius.Sm).toFloat()
                setColor(colorAccent)
            }
        }

        fun playPreviewAnimation(duration: Long) {
            if (!isAnimEnabled || duration == 0L) {
                previewBox.scaleX = 1f
                previewBox.scaleY = 1f
                return
            }
            val animX = ObjectAnimator.ofFloat(previewBox, "scaleX", 0.6f, 1.15f, 1.0f)
            val animY = ObjectAnimator.ofFloat(previewBox, "scaleY", 0.6f, 1.15f, 1.0f)
            animX.duration = duration
            animY.duration = duration
            animX.start()
            animY.start()
        }

        val seekBar = SeekBar(activity).apply {
            max = 1000 // 0 到 1000ms
            progress = currentDuration
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        currentDuration = progress
                        durationLabel.text = "動畫過渡時長：${progress} ms"
                        AppSettingsStore.setAnimationDurationMs(activity, progress)
                        playPreviewAnimation(progress.toLong())
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    playPreviewAnimation(currentDuration.toLong())
                }
            })
        }
        addView(seekBar)

        val previewContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(AirUiTokens.Space.Sm), 0, 0)
        }
        previewContainer.addView(previewBox)
        previewContainer.addView(TextView(activity).apply {
            text = "滑動滑桿即時預覽動畫縮放節奏"
            textSize = AirUiTokens.TextSize.Caption
            setTextColor(colorTextMuted)
            setPadding(dp(12), 0, 0, 0)
        })
        addView(previewContainer)
    }
}
