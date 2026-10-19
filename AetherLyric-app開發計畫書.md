# AetherLyric-app 開發計畫書 (基於 AirLyrics)

## 一、 專案定位與核心價值

**AetherLyric-app** 是 AetherLyric 音樂歌詞解決方案的 Android 客戶端。本專案以成熟開源歌詞專案 **AirLyrics** 為基底進行深度重構與擴充，核心目標是為行動裝置使用者提供流暢、美觀且具備「AI 歌詞補全與時間軸自動校正」能力的全新聽歌體驗。

### 核心痛點解決
* **版本差異導致時間軸錯位**：MV 版、Live 版、翻唱版等與串流平台錄音室版本不同步。
* **冷門歌曲歌詞缺失**：新歌、獨立音樂、YouTube / Bilibili 影音無現成動態歌詞。
* **繁簡與多語言需求**：簡體中文歌詞自動轉為台灣正體（`s2tw`），外語歌曲可即時生成雙語對照。

---

## 二、 基礎架構與重構規劃

### 2.1 基礎修改
1. **獨立識別碼與專案命名**：
   * Application ID: `com.aetherlyric.app`
   * App 名稱: **AetherLyric**
2. **清理多餘第三方歌詞源**：
   * 移除 AirLyrics 中維護困難、失效或不穩定的老舊爬蟲模組。
   * 將歌詞來源統一收斂為：
     * **AetherLyric-server (首要核心)**：提供快查代理、AI 轉錄、錨點校正與翻譯。
     * **本地快取 (Room 資料庫)**：離線優先顯示。
3. **伺服器連線設定頁面**：
   * 伺服器端點設定：允許使用者填寫自架伺服器位址（例如 `http://192.168.1.100:8000` 或外部網址）。
   * 內建「連線測試」按鈕：主動呼叫 `GET /api/v1/health` 驗證伺服器在線狀態與延遲。
   * API 金鑰 / Token 設定（供 Phase 2 驗證機制使用）。

---

## 三、 與 AetherLyric-server 通訊架構

客戶端採用 **Retrofit + OkHttp** 實作網路通訊層，支援與伺服器標準 API 互動：

```text
[Android Notification / 媒體監聽]
               │
               ▼
   [獲取當前曲名與歌手資訊]
               │
      (Step 1: 快查路徑)
               ▼
[GET /api/v1/lyrics/search] ───────────────┐
               │                           │
          (是否命中?)                       │
         /          \                      │
       (是)         (否)                   │
        │            │                     │
        ▼            ▼                     │
    [顯示歌詞]   [提示: 點擊 AI 生成/校正]   │
                     │                     │
            (Step 2: AI 校正路徑)           │
                     ▼                     │
          [SAF 選取音檔 / YTDLnis 傳送]    │
                     │                     │
                     ▼                     │
         [POST /api/v1/tasks] (上傳音檔)    │
                     │                     │
                     ▼                     │
          [輪詢 GET /tasks/{id}] (每 3 秒)  │
                     │                     │
                     ▼                     │
          [接收校準後的標準 LRC 歌詞]        │
                     │                     │
                     ▼                     │
              [寫入本地 Room 快取] ◄────────┘
                     │
                     ▼
             [即時載入並滾動顯示]
```

---

## 四、 核心功能與工作流程

### 4.1 雙路徑歌詞檢索機制
1. **快查路徑 (Fast Path - 毫秒級響應)**：
   * 當偵測到系統正在播放音樂時，優先呼叫 `GET /api/v1/lyrics/search?title=...&artist=...`。
   * 若命中外部資料庫（LRCLIB），立即呈現在播放介面或懸浮視窗，無需等待音訊處理。
