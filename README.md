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
1. **自架 AI 伺服器引擎 (AetherLyric-server)**：可連接自架伺服器，使用 Faster-Whisper / Groq / OpenAI 相容模型進行 AI 語音轉錄、逐字對齊與智慧歌詞校正。
2. **本機多源聚合引擎**：未連接伺服器或自架伺服器離線時，自動無縫降級使用本地多來源聚合搜尋（網易雲、QQ 音樂、酷狗音樂、LRCLIB、Musixmatch、Genius、AZLyrics 等）。

---

## 核心功能特色 (Key Features)

### 1. 雙引擎架構 (Dual-Engine Architecture)
- **伺服器健康檢測**：在「設定」頁面提供伺服器位址輸入與「測試連線」驗證，即時顯示連線燈號與佇列狀態。
- **無縫自動降級**：優先呼叫自架伺服器 API；連線逾時或快取未命中時，自動無痛切換為本地多源查詢。

### 2. 本地歌詞來源擴充 (Multi-Source Aggregation)
- 支援自由排序、勾選與啟用多家歌詞源：
  - **網易雲音樂 (NetEase Cloud Music)**
  - **QQ 音樂 (QQ Music)**
  - **酷狗音樂 (Kugou)**
  - **LRCLIB**
  - **Musixmatch**
  - **Genius**
  - **AZLyrics**
  - **Spicy Lyrics (逐字動態歌詞)**

### 3. 本地音訊 AI 歌詞辨識
- 主畫面支援直接選取本機音檔（MP3、FLAC、WAV 等）。
- 一鍵上傳至自架 AetherLyric-server 進行 AI 語音辨識與時間軸自動對齊，即時生成並儲存標準 LRC 歌詞。

### 4. 多歌詞版本管理 (Multi-Version Lyrics)
- 單一歌曲支援儲存與管理多種歌詞版本（如「官方原版」、「AI 辨識版」、「日文翻譯版」、「羅馬拼音版」、「自訂修改版」）。
- 一鍵即時切換版本，支援新增、命名與刪除。
- **版本專屬時間軸偏移**：切換版本時，自動套用該版本設定之專屬時間軸偏移量。

### 5. 歌詞時間軸微調與儲存
- 支援以 `±0.1s` 及 `±1s` 精細微調時間軸。
- 提供「儲存目前偏移量」按鈕，明確持久化至本地資料庫。
- 提供「是否套用到所有歌曲 (全域偏移量)」開關，輕鬆解決特定耳機延遲問題。

### 6. 歌詞手動編輯器
- 在最近播放與本機清單中點擊即可進入編輯模式。
- 支援手動修改錯字、微調各行時間戳記，並執行格式校驗與覆蓋儲存。

### 7. 原生極簡設計與介面固定
- 底部導航欄固定置底，防止旋轉橫向時突兀切換為側邊欄。
- 完整適配深色與淺色主題，圓角與色系完全融入現代 Material 設計語彙。

---

## 快速開始 (Quick Start)

### 系統需求
- Android 8.0 (API Level 26) 或以上。
- 音樂播放器需支援標準 Android MediaSession（如 Spotify、Apple Music、YouTube Music、Poweramp 等）。

### 安裝步驟
1. 前往 [Releases](https://github.com/milk120106/AetherLyric-app/releases) 下載最新 `app-debug.apk`。
2. 在手機上安裝並授予「通知讀取權限」與「懸浮視窗權限」。
3. 播放任意音樂，開啟 AetherLyric 懸浮窗即可開始享受同步歌詞。

### 連接自架伺服器
1. 前往 **設定 -> AetherLyric 伺服器**。
2. 輸入自架伺服器位址（例如 `http://192.168.1.100:8000`）與 API Key（選填）。
3. 點擊「測試連線」驗證通過後，開啟「啟用 AetherLyric 伺服器」開關即可。

---

## 建置與開發 (Build)

```bash
# 複製專案
git clone https://github.com/milk120106/AetherLyric-app.git
cd AetherLyric-app

# 確保 local.properties 已設定 Android SDK 路徑
echo "sdk.dir=D:/Android/Sdk" > local.properties

# 執行單元測試
./gradlew testDebugUnitTest

# 打包 Debug APK
./gradlew assembleDebug
```

產出 APK 路徑位於：`app/build/outputs/apk/debug/app-debug.apk`。

---

## 授權條款 (License)

本專案基於 [AirLyrics](https://github.com/AirLyrics/AirLyrics) (MIT License) 進行 Fork 與二次開發。
本專案同樣採用 [MIT 授權條款](LICENSE)。
