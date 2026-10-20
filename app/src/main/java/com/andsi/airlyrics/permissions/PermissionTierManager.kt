package com.andsi.airlyrics.permissions

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.andsi.airlyrics.logging.AppLogger

/**
 * 三層權限架構管理器。
 * 動態優先級：Shizuku > Accessibility (無障礙) > Basic (通知與懸浮窗)。
 */
object PermissionTierManager {

    enum class Tier(val displayName: String, val level: Int) {
        SHIZUKU("Shizuku (高權限 ADB)", 3),
        ACCESSIBILITY("無障礙模式 (系統級監聽)", 2),
        BASIC("基礎模式 (通知與懸浮窗)", 1)
    }

    /**
     * 取得目前有效之最高權限層級。
     */
    fun getCurrentTier(context: Context): Tier {
        return when {
            isShizukuAvailableAndGranted(context) -> Tier.SHIZUKU
            isAccessibilityEnabled(context) -> Tier.ACCESSIBILITY
            else -> Tier.BASIC
        }
    }

    /**
     * 檢查 Shizuku 是否已安裝且具備服務支援。
     */
    fun isShizukuInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * 檢查 Shizuku 是否正在執行並取得權限。
     */
    fun isShizukuAvailableAndGranted(context: Context): Boolean {
        if (!isShizukuInstalled(context)) return false
        return try {
            // 透過 Provider 或 Binder 探測 Shizuku 服務
            val uri = Uri.parse("content://moe.shizuku.privileged.api.provider")
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            val available = cursor != null
            cursor?.close()
            available
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 檢查無障礙服務是否已被使用者啟用。
     */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabledServices.contains(context.packageName)
    }

    /**
     * 檢查 Android 13+ (API 33+) 是否觸發「受限制的設定」(Restricted Settings)。
     */
    fun isRestrictedSettingsBlocked(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        return try {
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ OP_ACCESS_RESTRICTED_SETTINGS is 119
                appOps.checkOpNoThrow("android:access_restricted_settings", Process.myUid(), context.packageName)
            } else {
                // Android 13 OP_ACCESS_RESTRICTED_SETTINGS is 119
                appOps.unsafeCheckOpNoThrow("android:access_restricted_settings", Process.myUid(), context.packageName)
            }
            mode == AppOpsManager.MODE_ERRORED || mode == AppOpsManager.MODE_IGNORED
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 跳轉至應用程式設定詳細頁面 (用於引導解鎖「允許受限制的設定」)。
     */
    fun openAppDetailsSettings(context: Context) {
        AppLogger.i("PermissionTierManager", "引導跳轉至應用程式設定頁面以解除受限制設定")
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * 跳轉至系統開發人員選項 (用於引導設定無線偵錯與 Shizuku)。
     */
    fun openDevelopmentSettings(context: Context) {
        AppLogger.i("PermissionTierManager", "引導跳轉至開發人員選項以設定無線偵錯")
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallback)
        }
    }

    /**
     * 跳轉至系統無障礙設定頁面。
     */
    fun openAccessibilitySettings(context: Context) {
        AppLogger.i("PermissionTierManager", "跳轉至無障礙設定頁面")
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
