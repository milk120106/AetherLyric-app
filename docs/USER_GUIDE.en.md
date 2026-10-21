# AetherLyric-app User Guide

[繁體中文](USER_GUIDE.md) · [简体中文](USER_GUIDE.zh-CN.md) · [English](USER_GUIDE.en.md)

---

## 1. Media Page & Quick Controls
- **Lyrics Display Format Switcher**: Switch between "Original Only", "Translation Only", and "Bilingual" on the Media Page or Settings Home Page for immediate floating window updates.
- **Manual Multi-Source Lyrics Search**: Queries all enabled providers in parallel and opens a BottomSheet displaying Source Name, Synced LRC badge, song duration, and a 4-line lyrics preview.
- **Media Page AI Lyrics Recognition**: Toggle "Media Page AI Lyrics Recognition" on the Media Page to expand local audio upload, reference lyrics forced alignment, and video URL AI transcription.

## 2. Server & Engine Mode Configuration
- Navigate to `Settings -> Server` to manage multiple self-hosted server profiles.
- Switch between "Dual-Engine Mode (Local + Server)" and "Server-Only Mode (Server-Exclusive Search & Transcription)".
- Enable "Use WebSocket for Progress" and configure multi-layer authentication (`APP_ACCESS_TOKEN`, PIN code, Challenge Answer) and HTTP(S) Proxy settings.

## 3. Lyrics Export & Local Translation
- Enable "Local Google Translate" to automatically translate foreign-language lyrics while preserving `[mm:ss.xx]` timestamps.
- Export single or batch lyrics from the editor or `Settings -> Local Lyrics` in Standard LRC (Original Only), Bilingual LRC, or Plain Text TXT.
