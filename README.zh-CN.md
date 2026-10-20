<div align="center">

<img src="docs/assets/airlyrics-logo.png" width="120" alt="AetherLyric Logo" />

# AetherLyric-app
**全功能 Android 悬浮同步歌词客户端 · 双引擎 AI 语音转录架构**

[![Platform](https://img.shields.io/badge/Platform-Android_8.0+-brightgreen?style=flat-square)](https://github.com/milk120106/AetherLyric-app)
[![Version](https://img.shields.io/badge/Version-0.1.0-blueviolet?style=flat-square)](https://github.com/milk120106/AetherLyric-app/releases)
[![License](https://img.shields.io/badge/License-MIT-orange?style=flat-square)](LICENSE)

[繁體中文](README.md) · [简体中文](README.zh-CN.md) · [English](README.en.md)

</div>

---

## 项目概述 (Overview)

**AetherLyric-app** 是一款开源、轻量且功能强大的 Android 悬浮同步歌词应用程序（基于 [AirLyrics](https://github.com/AirLyrics/AirLyrics) 进行扩展与重构）。

本项目最大特色在于**双引擎模式 (Dual-Engine Mode)**：
1. **自建 AI 服务器引擎 ([AetherLyric-server](https://github.com/milk120106/AetherLyric-server))**：可连接自建服务器，使用 Faster-Whisper / Groq / OpenAI 兼容模型进行 AI 语音转录、逐字对齐 (POST `/api/v1/align`)、音视频网址解析与智能歌词格式校正。
2. **本地多源聚合引擎**：未连接服务器或自建服务器离线时，自动无缝降级使用本地多来源聚合搜索（网易云、QQ 音乐、酷狗音乐、LRCLIB、Musixmatch、Genius、AZLyrics 等）。

---

## 核心功能特色 (Key Features)

### 1. 双引擎架构与多服务器配置 (Dual-Engine Architecture)
- **多服务器配置管理**：支持保存多组自建服务器配置（自定义名称、服务器 URL），并通过下拉菜单即时切换。
- **公开状态与健康检测**：切换时自动调用 `GET /api/v1/health` 测试连接，即时呈现服务器在线状态、AI 模型名称、处理队列长度及 yt-dlp 模块可用性。App 端完全公开透明，无需存储或绑定管理员私钥。
- **无缝自动降级**：优先调用自建服务器 API；连接超时或缓存未命中时，自动平滑切换为本地多源查询。
- **云端锚点对齐**：纯文本来源（如 Genius、AZLyrics）可自动请求服务器 `/api/v1/align` 进行高精度逐行时间轴对齐；服务器离线时自动降级本地简易时间轴。

### 2. 本地歌词来源扩展与 BYOK (自带密钥)
- 支持自由拖曳排序、勾选与启用多家歌词来源：
  - **网易云音乐 (NetEase Cloud Music)**
  - **QQ 音乐 (QQ Music)**
  - **酷狗音乐 (Kugou)**
  - **LRCLIB**
  - **Musixmatch** (支持自定义 User Token)
  - **Genius** (支持自定义 Client Access Token)
  - **AZLyrics**
  - **Spicy Lyrics (逐字动态歌词)**
- **常用来源卡片与弹窗管理**：卡片上紧凑呈现前 4 大常用来源，点击「更多来源…」弹出完整弹窗轻松调整排序与启闭状态。
- **自定义 API 密钥 (BYOK)**：允许填写用户自备密钥，解锁更高查询配额与请求稳定性。

### 3. 多歌词版本管理与媒体首页切换
- 单一歌曲支持保存与管理多种歌词版本（如「官方原版」、「AI 识别版」、「日文翻译版」、「用户提供对齐版」、「视频转录版」）。
- 提供「重命名」与「删除」特定版本功能，悬浮窗口即时同步切换。
- **媒体首页歌词切换卡片**：播放音乐时直接在媒体首页呈现当前歌曲的所有可用版本，点击即可切换生效。

### 4. 严格优先级时间轴偏移量系统
- 实现三层优先级：**单曲专属偏移量 > 全局 (App 层级) 偏移量 > 默认 0**。
- 提供「保存为单曲偏移量」与「保存为全局偏移量」独立操作，切换不同版本时自动加载专属偏移量。
- 主设置页提供全局偏移量快速微调面板 (`±100ms`, `±500ms`, `0`)。

### 5. 三层权限架构与系统状态查看
- 集中于系统设置页面，客观展示三层权限运作状态：
  - **1. 基础模式**：使用标准媒体通知监听与悬浮窗权限。
  - **2. 无障碍模式**：通用稳定的前台 App 与媒体信息监听。
  - **3. Shizuku 模式**：高权限免无障碍服务，提供极致流畅与稳定体验。
- **Android 13+ 受限制设置解锁**：精准检测系统 restricted settings 状态，一键直达对应应用程序详细信息页面引导解除限制。

### 6. 扩展与外部控制 (Intent API & 安全管控)
- 支持标准 Android 广播 Intent API 控制：
  - `SHOW_OVERLAY`：显示悬浮歌词窗。
  - `HIDE_OVERLAY`：隐藏悬浮歌词窗。
  - `TOGGLE_OVERLAY`：切换悬浮歌词窗显示/隐藏。
  - `ACTION_SEND`：直接接收并加载外部传入之 LRC 歌词（具备 50KB 安全大小上限）。
- **安全防护机制**：
  - 开启 Intent API 时强制显示二次确认免责声明。
  - 未知 App 调用处置策略：拒绝 (默认) / 询问 / 允许。
  - 独立 Dialog 应用程序权限管理弹窗，支持即时关键字搜索与「仅显示音乐 App」筛选。

### 7. AI 音频识别、参考歌词对齐与视频网址生成
- **本地音频识别**：选取本机音频文件一键发送至服务器完成 AI 识别并生成歌词。
- **手动输入参考歌词进行对齐**：支持粘贴无时间戳的纯文本歌词，结合本机音频发送至服务器 `/api/v1/align` 进行精准对齐；若服务器未连接，则自动降级为本地简易平均时间戳对齐。
- **视频网址解析**：支持输入 YouTube、Bilibili 等音视频网址，即时检测服务器 yt-dlp 支持状态，通过后端自动下载音频并识别生成同步 LRC 歌词。
- **整合 YTDLnis 本地下載**：若自建服务器未启用 yt-dlp 或面临音视频平台登录限制，可一键调用 YTDLnis 本地下载音频后进行识别与对齐。
- **媒体页快捷入口**：支持在设置中自定义是否在媒体首页显示 AI 识别与视频解析区块。

### 8. 全局运行日志系统 (AppLogger)
- 独立子页面即时呈现 App 启动期间所有关键事件（权限、网络请求、来源查询、Intent 调用）。
- 支持 INFO / WARN / ERROR 级别原创色彩标签、关键字搜索过滤。
- 底部功能键支持：自动滚动开关、一键复制全部至剪贴板、导出为 TXT 文件与清空日志。

### 9. 自定义色彩主题、简洁模式与原厂设计适配
- 严格维持无 Emoji、标准中文与原创 5 种主题强调色。
- **简洁模式 (Compact Mode)**：开启后自动隐藏所有辅助说明与描述文字，呈现最清爽纯粹的操作界面。
- **自定义 HEX 颜色**：支持自由输入 Primary 与 Background HEX 码，提供即时预览色块、一键应用与重置回默认主题。
- 底部导航栏固定置底，防止旋转横屏时切换为侧边栏。

---

## 快速开始 (Quick Start)

### 系统需求
- 最低 Android 8.0 (API Level 26)，建议 Android 11.0 (API Level 30) 以上。
- 本 App 通过三层权限架构检测媒体状态：
  > 标准音乐播放器（如 Spotify）：支持 MediaSession，体验最佳。  
  > 视频平台（如 YouTube, Bilibili）：因其未完整实现标准协议，建议开启「无障碍模式」或「Shizuku 模式」以获得精准进度与同步。

### 安装步骤
1. 前往 [Releases](https://github.com/milk120106/AetherLyric-app/releases) 下载最新 `AetherLyric-v{版本号}-{buildType}.apk`。
2. 在手机上安装并授予「通知读取权限」与「悬浮窗权限」。
3. 播放任意音乐，开启 AetherLyric 悬浮窗即可开始享受同步歌词。

### 连接自建服务器
1. 前往 **设置 -> AetherLyric 服务器**。
2. 输入自建服务器名称与地址（例如 `http://192.168.1.100:8000`）。
3. 点击「保存设置」，系统会自动进行连接与能力检测。
4. 开启「启用 AetherLyric 服务器」开关即可享受双引擎模式。

---

## 构建与开发 (Build)

```bash
# 克隆项目
git clone https://github.com/milk120106/AetherLyric-app.git
cd AetherLyric-app

# 确保 local.properties 已配置 Android SDK 路径
echo "sdk.dir=/path/to/your/android/sdk" > local.properties

# 运行单元测试
./gradlew testDebugUnitTest

# 打包 Debug APK
./gradlew assembleDebug
```

产出 APK 文件名格式为：`AetherLyric-v{版本号}-{buildType}.apk`  
文件位置：`app/build/outputs/apk/debug/AetherLyric-v0.1.0-debug.apk`

---

## 许可证 (License)

本项目基于 [AirLyrics](https://github.com/AirLyrics/AirLyrics) (MIT License) 进行 Fork 与二次开发。  
本项目同样采用 [MIT 许可证](LICENSE)。
