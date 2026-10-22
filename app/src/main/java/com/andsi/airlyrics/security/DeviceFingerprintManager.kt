package com.andsi.airlyrics.security

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.andsi.airlyrics.logging.AppLogger
import com.andsi.airlyrics.permissions.PermissionTierManager
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

/**
 * 裝置唯一特徵識別管理器 (DeviceFingerprintManager)。
 * 實作階梯式硬體識別特徵抓取，並提供模擬器與 Root 環境偵測。
 */
object DeviceFingerprintManager {

    private var cachedFingerprint: String? = null
    private var cachedRootStatus: Boolean? = null

    /**
     * 獲取裝置唯一特徵指紋。
     * 階梯流程：
     * 1. 優先透過 Shizuku 執行特權指令獲取 ro.serialno
     * 2. 若無 Shizuku 則嘗試透過 Root 指令獲取
     * 3. 若無 Root，在 Android < 10 嘗試獲取硬體序號
     * 4. 若皆無，降級使用安全雜湊後之 Settings.Secure.ANDROID_ID
     */
    fun getDeviceFingerprint(context: Context): String {
        cachedFingerprint?.let { return it }

        // 1. Shizuku 特權指令
        if (PermissionTierManager.isShizukuAvailableAndGranted(context)) {
            val serial = getPropViaShizuku("ro.serialno")
            if (!serial.isNullOrBlank() && serial != "unknown") {
                val hashed = sha256("shizuku:$serial")
                cachedFingerprint = hashed
                return hashed
            }
        }

        // 2. Root 指令獲取
        val rootSerial = getPropViaRoot("ro.serialno")
        if (!rootSerial.isNullOrBlank() && rootSerial != "unknown") {
            val hashed = sha256("root:$rootSerial")
            cachedFingerprint = hashed
            return hashed
        }

        // 3. Android < 10 (API < 29) 硬體序號
        val legacySerial = getLegacyHardwareSerial()
        if (!legacySerial.isNullOrBlank() && legacySerial != "unknown") {
            val hashed = sha256("legacy:$legacySerial")
            cachedFingerprint = hashed
            return hashed
        }

        // 4. 降級使用 ANDROID_ID 雜湊
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: UUID.randomUUID().toString()
        val hashed = sha256("android_id:$androidId")
        cachedFingerprint = hashed
        return hashed
    }

    /**
     * 偵測目前環境是否為 Root 或 Android 模擬器。
     */
    fun isRootedOrEmulator(context: Context): Boolean {
        cachedRootStatus?.let { return it }

        val isEmulator = detectEmulator()
        val isRooted = detectRoot()
        val result = isEmulator || isRooted

        cachedRootStatus = result
        return result
    }

    private fun detectEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.startsWith("unknown")
            || Build.MODEL.contains("google_sdk")
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK built for x86")
            || Build.MANUFACTURER.contains("Genymotion")
            || Build.HARDWARE.contains("goldfish")
            || Build.HARDWARE.contains("ranchu")
            || Build.PRODUCT.contains("sdk_gphone")
            || Build.PRODUCT.contains("sdk")
            || Build.PRODUCT.contains("vbox86p"))
    }

    private fun detectRoot(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }

        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) {
            return true
        }

        return false
    }

    private fun getPropViaShizuku(propName: String): String? {
        return runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", propName))
            val output = BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).use {
                it.readLine()?.trim()
            }
            process.waitFor()
            output
        }.getOrNull()
    }

    private fun getPropViaRoot(propName: String): String? {
        return runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "getprop $propName"))
            val output = BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).use {
                it.readLine()?.trim()
            }
            process.waitFor()
            output
        }.getOrNull()
    }

    @SuppressLint("HardwareIds")
    @Suppress("DEPRECATION")
    private fun getLegacyHardwareSerial(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Build.getSerial()
                } else {
                    Build.SERIAL
                }
            }.getOrNull()
        }
        return null
    }

    private fun sha256(input: String): String {
        return runCatching {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(input.toByteArray(StandardCharsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        }.getOrDefault(UUID.nameUUIDFromBytes(input.toByteArray(StandardCharsets.UTF_8)).toString())
    }
}
