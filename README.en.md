<div align="center">

<img src="docs/assets/airlyrics-logo.png" width="120" alt="AetherLyric Logo" />

# AetherLyric-app
**Full-Featured Android Floating Synced Lyrics Client · Dual-Engine AI Transcription Architecture**

[![Platform](https://img.shields.io/badge/Platform-Android_8.0+-brightgreen?style=flat-square)](https://github.com/milk120106/AetherLyric-app)
[![Version](https://img.shields.io/badge/Version-0.1.0-blueviolet?style=flat-square)](https://github.com/milk120106/AetherLyric-app/releases)
[![License](https://img.shields.io/badge/License-MIT-orange?style=flat-square)](LICENSE)

[繁體中文](README.md) · [简体中文](README.zh-CN.md) · [English](README.en.md)

</div>

---

## Overview

**AetherLyric-app** is an open-source, lightweight, and powerful Android floating synced lyrics client (forked and extended from [AirLyrics](https://github.com/AirLyrics/AirLyrics)).

The core highlight of this project is its **Dual-Engine Mode**:
1. **Self-Hosted AI Server Engine ([AetherLyric-server](https://github.com/milk120106/AetherLyric-server))**: Connect to your self-hosted server running Faster-Whisper / Groq / OpenAI compatible models for AI audio transcription, word/line level alignment (POST `/api/v1/align`), video URL parsing, and smart lyrics formatting.
2. **Local Multi-Source Aggregation Engine**: When the server is not configured or offline, automatically and seamlessly falls back to querying multiple local online sources (NetEase Cloud Music, QQ Music, Kugou, LRCLIB, Musixmatch, Genius, AZLyrics, etc.).

---

## Key Features

### 1. Dual-Engine Architecture & Multi-Server Profiles
- **Multi-Server Profile Management**: Store multiple self-hosted server profiles (custom names and URLs) with instant switching via a convenient dropdown menu.
- **Public Health & Capabilities Inspection**: Automatically calls `GET /api/v1/health` on profile switch to display online status, AI transcription model name, task queue size, and yt-dlp module availability. Completely transparent client-side design with no admin secret keys needed.
- **Seamless Auto-Fallback**: Prioritizes self-hosted server APIs; automatically falls back to local multi-source lookup if the connection times out or fails.
- **Cloud Anchor Alignment**: Plain text sources (such as Genius and AZLyrics) can automatically invoke the server's `/api/v1/align` endpoint for high-precision timeline alignment; falls back to local proportional timestamps when offline.

### 2. Expanded Lyrics Sources & BYOK (Bring Your Own Key)
- Reorder, enable, or disable sources via drag-and-drop:
  - **NetEase Cloud Music**
  - **QQ Music**
  - **Kugou Music**
  - **LRCLIB**
  - **Musixmatch** (supports custom User Token)
  - **Genius** (supports custom Client Access Token)
  - **AZLyrics**
  - **Spicy Lyrics (Word-by-word synced lyrics)**
- **Top 4 Quick View & Full Management Dialog**: Displays top 4 active sources on the main card with a "More Sources…" dialog for complete reordering and toggling.
- **BYOK Support**: Input your own API tokens to unlock higher search quotas and stability.

### 3. Multi-Version Lyrics Management & Media Page Switcher
- Store and manage multiple lyrics versions per song (e.g., "Official", "AI Transcribed", "Japanese Translated", "User Aligned", "Video Extracted").
- Rename or delete specific versions with instant floating window synchronization.
- **Media Page Lyrics Version Selector**: Directly switch between saved versions on the media page with one click.

### 4. Strict Timeline Offset Priority Hierarchy
- Three-level priority: **Song-Specific Offset > Global (App-Level) Offset > Default 0**.
- Dedicated "Save as Song Offset" and "Save as Global Offset" buttons; automatically restores each version's custom offset upon switching.
- Quick fine-tuning adjustments on the main settings page (`±100ms`, `±500ms`, `0`).

### 5. Three-Tier Permission Architecture & System Settings
- Centrally managed on the System Settings page:
  - **1. Basic Mode**: Standard media notification listener and overlay window permissions.
  - **2. Accessibility Mode**: Reliable foreground app and media playback detection.
  - **3. Shizuku Mode**: Elevated permissions without accessibility service for ultra-smooth performance.
- **Android 13+ Restricted Settings Unlock**: Detects system restricted settings state and provides one-click navigation to the system App Info page to guide removal of restrictions.

### 6. Extensions & External Control (Intent API & Security)
- Standard Android broadcast Intent APIs:
  - `SHOW_OVERLAY`: Show floating lyrics.
  - `HIDE_OVERLAY`: Hide floating lyrics.
  - `TOGGLE_OVERLAY`: Toggle floating lyrics visibility.
  - `ACTION_SEND`: Directly receive and load incoming LRC text (with a 50KB safety limit).
- **Security & Authorization**:
  - Requires explicit confirmation before enabling Intent APIs.
  - Unknown app call policies: Reject (default) / Ask / Allow.
  - Dedicated App Permissions dialog with keyword search and "Music Apps Only" filter.

### 7. AI Audio Transcription, Reference Lyrics Alignment & Video URLs
- **Local Audio Recognition**: Select local audio files to send to the server for AI transcription and synced LRC generation.
- **Manual Reference Lyrics Alignment**: Paste un-timestamped lyrics text alongside an audio file to request precision alignment via `/api/v1/align`; falls back to local proportional timing if disconnected.
- **Video URL Parsing**: Input YouTube or Bilibili video URLs for backend audio extraction and lyrics generation.
- **YTDLnis Local Integration**: If the self-hosted server does not have yt-dlp enabled or faces platform login restrictions, launch YTDLnis locally via Intent to download audio for transcription.
- **Media Page Toggle**: Option to toggle visibility of the AI transcription card on the media page.

### 8. Global Runtime Log System (AppLogger)
- Real-time logging of permissions, network requests, lyrics lookups, and Intent calls.
- INFO / WARN / ERROR original color-coded badges and keyword filtering.
- One-click copy, TXT export, auto-scroll toggle, and log clearing.

### 9. Custom Color Themes, Compact Mode & Native Design
- Completely free of emojis, standard localization, and 5 native accent colors.
- **Compact Mode**: Automatically hides all description and subtitle text across all settings pages for a minimal interface.
- **Custom HEX Colors**: Customize Primary and Background colors with live previews, apply with one click, or reset to defaults.
- Fixed bottom navigation bar that prevents unwanted switching to a sidebar upon landscape rotation.

---

## Quick Start

### System Requirements
- Minimum Android 8.0 (API Level 26); Android 11.0 (API Level 30) or above recommended.
- Media playback detection:
  > Standard music players (e.g. Spotify): Support MediaSession with best performance.  
  > Video apps (e.g. YouTube, Bilibili): Due to non-standard implementations, Accessibility Mode or Shizuku Mode is recommended for accurate synchronization.

### Installation
1. Download the latest `AetherLyric-v{version}-{buildType}.apk` from [Releases](https://github.com/milk120106/AetherLyric-app/releases).
2. Install the APK and grant "Notification Access" and "Display Over Other Apps" permissions.
3. Play music and open the AetherLyric floating window to enjoy synced lyrics.

### Connecting to Self-Hosted Server
1. Go to **Settings -> AetherLyric Server**.
2. Add a server profile with name and address (e.g., `http://192.168.1.100:8000`).
3. Save the profile; connection status and capabilities will be tested automatically.
4. Enable "Enable AetherLyric Server" to activate Dual-Engine mode.

---

## Build & Development

```bash
# Clone repository
git clone https://github.com/milk120106/AetherLyric-app.git
cd AetherLyric-app

# Configure Android SDK path in local.properties
echo "sdk.dir=/path/to/your/android/sdk" > local.properties

# Run unit tests
./gradlew testDebugUnitTest

# Assemble Debug APK
./gradlew assembleDebug
```

Output APK naming format: `AetherLyric-v{version}-{buildType}.apk`  
Output location: `app/build/outputs/apk/debug/AetherLyric-v0.1.0-debug.apk`

---

## License

This project is forked and developed from [AirLyrics](https://github.com/AirLyrics/AirLyrics) (MIT License).  
This project is licensed under the [MIT License](LICENSE).
