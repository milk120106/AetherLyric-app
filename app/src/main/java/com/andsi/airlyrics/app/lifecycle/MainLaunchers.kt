package com.andsi.airlyrics.app.lifecycle

import android.net.Uri
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.andsi.airlyrics.lyrics.importer.LyricsFormatCatalog

/**
 * Owns ActivityResult launchers for MainActivity.
 *
 * This keeps Android callback registration out of MainActivity while preserving
 * the existing handwritten UI and import/permission behavior.
 */
internal class MainLaunchers(
    private val activity: AppCompatActivity,
    private val onLyricsFileResult: (Uri?) -> Unit,
    private val onFloatingFontFileResult: (Uri?) -> Unit,
    private val onLyricsDirectorySelected: (Uri) -> Unit,
    private val onNotificationPermissionResult: (Boolean) -> Unit
) {
    private val lyricsDocumentMimeTypes = LyricsFormatCatalog.pickerMimeTypes()

    private val lyricsFileLauncher =
        activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            onLyricsFileResult(uri)
        }

    private val floatingFontMimeTypes = arrayOf(
        "font/ttf",
        "font/otf",
        "application/font-sfnt",
        "application/x-font-ttf",
        "application/x-font-truetype",
        "application/x-font-otf",
        "application/x-font-opentype",
        "application/vnd.ms-opentype",
        "application/octet-stream"
    )

    private val floatingFontFileLauncher =
        activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            onFloatingFontFileResult(uri)
        }

    private val lyricsDirectoryLauncher =
        activity.registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) onLyricsDirectorySelected(uri)
        }

    private val notificationPermissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            onNotificationPermissionResult(granted)
        }

    private var pendingExportContent: String? = null

    private val createDocumentLauncher =
        activity.registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
            val content = pendingExportContent
            pendingExportContent = null
            if (uri != null && content != null) {
                try {
                    activity.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(content.toByteArray(Charsets.UTF_8))
                        os.flush()
                    }
                    Toast.makeText(activity, "歌詞匯出成功", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(activity, "歌詞寫入失敗: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

    fun selectLyricsFile() {
        lyricsFileLauncher.launch(lyricsDocumentMimeTypes)
    }

    fun selectFloatingFontFile() {
        floatingFontFileLauncher.launch(floatingFontMimeTypes)
    }

    fun selectLyricsDirectory() {
        lyricsDirectoryLauncher.launch(null)
    }

    fun requestNotificationPermission(permission: String) {
        notificationPermissionLauncher.launch(permission)
    }

    fun exportDocument(suggestedFileName: String, content: String) {
        pendingExportContent = content
        createDocumentLauncher.launch(suggestedFileName)
    }
}
