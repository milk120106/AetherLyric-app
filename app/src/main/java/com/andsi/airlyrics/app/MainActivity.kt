package com.andsi.airlyrics.app

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.andsi.airlyrics.app.viewmodel.MainViewModel
import com.andsi.airlyrics.network.aether.AetherLyricApiClient
import com.andsi.airlyrics.plugins.PluginStoreManager

class MainActivity : AppCompatActivity() {
    internal val mainViewModel: MainViewModel by viewModels {
        MainViewModel.factory(applicationContext)
    }
    internal lateinit var graph: MainGraph
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        graph = MainGraph(this, mainViewModel)
        graph.onCreate()

        // 註冊安全憑證不相符強制登出監聽器
        AetherLyricApiClient.onSecurityMismatchListener = { message ->
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    AlertDialog.Builder(this)
                        .setTitle("安全性警告")
                        .setMessage(message.ifBlank { "憑證與裝置不符或偵測到異常活動，基於安全性考量已強制登出。" })
                        .setPositiveButton("確認", null)
                        .show()
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::graph.isInitialized) graph.uiHost.interactionUi.capture()
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        graph.onStart()

        // 檢查啟動時自動停用之風險外掛，彈出單一合併彈窗提醒
        val pendingAlerts = PluginStoreManager.getPendingDisabledAlerts(this)
        if (pendingAlerts.isNotEmpty()) {
            val messageText = buildString {
                appendLine("系統在啟動檢測時發現以下外掛已被社群標記為危險或封禁，基於安全性考量已自動停用：")
                pendingAlerts.forEach { (name, reason) ->
                    appendLine("· $name: $reason")
                }
                append("如需重新啟用或尋求技術支援，請前往「擴充與外部控制 -> 社群擴充商店」。")
            }
            AlertDialog.Builder(this)
                .setTitle("外掛安全停用提醒")
                .setMessage(messageText)
                .setPositiveButton("確認", null)
                .show()
            PluginStoreManager.clearPendingDisabledAlerts(this)
        }
    }

    override fun onResume() {
        super.onResume()
        graph.onResume()
    }

    override fun onStop() {
        if (::graph.isInitialized) {
            graph.onStop()
        }
        super.onStop()
    }

    override fun onDestroy() {
        AetherLyricApiClient.onSecurityMismatchListener = null
        if (::graph.isInitialized) {
            graph.onDestroy()
        }
        super.onDestroy()
    }
}
