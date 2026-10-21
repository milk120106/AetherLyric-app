# Contributing Guide

[繁體中文](CONTRIBUTING.md) · [简体中文](CONTRIBUTING.zh-CN.md) · [English](CONTRIBUTING.en.md)

---

## 1. Code & Design Standards
- **Language & Comments**: Use Traditional Chinese (`zh-TW`) for code comments and commit messages.
- **No Emojis**: Do not use any emojis in source code, UI strings, or documentation.
- **Semantic Theme Colors**: Always use semantic palette colors from `AirLyricsTheme` to ensure proper contrast in both Light and Dark modes.
- **No Local Absolute Paths**: Never commit local filesystem paths into source code or documentation.

## 2. Pull Request Workflow
1. Fork the repository and create a feature branch.
2. Verify that `./gradlew assembleDebug` and unit tests pass without errors.
3. Submit a Pull Request with a clear summary of architectural and UI changes.
