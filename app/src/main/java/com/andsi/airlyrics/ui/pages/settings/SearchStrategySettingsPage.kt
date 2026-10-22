package com.andsi.airlyrics.ui.pages.settings

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import com.andsi.airlyrics.R
import com.andsi.airlyrics.design.tokens.AirUiTokens
import com.andsi.airlyrics.settings.store.LyricsOffsetStore
import com.andsi.airlyrics.settings.store.LyricsSettingsStore
import com.andsi.airlyrics.ui.components.*
import com.andsi.airlyrics.ui.model.MainUiHost
import com.andsi.airlyrics.ui.theme.*

/**
 * 設定子頁面 5：搜尋策略 (SearchStrategySettingsPage)。
 * 集中管理線上歌詞搜尋策略、候選清單彈出行為、Google 翻譯與全域時間軸偏移量。
 */
internal fun createSearchStrategySettingsPage(activity: MainUiHost): View = with(activity) {
    val container = pageContainer(activity, animateChanges = false)
    val lyricsSettings = lyricsSettingsState()

    container.addView(
        settingsBackHeader(
            title = "搜尋策略",
            subtitle = "自訂多來源檢索、自動備援、翻譯與全域時間軸對齊"
        )
    )

    // 1. 線上自動檢索與備援卡片
    container.addView(
        card(activity) {
            addView(bigText(activity, "自動檢索與儲存"))
            addView(smallHint(activity, "當本機無對應歌詞時，自動依優先級嘗試線上多來源搜尋並儲存"))

            val autoSearchButton = actionButton(
                activity,
                getString(if (lyricsSettings.autoSearchOnline) R.string.ui_online_fallback_on else R.string.ui_online_fallback_off)
            ) { }
            autoSearchButton.setOnClickListener {
                val enabled = uiActions.toggleLyricsAutoSearch()
                autoSearchButton.setText(if (enabled) R.string.ui_online_fallback_on else R.string.ui_online_fallback_off)
                playLocalRefreshFeedback(activity, autoSearchButton, null, getString(R.string.ui_updated))
            }
            addView(autoSearchButton)

            val autoSaveButton = actionButton(
                activity,
                getString(if (lyricsSettings.autoSaveLocal) R.string.ui_auto_save_on else R.string.ui_auto_save_off)
            ) { }
            autoSaveButton.setOnClickListener {
                val enabled = uiActions.toggleLyricsAutoSave()
                autoSaveButton.setText(if (enabled) R.string.ui_auto_save_on else R.string.ui_auto_save_off)
                playLocalRefreshFeedback(activity, autoSaveButton, null, getString(R.string.ui_updated))
            }
            addView(autoSaveButton)
        }
    )

    // 2. 候選清單與 Google 翻譯卡片
    container.addView(
        card(activity) {
            addView(bigText(activity, "搜尋互動與即時翻譯"))

            // 自動搜尋時彈出結果清單開關
            val autoPopupRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(AirUiTokens.Space.Sm), 0, dp(AirUiTokens.Space.Xs))
                val textCol = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        setMargins(0, 0, dp(AirUiTokens.Space.Md), 0)
                    }
                    addView(normalText(activity, "自動搜尋時彈出結果清單"))
                    addView(smallHint(activity, "關閉時依優先級自動套用最佳結果；開啟時於自動搜尋完成後彈出多來源候選清單"))
                }
                val popupSwitch = SwitchCompat(activity).apply {
                    isChecked = LyricsSettingsStore.isAutoSearchPopupEnabled(activity)
                    applyAirThemeTint(activity)
                    setOnCheckedChangeListener { _, checked ->
                        LyricsSettingsStore.setAutoSearchPopupEnabled(activity, checked)
                    }
                }
                addView(textCol)
                addView(popupSwitch)
            }
            addView(autoPopupRow)

            // 啟用本地 Google 翻譯開關
            val googleTranslateRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(AirUiTokens.Space.Xs), 0, dp(AirUiTokens.Space.Sm))
                val textCol = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        setMargins(0, 0, dp(AirUiTokens.Space.Md), 0)
                    }
                    addView(normalText(activity, "啟用本地 Google 翻譯"))
                    addView(smallHint(activity, "當外語歌曲無現成翻譯時，自動於背景呼叫 Google 翻譯補齊繁體中文對照並保留時間軸"))
                }
                val translateSwitch = SwitchCompat(activity).apply {
                    isChecked = LyricsSettingsStore.isLocalGoogleTranslateEnabled(activity)
                    applyAirThemeTint(activity)
                    setOnCheckedChangeListener { _, checked ->
                        LyricsSettingsStore.setLocalGoogleTranslateEnabled(activity, checked)
                    }
                }
                addView(textCol)
                addView(translateSwitch)
            }
            addView(googleTranslateRow)
        }
    )

    // 3. 全域時間軸偏移量設定卡片
    container.addView(globalOffsetCard(activity))

    return scroll(activity, container, maxWidthDp = 1200)
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
