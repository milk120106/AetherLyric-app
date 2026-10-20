package com.andsi.airlyrics.ui.pages.settings

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
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
import com.andsi.airlyrics.design.tokens.AirUiTokens
import com.andsi.airlyrics.external.ExternalControlStore
import com.andsi.airlyrics.logging.AppLogger
import com.andsi.airlyrics.permissions.PermissionTierManager
import com.andsi.airlyrics.ui.components.actionButton
import com.andsi.airlyrics.ui.components.bigText
import com.andsi.airlyrics.ui.components.card
import com.andsi.airlyrics.ui.components.pageContainer
import com.andsi.airlyrics.ui.components.scroll
import com.andsi.airlyrics.ui.components.settingRow
import com.andsi.airlyrics.ui.components.showAirDialog
import com.andsi.airlyrics.ui.components.smallHint
import com.andsi.airlyrics.ui.model.MainUiHost
import com.andsi.airlyrics.ui.theme.applyAirThemeTint
import com.andsi.airlyrics.ui.theme.colorAccent
import com.andsi.airlyrics.ui.theme.colorAccentMint
import com.andsi.airlyrics.ui.theme.colorDanger
import com.andsi.airlyrics.ui.theme.colorStroke
import com.andsi.airlyrics.ui.theme.colorSurfaceLight
import com.andsi.airlyrics.ui.theme.colorTextMuted
import com.andsi.airlyrics.ui.theme.colorTextStrong

/**
 * 設定子頁面 4：擴充與外部控制 (ExternalControlSettingsPage)
 * 集中管理 Intent 廣播 API、安全性免責聲明、各 App 白名單/黑名單，以及三層權限架構 (Shizuku > Accessibility > Basic)。
 */
