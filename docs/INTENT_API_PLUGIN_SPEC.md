# 外部 Intent API 與自訂 JSON 外掛規範

[繁體中文](INTENT_API_PLUGIN_SPEC.md) · [简体中文](INTENT_API_PLUGIN_SPEC.zh-CN.md) · [English](INTENT_API_PLUGIN_SPEC.en.md)

---

## 1. 外部控制 Broadcast Intent API

### 1.1 必備系統權限
外部應用程式發送廣播至 `com.andsi.airlyrics.external.AetherLyricIntentReceiver` 前，必須於其 `AndroidManifest.xml` 宣告：

```xml
<uses-permission android:name="com.airlyrics.app.permission.ACCESS_LYRICS" />
```

### 1.2 支援之 Action 清單
- `com.aetherlyric.app.action.SHOW_OVERLAY`：顯示懸浮歌詞視窗
- `com.aetherlyric.app.action.HIDE_OVERLAY`：隱藏懸浮歌詞視窗
- `com.aetherlyric.app.action.TOGGLE_OVERLAY`：切換懸浮歌詞視窗顯示狀態
- `com.aetherlyric.app.action.QUERY_LYRICS`：查詢本機快取歌詞（需傳入 `title`，選填 `artist`、`duration_ms`）
- `android.intent.action.SEND` (`text/plain`)：匯入外部純文字或 LRC 歌詞（上限 50KB）

### 1.3 `PendingIntent` 雙向回傳 (`reply_pending_intent`)
呼叫端可於 Intent Extra 附帶 `reply_pending_intent` (`PendingIntent`)，AetherLyric 處理完成後將透過該 `PendingIntent` 回傳以下欄位：
- `status`：`"ok"`、`"not_found"`、`"denied"` 或 `"error"`
- `message`：處理結果說明文字
- `title` / `artist`：曲目標題與演出者
- `lrc_content`：查詢命中或成功匯入之 LRC 歌詞內容

---

## 2. 自訂 JSON 歌詞來源外掛規範

### 2.1 外掛 JSON 結構範例
```json
{
  "name": "範例自訂歌詞源",
  "version": "1.0.0",
  "author": "AetherLyric Community",
  "updateUrl": "https://example.com/plugins/sample-lyrics.json",
  "searchUrl": "https://api.example.com/lyrics?title={title}&artist={artist}&duration={duration}",
  "method": "GET",
  "headers": {
    "Accept": "application/json"
  },
  "lyricsPath": "data.lrc",
  "translationPath": "data.tlyric",
  "titlePath": "data.title",
  "artistPath": "data.artist"
}
```

### 2.2 沙盒安全限制
- 僅允許 `http://` 與 `https://` 協定，禁止存取本地敏感位址與檔案系統。
- 單次請求連線與讀取逾時固定為 8 秒，回應內容大小上限為 2MB。
- 支援於「設定 -> 歌詞來源管理 -> 自訂 JSON 來源」開啟自動更新，並設定每 6 / 12 / 24 / 72 小時檢查 `updateUrl`。
