package com.andsi.airlyrics.ui.pages.settings

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.andsi.airlyrics.R
import com.andsi.airlyrics.design.tokens.AirUiTokens
import com.andsi.airlyrics.ui.components.bigText
import com.andsi.airlyrics.ui.components.card
import com.andsi.airlyrics.ui.components.pageContainer
import com.andsi.airlyrics.ui.components.scroll
import com.andsi.airlyrics.ui.components.settingRow
import com.andsi.airlyrics.ui.components.smallHint
import com.andsi.airlyrics.ui.model.MainUiHost
import com.andsi.airlyrics.ui.theme.colorAccentMint
import com.andsi.airlyrics.ui.theme.colorStroke
import com.andsi.airlyrics.ui.theme.colorSurfaceLight
import com.andsi.airlyrics.ui.theme.colorTextMuted
import com.andsi.airlyrics.ui.theme.colorTextStrong

/**
 * 設定子頁面 1：AetherLyric 伺服器 (ServerSettingsPage)
 * 集中管理自架伺服器連線、ADMIN_API_KEY、雙引擎開關與支援來源清單。
 */
internal fun createServerSettingsPage(activity: MainUiHost): View = with(activity) {
    val container = pageContainer(activity, animateChanges = false)

    container.addView(
        settingsBackHeader(
            title = getString(R.string.ui_server_subpage_title),
            subtitle = getString(R.string.ui_server_subpage_desc)
        )
    )

    // 1. 伺服器連線與雙引擎主卡片
    container.addView(createAetherLyricServerCard(activity))

    // 2. 伺服器支援能力與來源清單卡片
    container.addView(card(activity) {
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
                if (!com.andsi.airlyrics.settings.store.AppSettingsStore.isCompactMode(activity)) {
                    addView(TextView(activity).apply {
                        text = desc
                        textSize = AirUiTokens.TextSize.Caption
                        setTextColor(colorTextMuted)
                        setPadding(0, dp(AirUiTokens.Space.Xxs), 0, 0)
                    })
                }
            })
        }
    })

    return scroll(activity, container, maxWidthDp = 1200)
}