internal fun createExternalControlSettingsPage(activity: MainUiHost): View = with(activity) {
    val container = pageContainer(activity, animateChanges = false)

    container.addView(
        settingsBackHeader(
            title = getString(R.string.ui_external_control_subpage_title),
            subtitle = getString(R.string.ui_external_control_subpage_desc)
        )
    )

    // 1. 三層權限架構卡片
    val currentTier = PermissionTierManager.getCurrentTier(activity)
    container.addView(card(activity) {
        addView(bigText(activity, getString(R.string.ui_permission_tier_title)))
        addView(smallHint(activity, "動態最高優先級：Shizuku (高權限) > 無障礙模式 (系統級) > 基礎模式 (通知與懸浮窗)"))

        val tierStatusText = TextView(activity).apply {
            text = "目前生效優先級別：${currentTier.displayName}"
            textSize = AirUiTokens.TextSize.BodySmall
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorAccentMint)
            setPadding(0, dp(AirUiTokens.Space.Sm), 0, dp(AirUiTokens.Space.Sm))
        }
        addView(tierStatusText)

        // 層級 1: Shizuku
        val isShizukuOk = PermissionTierManager.isShizukuAvailableAndGranted(activity)
        val shizukuRow = settingRow(
            activity,
            name = getString(R.string.ui_tier_shizuku_status),
            value = if (isShizukuOk) "已授權連線" else if (PermissionTierManager.isShizukuInstalled(activity)) "未執行或未授權" else "未安裝"
        )
        addView(shizukuRow)
        if (!isShizukuOk) {
            addView(actionButton(activity, getString(R.string.ui_open_dev_settings)) {
                PermissionTierManager.openDevelopmentSettings(activity)
            })
        }

        // 層級 2: 無障礙模式
        val isA11yOk = PermissionTierManager.isAccessibilityEnabled(activity)
        val isRestricted = PermissionTierManager.isRestrictedSettingsBlocked(activity)
        val a11yRow = settingRow(
            activity,
            name = getString(R.string.ui_tier_accessibility_status),
            value = if (isA11yOk) "服務已開啟" else if (isRestricted) "已受系統限制" else "未開啟"
        )
        addView(a11yRow)

        if (isRestricted) {
            addView(TextView(activity).apply {
                text = "Android 13+ 偵測到受限制的設定，請點擊下方按鈕前往「應用程式資訊」，在右上角選單點擊「允許受限制的設定」。"
                textSize = AirUiTokens.TextSize.Caption
                setTextColor(colorDanger)
                setPadding(0, dp(AirUiTokens.Space.Xxs), 0, dp(AirUiTokens.Space.Xxs))
            })
            addView(actionButton(activity, getString(R.string.ui_unlock_restricted_settings)) {
                PermissionTierManager.openAppDetailsSettings(activity)
            })
        } else {
            addView(actionButton(activity, getString(R.string.ui_open_accessibility_settings)) {
                PermissionTierManager.openAccessibilitySettings(activity)
            })
        }

        // 層級 3: 基礎模式
        addView(settingRow(
            activity,
            name = getString(R.string.ui_tier_basic_status),
            value = if (hasNotificationListenerAccess() && overlayPermissionGranted) "正常運作" else "需授權"
        ))
    })

    // 2. Intent 遠端控制 API 防護卡片
    container.addView(card(activity) {
        addView(bigText(activity, getString(R.string.ui_intent_api_title)))
        addView(smallHint(activity, getString(R.string.ui_intent_api_desc)))

        val switchRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(AirUiTokens.Space.CardV), 0, 0)

            val label = TextView(activity).apply {
                text = "啟用 Intent 廣播接收器"
                textSize = AirUiTokens.TextSize.Body
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colorTextStrong)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(label)

            val intentSwitch = SwitchCompat(activity).apply {
                isChecked = ExternalControlStore.isIntentApiEnabled(activity)
                applyAirThemeTint(activity)
                setOnClickListener {
                    val willEnable = isChecked
                    if (willEnable) {
                        // 復原為原本開關狀態，等待二次確認
                        isChecked = false
                        showAirDialog(
                            title = getString(R.string.ui_intent_disclaimer_title),
                            message = getString(R.string.ui_intent_disclaimer_message),
                            positiveText = "同意並開啟",
                            negativeText = getString(R.string.ui_cancel),
                            onPositive = {
                                ExternalControlStore.setIntentApiEnabled(activity, true)
                                isChecked = true
                                AppLogger.i("ExternalControl", "使用者已同意免責聲明並啟用 Intent API")
                                Toast.makeText(activity, getString(R.string.ui_intent_enabled), Toast.LENGTH_SHORT).show()
                            }
                        )
                    } else {
                        ExternalControlStore.setIntentApiEnabled(activity, false)
                        AppLogger.i("ExternalControl", "使用者已停用 Intent API")
                        Toast.makeText(activity, getString(R.string.ui_intent_disabled), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            addView(intentSwitch)
        }
        addView(switchRow)

        // 支援之 Intent 廣播清單提示
        addView(TextView(activity).apply {
            text = "支援的 Broadcast Action：\n· com.aetherlyric.app.action.SHOW_OVERLAY\n· com.aetherlyric.app.action.HIDE_OVERLAY\n· com.aetherlyric.app.action.TOGGLE_OVERLAY\n· android.intent.action.SEND (text/plain, 限制 50KB)"
            textSize = AirUiTokens.TextSize.Caption
            setTextColor(colorTextMuted)
            setPadding(0, dp(AirUiTokens.Space.Sm), 0, 0)
        })
    })

    // 3. 擴充權限管理與白名單/黑名單卡片
    container.addView(card(activity) {
        addView(bigText(activity, "應用程式權限管理"))
        addView(smallHint(activity, "管理個別應用程式對懸浮歌詞控制與發送的存取權限"))

        // 未知 App 預設行為
        var currentBehavior = ExternalControlStore.getUnknownAppBehavior(activity)
        val behaviorRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(AirUiTokens.Space.CardV), 0, dp(AirUiTokens.Space.Sm))

            addView(TextView(activity).apply {
                text = getString(R.string.ui_unknown_app_behavior)
                textSize = AirUiTokens.TextSize.BodySmall
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colorTextStrong)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })

            val behaviorBtn = actionButton(activity, when (currentBehavior) {
                ExternalControlStore.BEHAVIOR_DENY -> getString(R.string.ui_behavior_deny)
                ExternalControlStore.BEHAVIOR_ALLOW -> getString(R.string.ui_behavior_allow)
                else -> getString(R.string.ui_behavior_ask)
            }) { }
            behaviorBtn.setOnClickListener {
                currentBehavior = when (currentBehavior) {
                    ExternalControlStore.BEHAVIOR_ALLOW -> ExternalControlStore.BEHAVIOR_DENY
                    ExternalControlStore.BEHAVIOR_DENY -> ExternalControlStore.BEHAVIOR_ASK
                    else -> ExternalControlStore.BEHAVIOR_ALLOW
                }
                ExternalControlStore.setUnknownAppBehavior(activity, currentBehavior)
                behaviorBtn.text = when (currentBehavior) {
                    ExternalControlStore.BEHAVIOR_DENY -> getString(R.string.ui_behavior_deny)
                    ExternalControlStore.BEHAVIOR_ALLOW -> getString(R.string.ui_behavior_allow)
                    else -> getString(R.string.ui_behavior_ask)
                }
                AppLogger.i("ExternalControl", "未知 App 預設行為變更為: ${behaviorBtn.text}")
            }
            addView(behaviorBtn)
        }
        addView(behaviorRow)

        // 智慧篩選與所有 App 切換檢視
        val appsListContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }

        var showAllApps = false
        val viewModeBtn = actionButton(activity, "切換為：所有已安裝應用程式") { }
        viewModeBtn.setOnClickListener {
            showAllApps = !showAllApps
            viewModeBtn.text = if (showAllApps) "切換為：智慧篩選 (僅音樂播放器)" else "切換為：所有已安裝應用程式"
            populateAppsList(activity, appsListContainer, showAllApps)
        }
        addView(viewModeBtn)
        addView(appsListContainer)

        populateAppsList(activity, appsListContainer, showAllApps)
    })

    return scroll(activity, container, maxWidthDp = 1200)
}

