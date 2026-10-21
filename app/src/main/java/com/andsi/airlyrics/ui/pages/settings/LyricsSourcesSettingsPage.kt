package com.andsi.airlyrics.ui.pages.settings

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.andsi.airlyrics.R
import com.andsi.airlyrics.design.tokens.AirUiTokens
import com.andsi.airlyrics.logging.AppLogger
import com.andsi.airlyrics.settings.store.LyricsSettingsStore
import com.andsi.airlyrics.ui.components.actionButton
import com.andsi.airlyrics.ui.components.bigText
import com.andsi.airlyrics.ui.components.card
import com.andsi.airlyrics.ui.components.pageContainer
import com.andsi.airlyrics.ui.components.scroll
import com.andsi.airlyrics.ui.components.smallHint
import com.andsi.airlyrics.ui.model.MainUiHost
import com.andsi.airlyrics.ui.theme.colorStroke
import com.andsi.airlyrics.ui.theme.colorSurfaceLight
import com.andsi.airlyrics.ui.theme.colorTextMuted
import com.andsi.airlyrics.ui.theme.colorTextStrong

/**
 * 設定子頁面 2：歌詞源管理 (LyricsSourcesSettingsPage)
 * 支援來源優先順序拖曳排序、勾選啟用與 BYOK 金鑰管理。
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

    // 2. 自訂 API Key (BYOK) 卡片
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
