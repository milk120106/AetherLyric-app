package com.andsi.airlyrics.ui.pages.settings

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.andsi.airlyrics.R
import com.andsi.airlyrics.community.CommunityServer
import com.andsi.airlyrics.community.CommunityServerExplorer
import com.andsi.airlyrics.community.NodeTrustLevel
import com.andsi.airlyrics.design.tokens.AirUiTokens
import com.andsi.airlyrics.ui.components.*
import com.andsi.airlyrics.ui.model.MainUiHost
import com.andsi.airlyrics.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 公開伺服器探索頁面 (CommunityServersPage)。
 * 實作兩段式加入流程、五級信任狀態視覺化標籤、能力標籤展示與黑名單攔截。
 */
internal fun createCommunityServersPage(activity: MainUiHost): View = with(activity) {
    val container = pageContainer(activity, animateChanges = false)

    container.addView(
        settingsBackHeader(
            title = "公開伺服器探索",
            subtitle = "探索社群公開伺服器節點，擴展 AI 轉錄與高可用性歌詞快取"
        )
    )

    val loadingRow = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(AirUiTokens.Space.CardH), dp(AirUiTokens.Space.Lg), dp(AirUiTokens.Space.CardH), dp(AirUiTokens.Space.Lg))
        addView(ProgressBar(activity).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                setMargins(0, 0, dp(AirUiTokens.Space.Sm), 0)
            }
        })
        addView(TextView(activity).apply {
            text = "正在讀取公開伺服器清單與黑名單資料庫…"
            textSize = AirUiTokens.TextSize.BodySmall
            setTextColor(colorTextMuted)
        })
    }
    container.addView(loadingRow)

    val listContainer = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    container.addView(listContainer)

    fun renderServerList(servers: List<CommunityServer>) {
        listContainer.removeAllViews()
        loadingRow.visibility = View.GONE

        servers.forEach { server ->
            val cardItem = card(activity) {
                isClickable = true
                isFocusable = true
                enableSoftPressFeedback(AirUiTokens.Motion.DefaultPressScale)

                // 第一列：節點名稱、地區代碼與五級信任標籤
                val topRow = LinearLayout(activity).apply {
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
                        text = "${server.name} [${server.regionCode}]"
                        textSize = AirUiTokens.TextSize.BodySmall
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(colorTextStrong)
                    })
                    addView(TextView(activity).apply {
                        text = "${server.url} · 延遲 ${server.latencyMs}ms"
                        textSize = AirUiTokens.TextSize.Tiny
                        setTextColor(colorTextMuted)
                    })
                }
                topRow.addView(titleCol)

                val (badgeColor, badgeText) = when (server.trustLevel) {
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
                }
                topRow.addView(trustBadge)
                addView(topRow)

                // 第二列：伺服器能力標籤 (AI辨識 / TTML / yt-dlp)
                if (server.capabilities.isNotEmpty()) {
                    val capsRow = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, dp(AirUiTokens.Space.Xs), 0, dp(AirUiTokens.Space.Xs))
                    }
                    server.capabilities.forEach { cap ->
                        val capPill = TextView(activity).apply {
                            text = cap
                            textSize = AirUiTokens.TextSize.Tiny
                            setTextColor(colorAccentMint)
                            setPadding(dp(6), dp(2), dp(6), dp(2))
                            background = GradientDrawable().apply {
                                cornerRadius = dp(AirUiTokens.Radius.Sm).toFloat()
                                setColor(colorSurfaceLight)
                                setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
                            }
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply {
                                setMargins(0, 0, dp(4), 0)
                            }
                        }
                        capsRow.addView(capPill)
                    }
                    addView(capsRow)
                }

                // 描述
                addView(TextView(activity).apply {
                    text = server.description
                    textSize = AirUiTokens.TextSize.Caption
                    setTextColor(colorText)
                    setPadding(0, dp(AirUiTokens.Space.Xs), 0, 0)
                })

                // 第二段：點擊彈出詳細資訊確認對話框
                setOnClickListener {
                    showServerJoinDetailDialog(activity, server)
                }
            }
            listContainer.addView(cardItem)
        }
    }

    activity.activity.lifecycleScope.launch(Dispatchers.IO) {
        val servers = CommunityServerExplorer.fetchCommunityServers(activity)
        withContext(Dispatchers.Main) {
            renderServerList(servers)
        }
    }

    return scroll(activity, container, maxWidthDp = 1200)
}

/**
 * 第二段加入確認對話框。
 */
private fun showServerJoinDetailDialog(activity: MainUiHost, server: CommunityServer) = with(activity) {
    // 檢查危險 IP 黑名單
    if (CommunityServerExplorer.isBlacklisted(server.url)) {
        showAirDialog(
            title = "安全警告：惡意節點攔截",
            message = "伺服器位址「${server.url}」命中安全中心已知危險 IP / 惡意節點黑名單，系統已主動攔截連線，嚴格禁止加入！",
            positiveText = "確定",
            negativeText = null
        )
        return@with
    }

    if (server.trustLevel == NodeTrustLevel.BANNED) {
        showAirDialog(
            title = "安全警告：封禁節點",
            message = "節點「${server.name}」已被官方安全資料庫標記為封禁節點，因存在惡意偽造風險，嚴格禁止加入本地列表。",
            positiveText = "確定",
            negativeText = null
        )
        return@with
    }

    val warningMsg = StringBuilder()
    if (server.regionCode != "TW" && server.regionCode != "LOCAL") {
        warningMsg.append("跨區連線提示：此節點所在區域為 [${server.regionCode}]，平均延遲約 ${server.latencyMs}ms，跨國傳輸時 AI 轉錄佇列可能存在較大延遲。\n\n")
    }
    if (server.trustLevel == NodeTrustLevel.SUSPICIOUS) {
        warningMsg.append("風險提示：此節點已被標註為可疑節點，可能存在不穩定或安全疑慮，請謹慎連線。\n\n")
    }

    warningMsg.append("支援能力：${if (server.capabilities.isEmpty()) "基礎搜尋" else server.capabilities.joinToString("、")}\n")
    warningMsg.append("維護人員：${server.maintainer}\n")
    warningMsg.append("位址：${server.url}\n\n")
    warningMsg.append("是否確認將此節點加入本地伺服器清單？")

    showAirDialog(
        title = "加入社群伺服器：${server.name}",
        message = warningMsg.toString().trim(),
        positiveText = "確認加入",
        negativeText = getString(R.string.ui_cancel),
        onPositive = {
            try {
                CommunityServerExplorer.addCommunityServerToLocal(activity, server)
                Toast.makeText(activity, "已成功將「${server.name}」加入伺服器列表", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(activity, "加入失敗：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    )
}