/**
 * 載入應用程式白名單/黑名單列表
 */
private fun populateAppsList(activity: MainUiHost, container: LinearLayout, showAll: Boolean) {
    container.removeAllViews()
    val pm = activity.packageManager
    val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

    val targetApps = packages.filter { app ->
        if (app.packageName == activity.packageName) return@filter false
        if (showAll) {
            (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0
        } else {
            // 智慧篩選：檢查是否為常見音樂 App 或具備媒體意圖
            val name = app.packageName.lowercase()
            name.contains("music") || name.contains("spotify") || name.contains("audio") ||
            name.contains("player") || name.contains("netease") || name.contains("kugou") ||
            name.contains("qqmusic") || name.contains("youtube") || name.contains("podcast")
        }
    }.sortedBy { it.loadLabel(pm).toString() }.take(30)

    if (targetApps.isEmpty()) {
        container.addView(TextView(activity).apply {
            text = "未找到相符的應用程式"
            textSize = AirUiTokens.TextSize.Caption
            setTextColor(activity.colorTextMuted)
            setPadding(0, activity.dp(AirUiTokens.Space.Sm), 0, 0)
        })
        return
    }

    targetApps.forEach { app ->
        val appName = app.loadLabel(pm).toString()
        val pkg = app.packageName
        var record = ExternalControlStore.getAppPermission(activity, pkg)
            ?: ExternalControlStore.AppPermissionRecord(pkg, appName)

        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, activity.dp(AirUiTokens.Space.Sm), 0, activity.dp(AirUiTokens.Space.Sm))

            val info = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

                addView(TextView(activity).apply {
                    text = appName
                    textSize = AirUiTokens.TextSize.BodySmall
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(activity.colorTextStrong)
                })
                addView(TextView(activity).apply {
                    text = pkg
                    textSize = AirUiTokens.TextSize.Caption
                    setTextColor(activity.colorTextMuted)
                })
            }
            addView(info)

            val toggleBtn = actionButton(activity, if (record.behavior == ExternalControlStore.BEHAVIOR_ALLOW) "允許" else "拒絕") { }
            toggleBtn.setOnClickListener {
                val newBehavior = if (record.behavior == ExternalControlStore.BEHAVIOR_ALLOW) ExternalControlStore.BEHAVIOR_DENY else ExternalControlStore.BEHAVIOR_ALLOW
                record = record.copy(behavior = newBehavior)
                ExternalControlStore.saveAppPermission(activity, record)
                toggleBtn.text = if (newBehavior == ExternalControlStore.BEHAVIOR_ALLOW) "允許" else "拒絕"
                AppLogger.i("ExternalControl", "已變更 App [$appName] 權限為: ${toggleBtn.text}")
            }
            addView(toggleBtn)
        }
        container.addView(row)
    }
}
