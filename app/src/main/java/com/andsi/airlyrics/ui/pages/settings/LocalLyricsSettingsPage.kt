package com.andsi.airlyrics.ui.pages.settings

import android.app.AlertDialog
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import com.andsi.airlyrics.R
import com.andsi.airlyrics.logging.AppLogger
import com.andsi.airlyrics.lyrics.storage.LyricsExportFormat
import com.andsi.airlyrics.lyrics.storage.LyricsExportFormatter
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
import com.andsi.airlyrics.ui.state.confirmOperation

/**
 * 設定子頁面 3：本地歌詞管理 (LocalLyricsSettingsPage)
 * 集中管理本機歌詞儲存目錄、手動匯入、三種格式歌詞匯出、批次快取清除與最近歌詞清單。
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

    // 2. 歌詞匯入與多格式匯出
    container.addView(card(activity) {
        addView(bigText(activity, "歌詞匯入與匯出"))
        addView(smallHint(activity, "支援手動選取 .lrc 檔案匯入，或將本機歌詞庫匯出為標準 LRC、雙語 LRC 或純文字 TXT 檔案"))

        addView(actionButton(activity, "手動匯入本機歌詞 (LRC)") {
            uiActions.importLyricsForCurrentMedia()
        })

        addView(actionButton(activity, "匯出全部本機歌詞（選擇格式）") {
            val allLyrics = LyricsStorage.listAllLyrics(activity)
            if (allLyrics.isEmpty()) {
                Toast.makeText(activity, "本機尚無儲存的歌詞", Toast.LENGTH_SHORT).show()
                return@actionButton
            }
            val formats = LyricsExportFormat.entries
            val formatLabels = formats.map { it.displayTitle }.toTypedArray()
            AlertDialog.Builder(activity)
                .setTitle("選擇匯出格式")
                .setItems(formatLabels) { _, which ->
                    val chosen = formats[which]
                    try {
                        val exportFile = LyricsExportFormatter.exportBatchSongs(activity, allLyrics, chosen)
                        AppLogger.i("LocalLyrics", "成功以 [${chosen.displayTitle}] 匯出 ${allLyrics.size} 首歌詞至: ${exportFile.name}")
                        Toast.makeText(activity, "已匯出至：${exportFile.name}", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        AppLogger.e("LocalLyrics", "匯出歌詞失敗", e)
                        Toast.makeText(activity, "匯出失敗: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton(getString(R.string.ui_cancel), null)
                .show()
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
