package com.andsi.airlyrics.ui.pages.settings

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import com.andsi.airlyrics.R
import com.andsi.airlyrics.community.NodeTrustLevel
import com.andsi.airlyrics.design.tokens.AirUiTokens
import com.andsi.airlyrics.lyrics.providers.CustomJsonPluginStore
import com.andsi.airlyrics.plugins.PluginCategory
import com.andsi.airlyrics.plugins.PluginStoreManager
import com.andsi.airlyrics.plugins.StorePluginItem
import com.andsi.airlyrics.ui.components.*
import com.andsi.airlyrics.ui.model.MainUiHost
import com.andsi.airlyrics.ui.theme.*

/**
 * 擴充商店頁面 (PluginStorePage)。
 * 支援分類分頁瀏覽、五級信任狀態視覺化標籤、維護者等級徽章與自動安全禁用。
 */
internal fun createPluginStorePage(activity: MainUiHost): View = with(activity) {
    val container = pageContainer(activity, animateChanges = false)

    container.addView(
        settingsBackHeader(
            title = "擴充商店",
            subtitle = "探索與安裝第三方歌詞來源、多語系翻譯與系統增強外掛"
        )
    )

    // 執行社群庫安全比對
    PluginStoreManager.performAutoDisableCheck(activity)

    var currentCategory = PluginCategory.LYRICS_SOURCE
    val listContainer = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    fun renderPlugins() {
        listContainer.removeAllViews()
        val allPlugins = PluginStoreManager.getStorePlugins(activity)
        val filtered = allPlugins.filter { it.category == currentCategory }

        if (filtered.isEmpty()) {
            listContainer.addView(
                card(activity) {
                    addView(normalText(activity, "此分類目前尚無擴充外掛"))
                }
            )
            return
        }

        filtered.forEach { item ->
            val itemCard = card(activity) {
                // 第一列：外掛名稱、版本、維護者等級與五級信任狀態標籤
                val headerRow = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }

                val titleCol = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    addView(TextView(activity).apply {
                        text = "${item.name} v${item.version}"
                        textSize = AirUiTokens.TextSize.BodySmall
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(colorTextStrong)
                    })
                    addView(TextView(activity).apply {
                        text = "作者：${item.author} · 維護層級：${item.maintainerBadge}"
                        textSize = AirUiTokens.TextSize.Tiny
                        setTextColor(colorTextMuted)
                    })
                }
                headerRow.addView(titleCol)

                // 五級信任標籤 (official=藍, verified=綠, unverified=灰, suspicious=黃, banned=紅)
                val (badgeColor, badgeText) = when (item.trustLevel) {
                    NodeTrustLevel.OFFICIAL -> Color.parseColor("#1976D2") to "官方"
                    NodeTrustLevel.VERIFIED -> Color.parseColor("#388E3C") to "已驗證"
                    NodeTrustLevel.UNVERIFIED -> Color.parseColor("#757575") to "未驗證"
                    NodeTrustLevel.SUSPICIOUS -> Color.parseColor("#F57C00") to "可疑"
                    NodeTrustLevel.BANNED -> Color.parseColor("#D32F2F") to "已封禁"
                }

                val trustBadge = TextView(activity).apply {
                    text = badgeText
                    textSize = AirUiTokens.TextSize.Tiny
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                    setPadding(dp(8), dp(2), dp(8), dp(2))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(AirUiTokens.Radius.Pill).toFloat()
                        setColor(badgeColor)
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(dp(AirUiTokens.Space.Xs), 0, 0, 0)
                    }
                }
                headerRow.addView(trustBadge)
                addView(headerRow)

                // 描述
                addView(TextView(activity).apply {
                    text = item.description
                    textSize = AirUiTokens.TextSize.Caption
                    setTextColor(colorText)
                    setPadding(0, dp(AirUiTokens.Space.Xs), 0, dp(AirUiTokens.Space.Sm))
                })

                if (item.banReason != null) {
                    addView(TextView(activity).apply {
                        text = "安全警告：${item.banReason}"
                        textSize = AirUiTokens.TextSize.Tiny
                        setTextColor(colorDanger)
                        setPadding(0, 0, 0, dp(AirUiTokens.Space.Xs))
                    })
                }

                // 操作按鈕列
                val actionsRow = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }

                if (item.trustLevel == NodeTrustLevel.BANNED) {
                    val bannedNotice = TextView(activity).apply {
                        text = "此來源已被官方安全庫永久封禁，禁止安裝"
                        textSize = AirUiTokens.TextSize.Caption
                        setTextColor(colorDanger)
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    actionsRow.addView(bannedNotice)
                } else if (item.isInstalled) {
                    val statusLabel = TextView(activity).apply {
                        text = "已安裝至本地"
                        textSize = AirUiTokens.TextSize.BodySmall
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(colorAccentMint)
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    actionsRow.addView(statusLabel)

                    val enableSwitch = SwitchCompat(activity).apply {
                        isChecked = item.isEnabled
                        applyAirThemeTint(activity)
                        setOnCheckedChangeListener { _, checked ->
                            CustomJsonPluginStore.setPluginEnabled(activity, item.id, checked)
                            Toast.makeText(activity, if (checked) "已啟用外掛" else "已停用外掛", Toast.LENGTH_SHORT).show()
                        }
                    }
                    actionsRow.addView(enableSwitch)
                } else {
                    val installBtn = actionButton(activity, "取得並安裝") {
                        try {
                            PluginStoreManager.installStorePlugin(activity, item)
                            Toast.makeText(activity, "外掛「${item.name}」安裝成功！\n本擴充採用 MIT 開源授權，請確認您信任該來源。", Toast.LENGTH_LONG).show()
                            renderPlugins()
                        } catch (e: Exception) {
                            Toast.makeText(activity, "安裝失敗：${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }.apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }
                    actionsRow.addView(View(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
                    })
                    actionsRow.addView(installBtn)
                }

                addView(actionsRow)
            }
            listContainer.addView(itemCard)
        }
    }

    // 分類分頁列
    val tabRow = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(AirUiTokens.Space.CardH), dp(AirUiTokens.Space.Sm), dp(AirUiTokens.Space.CardH), dp(AirUiTokens.Space.Sm))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    val categories = listOf(
        PluginCategory.LYRICS_SOURCE to "歌詞來源",
        PluginCategory.TRANSLATOR to "翻譯引擎",
        PluginCategory.SYSTEM_TOOL to "系統工具"
    )

    fun updateTabs() {
        tabRow.removeAllViews()
        categories.forEach { (cat, title) ->
            val isSelected = cat == currentCategory
            val tabBtn = TextView(activity).apply {
                text = title
                textSize = AirUiTokens.TextSize.BodySmall
                typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (isSelected) colorOnAccent else colorTextStrong)
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = GradientDrawable().apply {
                    cornerRadius = dp(AirUiTokens.Radius.Pill).toFloat()
                    setColor(if (isSelected) colorAccent else colorSurfaceLight)
                    setStroke(dp(AirUiTokens.Stroke.Hairline), if (isSelected) colorAccent else colorStroke)
                }
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(3), 0, dp(3), 0)
                }
                isClickable = true
                isFocusable = true
                enableSoftPressFeedback(AirUiTokens.Motion.DefaultPressScale)
                setOnClickListener {
                    currentCategory = cat
                    updateTabs()
                    renderPlugins()
                }
            }
            tabRow.addView(tabBtn)
        }
    }

    updateTabs()
    container.addView(tabRow)

    renderPlugins()
    container.addView(listContainer)

    container.addView(TextView(activity).apply {
        text = "本擴充採用 MIT 開源授權，請確認您信任該來源。"
        textSize = AirUiTokens.TextSize.BodySmall
        setTextColor(colorTextMuted)
        setPadding(dp(AirUiTokens.Space.CardH), dp(AirUiTokens.Space.Sm), dp(AirUiTokens.Space.CardH), dp(AirUiTokens.Space.Sm))
    })

    Thread {
        val ok = kotlinx.coroutines.runBlocking { PluginStoreManager.syncRemoteStorePlugins(activity) }
        if (ok) container.post { renderPlugins() }
    }.start()

    return scroll(activity, container, maxWidthDp = 1200)
}
