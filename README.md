<div align="center">

<img src="docs/assets/airlyrics-logo.png" width="120" alt="AetherLyric Logo" />

# AetherLyric-app
**全功能 Android 懸浮同步歌詞客戶端 · 雙引擎與僅伺服器 AI 語音轉錄架構**

[![Platform](https://img.shields.io/badge/Platform-Android_8.0+-brightgreen?style=flat-square)](https://github.com/milk120106/AetherLyric-app)
[![Version](https://img.shields.io/badge/Version-0.1.0-blueviolet?style=flat-square)](https://github.com/milk120106/AetherLyric-app/releases)
[![License](https://img.shields.io/badge/License-MIT-orange?style=flat-square)](LICENSE)

[繁體中文](README.md) · [简体中文](README.zh-CN.md) · [English](README.en.md) · [系統架構與開發說明](docs/ARCHITECTURE.md) · [外部 Intent API 與 JSON 外掛規範](docs/INTENT_API_PLUGIN_SPEC.md)

</div>

---

## 專案概述 (Overview)

**AetherLyric-app** 是一款開源、輕量且功能強大的 Android 懸浮同步歌詞應用程式（基於 [AirLyrics](https://github.com/AirLyrics/AirLyrics) 進行擴充與重構）。

本專案支援彈性的**伺服器協同模式**：
1. **雙引擎模式 (Dual-Engine Mode)**：優先透過自架 [AetherLyric-server](https://github.com/milk120106/AetherLyric-server) 進行搜尋、Faster-Whisper / Groq / OpenAI 相容模型 AI 語音轉錄、逐字對齊 (`POST /api/v1/align`) 與影音網址解析；當伺服器離線或未命中時，自動無縫切換至本機多來源聚合引擎。
2. **僅伺服器模式 (Server-Only Mode)**：所有歌詞搜尋、時間軸對齊與 AI 轉錄完全交由自架伺服器統一處理，不在本機直接請求第三方歌詞來源。

---

## 核心功能特色 (Key Features)

### 1. 伺服器協同架構、WebSocket 進度推播與多層安全防護
- **多伺服器設定檔管理**：支援儲存多組自架伺服器設定（自訂名稱、伺服器 URL、Proxy 與驗證憑證），並提供快速選單即時切換。
- **雙引擎與僅伺服器模式切換**：可依網路與隱私需求自由切換「雙引擎模式（本地 + 伺服器協同）」與「僅伺服器模式（全交由伺服器搜尋與辨識）」。
- **WebSocket 即時任務進度推播**：開啟「使用 WebSocket 傳輸進度」後，優先透過 `/ws/tasks/{taskId}` 即時接收轉錄、下載與對齊進度；若網路環境不支援 WebSocket，自動無縫降級為 HTTP 輪詢。
- **多層安全驗證與 Android Keystore 加密**：支援 `APP_ACCESS_TOKEN`、PIN 碼 (`X-Aether-Pin`) 與問答驗證答案 (`X-Aether-Challenge-Answer`)，所有敏感憑證皆透過 Android Keystore (`AES/GCM/NoPadding`) 加密儲存。
- **HTTP(S) Proxy 代理支援**：支援為伺服器連線配置獨立的 HTTP/HTTPS Proxy 主機、連接埠與帳密驗證。

### 2. 多源平行搜尋、候選結果 BottomSheet 與自訂 JSON 外掛
- **多源平行搜尋引擎**：平行查詢已啟用之多家歌詞來源（網易雲音樂、QQ 音樂、酷狗音樂、LRCLIB、Musixmatch、Genius、AZLyrics、自訂 JSON 外掛與自架伺服器），並依據「使用者設定優先順序 -> 是否具備 LRC 時間軸 -> 歌曲時長相近度」智慧排序。
- **搜尋結果候選清單 (BottomSheet)**：手動搜尋或開啟「自動搜尋時彈出結果清單」時，以清單完整呈現來源名稱、時間軸標籤、歌曲時長、匹配標題與前 4 行歌詞預覽，點選即可一鍵套用。
- **自訂 JSON 來源外掛系統**：支援透過網址 (URL) 或直接貼上 JSON 規則匯入第三方歌詞來源，具備嚴格沙盒安全限制（僅允許 HTTP/HTTPS、8 秒連線逾時、2MB 回應上限，禁止動態腳本執行）以及 6 / 12 / 24 / 72 小時定時自動更新機制。
- **自訂 API 金鑰 (BYOK)**：允許填寫使用者自備之 Genius 與 Musixmatch 金鑰，解鎖更高查詢配額與穩定性。

### 3. 本地 Google 翻譯與三種歌詞顯示/匯出格式
- **本地 Google 翻譯補齊**：開啟「啟用本地 Google 翻譯」後，當外語歌曲無現成翻譯時，自動於背景將每行歌詞翻譯為繁體中文 (`zh-TW`) 並完整保留 `[mm:ss.xx]` 時間軸標籤。
- **歌詞顯示格式快速切換**：於設定首頁與媒體首頁皆提供「僅原文」、「僅翻譯」、「雙語對照」三種顯示模式一鍵切換。
- **三種格式歌詞匯出**：單曲編輯器與本地歌詞管理頁面均支援選擇匯出為：
  1. **標準 LRC**（僅原文，保留 `[mm:ss.xx]` 時間軸）
  2. **雙語 LRC**（原文與翻譯同行或雙時間軸 LRC）
  3. **純文字 TXT**（自動剝除所有時間戳標籤）

### 4. 多歌詞版本管理與嚴格優先級時間軸偏移量
- **多版本管理**：單一歌曲支援儲存與切換多種歌詞版本（官方原版、AI 辨識版、翻譯版、手動匯入版），懸浮視窗即時同步。
- **嚴格優先級時間軸偏移量**：實作「單曲專屬偏移量 > 全域 (App 層級) 偏移量 > 預設 0」，於「設定 -> 系統」內提供全域偏移量快速微調面板 (`±100ms`, `±500ms`, `0`)。

### 5. 權限模式與外部控制 Intent API 安全防護
- **權限模式**：於系統設定頁面提供三種媒體偵測方式說明與狀態檢視（基礎通知監聽、進階無障礙輔助、Shizuku 模式），並支援 Android 13+ 受限制設定引導解鎖。
- **外部控制 Intent API 與自訂權限保護**：
  - 宣告並強制驗證系統級自訂權限 `com.airlyrics.app.permission.ACCESS_LYRICS`。
  - 支援 `SHOW_OVERLAY`、`HIDE_OVERLAY`、`TOGGLE_OVERLAY`、`QUERY_LYRICS` 與 `ACTION_SEND`（50KB 上限）。
  - 支援 `PendingIntent` (`reply_pending_intent`) 雙向安全回傳狀態與歌詞內容，並透過 `creatorPackage` 驗證呼叫端真實套件名稱。
  - 應用程式權限選單採用背景非同步載入與快取機制，秒開不卡頓。

### 6. 全域執行日誌 (AppLogger) 與亮暗色語意化主題
- **全域執行日誌**：即時記錄權限、網路請求、來源查詢與 Intent 調用，支援關鍵字過濾、複製與匯出。
- **亮暗色模式即時生效**：切換淺色或深色主題時即時重建介面與系統狀態列，自訂背景色與標籤徽章自動適配亮暗對比度，並於「設定 -> 系統」內提供「簡潔模式」開關。

---

## 快速開始 (Quick Start)

### 系統需求
- 最低 Android 8.0 (API Level 26)，建議 Android 11.0 (API Level 30) 以上。

### 安裝與設定
1. 前往 [Releases](https://github.com/milk120106/AetherLyric-app/releases) 下載最新 `AetherLyric-v{版本號}-{buildType}.apk`。
2. 安裝並授予「通知讀取權限」與「懸浮視窗權限」。
3. 若需連接自架伺服器，前往 **設定 -> 伺服器**，輸入伺服器位址並點選「測試連線」，即可啟用雙引擎或僅伺服器模式。

---

## 建置與開發 (Build)

```bash
# 複製專案
git clone https://github.com/milk120106/AetherLyric-app.git
cd AetherLyric-app

# 執行單元測試
./gradlew testDebugUnitTest

# 打包 Debug APK
./gradlew assembleDebug
```

產出 APK 檔案位置：`app/build/outputs/apk/debug/AetherLyric-v0.1.0-debug.apk`

---

## 授權條款 (License)

本專案基於 [AirLyrics](https://github.com/AirLyrics/AirLyrics) (MIT License) 進行 Fork 與二次開發，採用 [MIT 授權條款](LICENSE)。
