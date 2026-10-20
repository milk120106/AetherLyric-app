<div align="center">

<img src="docs/assets/airlyrics-logo.png" width="120" alt="AetherLyric Logo" />

# AetherLyric-app
**全功能 Android 懸浮同步歌詞客戶端 · 雙引擎 AI 語音轉錄架構**

[![Platform](https://img.shields.io/badge/Platform-Android_8.0+-brightgreen?style=flat-square)](https://github.com/milk120106/AetherLyric-app)
[![Version](https://img.shields.io/badge/Version-0.1.0-blueviolet?style=flat-square)](https://github.com/milk120106/AetherLyric-app/releases)
[![License](https://img.shields.io/badge/License-MIT-orange?style=flat-square)](LICENSE)

[繁體中文](README.md) · [简体中文](README.zh-CN.md) · [English](README.en.md)

</div>

---

## 專案概述 (Overview)

**AetherLyric-app** 是一款開源、輕量且功能強大的 Android 懸浮同步歌詞應用程式（基於 [AirLyrics](https://github.com/AirLyrics/AirLyrics) 進行擴充與重構）。

本專案最大特色在於**雙引擎模式 (Dual-Engine Mode)**：
1. **自架 AI 伺服器引擎 ([AetherLyric-server](https://github.com/milk120106/AetherLyric-server))**：可連接自架伺服器，使用 Faster-Whisper / Groq / OpenAI 相容模型進行 AI 語音轉錄、逐字對齊 (POST `/api/v1/align`)、影音網址解析與智慧歌詞格式校正。
2. **本機多源聚合引擎**：未連接伺服器或自架伺服器離線時，自動無縫降級使用本地多來源聚合搜尋（網易雲、QQ 音樂、酷狗音樂、LRCLIB、Musixmatch、Genius、AZLyrics 等）。

---

## 核心功能特色 (Key Features)

### 1. 雙引擎架構與多伺服器設定 (Dual-Engine Architecture)
- **多伺服器設定檔管理**：支援儲存多組自架伺服器設定（自訂名稱、伺服器 URL），並提供快速下拉選單即時切換。
- **公開狀態與健康檢測**：切換時自動呼叫 `GET /api/v1/health` 測試連線，即時呈現伺服器在線狀態、AI 模型名稱、處理佇列長度及 yt-dlp 模組可用性。App 端完全公開透明，無需儲存或綁定管理者私鑰。
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
- **常用來源卡片與彈窗管理**：卡片上緊湊呈現前 4 大常用來源，點擊「更多來源…」彈出完整視窗輕鬆調整排序與啟閉狀態。
- **自訂 API 金鑰 (BYOK)**：允許填寫使用者自備金鑰，解鎖更高查詢配額與請求穩定性。

### 3. 多歌詞版本管理與媒體首頁切換
- 單一歌曲支援儲存與管理多種歌詞版本（如「官方原版」、「AI 辨識版」、「日文翻譯版」、「使用者提供對齊版」、「影片轉錄版」）。
- 提供「重新命名」與「刪除」特定版本功能，懸浮視窗即時同步切換。
- **媒體首頁歌詞切換卡片**：播放音樂時直接在媒體首頁呈現目前歌曲的所有可用版本，點擊即可切換生效。

### 4. 嚴格優先級時間軸偏移量系統
- 實作三層優先級：**單曲專屬偏移量 > 全域 (App 層級) 偏移量 > 預設 0**。
- 提供「儲存為單曲偏移量」與「儲存為全域偏移量」獨立操作，切換不同版本時自動載入專屬偏移量。
- 主設定頁提供全域偏移量快速微調面板 (`±100ms`, `±500ms`, `0`)。

### 5. 三層權限架構與系統狀態檢視
- 集中於系統設定頁面，客觀展示三層權限運作狀態：
  - **1. 基礎模式**：使用標準媒體通知監聽與懸浮視窗權限。
  - **2. 無障礙模式**：通用穩定的前台 App 與媒體資訊監聽。
  - **3. Shizuku 模式**：高權限免無障礙服務，提供極致流暢與穩定體驗。
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
  - 獨立 Dialog 應用程式權限管理選單，支援即時關鍵字搜尋與「僅顯示音樂 App」篩選。

### 7. AI 音訊辨識、參考歌詞對齊與影片網址生成
- **本地音檔辨識**：選取本機音檔一鍵送至伺服器完成 AI 辨識並產生歌詞。
- **手動輸入參考歌詞進行對齊**：支援貼上無時間戳的純文字歌詞，結合本機音檔發送至伺服器 `/api/v1/align` 進行精準對齊；若伺服器未連線，則自動降級為本地簡易平均時間戳對齊。
- **影片網址解析**：支援輸入 YouTube、Bilibili 等影音網址，即時偵測伺服器 yt-dlp 支援狀態，透過後端自動下載音訊並辨識生成同步 LRC 歌詞。
- **整合 YTDLnis 本地下載**：若自架伺服器未啟用 yt-dlp 或面臨影音平台登入限制，可一鍵調用 YTDLnis 本地下載音訊後進行辨識與對齊。
- **媒體頁快捷入口**：支援在設定中自訂是否於媒體首頁顯示 AI 辨識與影片解析區塊。

### 8. 全域執行日誌系統 (AppLogger)
- 獨立子頁面即時呈現 App 啟動期間所有關鍵事件（權限、網路請求、來源查詢、Intent 調用）。
- 支援 INFO / WARN / ERROR 等級原創色彩標籤、關鍵字搜尋過濾。
- 底部功能鍵支援：自動滾動開關、一鍵複製全部至剪貼簿、匯出為 TXT 檔案與清除日誌。

### 9. 自訂色彩主題、簡潔模式與原廠設計適配
- 嚴格維持無 Emoji、標準繁體中文與原創 5 種主題強調色。
- **簡潔模式 (Compact Mode)**：開啟後自動隱藏所有輔助說明與描述文字，呈現最清爽純粹的操作介面。
- **自訂 HEX 顏色**：支援自由輸入 Primary 與 Background HEX 碼，提供即時預覽色塊、一鍵套用與重設回預設主題。
- 底部導航欄固定置底，防止旋轉橫向時切換為側邊欄。

---

## 快速開始 (Quick Start)

### 系統需求
- 最低 Android 8.0 (API Level 26)，建議 Android 11.0 (API Level 30) 以上。
- 本 App 透過三層權限架構偵測媒體狀態：
  > 標準音樂播放器（如 Spotify）：支援 MediaSession，體驗最佳。  
  > 影音平台（如 YouTube, Bilibili）：因其未完整實作標準協議，建議開啟「無障礙模式」或「Shizuku 模式」以獲得精準進度與同步。

### 安裝步驟
1. 前往 [Releases](https://github.com/milk120106/AetherLyric-app/releases) 下載最新 `AetherLyric-v{版本號}-{buildType}.apk`。
2. 在手機上安裝並授予「通知讀取權限」與「懸浮視窗權限」。
3. 播放任意音樂，開啟 AetherLyric 懸浮窗即可開始享受同步歌詞。

### 連接自架伺服器
1. 前往 **設定 -> AetherLyric 伺服器**。
2. 輸入自架伺服器名稱與位址（例如 `http://192.168.1.100:8000`）。
3. 點擊「儲存設定」，系統會自動進行連線與能力檢測。
4. 開啟「啟用 AetherLyric 伺服器」開關即可享受雙引擎模式。

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
