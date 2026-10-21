# AetherLyric-app 系統架構與開發說明

[繁體中文](ARCHITECTURE.md) · [简体中文](ARCHITECTURE.zh-CN.md) · [English](ARCHITECTURE.en.md)

---

## 1. 核心架構概覽

AetherLyric-app 採用模組化分層設計，結合本機多源歌詞檢索與自架伺服器 AI 語音轉錄能力：

1. **介面與主題系統 (`ui/`)**
   - 支援亮色與暗色模式即時重建（`MainActivityUiHost.toggleThemeMode()` 連動 `applyPaletteChange`），並自動驗證自訂背景色與主題亮度相容性。
   - **設定首頁 (`SettingsHomePage`)**：集中管理搜尋策略、自動彈出候選清單開關、本地 Google 翻譯開關與歌詞顯示格式（僅原文 / 僅翻譯 / 雙語對照）快速切換。
   - **系統設定頁 (`SystemSettingsPage`)**：集中管理「權限模式」（基礎通知監聽、進階無障礙輔助、狀態列擷取 / Shizuku）、「簡潔模式」與「全域時間軸偏移量」。
   - **媒體首頁 (`MediaPage`)**：整合「歌詞顯示格式」快速切換、「手動搜尋歌詞（多源候選清單）」入口，以及「媒體頁 AI 歌詞辨識」摺疊開關與功能卡。

2. **多源平行搜尋與翻譯引擎 (`lyrics/`)**
   - `LyricsRepository.searchAllSourcesParallel`：使用協程平行查詢所有已啟用的本機來源、自訂 JSON 外掛與自架伺服器。
   - **嚴格排序演算法**：依序比較 `(1) 使用者設定之來源優先順序 -> (2) 是否包含同步 LRC 時間軸 -> (3) 與目標曲目時長之差距絕對值`。
   - `GoogleTranslateService`：當外語歌詞無現成翻譯且已開啟「啟用本地 Google 翻譯」時，於 `Dispatchers.IO` 逐行批次翻譯為 `zh-TW` 並完整保留 `[mm:ss.xx]` 時間戳。
   - `LyricsExportFormatter`：支援匯出為「標準 LRC（僅原文）」、「雙語 LRC」與「純文字 TXT（剝除時間戳）」三種格式。

3. **伺服器通訊與硬體加密儲存 (`network/aether/` & `settings/store/`)**
   - `AetherLyricServerStore`：支援「雙引擎模式 (`DUAL_ENGINE`)」與「僅伺服器模式 (`SERVER_ONLY`)」，並透過 Android Keystore (`AES/GCM/NoPadding`) 加密儲存 `APP_ACCESS_TOKEN`、PIN 碼、問答驗證答案與 Proxy 密碼。
   - `AetherLyricApiClient`：支援 HTTP(S) Proxy 代理、多層驗證標頭 (`Authorization`, `X-App-Access-Token`, `X-Auth-Pin`, `X-Auth-Answer`)，以及 RFC 6455 WebSocket (`/ws/tasks/{taskId}`) 即時進度監聽與 HTTP 輪詢自動降級。
