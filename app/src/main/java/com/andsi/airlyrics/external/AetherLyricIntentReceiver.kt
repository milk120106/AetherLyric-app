package com.andsi.airlyrics.external

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.andsi.airlyrics.core.model.SongIdentity
import com.andsi.airlyrics.floating.FloatingServiceCommand
import com.andsi.airlyrics.logging.AppLogger
import com.andsi.airlyrics.lyrics.storage.LyricsStorage
import com.andsi.airlyrics.lyrics.storage.LyricsVersion
import com.andsi.airlyrics.lyrics.storage.LyricsVersionStore
import com.andsi.airlyrics.lyrics.storage.PlainLyricsStorageOps

/**
 * 外部擴充 Intent 廣播接收器。
 * 具備系統級自訂權限保護 (`com.airlyrics.app.permission.ACCESS_LYRICS`)、
 * 來源套件驗證與 `PendingIntent` (`reply_pending_intent`) 雙向回傳支援。
 */
class AetherLyricIntentReceiver : BroadcastReceiver() {

    companion object {
        const val PERMISSION_ACCESS_LYRICS = "com.airlyrics.app.permission.ACCESS_LYRICS"
        const val ACTION_SHOW_OVERLAY = "com.aetherlyric.app.action.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.aetherlyric.app.action.HIDE_OVERLAY"
        const val ACTION_TOGGLE_OVERLAY = "com.aetherlyric.app.action.TOGGLE_OVERLAY"
        const val ACTION_QUERY_LYRICS = "com.aetherlyric.app.action.QUERY_LYRICS"
        const val EXTRA_REPLY_PENDING_INTENT = "reply_pending_intent"
        const val MAX_TEXT_BYTES = 50 * 1024 // 50 KB 限制
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        val replyPendingIntent = extractReplyPendingIntent(intent)

        // 檢查 Intent API 是否啟用
        if (!ExternalControlStore.isIntentApiEnabled(context)) {
            AppLogger.w("IntentReceiver", "攔截到 Intent [$action]，但 Intent API 目前已關閉，已忽略此請求")
            sendPendingReply(replyPendingIntent, context, status = "error", message = "Intent API 未啟用")
            return
        }

        // 優先採用 PendingIntent 的系統驗證 creatorPackage，避免偽造 sender_package
        val verifiedCreatorPkg = replyPendingIntent?.creatorPackage
        val senderPackage = verifiedCreatorPkg
            ?: intent.getStringExtra("sender_package")
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
            sendPendingReply(replyPendingIntent, context, status = "denied", message = "套件 [$senderPackage] 未獲授權")
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
                sendPendingReply(replyPendingIntent, context, status = "ok", message = "已顯示懸浮窗")
            }
            ACTION_HIDE_OVERLAY -> {
                ContextCompat.startForegroundService(
                    context,
                    FloatingServiceCommand.Hide.toIntent(context)
                )
                AppLogger.i("IntentReceiver", "已執行隱藏懸浮窗指令")
                sendPendingReply(replyPendingIntent, context, status = "ok", message = "已隱藏懸浮窗")
            }
            ACTION_TOGGLE_OVERLAY -> {
                ContextCompat.startForegroundService(
                    context,
                    FloatingServiceCommand.ToggleVisibleFromNotification.toIntent(context)
                )
                AppLogger.i("IntentReceiver", "已執行切換懸浮窗指令")
                sendPendingReply(replyPendingIntent, context, status = "ok", message = "已切換懸浮窗狀態")
            }
            ACTION_QUERY_LYRICS -> {
                handleQueryLyricsAction(context, intent, replyPendingIntent)
            }
            Intent.ACTION_SEND -> {
                handleSendAction(context, intent, senderPackage, replyPendingIntent)
            }
        }
    }

    private fun handleQueryLyricsAction(
        context: Context,
        intent: Intent,
        replyPendingIntent: PendingIntent?
    ) {
        val title = intent.getStringExtra("title").orEmpty().trim()
        val artist = intent.getStringExtra("artist").orEmpty().trim()
        val durationMs = intent.getLongExtra("duration_ms", 0L)

        if (title.isBlank()) {
            sendPendingReply(replyPendingIntent, context, status = "error", message = "缺少歌曲標題 (title)")
            return
        }

        val lrcContent = PlainLyricsStorageOps.readPlainLyrics(context, title, artist, durationMs).orEmpty()
        if (lrcContent.isNotBlank()) {
            sendPendingReply(
                pendingIntent = replyPendingIntent,
                context = context,
                status = "ok",
                message = "已找到本機快取歌詞",
                title = title,
                artist = artist,
                lrcContent = lrcContent
            )
        } else {
            sendPendingReply(
                pendingIntent = replyPendingIntent,
                context = context,
                status = "not_found",
                message = "本機尚無此歌曲之歌詞快取",
                title = title,
                artist = artist,
                lrcContent = ""
            )
        }
    }

    private fun handleSendAction(
        context: Context,
        intent: Intent,
        sender: String,
        replyPendingIntent: PendingIntent?
    ) {
        if (intent.type != "text/plain") {
            AppLogger.w("IntentReceiver", "ACTION_SEND 僅支援 text/plain 類型")
            sendPendingReply(replyPendingIntent, context, status = "error", message = "僅支援 text/plain 類型")
            return
        }

        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        if (text.isNullOrBlank()) {
            AppLogger.w("IntentReceiver", "收到空白純文字內容")
            sendPendingReply(replyPendingIntent, context, status = "error", message = "歌詞內容不得為空")
            return
        }

        val byteSize = text.toByteArray(Charsets.UTF_8).size
        if (byteSize > MAX_TEXT_BYTES) {
            val err = "傳入歌詞大小超過 50KB 限制 (目前: ${byteSize / 1024} KB)，已拒絕接收"
            AppLogger.e("IntentReceiver", err)
            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            sendPendingReply(replyPendingIntent, context, status = "error", message = err)
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
        sendPendingReply(
            pendingIntent = replyPendingIntent,
            context = context,
            status = "ok",
            message = "已成功儲存並套用外部歌詞",
            title = title,
            artist = artist,
            lrcContent = text
        )
    }

    private fun extractReplyPendingIntent(intent: Intent): PendingIntent? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_REPLY_PENDING_INTENT, PendingIntent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_REPLY_PENDING_INTENT)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun sendPendingReply(
        pendingIntent: PendingIntent?,
        context: Context,
        status: String,
        message: String,
        title: String? = null,
        artist: String? = null,
        lrcContent: String? = null
    ) {
        if (pendingIntent == null) return
        try {
            val replyIntent = Intent().apply {
                putExtra("status", status)
                putExtra("message", message)
                if (title != null) putExtra("title", title)
                if (artist != null) putExtra("artist", artist)
                if (lrcContent != null) putExtra("lrc_content", lrcContent)
            }
            pendingIntent.send(context, 0, replyIntent)
        } catch (e: Exception) {
            AppLogger.w("IntentReceiver", "回傳 PendingIntent 失敗: ${e.message}")
        }
    }
}
