# 多源歌词匹配与优先级算法

[繁體中文](lyrics-matching.md) · [简体中文](lyrics-matching.zh-CN.md) · [English](lyrics-matching.en.md)

---

## 1. 并行搜索与严格排序规则
`LyricsRepository.searchAllSourcesParallel` 会同时查询所有已启用之歌词来源（包含内置平台、自定义 JSON 插件与自建服务器），并按照以下三级严格优先级排序候选结果：
1. **用户设定的来源优先顺序 (`priorityRank`)**
2. **是否具备同步 LRC 时间轴标签 (`hasSyncedLrc`)**：拥有 `[mm:ss.xx]` 时间戳的同步歌词优先于纯文本歌词。
3. **歌曲时长相近度**：与当前播放曲目时长 (`durationMs`) 差值越小者优先。
