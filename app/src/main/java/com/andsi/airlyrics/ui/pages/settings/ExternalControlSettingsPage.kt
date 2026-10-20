package com.andsi.airlyrics.ui.pages.settings

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
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
import com.andsi.airlyrics.external.ExternalControlStore
import com.andsi.airlyrics.logging.AppLogger
import com.andsi.airlyrics.settings.store.AppSettingsStore
import com.andsi.airlyrics.ui.components.actionButton
import com.andsi.airlyrics.ui.components.bigText
import com.andsi.airlyrics.ui.components.card
import com.andsi.airlyrics.ui.components.enableSoftPressFeedback
import com.andsi.airlyrics.ui.components.pageContainer
import com.andsi.airlyrics.ui.components.scroll
import com.andsi.airlyrics.ui.components.showAirDialog
import com.andsi.airlyrics.ui.components.smallHint
import com.andsi.airlyrics.ui.model.MainUiHost
import com.andsi.airlyrics.ui.theme.applyAirThemeTint
import com.andsi.airlyrics.ui.theme.colorAccent
import com.andsi.airlyrics.ui.theme.colorOnAccent
import com.andsi.airlyrics.ui.theme.colorStroke
import com.andsi.airlyrics.ui.theme.colorSurfaceLight
import com.andsi.airlyrics.ui.theme.colorTextMuted
import com.andsi.airlyrics.ui.theme.colorTextStrong

/**
 * 設定子頁面 4：擴充與外部控制 (ExternalControlSettingsPage)
 * 集中管理 Intent 廣播 API、安全性免責聲明、各 App 白名單/黑名單管理（選單彈窗式）。
 */
internal fun createExternalControlSettingsPage(activity: MainUiHost): View = with(activity) {
    val container = pageContainer(activity, animateChanges = false)
    val isCompact = AppSettingsStore.isCompactMode(activity)

    container.addView(
        settingsBackHeader(
            title = getString(R.string.ui_external_control_subpage_title),
            subtitle = getString(R.string.ui_external_control_subpage_desc)
        )
    )

    // 1. Intent 遠端控制 API 防護卡片
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

        if (!isCompact) {
            addView(TextView(activity).apply {
                text = "支援的 Broadcast Action：\n· com.aetherlyric.app.action.SHOW_OVERLAY\n· com.aetherlyric.app.action.HIDE_OVERLAY\n· com.aetherlyric.app.action.TOGGLE_OVERLAY\n· android.intent.action.SEND (text/plain, 限制 50KB)"
                textSize = AirUiTokens.TextSize.Caption
                setTextColor(colorTextMuted)
                setPadding(0, dp(AirUiTokens.Space.Sm), 0, 0)
            })
        }
    })

    // 2. 應用程式權限管理卡片 (改為 Dialog 選單彈窗設計，移除嵌入式大按鈕)
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

            val behaviorBtn = TextView(activity).apply {
                text = when (currentBehavior) {
                    ExternalControlStore.BEHAVIOR_DENY -> getString(R.string.ui_behavior_deny)
                    ExternalControlStore.BEHAVIOR_ALLOW -> getString(R.string.ui_behavior_allow)
                    else -> getString(R.string.ui_behavior_ask)
                }
                textSize = AirUiTokens.TextSize.Caption
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colorOnAccent)
                gravity = Gravity.CENTER
                setPadding(dp(AirUiTokens.Space.Lg), dp(AirUiTokens.Space.Xs), dp(AirUiTokens.Space.Lg), dp(AirUiTokens.Space.Xs))
                background = GradientDrawable().apply {
                    cornerRadius = dp(AirUiTokens.Radius.Pill).toFloat()
                    setColor(colorAccent)
                }
                enableSoftPressFeedback(AirUiTokens.Motion.DefaultPressScale)
                setOnClickListener {
                    currentBehavior = when (currentBehavior) {
                        ExternalControlStore.BEHAVIOR_ALLOW -> ExternalControlStore.BEHAVIOR_DENY
                        ExternalControlStore.BEHAVIOR_DENY -> ExternalControlStore.BEHAVIOR_ASK
                        else -> ExternalControlStore.BEHAVIOR_ALLOW
                    }
                    ExternalControlStore.setUnknownAppBehavior(activity, currentBehavior)
                    text = when (currentBehavior) {
                        ExternalControlStore.BEHAVIOR_DENY -> getString(R.string.ui_behavior_deny)
                        ExternalControlStore.BEHAVIOR_ALLOW -> getString(R.string.ui_behavior_allow)
                        else -> getString(R.string.ui_behavior_ask)
                    }
                    AppLogger.i("ExternalControl", "未知 App 預設行為變更為: $text")
                }
            }
            addView(behaviorBtn)
        }
        addView(behaviorRow)

        // 彈出式 App 選擇選單按鈕
        val manageAppsButton = actionButton(activity, "選擇應用程式進行權限設定…") {
            showAppPermissionsDialog(activity)
        }
        addView(manageAppsButton)
    })

    return scroll(activity, container, maxWidthDp = 1200)
}

/**
 * 彈窗選單 (Dialog) 選擇與設定個別應用程式權限
 */
