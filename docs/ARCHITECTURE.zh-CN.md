# AetherLyric-app 系统架构与开发说明

[繁體中文](ARCHITECTURE.md) · [简体中文](ARCHITECTURE.zh-CN.md) · [English](ARCHITECTURE.en.md)

---

## 1. 核心架构概览

AetherLyric-app 采用模块化分层设计，结合本地多源歌词检索与自建服务器 AI 语音转录能力：

1. **界面与主题系统 (`ui/`)**
   - 支持亮色与暗色模式即时重建（`MainActivityUiHost.toggleThemeMode()` 联动 `applyPaletteChange`），并自动验证自定义背景色与主题亮度兼容性。
   - **设置首页 (`SettingsHomePage`)**：集中管理搜索策略、自动弹出候选列表开关、本地 Google 翻译开关与歌词显示格式（仅原文 / 仅翻译 / 双语对照）快速切换。
   - **系统设置页 (`SystemSettingsPage`)**：集中管理“权限模式”（基础通知监听、进阶无障碍辅助、状态栏撷取 / Shizuku）、“简洁模式”与“全局时间轴偏移量”。
   - **媒体首页 (`MediaPage`)**：整合“歌词显示格式”快速切换、“手动搜索歌词（多源候选列表）”入口，以及“媒体页 AI 歌词识别”折叠开关与功能卡。

2. **多源并行搜索与翻译引擎 (`lyrics/`)**
   - `LyricsRepository.searchAllSourcesParallel`：使用协程并行查询所有已启用的本地来源、自定义 JSON 插件与自建服务器。
   - **严格排序算法**：依次比较 `(1) 用户设置的来源优先顺序 -> (2) 是否包含同步 LRC 时间轴 -> (3) 与目标曲目时长的差距绝对值`。
   - `GoogleTranslateService`：当外语歌词无现成翻译且已开启“启用本地 Google 翻译”时，在 `Dispatchers.IO` 逐行批量翻译为中文并完整保留 `[mm:ss.xx]` 时间戳。
   - `LyricsExportFormatter`：支持导出为“标准 LRC（仅原文）”、“双语 LRC”与“纯文本 TXT（剥除时间戳）”三种格式。

3. **服务器通信与硬件加密存储 (`network/aether/` & `settings/store/`)**
   - `AetherLyricServerStore`：支持“双引擎模式 (`DUAL_ENGINE`)”与“仅服务器模式 (`SERVER_ONLY`)”，并通过 Android Keystore (`AES/GCM/NoPadding`) 加密存储 `APP_ACCESS_TOKEN`、PIN 码、问答验证答案与 Proxy 密码。
   - `AetherLyricApiClient`：支持 HTTP(S) Proxy 代理、多层验证标头 (`Authorization`, `X-App-Access-Token`, `X-Auth-Pin`, `X-Auth-Answer`)，以及 RFC 6455 WebSocket (`/ws/tasks/{taskId}`) 实时进度监听与 HTTP 轮询自动降级。
