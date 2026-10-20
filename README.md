# AetherLyric

<div align="center">

# AetherLyric-app
**全功能 Android 懸浮同步歌詞客戶端 · 雙引擎 AI 語音轉錄架構**

[![Platform](https://img.shields.io/badge/Platform-Android_8.0+-brightgreen?style=flat-square)](https://github.com/milk120106/AetherLyric-app)
[![Version](https://img.shields.io/badge/Version-0.1.0-blueviolet?style=flat-square)](https://github.com/milk120106/AetherLyric-app/releases)
[![License](https://img.shields.io/badge/License-MIT-orange?style=flat-square)](LICENSE)

</div>

---

## 專案概述 (Overview)

**AetherLyric-app** 是一款開源、輕量且功能強大的 Android 懸浮同步歌詞應用程式（基於 AirLyrics 進行擴充與重構）。

本專案最大特色在於**雙引擎模式 (Dual-Engine Mode)**：
1. **自架 AI 伺服器引擎 (AetherLyric-server)**：可連接自架伺服器，使用 Faster-Whisper / Groq / OpenAI 相容模型進行 AI 語音轉錄、逐字對齊 (POST `/api/v1/align`)、影音網址解析與智慧歌詞格式校正。
2. **本機多源聚合引擎**：未連接伺服器或自架伺服器離線時，自動無縫降級使用本地多來源聚合搜尋（網易雲、QQ 音樂、酷狗音樂、LRCLIB、Musixmatch、Genius、AZLyrics 等）。

---

## 核心功能特色 (Key Features)

### 1. 雙引擎架構 (Dual-Engine Architecture)
- **伺服器健康檢測**：在「設定 -> AetherLyric 伺服器」提供位址輸入、存取金鑰 (ADMIN_API_KEY) 與「測試連線」驗證，即時顯示在線狀態、佇列長度、轉錄引擎與 yt-dlp 影音解析能力。
- **無縫自動降級**：優先呼叫自架伺服器 API；連線逾時或快取未命中時，自動無痛切換為本地多源查詢。
- **雲端錨點校準**：純文字來源（如 Genius、AZLyrics）可自動請求伺服器 `/api/v1/align` 進行高精準度逐行時間軸對齊；伺服器離線時自動降級本地簡易時間軸。

### 2. 本地歌詞來源擴充與 BYOK (自帶金鑰)
- 支援自由拖曳排序、勾選與啟用多家歌詞來源：
  - **網易雲音樂 (NetEase Cloud Music)**
  - **QQ 音樂 (QQ Music)**
  - **酷狗音樂 (Kugou)**
  - **LRCLIB**
  - **Musixmatch** (支援自訂 User Token)
  - **Genius** (支援自訂 Client Access Token)
  - **AZLyrics**
  - **Spicy Lyrics (逐字動態歌詞)**
- **自訂 API 金鑰 (BYOK)**：允許填寫使用者自備金鑰，解鎖更高查詢配額與請求穩定性。

### 3. 多歌詞版本管理與媒體首頁切換
- 單一歌曲支援儲存與管理多種歌詞版本（如「官方原版」、「AI 辨識版」、「日文翻譯版」、「使用者提供對齊版」、「影片轉錄版」）。
- 提供「重新命名」與「刪除」特定版本功能，懸浮視窗即時同步切換。
- **媒體首頁歌詞切換卡片**：播放音樂時直接在媒體首頁呈現目前歌曲的所有可用版本，點擊即可切換生效。

### 4. 嚴格優先級時間軸偏移量系統
- 實作三層優先級：**單曲專屬偏移量 > 全域 (App 層級) 偏移量 > 預設 0**。
- 提供「儲存為單曲偏移量」與「儲存為全域偏移量」獨立操作，切換不同版本時自動載入專屬偏移量。
- 主設定頁提供全域偏移量快速微調面板 (`±100ms`, `±500ms`, `0`)。

### 5. 三層權限架構與系統設定跳轉
- **Shizuku 模式**：高權限免無障礙服務，提供極致流暢與穩定體驗。
- **無障礙模式**：通用穩定的前台 App 與媒體資訊監聽。
- **基礎模式**：使用標準通知讀取與懸浮視窗權限。
- **Android 13+ 受限制設定解鎖**：精準偵測系統 restricted settings 狀態，一鍵直達對應應用程式詳細資訊頁面引導解除限制。

### 6. 擴充與外部控制 (Intent API & 安全管控)
- 支援標準 Android 廣播 Intent API 控制：
  - `SHOW_OVERLAY`：顯示懸浮歌詞窗。
  - `HIDE_OVERLAY`：隱藏懸浮歌詞窗。
  - `TOGGLE_OVERLAY`：切換懸浮歌詞窗顯示/隱藏。
  - `ACTION_SEND`：直接接收並載入外部傳入之 LRC 歌詞（具備 50KB 安全大小上限）。
- **安全防護機制**：
  - 開啟 Intent API 時強制顯示二次確認免責聲明。
  - 未知 App 呼叫處置策略：拒絕 (預設) / 詢問 / 允許。
  - 外部調用白黑名單資料庫，支援「智慧篩選」音樂播放器與「顯示所有 App」雙檢視。

### 7. AI 音訊辨識、參考歌詞對齊與影片網址生成
- **本地音檔辨識**：選取本機音檔一鍵送至伺服器完成 AI 辨識並產生歌詞。
- **參考歌詞對齊**：支援手動貼上無時間戳的純文字歌詞，結合音檔自動校準時間軸；若未連線伺服器則自動觸發本地簡易時間軸對齊（根據音檔長度平均分配時間戳）。
- **影片網址解析**：支援輸入 YouTube、Bilibili 等影音網址，即時偵測伺服器 yt-dlp 支援狀態，透過後端自動下載音訊並辨識生成同步 LRC 歌詞。

### 8. 全域執行日誌系統 (AppLogger)
- 獨立子頁面即時呈現 App 啟動期間所有關鍵事件（權限、網路請求、來源查詢、Intent 調用）。
- 支援 INFO / WARN / ERROR 等級原創色彩標籤、關鍵字搜尋過濾。
- 底部功能鍵支援：自動滾動開關、一鍵複製全部至剪貼簿、匯出為 TXT 檔案與清除日誌。

### 9. 自訂色彩主題與原廠設計適配
- 嚴格維持無 Emoji、標準繁體中文與原創 5 種主題強調色。
- **自訂 HEX 顏色**：支援自由輸入 Primary 與 Background HEX 碼，提供即時預覽色塊、一鍵套用與重設回預設主題。
- 底部導航欄固定置底，防止旋轉橫向時突兀切換為側邊欄。

---

## 快速開始 (Quick Start)

### 系統需求
- Android 8.0 (API Level 26) 或以上。
- 本 App 透過三層權限架構偵測媒體狀態：
  > 標準音樂播放器（如 Spotify）：支援 MediaSession，體驗最佳。  
  > 影音平台（如 YouTube, Bilibili）：因其未完整實作標準協議，建議開啟「無障礙模式」或「Shizuku 模式」以獲得精準進度與同步。

### 安裝步驟
1. 前往 [Releases](https://github.com/milk120106/AetherLyric-app/releases) 下載最新 `AetherLyric-v0.1.0.apk`。
2. 在手機上安裝並授予「通知讀取權限」與「懸浮視窗權限」。
3. 播放任意音樂，開啟 AetherLyric 懸浮窗即可開始享受同步歌詞。

### 連接自架伺服器
1. 前往 **設定 -> AetherLyric 伺服器**。
2. 輸入自架伺服器位址（例如 `http://192.168.1.100:8000`）與 API Key（選填）。
3. 點擊「測試連線」驗證通過後，開啟「啟用 AetherLyric 伺服器」開關即可享受雙引擎模式。

---

## 建置與開發 (Build)

```bash
# 複製專案
git clone https://github.com/milk120106/AetherLyric-app.git
cd AetherLyric-app

# 確保 local.properties 已設定 Android SDK 路徑
echo "sdk.dir=/path/to/your/android/sdk" > local.properties

# 執行單元測試
./gradlew testDebugUnitTest

# 打包 Debug APK
./gradlew assembleDebug
```

產出 APK 檔名格式為：`AetherLyric-v{版本號}-{buildType}.apk`  
檔案位置：`app/build/outputs/apk/debug/AetherLyric-v0.1.0-debug.apk`

---

## 授權條款 (License)

本專案基於 [AirLyrics](https://github.com/AirLyrics/AirLyrics) (MIT License) 進行 Fork 與二次開發。
本專案同樣採用 [MIT 授權條款](LICENSE)。