private fun showAppPermissionsDialog(activity: MainUiHost) = with(activity) {
    showAirDialog(
        title = "應用程式權限管理",
        positiveText = getString(R.string.ui_ok),
        body = {
            val searchInput = EditText(activity).apply {
                hint = "搜尋 App 名稱或套件名稱…"
                textSize = AirUiTokens.TextSize.BodySmall
                setTextColor(colorTextStrong)
                setHintTextColor(colorTextMuted)
                background = GradientDrawable().apply {
                    cornerRadius = dp(AirUiTokens.Radius.Sm).toFloat()
                    setColor(colorSurfaceLight)
                    setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
                }
                setPadding(dp(AirUiTokens.Space.Sm), dp(AirUiTokens.Space.Sm), dp(AirUiTokens.Space.Sm), dp(AirUiTokens.Space.Sm))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            addView(searchInput)

            var filterMusicOnly = true
            val filterToggle = TextView(activity).apply {
                text = "目前篩選：僅音樂播放器 (點擊切換為所有 App)"
                textSize = AirUiTokens.TextSize.Caption
                setTextColor(colorAccent)
                setPadding(0, dp(AirUiTokens.Space.Sm), 0, dp(AirUiTokens.Space.Sm))
                enableSoftPressFeedback(AirUiTokens.Motion.DefaultPressScale)
            }
            addView(filterToggle)

            val listContainer = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
            }
            addView(listContainer)

            fun renderList(query: String) {
                listContainer.removeAllViews()
                val pm = activity.packageManager
                val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                val filtered = packages.filter { app ->
                    if (app.packageName == activity.packageName) return@filter false
                    val appName = app.loadLabel(pm).toString()
                    val matchesQuery = query.isBlank() ||
                        appName.contains(query, ignoreCase = true) ||
                        app.packageName.contains(query, ignoreCase = true)
                    if (!matchesQuery) return@filter false
                    if (filterMusicOnly) {
                        val name = app.packageName.lowercase()
                        name.contains("music") || name.contains("spotify") || name.contains("audio") ||
                        name.contains("player") || name.contains("netease") || name.contains("kugou") ||
                        name.contains("qqmusic") || name.contains("youtube") || name.contains("podcast")
                    } else {
                        (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                    }
                }.sortedBy { it.loadLabel(pm).toString() }.take(50)

                if (filtered.isEmpty()) {
                    listContainer.addView(TextView(activity).apply {
                        text = "未找到相符的應用程式"
                        textSize = AirUiTokens.TextSize.BodySmall
                        setTextColor(colorTextMuted)
                        setPadding(0, dp(AirUiTokens.Space.Lg), 0, dp(AirUiTokens.Space.Lg))
                    })
                    return
                }

                filtered.forEach { app ->
                    val appName = app.loadLabel(pm).toString()
                    val pkg = app.packageName
                    var record = ExternalControlStore.getAppPermission(activity, pkg)
                        ?: ExternalControlStore.AppPermissionRecord(pkg, appName)

                    val itemRow = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, dp(AirUiTokens.Space.Sm), 0, dp(AirUiTokens.Space.Sm))

                        val textLayout = LinearLayout(activity).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                            addView(TextView(activity).apply {
                                text = appName
                                textSize = AirUiTokens.TextSize.BodySmall
                                typeface = Typeface.DEFAULT_BOLD
                                setTextColor(colorTextStrong)
                            })
                            addView(TextView(activity).apply {
                                text = pkg
                                textSize = AirUiTokens.TextSize.Caption
                                setTextColor(colorTextMuted)
                            })
                        }
                        addView(textLayout)

                        val toggleBtn = TextView(activity).apply {
                            text = if (record.behavior == ExternalControlStore.BEHAVIOR_ALLOW) "允許" else "拒絕"
                            textSize = AirUiTokens.TextSize.Caption
                            typeface = Typeface.DEFAULT_BOLD
                            setTextColor(if (record.behavior == ExternalControlStore.BEHAVIOR_ALLOW) colorOnAccent else colorTextStrong)
                            gravity = Gravity.CENTER
                            setPadding(dp(AirUiTokens.Space.Lg), dp(AirUiTokens.Space.Xs), dp(AirUiTokens.Space.Lg), dp(AirUiTokens.Space.Xs))
                            background = GradientDrawable().apply {
                                cornerRadius = dp(AirUiTokens.Radius.Pill).toFloat()
                                setColor(if (record.behavior == ExternalControlStore.BEHAVIOR_ALLOW) colorAccent else colorSurfaceLight)
                                setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
                            }
                            enableSoftPressFeedback(AirUiTokens.Motion.DefaultPressScale)
                            setOnClickListener {
                                val newBehavior = if (record.behavior == ExternalControlStore.BEHAVIOR_ALLOW) ExternalControlStore.BEHAVIOR_DENY else ExternalControlStore.BEHAVIOR_ALLOW
                                record = record.copy(behavior = newBehavior)
                                ExternalControlStore.saveAppPermission(activity, record)
                                text = if (newBehavior == ExternalControlStore.BEHAVIOR_ALLOW) "允許" else "拒絕"
                                setTextColor(if (newBehavior == ExternalControlStore.BEHAVIOR_ALLOW) colorOnAccent else colorTextStrong)
                                (background as? GradientDrawable)?.setColor(if (newBehavior == ExternalControlStore.BEHAVIOR_ALLOW) colorAccent else colorSurfaceLight)
                                AppLogger.i("ExternalControl", "已變更 App [$appName] 權限為: $text")
                            }
                        }
                        addView(toggleBtn)
                    }
                    listContainer.addView(itemRow)
                }
            }

            filterToggle.setOnClickListener {
                filterMusicOnly = !filterMusicOnly
                filterToggle.text = if (filterMusicOnly) "目前篩選：僅音樂播放器 (點擊切換為所有 App)" else "目前篩選：所有已安裝 App (點擊切換為音樂播放器)"
                renderList(searchInput.text.toString())
            }

            searchInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    renderList(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) {}
            })

            renderList("")
        }
    )
}
