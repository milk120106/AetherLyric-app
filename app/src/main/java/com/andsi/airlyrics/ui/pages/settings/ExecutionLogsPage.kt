package com.andsi.airlyrics.ui.pages.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import com.andsi.airlyrics.R
import com.andsi.airlyrics.design.tokens.AirUiTokens
import com.andsi.airlyrics.logging.AppLogger
import com.andsi.airlyrics.ui.components.*
import com.andsi.airlyrics.ui.model.MainUiHost
import com.andsi.airlyrics.ui.theme.*

/**
 * 執行日誌獨立子頁面 (ExecutionLogsPage)
 * 即時顯示全域 Log、關鍵字搜尋、等級過濾、自動滾動、複製全部、TXT 匯出與清除日誌。
 */
internal fun createExecutionLogsPage(activity: MainUiHost): View = with(activity) {
    val container = pageContainer(activity, animateChanges = false)

    container.addView(
        settingsBackHeader(
            title = getString(R.string.ui_execution_logs_title),
            subtitle = getString(R.string.ui_execution_logs_desc)
        )
    )

    var currentFilterText = ""
    var currentFilterLevel: AppLogger.LogLevel? = null
    var isAutoScrollEnabled = true

    // 日誌條目容器
    val logsListContainer = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    val emptyHint = TextView(activity).apply {
        setText(R.string.ui_logs_empty)
        textSize = AirUiTokens.TextSize.Body
        setTextColor(colorTextMuted)
        gravity = Gravity.CENTER
        setPadding(dp(AirUiTokens.Space.CardH), dp(AirUiTokens.Space.Xxl), dp(AirUiTokens.Space.CardH), dp(AirUiTokens.Space.Xxl))
    }

    fun renderEntry(entry: AppLogger.LogEntry): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(AirUiTokens.Space.CardH),
                dp(AirUiTokens.Space.Sm),
                dp(AirUiTokens.Space.CardH),
                dp(AirUiTokens.Space.Sm)
            )
            background = GradientDrawable().apply {
                cornerRadius = dp(AirUiTokens.Radius.Sm).toFloat()
                setColor(colorSurfaceLight)
            }
            val marginParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dp(AirUiTokens.Space.Xs))
            }
            layoutParams = marginParams

            // 頂部時間戳與等級標籤
            val headerRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )

                // 時間戳
                addView(TextView(activity).apply {
                    text = entry.formattedTime()
                    textSize = AirUiTokens.TextSize.Caption
                    setTextColor(colorTextMuted)
                    typeface = Typeface.MONOSPACE
                })

                // 等級 Badge
                val (badgeBgColor, badgeTextColor) = when (entry.level) {
                    AppLogger.LogLevel.INFO -> colorAccent to colorOnAccent
                    AppLogger.LogLevel.WARN -> colorAccentSoft to colorTextStrong
                    AppLogger.LogLevel.ERROR -> colorDanger to android.graphics.Color.WHITE
                }

                addView(TextView(activity).apply {
                    text = entry.level.name
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(badgeTextColor)
                    setPadding(dp(6), dp(2), dp(6), dp(2))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(4).toFloat()
                        setColor(badgeBgColor)
                    }
                    val badgeParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(dp(AirUiTokens.Space.Sm), 0, dp(AirUiTokens.Space.Sm), 0)
                    }
                    layoutParams = badgeParams
                })

                // Tag
                addView(TextView(activity).apply {
                    text = entry.tag
                    textSize = AirUiTokens.TextSize.Caption
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(colorTextStrong)
                })
            }
            addView(headerRow)

            // 日誌訊息內文
            addView(TextView(activity).apply {
                text = entry.message
                textSize = AirUiTokens.TextSize.BodySmall
                setTextColor(colorText)
                typeface = Typeface.MONOSPACE
                setTextIsSelectable(true)
                setPadding(0, dp(AirUiTokens.Space.Xxs), 0, 0)
            })
        }
    }

    var logsScrollView: ScrollView? = null

    fun refreshLogs() {
        logsListContainer.removeAllViews()
        val filtered = AppLogger.filter(currentFilterText, currentFilterLevel)
        if (filtered.isEmpty()) {
            logsListContainer.addView(emptyHint)
        } else {
            filtered.forEach { entry ->
                logsListContainer.addView(renderEntry(entry))
            }
            if (isAutoScrollEnabled) {
                logsScrollView?.post {
                    logsScrollView?.fullScroll(View.FOCUS_DOWN)
                }
            }
        }
    }

    // 1. 搜尋與過濾卡片
    container.addView(card(activity) {
        val searchInput = EditText(activity).apply {
            hint = getString(R.string.ui_logs_search_hint)
            textSize = AirUiTokens.TextSize.Body
            setTextColor(colorTextStrong)
            setHintTextColor(colorTextMuted)
            background = GradientDrawable().apply {
                cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
                setColor(colorSurfaceLight)
                setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
            }
            setPadding(
                dp(AirUiTokens.Space.CardH),
                dp(AirUiTokens.Space.CardV),
                dp(AirUiTokens.Space.CardH),
                dp(AirUiTokens.Space.CardV)
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    currentFilterText = s?.toString() ?: ""
                    refreshLogs()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        addView(searchInput)

        // 等級篩選按鈕列
        val filterRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(AirUiTokens.Space.Sm), 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val levelOptions = listOf(
            null to "全部",
            AppLogger.LogLevel.INFO to "INFO",
            AppLogger.LogLevel.WARN to "WARN",
            AppLogger.LogLevel.ERROR to "ERROR"
        )

        val filterButtons = mutableListOf<TextView>()

        levelOptions.forEach { (level, label) ->
            val btn = TextView(activity).apply {
                text = label
                textSize = AirUiTokens.TextSize.Button
                val isSelected = currentFilterLevel == level
                typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (isSelected) colorOnAccent else colorTextStrong)
                gravity = Gravity.CENTER
                setPadding(dp(AirUiTokens.Space.Md), dp(AirUiTokens.Space.Xs), dp(AirUiTokens.Space.Md), dp(AirUiTokens.Space.Xs))
                background = GradientDrawable().apply {
                    cornerRadius = dp(AirUiTokens.Radius.Sm).toFloat()
                    setColor(if (isSelected) colorAccent else colorSurfaceLight)
                    setStroke(dp(AirUiTokens.Stroke.Hairline), if (isSelected) colorAccent else colorStroke)
                }
                val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                layoutParams = params
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    currentFilterLevel = level
                    filterButtons.forEachIndexed { idx, b ->
                        val selected = levelOptions[idx].first == currentFilterLevel
                        b.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                        b.setTextColor(if (selected) colorOnAccent else colorTextStrong)
                        (b.background as? GradientDrawable)?.apply {
                            setColor(if (selected) colorAccent else colorSurfaceLight)
                            setStroke(dp(AirUiTokens.Stroke.Hairline), if (selected) colorAccent else colorStroke)
                        }
                    }
                    refreshLogs()
                }
            }
            filterButtons.add(btn)
            filterRow.addView(btn)
        }
        addView(filterRow)
    })

    // 2. 控制與操作列卡片
    container.addView(card(activity) {
        val controlsTopRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            // 自動滾動開關
            val autoScrollText = TextView(activity).apply {
                setText(R.string.ui_logs_auto_scroll)
                textSize = AirUiTokens.TextSize.Body
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colorTextStrong)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val autoScrollSwitch = SwitchCompat(activity).apply {
                isChecked = isAutoScrollEnabled
                applyAirThemeTint(activity)
                setOnCheckedChangeListener { _, isChecked ->
                    isAutoScrollEnabled = isChecked
                }
            }
            addView(autoScrollText)
            addView(autoScrollSwitch)
        }
        addView(controlsTopRow)

        // 操作按鈕行
        val buttonsRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(AirUiTokens.Space.Sm), 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            // 複製全部
            val copyBtn = actionButton(activity, getString(R.string.ui_logs_copy_all)) {
                val allText = AppLogger.exportText()
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("AetherLyric_Logs", allText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(activity, R.string.ui_logs_copied, Toast.LENGTH_SHORT).show()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(0, 0, dp(2), 0)
                }
            }
            addView(copyBtn)

            // 匯出 TXT
            val exportBtn = actionButton(activity, getString(R.string.ui_logs_export_txt)) {
                AppLogger.exportToFile(activity).fold(
                    onSuccess = { file ->
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.ui_logs_exported, file.absolutePath),
                            Toast.LENGTH_LONG
                        ).show()
                    },
                    onFailure = { e ->
                        Toast.makeText(activity, "匯出失敗：${e.message}", Toast.LENGTH_SHORT).show()
                    }
                )
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
            }
            addView(exportBtn)

            // 清除日誌
            val clearBtn = actionButton(activity, getString(R.string.ui_logs_clear)) {
                AppLogger.clear()
                refreshLogs()
                Toast.makeText(activity, "日誌已清除", Toast.LENGTH_SHORT).show()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(2), 0, 0, 0)
                }
            }
            addView(clearBtn)
        }
        addView(buttonsRow)
    })

    // 3. 日誌主列表卡片
    val logCard = card(activity) {
        addView(logsListContainer)
    }
    container.addView(logCard)

    // 即時日誌監聽
    val logListener: (AppLogger.LogEntry) -> Unit = { entry ->
        activity.activity.runOnUiThread {
            val q = currentFilterText.trim().lowercase()
            val matchLevel = currentFilterLevel == null || entry.level == currentFilterLevel
            val matchQuery = q.isEmpty() || entry.tag.lowercase().contains(q) || entry.message.lowercase().contains(q)
            if (matchLevel && matchQuery) {
                if (logsListContainer.childCount == 1 && logsListContainer.getChildAt(0) == emptyHint) {
                    logsListContainer.removeView(emptyHint)
                }
                logsListContainer.addView(renderEntry(entry))
                if (isAutoScrollEnabled) {
                    logsScrollView?.post {
                        logsScrollView?.fullScroll(View.FOCUS_DOWN)
                    }
                }
            }
        }
    }
    AppLogger.addListener(logListener)

    container.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {}
        override fun onViewDetachedFromWindow(v: View) {
            AppLogger.removeListener(logListener)
        }
    })

    refreshLogs()

    val pageScroll = scroll(activity, container, animateChildren = false)
    logsScrollView = pageScroll
    return pageScroll
}
