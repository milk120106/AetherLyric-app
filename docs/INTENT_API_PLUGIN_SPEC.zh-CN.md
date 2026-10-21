# 外部 Intent API 与自定义 JSON 插件规范

[繁體中文](INTENT_API_PLUGIN_SPEC.md) · [简体中文](INTENT_API_PLUGIN_SPEC.zh-CN.md) · [English](INTENT_API_PLUGIN_SPEC.en.md)

---

## 1. 外部控制 Broadcast Intent API

### 1.1 必备系统权限
外部应用程序发送广播至 `com.andsi.airlyrics.external.AetherLyricIntentReceiver` 前，必须在其 `AndroidManifest.xml` 声明：

```xml
<uses-permission android:name="com.airlyrics.app.permission.ACCESS_LYRICS" />
```

### 1.2 支持的 Action 列表
- `com.aetherlyric.app.action.SHOW_OVERLAY`：显示悬浮歌词窗口
- `com.aetherlyric.app.action.HIDE_OVERLAY`：隐藏悬浮歌词窗口
- `com.aetherlyric.app.action.TOGGLE_OVERLAY`：切换悬浮歌词窗口显示状态
- `com.aetherlyric.app.action.QUERY_LYRICS`：查询本机缓存歌词（需传入 `title`，选填 `artist`、`duration_ms`）
- `android.intent.action.SEND` (`text/plain`)：导入外部纯文本或 LRC 歌词（上限 50KB）

### 1.3 `PendingIntent` 双向回传 (`reply_pending_intent`)
调用端可在 Intent Extra 附带 `reply_pending_intent` (`PendingIntent`)，AetherLyric 处理完成后将通过该 `PendingIntent` 回传以下字段：
- `status`：`"ok"`、`"not_found"`、`"denied"` 或 `"error"`
- `message`：处理结果说明文字
- `title` / `artist`：曲目标题与歌手
- `lrc_content`：查询命中或成功导入的 LRC 歌词内容

---

## 2. 自定义 JSON 歌词来源插件规范

### 2.1 插件 JSON 结构示例
```json
{
  "name": "示例自定义歌词源",
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
- 仅允许 `http://` 与 `https://` 协议，禁止访问本地敏感地址与文件系统。
- 单次请求连接与读取超时固定为 8 秒，响应内容大小上限为 2MB。
- 支持在“设置 -> 歌词来源管理 -> 自定义 JSON 来源”开启自动更新，并设置每 6 / 12 / 24 / 72 小时检查 `updateUrl`。
