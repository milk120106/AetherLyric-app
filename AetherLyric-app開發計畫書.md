# 【AetherLyric-app 開發計畫書】

## 一、 專案概述與基本設定
本專案基於開源的 AirLyrics (MIT 授權) 進行 Fork，專案資料夾名為 AetherLyric-app。

* **App 顯示名稱**：將 strings.xml 中的 app_name 設為「AetherLyric」。
* **應用程式 ID**：將 build.gradle.kts 中的 applicationId 設為「com.aetherlyric.app」。
* **核心功能**：提供雙引擎模式，保留原有線上歌詞源，並支援連接自架伺服器。

---

## 二、 核心架構：雙引擎模式 (Dual-Engine Mode)

### 伺服器設定區塊 (Settings Screen)
* 新增輸入框：「AetherLyric 伺服器位址」（提示：http://192.168.x.x:8000）。
* 新增按鈕：「測試連線」。發送 GET /api/v1/health，成功顯示綠燈，失敗顯示錯誤。
* 新增開關：「啟用 AetherLyric 伺服器」。僅在連線成功且位址非空白時允許開啟。
* 將位址與開關狀態存入 DataStore 或 SharedPreferences。

### 歌詞獲取策略
* 若「伺服器功能」關閉：使用本地端原有的多源聚合邏輯。
* 若「伺服器功能」開啟：優先呼叫自架伺服器的 API。若無快取，觸發非同步生成任務，並顯示 Loading 提示。

### UI 狀態提示
* 不在懸浮窗 (Overlay) 上新增任何狀態圖示，維持原有介面簡潔。
* 狀態顯示僅限於「設定頁面」開關旁，或「App 主畫面」標題列。

---

## 三、 本地歌詞來源擴充 (未開啟伺服器時)

* **保留並整理原有來源**：LRCLIB、網易雲、Musixmatch、Spicy Lyrics。
* **新增社群來源**：QQ 音樂、酷狗音樂 (透過公開 API 查詢)。
* **來源優先級設定**：在設定頁面提供勾選清單，讓使用者自訂抓取順序。
* **錯誤處理**：單一來源失效必須用 try-catch 捕捉並跳過，確保 App 不崩潰。

---

## 四、 權限架構與媒體監聽

* **基礎權限**：NotificationListenerService 與 MediaSessionManager。
* **無障礙模式**：AccessibilityService，當媒體會話失效時讀取畫面上節點。
* **Shizuku ADB 模式**：
  * 整合 Shizuku API (`dev.rikka.shizuku:api:13.1.5`)。
  * 引導 Android 11+ 用戶開啟無線偵錯。
  * 動態偵測權限優先級：Shizuku > Accessibility > Basic。

---

## 五、 API Client 與開發輔助

* **建立 AetherLyricApiClient**：使用 Retrofit 或 Ktor 串接 `/api/v1/lyrics/search` 與 `/api/v1/tasks`。
* **一鍵編譯腳本 (`deploy.ps1`)**：
  * 執行 `.\gradlew assembleDebug`
  * 執行 `adb install -r app\build\outputs\apk\debug\app-debug.apk`
  * 執行 `adb shell am start -n com.aetherlyric.app/.MainActivity`
