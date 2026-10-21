# Lyrics Format & Timeline Specification

[繁體中文](LYRICS_FORMAT.md) · [简体中文](LYRICS_FORMAT.zh-CN.md) · [English](LYRICS_FORMAT.en.md)

---

## 1. Supported Lyrics Formats
- **Standard LRC**: Line-synced format with `[mm:ss.xx]` timestamps.
- **Bilingual LRC**: Line-synced format containing both original lyrics and translated lines sharing matching `[mm:ss.xx]` timestamps or separated inline.
- **Word-by-Word LRC**: Enhanced format containing intra-line `<mm:ss.xx>` word-level timing tags.
- **Plain Text TXT**: Un-timestamped plain text lines (can be aligned via `/api/v1/align` or exported by stripping timestamps).
