# Multi-Source Lyrics Matching & Ranking Algorithm

[繁體中文](lyrics-matching.md) · [简体中文](lyrics-matching.zh-CN.md) · [English](lyrics-matching.en.md)

---

## 1. Parallel Query & Strict Ranking Rules
`LyricsRepository.searchAllSourcesParallel` concurrently queries all enabled providers (built-in platforms, custom JSON plugins, and the self-hosted server) and ranks candidates strictly by:
1. **User-Configured Source Priority (`priorityRank`)**
2. **Synced LRC Timestamp Availability (`hasSyncedLrc`)**: Synced `[mm:ss.xx]` lyrics are prioritized over plain-text lyrics.
3. **Duration Closeness**: Candidates whose duration is closest to the active track's `durationMs` are ranked higher.
