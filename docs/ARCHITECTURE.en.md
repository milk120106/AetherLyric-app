# AetherLyric-app Architecture & Developer Guide

[繁體中文](ARCHITECTURE.md) · [简体中文](ARCHITECTURE.zh-CN.md) · [English](ARCHITECTURE.en.md)

---

## 1. Core Architecture Overview

AetherLyric-app uses a modular layered architecture combining local multi-source lyrics retrieval with self-hosted AI audio transcription:

1. **UI & Theme System (`ui/`)**
   - Supports instant Light/Dark mode rebuilding (`MainActivityUiHost.toggleThemeMode()` triggering `applyPaletteChange`) and validates custom background luminance against the active theme mode.
   - **Settings Home Page (`SettingsHomePage`)**: Centralizes search strategy, auto-popup candidate list toggle, local Google Translate toggle, and quick Lyrics Display Format switching (Original Only / Translation Only / Bilingual).
   - **System Settings Page (`SystemSettingsPage`)**: Houses "Permission Modes" (Standard Notification Listener, Accessibility Service, Status Bar / Shizuku), "Compact Mode", and "Global Timeline Offset".
   - **Media Page (`MediaPage`)**: Integrates the quick Lyrics Display Format switcher, manual multi-source candidate search trigger, and the collapsible "Media Page AI Lyrics Recognition" toggle and card.

2. **Parallel Multi-Source Search & Translation Engine (`lyrics/`)**
   - `LyricsRepository.searchAllSourcesParallel`: Concurrently queries all enabled local providers, custom JSON plugins, and the self-hosted server via Kotlin coroutines.
   - **Strict Ranking Algorithm**: Ranks candidates by `(1) User-configured source priority -> (2) Synced LRC timestamp availability -> (3) Closeness to target track duration`.
   - `GoogleTranslateService`: Translates foreign lyric lines into Traditional Chinese (`zh-TW`) on `Dispatchers.IO` while preserving `[mm:ss.xx]` timestamps when no translation exists.
   - `LyricsExportFormatter`: Supports exporting single or batch lyrics in Standard LRC (Original Only), Bilingual LRC, or Plain Text TXT (timestamps stripped).

3. **Server Communication & Hardware-Backed Encryption (`network/aether/` & `settings/store/`)**
   - `AetherLyricServerStore`: Supports `DUAL_ENGINE` and `SERVER_ONLY` modes and encrypts `APP_ACCESS_TOKEN`, PIN code, Challenge Answer, and Proxy passwords using Android Keystore (`AES/GCM/NoPadding`).
   - `AetherLyricApiClient`: Supports HTTP(S) Proxy routing, multi-layer authentication headers (`Authorization`, `X-App-Access-Token`, `X-Auth-Pin`, `X-Auth-Answer`), and RFC 6455 WebSocket (`/ws/tasks/{taskId}`) progress streaming with automatic fallback to HTTP polling.
