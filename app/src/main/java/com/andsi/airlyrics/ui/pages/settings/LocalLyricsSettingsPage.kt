package com.andsi.airlyrics.ui.pages.settings

import android.os.Environment
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import com.andsi.airlyrics.R
import com.andsi.airlyrics.design.tokens.AirUiTokens
import com.andsi.airlyrics.logging.AppLogger
import com.andsi.airlyrics.lyrics.storage.LyricsStorage
import com.andsi.airlyrics.ui.components.actionButton
import com.andsi.airlyrics.ui.components.bigText
import com.andsi.airlyrics.ui.components.card
import com.andsi.airlyrics.ui.components.dangerActionButton
import com.andsi.airlyrics.ui.components.normalText
import com.andsi.airlyrics.ui.components.pageContainer
import com.andsi.airlyrics.ui.components.scroll
import com.andsi.airlyrics.ui.components.smallHint
import com.andsi.airlyrics.ui.model.ConfirmationAction
import com.andsi.airlyrics.ui.model.MainUiHost
import com.andsi.airlyrics.ui.navigation.SettingsSubPage
import com.andsi.airlyrics.ui.state.confirmOperation
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 設定子頁面 3：本地歌詞管理 (LocalLyricsSettingsPage)
 * 集中管理本機歌詞儲存目錄、手動匯入、TXT/LRC 匯出、批次快取清除與最近歌詞清單。
 */
internal fun createLocalLyricsSettingsPage(activity: MainUiHost): View = with(activity) {
    val container = pageContainer(activity, animateChanges = false)
    val settings = lyricsSettingsState()

    container.addView(
        settingsBackHeader(
            title = getString(R.string.ui_local_lyrics_subpage_title),
            subtitle = getString(R.string.ui_local_lyrics_subpage_desc)
        )
    )

    // 1. 歌詞儲存路徑
    container.addView(card(activity) {
        addView(bigText(activity, getString(R.string.ui_local_lyrics_folder)))
        addView(normalText(activity, getString(R.string.ui_save_folder) + settings.lyricsDirectoryPath))
        addView(smallHint(activity, getString(R.string.ui_lyrics_folder_scope_hint)))
        addView(actionButton(activity, getString(R.string.ui_choose_lyrics_save_folder)) {
            uiActions.selectLyricsDirectory()
        })
        addView(actionButton(activity, getString(R.string.ui_copy_lyrics_save_folder)) {
            uiActions.copyLyricsDirectory()
        })
    })

    // 2. 歌詞匯入與匯出
    container.addView(card(activity) {
        addView(bigText(activity, "歌詞匯入與匯出"))
        addView(smallHint(activity, "支援手動選取 .lrc 檔案匯入，或將本機歌詞庫打包匯出為 TXT 備份檔"))

        addView(actionButton(activity, "手動匯入本機歌詞 (LRC)") {
            uiActions.importLyricsForCurrentMedia()
        })

        addView(actionButton(activity, getString(R.string.ui_export_all_txt)) {
            try {
                val allLyrics = LyricsStorage.listAllLyrics(activity)
                if (allLyrics.isEmpty()) {
                    Toast.makeText(activity, "本機尚無儲存的歌詞", Toast.LENGTH_SHORT).show()
                    return@actionButton
                }
                val sb = StringBuilder()
                sb.append("=== AetherLyric 本地歌詞匯出庫 (共 ${allLyrics.size} 首) ===\n")
                sb.append("匯出時間: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n\n")

                allLyrics.forEach { item ->
                    val text = LyricsStorage.readLocalLyricsItemText(activity, item, LyricsStorage.LocalLyricsEditTarget.PLAIN)
                    sb.append("----------------------------------------\n")
                    sb.append("歌曲: ${item.title} - ${item.artist}\n")
                    sb.append("檔案: ${item.name}\n")
                    sb.append("內容:\n${text.orEmpty()}\n\n")
                }

                val downloadDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: activity.cacheDir
                val exportFile = File(downloadDir, "AetherLyric_lyrics_export_${System.currentTimeMillis()}.txt")
                exportFile.writeText(sb.toString(), Charsets.UTF_8)
                AppLogger.i("LocalLyrics", "成功匯出 ${allLyrics.size} 首歌詞至: ${exportFile.absolutePath}")
                Toast.makeText(activity, "已匯出至：${exportFile.name}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                AppLogger.e("LocalLyrics", "匯出歌詞失敗", e)
                Toast.makeText(activity, "匯出失敗: ${e.message}", Toast.LENGTH_LONG).show()
            }
        })
    })

    // 3. 快取清理與刪除
    container.addView(card(activity) {
        addView(bigText(activity, "快取清理與批次刪除"))
        addView(smallHint(activity, "批次清理線上查詢產生的非持久快取，或一鍵刪除所有已儲存歌詞"))

        // 批次清除快取按鈕
        addView(actionButton(activity, getString(R.string.ui_batch_clean_cache)) {
            LyricsStorage.pruneOnlineCache(activity)
            AppLogger.i("LocalLyrics", "已執行批次快取清理")
            Toast.makeText(activity, getString(R.string.ui_batch_cache_cleared), Toast.LENGTH_SHORT).show()
        })

        // 刪除全部已儲存歌詞
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            addView(bigText(activity, getString(R.string.ui_delete_all_saved_lyrics)).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(dangerActionButton(activity, getString(R.string.ui_delete)) {
                activity.confirmOperation(
                    action = ConfirmationAction.DeleteAll,
                    title = getString(R.string.ui_delete_all_saved_lyrics_confirm),
                    message = getString(R.string.ui_delete_all_saved_lyrics_message),
                    positiveText = getString(R.string.ui_delete)
                )
            })
        })
    })

    // 4. 最近歌詞清單卡片
    val recentLyricsCard = createRecentLyricsCard(activity)
    container.addView(recentLyricsCard.view)

    return scroll(activity, container, maxWidthDp = 1200)
}
