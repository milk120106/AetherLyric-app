package com.andsi.airlyrics.external

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.andsi.airlyrics.core.model.SongIdentity
import com.andsi.airlyrics.floating.FloatingLyricsService
import com.andsi.airlyrics.floating.FloatingServiceCommand
import com.andsi.airlyrics.logging.AppLogger
import com.andsi.airlyrics.lyrics.storage.LyricsStorage
import com.andsi.airlyrics.lyrics.storage.LyricsVersion
import com.andsi.airlyrics.lyrics.storage.LyricsVersionStore
import com.andsi.airlyrics.lyrics.storage.PlainLyricsStorageOps

/**
 * 外部擴充 Intent 廣播接收器。
 * 支援外部 App 透過廣播發送指令控制懸浮窗與傳入純文字歌詞 (限制 50KB)。
 */
class AetherLyricIntentReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SHOW_OVERLAY = "com.aetherlyric.app.action.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.aetherlyric.app.action.HIDE_OVERLAY"
        const val ACTION_TOGGLE_OVERLAY = "com.aetherlyric.app.action.TOGGLE_OVERLAY"
        const val MAX_TEXT_BYTES = 50 * 1024 // 50 KB 限制
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return

        // 檢查 Intent API 是否啟用
        if (!ExternalControlStore.isIntentApiEnabled(context)) {
            AppLogger.w("IntentReceiver", "攔截到 Intent [$action]，但 Intent API 目前已關閉，已忽略此請求")
            return
        }

        // 驗證來源 App 權限 (若有提供 calling package 或 extra)
        val senderPackage = intent.getStringExtra("sender_package")
            ?: intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)
            ?: "unknown"

        val permissionRecord = ExternalControlStore.getAppPermission(context, senderPackage)
        val unknownBehavior = ExternalControlStore.getUnknownAppBehavior(context)

        val isAllowed = if (permissionRecord != null) {
            permissionRecord.behavior == ExternalControlStore.BEHAVIOR_ALLOW
        } else {
            unknownBehavior == ExternalControlStore.BEHAVIOR_ALLOW
        }

        if (!isAllowed) {
            AppLogger.w("IntentReceiver", "來自 App [$senderPackage] 的 Intent [$action] 遭到權限原則拒絕")
            return
        }

        AppLogger.i("IntentReceiver", "接收到外部 Intent [$action] 來自: $senderPackage")

        when (action) {
            ACTION_SHOW_OVERLAY -> {
                ContextCompat.startForegroundService(
                    context,
                    FloatingServiceCommand.Show.toIntent(context)
                )
                AppLogger.i("IntentReceiver", "已執行顯示懸浮窗指令")
            }
            ACTION_HIDE_OVERLAY -> {
                ContextCompat.startForegroundService(
                    context,
                    FloatingServiceCommand.Hide.toIntent(context)
                )
                AppLogger.i("IntentReceiver", "已執行隱藏懸浮窗指令")
            }
            ACTION_TOGGLE_OVERLAY -> {
                ContextCompat.startForegroundService(
                    context,
                    FloatingServiceCommand.ToggleVisibleFromNotification.toIntent(context)
                )
                AppLogger.i("IntentReceiver", "已執行切換懸浮窗指令")
            }
            Intent.ACTION_SEND -> {
                handleSendAction(context, intent, senderPackage)
            }
        }
    }

    private fun handleSendAction(context: Context, intent: Intent, sender: String) {
        if (intent.type != "text/plain") {
            AppLogger.w("IntentReceiver", "ACTION_SEND 僅支援 text/plain 類型")
            return
        }

        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        if (text.isNullOrBlank()) {
            AppLogger.w("IntentReceiver", "收到空白純文字內容")
            return
        }

        val byteSize = text.toByteArray(Charsets.UTF_8).size
        if (byteSize > MAX_TEXT_BYTES) {
            val err = "傳入歌詞大小超過 50KB 限制 (目前: ${byteSize / 1024} KB)，已拒絕接收"
            AppLogger.e("IntentReceiver", err)
            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            return
        }

        val title = intent.getStringExtra(Intent.EXTRA_SUBJECT)
            ?: intent.getStringExtra("title")
            ?: "外部匯入歌詞"
        val artist = intent.getStringExtra("artist") ?: sender

        val songIdent = SongIdentity(title = title, artist = artist, album = "", durationMs = 0L)
        PlainLyricsStorageOps.savePlainLyrics(
            context = context,
            title = title,
            artist = artist,
            duration = 0L,
            plainLrc = text,
            plainSource = LyricsStorage.SOURCE_MANUAL_IMPORT,
            plainProvider = "Intent:$sender"
        )

        val newVer = LyricsVersion(
            songKey = songIdent.storageKey(),
            versionName = "外部匯入 ($sender)",
            lrcContent = text,
            source = "intent"
        )
        LyricsVersionStore.addVersion(context, newVer)
        LyricsVersionStore.switchVersion(context, songIdent, newVer.versionId)

        // 重新整理懸浮歌詞
        ContextCompat.startForegroundService(
            context,
            FloatingServiceCommand.ReloadLyrics.toIntent(context)
        )

        AppLogger.i("IntentReceiver", "成功透過 Intent 接收並儲存歌詞: $title - $artist (${byteSize} bytes)")
        Toast.makeText(context, "已成功接收外部歌詞：$title", Toast.LENGTH_SHORT).show()
    }
}
