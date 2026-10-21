package com.andsi.airlyrics.ui.dialogs

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.andsi.airlyrics.R
import com.andsi.airlyrics.core.model.LyricsSearchMode
import com.andsi.airlyrics.core.model.SongIdentity
import com.andsi.airlyrics.design.tokens.AirUiTokens
import com.andsi.airlyrics.lyrics.LyricsRepository
import com.andsi.airlyrics.lyrics.LyricsSearchCandidate
import com.andsi.airlyrics.lyrics.parser.LrcParser
import com.andsi.airlyrics.lyrics.storage.LyricsVersion
import com.andsi.airlyrics.lyrics.storage.LyricsVersionStore
import com.andsi.airlyrics.ui.components.enableSoftPressFeedback
import com.andsi.airlyrics.ui.model.MainUiHost
import com.andsi.airlyrics.ui.theme.colorAccent
import com.andsi.airlyrics.ui.theme.colorAccentMint
import com.andsi.airlyrics.ui.theme.colorCard
import com.andsi.airlyrics.ui.theme.colorOnAccent
import com.andsi.airlyrics.ui.theme.colorStroke
import com.andsi.airlyrics.ui.theme.colorSurfaceLight
import com.andsi.airlyrics.ui.theme.colorText
import com.andsi.airlyrics.ui.theme.colorTextMuted
import com.andsi.airlyrics.ui.theme.colorTextStrong
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 多源並行歌詞搜尋結果底部彈出選單 (LyricsSearchResultsBottomSheet)。
 * 清楚標示各候選歌詞之：
 * 1. 來源名稱
 * 2. 是否具備 LRC 時間軸標籤（逐字時間軸 / LRC 時間軸 / 純文字）
 * 3. 歌曲時長資訊
 * 4. 匹配的歌手與歌名及前幾句歌詞預覽
 */
internal fun showManualLyricsSearchBottomSheet(
    activity: MainUiHost,
    song: SongIdentity,
    searchMode: LyricsSearchMode = LyricsSearchMode.Original,
    onApplied: (() -> Unit)? = null
) {
    showLyricsCandidatesBottomSheetInternal(
        activity = activity,
        song = song,
        preloadedCandidates = null,
        searchMode = searchMode,
        onApplied = onApplied
    )
}

internal fun showPreloadedLyricsCandidatesBottomSheet(
    activity: MainUiHost,
    song: SongIdentity,
    candidates: List<LyricsSearchCandidate>,
    searchMode: LyricsSearchMode = LyricsSearchMode.Original,
    onApplied: (() -> Unit)? = null
) {
    showLyricsCandidatesBottomSheetInternal(
        activity = activity,
        song = song,
        preloadedCandidates = candidates,
        searchMode = searchMode,
        onApplied = onApplied
    )
}

