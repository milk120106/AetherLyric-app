# 隱私權政策 (Privacy Policy)

[繁體中文](PRIVACY.md) · [简体中文](PRIVACY.zh-CN.md) · [English](PRIVACY.en.md)

**AetherLyric-app** 是一款專為 Android 設計的開源懸浮同步歌詞應用程式。本應用程式秉持極致隱私保護原則，**絕不會收集、上傳、出售或共享使用者的任何個人資料**。

---

## 1. 資料收集原則 (Data Collection)

- **不收集個人資料**：AetherLyric-app 不會收集使用者之任何個人身份識別資訊（如姓名、電子郵件、電話號碼、設備唯一識別碼等）。
- **無追蹤與廣告元件**：本應用程式不包含任何第三方統計分析 SDK、廣告 SDK、使用者追蹤 SDK、帳號登入系統或崩潰自動回報服務。

---

## 2. Android 權限使用說明 (Permissions)

AetherLyric-app 僅在實現核心功能所需時，才會向系統申請下列權限：

| 權限項目 | 權限類型 | 用途說明 |
| :--- | :--- | :--- |
| **懸浮視窗權限**<br>`SYSTEM_ALERT_WINDOW` | 系統懸浮窗 | 於其他應用程式頂層顯示同步滾動之懸浮歌詞視窗。 |
| **通知存取權限**<br>`NotificationListenerService` | 媒體通知監聽 | 於裝置本機即時偵測播放中的音樂曲目資訊（歌名、歌手、專輯、封面與播放狀態）。**絕不上傳或紀錄任何私人通訊通知內容**。 |
| **前台服務通知**<br>`POST_NOTIFICATIONS` | 系統通知 | 讓 Android 系統維持懸浮歌詞前台服務穩定運行，避免背景運作時遭系統中斷。 |
| **使用情況存取權限**<br>`PACKAGE_USAGE_STATS` | 可選權限 | 僅於本機用於「顯示範圍 (Display Scope)」功能，判斷使用者所勾選之 App 是否正處於前台可見狀態。**絕不讀取應用程式內容，亦不紀錄使用數據**。 |
| **網路存取權限**<br>`INTERNET` | 網路連線 | 於使用者執行線上歌詞搜尋、本地 Google 翻譯、自訂 JSON 外掛查詢或伺服器轉錄時發送請求。 |
| **無障礙服務權限**<br>`AccessibilityService` | 可選權限 | 針對未完整實作標準 MediaSession 協議之影音 App（如 YouTube、Bilibili），於本機輔助偵測播放進度與狀態。**絕不記錄任何鍵盤輸入或畫面隱私內容**。 |
| **Shizuku 存取權限** | 可選權限 | 透過使用者已安裝並授權之 Shizuku 進行高權限免無障礙之媒體偵測。純本機程序間通訊，不涉及任何外連行為。 |
| **外部歌詞存取自訂權限**<br>`com.airlyrics.app.permission.ACCESS_LYRICS` | 自訂權限 | 保護外部控制廣播接收器 (`AetherLyricIntentReceiver`)，防止未宣告權限之第三方應用程式發送惡意廣播。 |
| **檔案選取器**<br>`ACTION_OPEN_DOCUMENT` | 儲存存取 | 允許使用者手動選取本機音檔進行 AI 辨識、匯入自訂字型或匯入/匯出本機歌詞檔。 |

---

## 3. 本地資料儲存與硬體級加密 (Local Storage & Hardware Encryption)

- 本應用程式會在裝置本機儲存以下資料：
  - 應用程式設定與介面偏好（主題色彩、亮暗模式、簡潔模式開關等）。
  - 自架 AetherLyric 伺服器設定檔（名稱、URL、引擎協同模式、WebSocket 開關、Proxy 設定）。
  - **Android Keystore 硬體加密憑證**：伺服器驗證憑證（`APP_ACCESS_TOKEN`、PIN 碼、問答驗證答案）與 Proxy 密碼均透過 Android Keystore (`AES/GCM/NoPadding`) 加密儲存，不會以明文落盤。
  - 歌詞來源之排序、啟用狀態、自訂 JSON 外掛規則與自訂 API 金鑰 (BYOK)。
  - 本地快取歌詞檔、多版本歌詞庫與自訂時間軸偏移量記錄。
  - 應用程式執行日誌（AppLogger，僅保存在 RAM 與本機臨時儲存空間）。
- **上述所有資料均完全保留於使用者裝置本機**。除非使用者主動透過系統分享、匯出功能或第三方工具進行傳輸，否則本應用程式絕不會主動將其傳送至任何遠端伺服器。

---

## 4. 聯網歌詞搜尋、本地翻譯與自架 AI 伺服器 (Online Search & Server Modes)

- **多源平行歌詞搜尋**：
  - 當使用者搜尋線上歌詞時，應用程式會將查詢所需的曲目元資料（歌名、歌手、專輯與歌曲時長）發送至已啟用的歌詞提供商或使用者安裝之自訂 JSON 外掛來源。
  - 若使用者切換為「**僅伺服器模式**」，App 將停用所有本機對第三方平台的直接查詢，全數交由使用者指定的自架伺服器處理。
- **本地 Google 翻譯**：
  - 當使用者主動開啟「啟用本地 Google 翻譯」且歌曲無現成翻譯時，應用程式僅將純歌詞文本發送至翻譯端點以產生繁體中文對照，不包含任何個人識別資訊。
- **自架 AetherLyric-server 引擎**：
  - 當使用者啟用伺服器並設定自架位址時，主動選取的音訊檔案、純文字歌詞或輸入之影片網址，將直接透過 HTTP(S) 或 WebSocket (`/ws/tasks/{taskId}`) 發送至**使用者指定的自架伺服器**。
  - **AetherLyric 開發團隊不維護任何中央雲端伺服器**，無法存取、截獲亦不擁有任何使用者傳送至自架伺服器的音訊與文本內容。

---

## 5. 外部控制與廣播 Intent API 安全性 (External Control Security)

- AetherLyric-app 支援透過標準 Android Broadcast Intent 接收外部自動化指令与雙向 `PendingIntent` 查詢。
- 為了保障系統安全性：
  - 強制要求呼叫端具備 `com.airlyrics.app.permission.ACCESS_LYRICS` 權限。
  - 透過 `PendingIntent.getCreatorPackage()` 驗證真實呼叫方套件名稱。
  - 未知應用程式之調用預設採取「拒絕」處置，並提供獨立的應用程式白名單與黑名單權限管理介面。

---

## 6. 聯絡與回饋 (Contact)

若對本隱私權政策或資料安全有任何疑問、建議或漏洞回報，請透過 GitHub Issues 與我們聯繫：

[https://github.com/milk120106/AetherLyric-app/issues](https://github.com/milk120106/AetherLyric-app/issues)
