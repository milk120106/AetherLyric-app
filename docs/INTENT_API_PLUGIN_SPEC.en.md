# External Intent API & Custom JSON Plugin Specification

[繁體中文](INTENT_API_PLUGIN_SPEC.md) · [简体中文](INTENT_API_PLUGIN_SPEC.zh-CN.md) · [English](INTENT_API_PLUGIN_SPEC.en.md)

---

## 1. External Control Broadcast Intent API

### 1.1 Required Android Permission
Third-party applications sending broadcasts to `com.andsi.airlyrics.external.AetherLyricIntentReceiver` must declare the following permission in their `AndroidManifest.xml`:

```xml
<uses-permission android:name="com.airlyrics.app.permission.ACCESS_LYRICS" />
```

### 1.2 Supported Actions
- `com.aetherlyric.app.action.SHOW_OVERLAY`: Show floating lyrics overlay
- `com.aetherlyric.app.action.HIDE_OVERLAY`: Hide floating lyrics overlay
- `com.aetherlyric.app.action.TOGGLE_OVERLAY`: Toggle floating lyrics overlay visibility
- `com.aetherlyric.app.action.QUERY_LYRICS`: Query locally cached lyrics (requires `title` extra; optional `artist`, `duration_ms`)
- `android.intent.action.SEND` (`text/plain`): Import plain text or LRC lyrics (capped at 50KB)

### 1.3 Two-Way `PendingIntent` Callback (`reply_pending_intent`)
Callers may attach a `PendingIntent` under the extra key `reply_pending_intent`. Upon completion, AetherLyric sends a result Intent containing:
- `status`: `"ok"`, `"not_found"`, `"denied"`, or `"error"`
- `message`: Human-readable status description
- `title` / `artist`: Track title and artist
- `lrc_content`: Retrieved or imported LRC lyrics text

---

## 2. Custom JSON Lyrics Plugin Specification

### 2.1 Sample Plugin JSON Schema
```json
{
  "name": "Sample Custom Lyrics Provider",
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

### 2.2 Sandbox Security Limits
- Only `http://` and `https://` schemes are allowed; local filesystem and sensitive loopback addresses are blocked.
- Strict 8-second connection/read timeout and 2MB maximum response payload size.
- Supports scheduled auto-updates every 6 / 12 / 24 / 72 hours via `updateUrl` under `Settings -> Lyrics Sources -> Custom JSON Sources`.
