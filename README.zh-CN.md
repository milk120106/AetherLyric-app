<div align="center">

<img src="docs/assets/airlyrics-logo.png" width="120" alt="AetherLyric Logo" />

# AetherLyric-app
**全功能 Android 悬浮同步歌词客户端 · 双引擎与仅服务器 AI 语音转录架构**

[![Platform](https://img.shields.io/badge/Platform-Android_8.0+-brightgreen?style=flat-square)](https://github.com/milk120106/AetherLyric-app)
[![Version](https://img.shields.io/badge/Version-0.1.0-blueviolet?style=flat-square)](https://github.com/milk120106/AetherLyric-app/releases)
[![License](https://img.shields.io/badge/License-MIT-orange?style=flat-square)](LICENSE)

[繁體中文](README.md) · [简体中文](README.zh-CN.md) · [English](README.en.md) · [系统架构与开发说明](docs/ARCHITECTURE.zh-CN.md) · [外部 Intent API 与 JSON 插件规范](docs/INTENT_API_PLUGIN_SPEC.zh-CN.md)

</div>

---

## 项目概述 (Overview)

**AetherLyric-app** 是一款开源、轻量且功能强大的 Android 悬浮同步歌词应用程序（基于 [AirLyrics](https://github.com/AirLyrics/AirLyrics) 进行扩展与重构）。

本项目支持灵活的**服务器协同模式**：
1. **双引擎模式 (Dual-Engine Mode)**：优先通过自建 [AetherLyric-server](https://github.com/milk120106/AetherLyric-server) 进行搜索、Faster-Whisper / Groq / OpenAI 兼容模型 AI 语音转录、逐字对齐 (`POST /api/v1/align`) 与音视频网址解析；当服务器离线或未命中时，自动无缝切换至本地多来源聚合引擎。
2. **仅服务器模式 (Server-Only Mode)**：所有歌词搜索、时间轴对齐与 AI 转录完全交由自建服务器统一处理，不在本机直接请求第三方歌词来源。

---

## 核心功能特色 (Key Features)

### 1. 服务器协同架构、WebSocket 进度推送与多层安全防护
- **多服务器配置管理**：支持保存多组自建服务器配置（自定义名称、服务器 URL、Proxy 与验证凭证），并提供快速菜单即时切换。
- **双引擎与仅服务器模式切换**：可依网络与隐私需求自由切换「双引擎模式（本地 + 服务器协同）」与「仅服务器模式（全交由服务器搜索与识别）」。
- **WebSocket 实时任务进度推送**：开启「使用 WebSocket 传输进度」后，优先通过 `/ws/tasks/{taskId}` 实时接收转录、下载与对齐进度；若网络环境不支持 WebSocket，自动无缝降级为 HTTP 轮询。
- **多层安全验证与 Android Keystore 加密**：支持 `APP_ACCESS_TOKEN`、PIN 码 (`X-Aether-Pin`) 与问答验证答案 (`X-Aether-Challenge-Answer`)，所有敏感凭证均通过 Android Keystore (`AES/GCM/NoPadding`) 加密存储。
- **HTTP(S) Proxy 代理支持**：支持为服务器连接配置独立的 HTTP/HTTPS Proxy 主机、端口与账号密码验证。

### 2. 多源并行搜索、候选结果 BottomSheet 与自定义 JSON 插件
- **多源并行搜索引擎**：并行查询已启用的多家歌词来源（网易云音乐、QQ 音乐、酷狗音乐、LRCLIB、Musixmatch、Genius、AZLyrics、自定义 JSON 插件与自建服务器），并依据「用户设置优先顺序 -> 是否具备 LRC 时间轴 -> 歌曲时长相近度」智能排序。
- **搜索结果候选列表 (BottomSheet)**：手动搜索或开启「自动搜索时弹出结果列表」时，以列表完整呈现来源名称、时间轴标签、歌曲时长、匹配标题与前 4 行歌词预览，点选即可一键应用。
- **自定义 JSON 来源插件系统**：支持通过网址 (URL) 或直接粘贴 JSON 规则导入第三方歌词来源，具备严格沙盒安全限制（仅允许 HTTP/HTTPS、8 秒连接超时、2MB 响应上限，禁止动态脚本执行）以及 6 / 12 / 24 / 72 小时定时自动更新机制。
- **自定义 API 密钥 (BYOK)**：允许填写用户自备的 Genius 与 Musixmatch 密钥，解锁更高查询配额与稳定性。

### 3. 本地 Google 翻译与三种歌词显示/导出格式
- **本地 Google 翻译补齐**：开启「启用本地 Google 翻译」后，当外语歌曲无现成翻译时，自动在后台将每行歌词翻译为中文并完整保留 `[mm:ss.xx]` 时间轴标签。
- **歌词显示格式快速切换**：在设置首页与媒体首页均提供「仅原文」、「仅翻译」、「双语对照」三种显示模式一键切换。
- **三种格式歌词导出**：单曲编辑器与本地歌词管理页面均支持选择导出为：
  1. **标准 LRC**（仅原文，保留 `[mm:ss.xx]` 时间轴）
  2. **双语 LRC**（原文与翻译同行或双时间轴 LRC）
  3. **纯文本 TXT**（自动剥除所有时间戳标签）

### 4. 多歌词版本管理与严格优先级时间轴偏移量
- **多版本管理**：单一歌曲支持保存与切换多种歌词版本（官方原版、AI 识别版、翻译版、手动导入版），悬浮窗即时同步。
- **严格优先级时间轴偏移量**：实现「单曲专属偏移量 > 全局 (App 层级) 偏移量 > 默认 0」，在「设置 -> 系统」内提供全局偏移量快速微调面板 (`±100ms`, `±500ms`, `0`)。

### 5. 权限模式与外部控制 Intent API 安全防护
- **权限模式**：在系统设置页面提供三种媒体检测方式说明与状态查看（基础通知监听、进阶无障碍辅助、Shizuku 模式），并支持 Android 13+ 受限制设置引导解锁。
- **外部控制 Intent API 与自定义权限保护**：
  - 声明并强制验证系统级自定义权限 `com.airlyrics.app.permission.ACCESS_LYRICS`。
  - 支持 `SHOW_OVERLAY`、`HIDE_OVERLAY`、`TOGGLE_OVERLAY`、`QUERY_LYRICS` 与 `ACTION_SEND`（50KB 上限）。
  - 支持 `PendingIntent` (`reply_pending_intent`) 双向安全回传状态与歌词内容，并通过 `creatorPackage` 验证调用端真实包名。
  - 应用程序权限菜单采用后台异步加载与缓存机制，秒开不卡顿。

### 6. 全局运行日志 (AppLogger) 与亮暗色语义化主题
- **全局运行日志**：实时记录权限、网络请求、来源查询与 Intent 调用，支持关键字过滤、复制与导出。
- **亮暗色模式即时生效**：切换浅色或深色主题时即时重建界面与系统状态栏，自定义背景色与标签徽章自动适配亮暗对比度，并在「设置 -> 系统」内提供「简洁模式」开关。

---

## 快速开始 (Quick Start)

### 系统需求
- 最低 Android 8.0 (API Level 26)，建议 Android 11.0 (API Level 30) 以上。

### 安装与设置
1. 前往 [Releases](https://github.com/milk120106/AetherLyric-app/releases) 下载最新 `AetherLyric-v{版本号}-{buildType}.apk`。
2. 安装并授予「通知读取权限」与「悬浮窗权限」。
3. 若需连接自建服务器，前往 **设置 -> 服务器**，输入服务器地址并点选「测试连接」，即可启用双引擎或仅服务器模式。

---

## 构建与开发 (Build)

```bash
# 克隆项目
git clone https://github.com/milk120106/AetherLyric-app.git
cd AetherLyric-app

# 运行单元测试
./gradlew testDebugUnitTest

# 打包 Debug APK
./gradlew assembleDebug
```

产出 APK 文件位置：`app/build/outputs/apk/debug/AetherLyric-v0.1.0-debug.apk`

---

## 许可证 (License)

本项目基于 [AirLyrics](https://github.com/AirLyrics/AirLyrics) (MIT License) 进行 Fork 与二次开发，采用 [MIT 许可证](LICENSE)。