private fun showLyricsCandidatesBottomSheetInternal(
    activity: MainUiHost,
    song: SongIdentity,
    preloadedCandidates: List<LyricsSearchCandidate>?,
    searchMode: LyricsSearchMode,
    onApplied: (() -> Unit)?
) = with(activity) {
    val bottomSheet = BottomSheetDialog(activity.activity)

    val rootContainer = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(
            dp(AirUiTokens.Space.CardH),
            dp(AirUiTokens.Space.CardV),
            dp(AirUiTokens.Space.CardH),
            dp(AirUiTokens.Space.Xxl)
        )
        background = GradientDrawable().apply {
            val topRadius = dp(AirUiTokens.Radius.Dialog).toFloat()
            cornerRadii = floatArrayOf(topRadius, topRadius, topRadius, topRadius, 0f, 0f, 0f, 0f)
            setColor(colorCard)
            setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
        }
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    // 頂部拖曳指示條
    val dragHandle = View(activity).apply {
        background = GradientDrawable().apply {
            cornerRadius = dp(4).toFloat()
            setColor(colorStroke)
        }
        layoutParams = LinearLayout.LayoutParams(dp(40), dp(4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setMargins(0, dp(AirUiTokens.Space.Xs), 0, dp(AirUiTokens.Space.Md))
        }
    }
    rootContainer.addView(dragHandle)

    // 標題與歌曲資訊
    val modeLabel = if (searchMode == LyricsSearchMode.International) "（國際標題搜尋）" else "（多源並行搜尋）"
    rootContainer.addView(TextView(activity).apply {
        text = "選擇歌詞搜尋結果 $modeLabel"
        textSize = AirUiTokens.TextSize.Title
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(colorTextStrong)
    })

    val durationLabel = formatDurationMs(song.durationMs)
    rootContainer.addView(TextView(activity).apply {
        text = "${song.title} · ${song.artist.ifBlank { getString(R.string.ui_unknown_artist) }} ($durationLabel)"
        textSize = AirUiTokens.TextSize.Caption
        setTextColor(colorTextMuted)
        setPadding(0, dp(AirUiTokens.Space.Xxs), 0, dp(AirUiTokens.Space.Md))
    })

    // 載入中區塊
    val loadingRow = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(AirUiTokens.Space.Lg), 0, dp(AirUiTokens.Space.Lg))
        visibility = if (preloadedCandidates == null) View.VISIBLE else View.GONE
        addView(ProgressBar(activity).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(colorAccent)
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                setMargins(0, 0, dp(AirUiTokens.Space.Sm), 0)
            }
        })
        addView(TextView(activity).apply {
            text = "正在並行查詢所有已啟用的歌詞來源…"
            textSize = AirUiTokens.TextSize.BodySmall
            setTextColor(colorTextMuted)
        })
    }
    rootContainer.addView(loadingRow)

    val listContainer = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    val scrollView = ScrollView(activity).apply {
        isFillViewport = true
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.62f).toInt()
        )
        addView(listContainer)
    }
    rootContainer.addView(scrollView)

    fun renderCandidates(candidates: List<LyricsSearchCandidate>) {
        listContainer.removeAllViews()
        if (candidates.isEmpty()) {
            listContainer.addView(TextView(activity).apply {
                text = "所有已啟用來源皆未找到相符的歌詞，請確認網路連線或嘗試開啟更多歌詞來源。"
                textSize = AirUiTokens.TextSize.BodySmall
                setTextColor(colorTextMuted)
                setPadding(0, dp(AirUiTokens.Space.Lg), 0, dp(AirUiTokens.Space.Lg))
            })
            return
        }

        candidates.forEach { candidate ->
            val cardItem = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    dp(AirUiTokens.Space.CardH),
                    dp(AirUiTokens.Space.CardV),
                    dp(AirUiTokens.Space.CardH),
                    dp(AirUiTokens.Space.CardV)
                )
                background = GradientDrawable().apply {
                    cornerRadius = dp(AirUiTokens.Radius.Md).toFloat()
                    setColor(colorSurfaceLight)
                    setStroke(dp(AirUiTokens.Stroke.Hairline), colorStroke)
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, dp(AirUiTokens.Space.Sm))
                }
                isClickable = true
                isFocusable = true
                enableSoftPressFeedback(AirUiTokens.Motion.DefaultPressScale)
            }

            // 第一列：來源名稱 + LRC 時間軸標籤 + 時長資訊
            val topMetaRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val sourceBadge = TextView(activity).apply {
                text = candidate.sourceName
                textSize = AirUiTokens.TextSize.Caption
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colorOnAccent)
                setPadding(dp(8), dp(2), dp(8), dp(2))
                background = GradientDrawable().apply {
                    cornerRadius = dp(AirUiTokens.Radius.Pill).toFloat()
                    setColor(colorAccent)
                }
            }
            topMetaRow.addView(sourceBadge)

            val formatType = when {
                candidate.result.plainLrc.contains("<tt", ignoreCase = true) ||
                candidate.result.plainLrc.contains("<p begin=", ignoreCase = true) ||
                candidate.result.translatedLrc?.contains("<tt", ignoreCase = true) == true -> "TTML"
                candidate.hasSyncedLrc || candidate.hasWordTiming -> "LRC"
                else -> "TXT"
            }
            val formatBadge = TextView(activity).apply {
                text = formatType
                textSize = AirUiTokens.TextSize.Caption
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colorOnAccent)
                setPadding(dp(8), dp(2), dp(8), dp(2))
                background = GradientDrawable().apply {
                    cornerRadius = dp(AirUiTokens.Radius.Pill).toFloat()
                    setColor(when (formatType) {
                        "TTML" -> colorAccent
                        "LRC" -> colorAccentMint
                        else -> colorStroke
                    })
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(dp(AirUiTokens.Space.Xs), 0, 0, 0)
                }
            }
            topMetaRow.addView(formatBadge)

            val timelineLabel = when {
                candidate.hasWordTiming -> "逐字時間軸"
                candidate.hasSyncedLrc -> "LRC 時間軸"
                else -> "純文字（無時間軸）"
            }
            val timelineBadge = TextView(activity).apply {
                text = timelineLabel
                textSize = AirUiTokens.TextSize.Caption
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (candidate.hasSyncedLrc) colorAccentMint else colorTextMuted)
                setPadding(dp(8), dp(2), dp(8), dp(2))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(dp(AirUiTokens.Space.Xs), 0, 0, 0)
                }
            }
            topMetaRow.addView(timelineBadge)

            val durationText = TextView(activity).apply {
                text = "時長 ${formatDurationMs(candidate.durationMs)}"
                textSize = AirUiTokens.TextSize.Caption
                setTextColor(colorTextMuted)
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            topMetaRow.addView(durationText)
            cardItem.addView(topMetaRow)

            // 第二列：匹配的歌名與歌手
            cardItem.addView(TextView(activity).apply {
                val mTitle = candidate.matchedTitle.ifBlank { song.title }
                val mArtist = candidate.matchedArtist.ifBlank { song.artist }
                text = "$mTitle - $mArtist"
                textSize = AirUiTokens.TextSize.BodySmall
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colorTextStrong)
                setPadding(0, dp(AirUiTokens.Space.Xs), 0, dp(2))
            })

            // 第三列：歌詞預覽片段
            cardItem.addView(TextView(activity).apply {
                text = candidate.previewSnippet
                textSize = AirUiTokens.TextSize.Caption
                setTextColor(colorText)
                maxLines = 2
            })

            cardItem.setOnClickListener {
                cardItem.isEnabled = false
                activity.activity.lifecycleScope.launch(Dispatchers.IO) {
                    val committed = LyricsRepository.applySelectedCandidate(
                        context = activity,
                        song = song,
                        candidate = candidate,
                        searchMode = searchMode
                    )
                    val mergedLrc = LrcParser.mergeOriginalAndTranslationForStorage(
                        plainLrc = committed.plainLrc,
                        translatedLrc = committed.translatedLrc
                    )
                    val newVer = LyricsVersion(
                        songKey = song.storageKey(),
                        versionName = "${candidate.sourceName} (${if (candidate.hasSyncedLrc) "LRC" else "純文字"})",
                        lrcContent = mergedLrc,
                        source = candidate.providerId
                    )
                    LyricsVersionStore.addVersion(activity, newVer)
                    LyricsVersionStore.switchVersion(activity, song, newVer.versionId)

                    withContext(Dispatchers.Main) {
                        uiActions.reloadFloatingLyrics()
                        onApplied?.invoke()
                        Toast.makeText(
                            activity,
                            "已套用來自「${candidate.sourceName}」的歌詞",
                            Toast.LENGTH_SHORT
                        ).show()
                        bottomSheet.dismiss()
                    }
                }
            }

            listContainer.addView(cardItem)
        }
    }

    bottomSheet.setContentView(rootContainer)
    bottomSheet.show()

    if (preloadedCandidates != null) {
        renderCandidates(preloadedCandidates)
    } else {
        activity.activity.lifecycleScope.launch(Dispatchers.IO) {
            val results = LyricsRepository.searchAllSourcesParallel(
                context = activity,
                title = song.title,
                artist = song.artist,
                album = song.album,
                durationMs = song.durationMs,
                searchMode = searchMode
            )
            withContext(Dispatchers.Main) {
                loadingRow.visibility = View.GONE
                renderCandidates(results)
            }
        }
    }
}

private fun formatDurationMs(durationMs: Long): String {
    if (durationMs <= 0L) return "--:--"
    val totalSec = durationMs / 1000L
    val m = totalSec / 60L
    val s = totalSec % 60L
    return String.format(Locale.US, "%02d:%02d", m, s)
}
