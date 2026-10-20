# AetherLyric

<div align="center">

# AetherLyric-app
**全功能 Android 悬浮同步歌词客户端 · 双引擎 AI 语音转录架构**

[![Platform](https://img.shields.io/badge/Platform-Android_8.0+-brightgreen?style=flat-square)](https://github.com/milk120106/AetherLyric-app)
[![Version](https://img.shields.io/badge/Version-0.1.0-blueviolet?style=flat-square)](https://github.com/milk120106/AetherLyric-app/releases)
[![License](https://img.shields.io/badge/License-MIT-orange?style=flat-square)](LICENSE)

</div>

---

## 项目概述 (Overview)

**AetherLyric-app** 是一款开源、轻量且功能强大的 Android 悬浮同步歌词应用程序（基于 AirLyrics 进行扩展与重构）。

本项目最大特色在于**双引擎模式 (Dual-Engine Mode)**：
1. **自建 AI 服务器引擎 (AetherLyric-server)**：可连接自建服务器，使用 Faster-Whisper / Groq / OpenAI 兼容模型进行 AI 语音转录、逐字对齐与智能歌词校正。
2. **本地多源聚合引擎**：未连接服务器或自建服务器离线时，自动无缝降级使用本地多来源聚合搜索（网易云、QQ 音乐、酷狗音乐、LRCLIB、Musixmatch、Genius、AZLyrics 等）。

---

## 核心功能特色 (Key Features)

### 1. 双引擎架构 (Dual-Engine Architecture)
- **服务器健康检测**：在「设置」页面提供服务器地址输入与「测试连接」验证，实时显示连接指示灯与队列状态。
- **无缝自动降级**：优先调用自建服务器 API；连接超时或缓存未命中时，自动切换为本地多源查询。

### 2. 本地歌词来源扩展 (Multi-Source Aggregation)
- 支持自由排序、勾选与启用多家歌词源：
  - **网易云音乐 (NetEase Cloud Music)**
  - **QQ 音乐 (QQ Music)**
  - **酷狗音乐 (Kugou)**
  - **LRCLIB**
  - **Musixmatch**
  - **Genius**
  - **AZLyrics**
  - **Spicy Lyrics (逐字动态歌词)**

### 3. 本地音频 AI 歌词识别
- 主界面支持直接选取本地音频文件（MP3、FLAC、WAV 等）。
- 一键上传至自建 AetherLyric-server 进行 AI 语音识别与时间轴自动对齐，实时生成并保存标准 LRC 歌词。

### 4. 多歌词版本管理 (Multi-Version Lyrics)
- 单一歌曲支持保存与管理多种歌词版本（如「官方原版」、「AI 识别版」、「日文翻译版」、「罗马音版」、「自定义修改版」）。
- 一键即时切换版本，支持新增、重命名与删除。
- **版本专属时间轴偏移**：切换版本时，自动应用该版本设定之专属时间轴偏移量。

### 5. 歌词时间轴微调与保存
- 支持以 `±0.1s` 及 `±1s` 精细微调时间轴。
- 提供「保存当前偏移量」按钮，明确持久化至本地数据库。
- 提供「是否应用到所有歌曲 (全局偏移量)」开关，轻松解决特定蓝牙耳机延迟问题。

### 6. 歌词手动编辑器
- 在最近播放与本地列表中点击即可进入编辑模式。
- 支持手动修改错字、微调各行时间戳，并执行格式校验与覆盖保存。

### 7. 原生极简设计与界面固定
- 底部导航栏固定置底，防止旋转横屏时突兀切换为侧边栏。
- 完整适配深色与浅色主题，圆角与色系完全融入现代 Material 设计语言。

---

## 许可证 (License)

本项目基于 [AirLyrics](https://github.com/AirLyrics/AirLyrics) (MIT License) 进行 Fork 与二次开发。
本项目同样采用 [MIT 许可证](LICENSE)。
