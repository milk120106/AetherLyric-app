package com.andsi.airlyrics.ui.pages.settings

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
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
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
                text = "支援的 Broadcast Action（需具備 com.airlyrics.app.permission.ACCESS_LYRICS 權限，並支援 reply_pending_intent 雙向回傳）：\n· com.aetherlyric.app.action.SHOW_OVERLAY\n· com.aetherlyric.app.action.HIDE_OVERLAY\n· com.aetherlyric.app.action.TOGGLE_OVERLAY\n· com.aetherlyric.app.action.QUERY_LYRICS\n· android.intent.action.SEND (text/plain, 限制 50KB)"
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

private data class CachedInstalledAppEntry(
    val packageName: String,
    val appName: String,
    val isMusicCandidate: Boolean,
    val isUserApp: Boolean
)

@Volatile
private var cachedInstalledApps: List<CachedInstalledAppEntry>? = null

/**
 * 彈窗選單 (Dialog) 選擇與設定個別應用程式權限
 * 採用 Dispatchers.IO 非同步掃描 PackageManager 並搭配記憶體快取與重新整理按鈕，避免主執行緒卡頓。
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
            val toolbarRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(AirUiTokens.Space.Sm), 0, dp(AirUiTokens.Space.Sm))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val filterToggle = TextView(activity).apply {
                text = "目前篩選：僅音樂播放器 (點擊切換)"
                textSize = AirUiTokens.TextSize.Caption
                setTextColor(colorAccent)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                enableSoftPressFeedback(AirUiTokens.Motion.DefaultPressScale)
            }

            val refreshBtn = TextView(activity).apply {
                text = getString(R.string.ui_refresh_media_status)
                textSize = AirUiTokens.TextSize.Caption
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colorAccent)
                setPadding(dp(AirUiTokens.Space.Md), dp(AirUiTokens.Space.Xxs), dp(AirUiTokens.Space.Md), dp(AirUiTokens.Space.Xxs))
                background = GradientDrawable().apply {
                    cornerRadius = dp(AirUiTokens.Radius.Pill).toFloat()
                    setColor(colorSurfaceLight)
                    setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
                }
                enableSoftPressFeedback(AirUiTokens.Motion.DefaultPressScale)
            }

            toolbarRow.addView(filterToggle)
            toolbarRow.addView(refreshBtn)
            addView(toolbarRow)

            val loadingLayout = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(AirUiTokens.Space.Lg), 0, dp(AirUiTokens.Space.Lg))
                visibility = View.GONE
                addView(ProgressBar(activity).apply {
                    isIndeterminate = true
                    indeterminateTintList = ColorStateList.valueOf(colorAccent)
                    layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                        setMargins(0, 0, dp(AirUiTokens.Space.Sm), 0)
                    }
                })
                addView(TextView(activity).apply {
                    text = "正在載入已安裝的應用程式…"
                    textSize = AirUiTokens.TextSize.BodySmall
                    setTextColor(colorTextMuted)
                })
            }
            addView(loadingLayout)

            val listContainer = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
            }
            addView(listContainer)

            fun renderFromCache(query: String) {
                val apps = cachedInstalledApps ?: return
                listContainer.removeAllViews()
                val trimmed = query.trim()
                val filtered = apps.asSequence().filter { app ->
                    val matchesQuery = trimmed.isEmpty() ||
                        app.appName.contains(trimmed, ignoreCase = true) ||
                        app.packageName.contains(trimmed, ignoreCase = true)
                    if (!matchesQuery) return@filter false
                    if (filterMusicOnly) app.isMusicCandidate else app.isUserApp
                }.take(50).toList()

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
                    val appName = app.appName
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

            fun loadAppsAsync(forceRefresh: Boolean) {
                if (!forceRefresh && cachedInstalledApps != null) {
                    loadingLayout.visibility = View.GONE
                    renderFromCache(searchInput.text?.toString().orEmpty())
                    return
                }
                loadingLayout.visibility = View.VISIBLE
                listContainer.removeAllViews()
                val selfPkg = activity.packageName
                val pm = activity.packageManager
                activity.activity.lifecycleScope.launch(Dispatchers.IO) {
                    val scanned = runCatching {
                        pm.getInstalledApplications(PackageManager.GET_META_DATA)
                            .asSequence()
                            .filter { it.packageName != selfPkg }
                            .map { app ->
                                val label = runCatching { app.loadLabel(pm).toString() }.getOrDefault(app.packageName)
                                val lowerPkg = app.packageName.lowercase()
                                val isMusic = lowerPkg.contains("music") || lowerPkg.contains("spotify") ||
                                    lowerPkg.contains("audio") || lowerPkg.contains("player") ||
                                    lowerPkg.contains("netease") || lowerPkg.contains("kugou") ||
                                    lowerPkg.contains("qqmusic") || lowerPkg.contains("youtube") ||
                                    lowerPkg.contains("podcast") || lowerPkg.contains("apple.android.music")
                                val isUser = (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                                CachedInstalledAppEntry(
                                    packageName = app.packageName,
                                    appName = label,
                                    isMusicCandidate = isMusic,
                                    isUserApp = isUser || isMusic
                                )
                            }
                            .sortedBy { it.appName.lowercase() }
                            .toList()
                    }.getOrDefault(emptyList())
                    cachedInstalledApps = scanned
                    withContext(Dispatchers.Main) {
                        loadingLayout.visibility = View.GONE
                        renderFromCache(searchInput.text?.toString().orEmpty())
                    }
                }
            }

            filterToggle.setOnClickListener {
                filterMusicOnly = !filterMusicOnly
                filterToggle.text = if (filterMusicOnly) {
                    "目前篩選：僅音樂播放器 (點擊切換)"
                } else {
                    "目前篩選：所有已安裝 App (點擊切換)"
                }
                renderFromCache(searchInput.text?.toString().orEmpty())
            }

            refreshBtn.setOnClickListener {
                loadAppsAsync(forceRefresh = true)
            }

            searchInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    renderFromCache(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) {}
            })

            loadAppsAsync(forceRefresh = false)
        }
    )
}