2. **AI 生成與校正路徑 (AI Path - 深度處理)**：
   * 當快查無結果，或使用者發現當前歌曲時間軸嚴重不同步時，介面顯示「**AI 生成/校正時間軸**」按鈕。
   * 音訊來源取得方式：
     * **本機檔案選取**：透過 Android SAF (Storage Access Framework) 選擇本地音檔。
     * **外部下載器 Intent 串接**：支援由 **YTDLnis** 或其他下載工具一鍵「分享至 AetherLyric」發起處理任務。
   * 任務進度回饋：
     * 客戶端將音檔送至 `POST /api/v1/tasks`。
     * 每 3 秒輪詢 `GET /api/v1/tasks/{task_id}`，介面顯示動態進度條與階段狀態（如「音訊上傳中...」、「AI 語音辨識中...」、「時間軸錨點對齊中...」）。
     * 任務完成後接收 `result` (LRC 歌詞)，自動存入本地資料庫並立即切換顯示。

### 4.2 歌詞顯示與微調介面 (UI / UX)
1. **沉浸式滾動歌詞與桌面懸浮視窗**：
   * 保留 AirLyrics 優異的桌面懸浮窗（Floating Lyrics）與狀態列歌詞功能。
   * 支援手勢調整字型大小、顏色、透明度、背景陰影與鎖定位置。
2. **置信度標記 (Confidence Indicator)**：
   * 若伺服器回傳的行置信度評分低於 0.6，該行歌詞在 UI 上以警示色彩（如淡黃色）微幅醒目標註，提醒使用者該處可能存在發音模糊或微小偏差。
3. **時間軸微調控制項**：
   * **全域偏移調整**：提供即時的滑桿或「+0.5s / -0.5s」快捷按鈕，微調整首歌的時間軸。
   * **單行歌詞時間戳校準**：長按單行歌詞可手動標記當前播放進度為該行的精確起始點。
4. **多語言與繁簡體切換**：
   * 歌詞長按選單支援「繁簡轉換（台灣正體）」與「生成雙語對照」。

### 4.3 使用者偏好設定頁面
* **轉錄引擎設定**：
  * 自動 (Auto)
  * 本地 CPU (適合內網或純自架伺服器)
  * 雲端 Groq / Gemini (速度最快、品質最高)
* **AI 校正開關**：允許使用者切換是否在轉錄後執行 LLM 錯字校準。
* **繁簡轉換開關**：預設維持原歌詞編碼，可開啟自動轉為台灣正體（`s2tw`）。
* **歌詞翻譯開關**：預設不開啟，可勾選開啟 Google 雙語對照模式。

---

## 五、 本地資料庫與快取策略 (Room DB)

為了節省伺服器流量與重複算力消耗，客戶端建置本地 Room 資料庫進行持久化保存：

```sql
-- 歌詞快取資料表設計
CREATE TABLE lyrics_cache (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    track_title TEXT NOT NULL,
    artist_name TEXT,
    duration INTEGER,
    lrc_content TEXT NOT NULL,
    is_ai_aligned INTEGER DEFAULT 0,  -- 0: 外部直接抓取, 1: 經過 AI 錨點對齊
    language TEXT,                    -- zh-TW, en, ja 等
    created_at INTEGER NOT NULL
);

CREATE INDEX idx_track_artist ON lyrics_cache(track_title, artist_name);
```

---

## 六、 開發里程碑與排期規劃

### Phase 3：Android 客戶端重構與伺服器對接
1. Fork AirLyrics，調整專案包名為 `com.aetherlyric.app`，清理無效的第三方爬蟲代碼。
2. 實作 Retrofit 客戶端介面，建立針對 AetherLyric-server 的 API 封裝類。
3. 建立「伺服器連線設定」介面，實作 `GET /api/v1/health` 測試功能。
4. 實作 Fast Path：即時監聽媒體並呼叫 `GET /api/v1/lyrics/search` 顯示歌詞。

### Phase 4：AI 轉錄 UI 與進階使用者體驗
1. 實作音訊檔案選取 (SAF) 與任務上傳 UI，建置輪詢進度條元件。
2. 串接 **YTDLnis** 外部下載器 Intent，實現「下載完成直接轉錄對齊」。
3. 實作歌詞置信度（Confidence）警示標記與全域/單行時間軸微調 UI。
4. 實作使用者偏好設定頁面（轉錄引擎、繁簡轉換、雙語翻譯）。
5. 整合 Room 本地快取資料庫，完成離線歌詞持久化。
