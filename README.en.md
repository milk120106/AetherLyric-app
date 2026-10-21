<div align="center">

<img src="docs/assets/airlyrics-logo.png" width="120" alt="AetherLyric Logo" />

# AetherLyric-app
**Full-Featured Android Floating Synced Lyrics Client · Dual-Engine & Server-Only AI Transcription Architecture**

[![Platform](https://img.shields.io/badge/Platform-Android_8.0+-brightgreen?style=flat-square)](https://github.com/milk120106/AetherLyric-app)
[![Version](https://img.shields.io/badge/Version-0.1.0-blueviolet?style=flat-square)](https://github.com/milk120106/AetherLyric-app/releases)
[![License](https://img.shields.io/badge/License-MIT-orange?style=flat-square)](LICENSE)

[繁體中文](README.md) · [简体中文](README.zh-CN.md) · [English](README.en.md) · [Architecture & Developer Guide](docs/ARCHITECTURE.en.md) · [External Intent API & JSON Plugin Spec](docs/INTENT_API_PLUGIN_SPEC.en.md)

</div>

---

## Overview

**AetherLyric-app** is an open-source, lightweight, and powerful Android floating synced lyrics client (forked and extended from [AirLyrics](https://github.com/AirLyrics/AirLyrics)).

The project offers flexible **Server Collaboration Modes**:
1. **Dual-Engine Mode**: Prioritizes your self-hosted [AetherLyric-server](https://github.com/milk120106/AetherLyric-server) for lyrics search, Faster-Whisper / Groq / OpenAI compatible AI audio transcription, word/line alignment (`POST /api/v1/align`), and video URL extraction; seamlessly falls back to the local multi-source aggregation engine when offline or upon cache miss.
2. **Server-Only Mode**: Delegates all lyrics searches, timeline alignment, and AI transcription exclusively to your self-hosted server without querying third-party providers directly from the device.

---

## Key Features

### 1. Server Collaboration Modes, WebSocket Progress Streaming & Multi-Layer Security
- **Multi-Server Profile Management**: Save multiple self-hosted server profiles (custom name, URL, Proxy, and authentication credentials) with instant switching.
- **Dual-Engine vs. Server-Only Mode**: Switch between "Dual-Engine Mode (Local + Server)" and "Server-Only Mode (Server-Exclusive Search & Transcription)" at any time.
- **WebSocket Real-Time Task Progress**: When "Use WebSocket for Progress" is enabled, connects to `/ws/tasks/{taskId}` for real-time AI transcription and download progress updates, with automatic fallback to HTTP polling.
- **Multi-Layer Authentication with Android Keystore Encryption**: Supports `APP_ACCESS_TOKEN`, PIN code (`X-Aether-Pin`), and Challenge-Response Answer (`X-Aether-Challenge-Answer`), all encrypted at rest via Android Keystore (`AES/GCM/NoPadding`).
- **HTTP(S) Proxy Support**: Configure dedicated HTTP/HTTPS proxy host, port, and username/password authentication for server connections.

### 2. Parallel Multi-Source Search, Candidate BottomSheet & Custom JSON Plugins
- **Parallel Multi-Source Search Engine**: Concurrently queries enabled sources (NetEase Cloud Music, QQ Music, Kugou, LRCLIB, Musixmatch, Genius, AZLyrics, Custom JSON Plugins, and Self-Hosted Server) and ranks results strictly by: `User Priority Order -> Synced LRC Availability -> Duration Closeness`.
- **Interactive Candidate BottomSheet**: Displays all search candidates with Source Name, Synced LRC badge, song duration, matched title/artist, and a 4-line lyrics preview. Supports manual trigger on the Media Page or automatic popup on search completion.
- **Custom JSON Lyrics Plugin System**: Install third-party JSON lyrics providers via URL or direct JSON paste. Enforces strict sandbox limits (HTTP/HTTPS only, 8s timeout, 2MB max payload, no dynamic script execution) and supports scheduled auto-updates every 6 / 12 / 24 / 72 hours.
- **BYOK (Bring Your Own Key)**: Configure custom Genius and Musixmatch tokens for higher rate limits.

### 3. Local Google Translate & 3-Mode Lyrics Display / Export
- **Local Google Translate Enrichment**: Automatically translates foreign-language lyrics into Traditional Chinese (`zh-TW`) in the background while preserving `[mm:ss.xx]` timestamps when no existing translation is available.
- **Quick Display Format Switcher**: Switch between "Original Only", "Translation Only", and "Bilingual" directly from both the Settings Home Page and the Media Page.
- **3-Format Lyrics Export**: Both the single-song editor and batch export support:
  1. **Standard LRC** (Original text only, preserving `[mm:ss.xx]` timestamps)
  2. **Bilingual LRC** (Original and translation lines with `[mm:ss.xx]` timestamps)
  3. **Plain Text TXT** (Strips all timestamp and metadata tags automatically)

### 4. Multi-Version Lyrics Management & Strict Timeline Offset Hierarchy
- **Multi-Version Management**: Store and switch between multiple versions per song (Official, AI Transcribed, Translated, Manual Import) with instant overlay synchronization.
- **Strict Offset Priority**: Enforces `Song-Specific Offset > Global (App-Level) Offset > Default 0`, with a quick global offset fine-tuning card (`±100ms`, `±500ms`, `0`) under `Settings -> System`.

### 5. Permission Modes & External Intent API Security
- **Permission Modes**: Clear overview of the three detection modes (Standard Notification Listener, Accessibility Service, and Shizuku Mode) under `Settings -> System`, with one-click guidance for Android 13+ Restricted Settings.
- **External Intent API Security & PendingIntent Callback**:
  - Enforces custom Android permission `com.airlyrics.app.permission.ACCESS_LYRICS`.
  - Supports `SHOW_OVERLAY`, `HIDE_OVERLAY`, `TOGGLE_OVERLAY`, `QUERY_LYRICS`, and `ACTION_SEND` (50KB cap).
  - Supports two-way `PendingIntent` (`reply_pending_intent`) callbacks returning `status`, `message`, `title`, `artist`, and `lrc_content`, verifying the caller's identity via `creatorPackage`.
  - Asynchronous `PackageManager` scanning with in-memory caching ensures instant opening of the App Permissions dialog.

### 6. Global Runtime Logger (AppLogger) & Semantic Light/Dark Theme
- **Global Runtime Logger**: Real-time inspection of permissions, network requests, lyrics searches, and Intent calls with semantic INFO / WARN / ERROR badges.
- **Instant Light/Dark Mode Switching**: Toggling light/dark mode immediately rebuilds the UI and system bars with semantic contrast adaptation, plus a "Compact Mode" toggle under `Settings -> System`.

---

## Quick Start

### System Requirements
- Minimum Android 8.0 (API Level 26); Android 11.0 (API Level 30) or above recommended.

### Installation & Setup
1. Download the latest `AetherLyric-v{version}-{buildType}.apk` from [Releases](https://github.com/milk120106/AetherLyric-app/releases).
2. Install the APK and grant "Notification Access" and "Display Over Other Apps".
3. To connect to a self-hosted server, navigate to **Settings -> Server**, enter your server URL, and tap "Test Connection".

---

## Build & Development

```bash
# Clone repository
git clone https://github.com/milk120106/AetherLyric-app.git
cd AetherLyric-app

# Run unit tests
./gradlew testDebugUnitTest

# Assemble Debug APK
./gradlew assembleDebug
```

Output APK location: `app/build/outputs/apk/debug/AetherLyric-v0.1.0-debug.apk`

---

## License

Forked and extended from [AirLyrics](https://github.com/AirLyrics/AirLyrics) (MIT License). Licensed under the [MIT License](LICENSE).
