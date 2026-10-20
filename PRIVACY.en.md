# Privacy Policy

[繁體中文](PRIVACY.md) · [简体中文](PRIVACY.zh-CN.md) · [English](PRIVACY.en.md)

**AetherLyric-app** is an open-source floating synced lyrics application designed for Android. We are committed to strict user privacy protection: **the app does not collect, upload, sell, or share personal data**.

---

## 1. Data Collection

- **No Personal Data Collected**: AetherLyric-app does not collect personal identity information (such as names, email addresses, phone numbers, or persistent device hardware identifiers).
- **No Tracking or Advertising SDKs**: The app does not integrate third-party analytics SDKs, advertising SDKs, tracking libraries, user account systems, or automated crash reporting services.

---

## 2. Android Permissions

AetherLyric-app requests Android permissions only when necessary for its core features:

| Permission | Type | Purpose |
| :--- | :--- | :--- |
| **Display over other apps**<br>`SYSTEM_ALERT_WINDOW` | Overlay | Renders floating synchronized lyrics on top of other running applications. |
| **Notification access**<br>`NotificationListenerService` | Media Notification Listener | Locally detects current playback metadata (track title, artist, album, cover art, and playback state). **Does not inspect, store, or upload private messaging notifications**. |
| **Foreground service notifications**<br>`POST_NOTIFICATIONS` | System Notification | Maintains continuous operation of the floating lyrics foreground service and prevents premature termination by the operating system. |
| **Usage access**<br>`PACKAGE_USAGE_STATS` | Optional | Used solely on-device for the "Display Scope" feature to determine whether user-selected apps are currently in the foreground. **Does not read app contents or record usage logs**. |
| **Internet access**<br>`INTERNET` | Network Connection | Connects to online lyrics providers or user-specified self-hosted servers during lyrics lookups or AI transcription requests. |
| **Accessibility Service**<br>`AccessibilityService` | Optional | Assists media detection and playback progress tracking on video platforms (such as YouTube or Bilibili) that do not implement standard MediaSession protocols. **Does not log keystrokes or record screen contents**. |
| **Shizuku access** | Optional | Performs elevated media detection without accessibility services via user-authorized Shizuku installations. Purely inter-process communication on-device. |
| **File picker**<br>`ACTION_OPEN_DOCUMENT` | Storage Access | Allows users to manually select local audio files for AI transcription, import custom font files, or import/export local LRC lyrics files. |

---

## 3. Local Data Storage

- The app stores the following data locally on the device:
  - Application settings and UI preferences (theme colors, compact mode status).
  - Self-hosted AetherLyric server configuration profiles (profile names and URLs).
  - Lyrics provider ordering, enabled states, and custom API tokens (BYOK).
  - Locally cached lyrics, multi-version lyrics libraries, and custom timeline offset records.
  - Runtime execution logs (AppLogger, retained in memory and temporary local cache).
- **All data listed above remains strictly on the user's device**. Unless the user explicitly initiates an export, share, or backup action, AetherLyric-app never transmits this data to any remote server.

---

## 4. Online Lyrics Search & Self-Hosted AI Server

- **Online Lyrics Search**:
  - When searching for lyrics online, the app transmits required query metadata (song title, artist, album, and duration) to selected providers (NetEase Cloud Music, QQ Music, Kugou, LRCLIB, Musixmatch, Genius, AZLyrics, etc.).
  - The app **never includes personal identifiers, account tokens, or advertising IDs** in these queries.
- **Spicy Lyrics Integration**:
  - When Spicy Lyrics is enabled, track metadata is queried against Spotify and the Spicy Lyrics API for word-by-word timestamps.
  - Retrieved lyrics and manual edits are stored in app-private storage excluded from system backups for a maximum of 30 days and are automatically purged upon expiration.
- **Self-Hosted AetherLyric Server**:
  - When Dual-Engine mode is activated, user-selected audio files, reference plain-text lyrics, or video URLs are transmitted directly to the **user-specified self-hosted server** (e.g. POST `/api/v1/align`).
  - **The AetherLyric development team does not host or operate any central servers** and cannot access, monitor, or intercept audio files or text processed by self-hosted instances.

---

## 5. External Control & Broadcast Intent API Security

- AetherLyric-app supports automation via standard Android Broadcast Intents (e.g., showing/hiding the floating window, loading lyrics).
- For system security:
  - Enabling Intent APIs requires explicit confirmation of a security disclaimer.
  - Requests from unrecognized applications are rejected by default.
  - A dedicated App Permissions dialog allows granular management of allowed and denied applications.

---

## 6. Third-Party Services and Tools

- The application interacts with external services (lyrics providers, self-hosted servers, or installed helper tools such as YTDLnis) only upon user-initiated actions.
- Each third-party lyrics provider operates under its own privacy policy and terms of service.

---

## 7. Children's Privacy

AetherLyric-app is not directed to children and does not knowingly collect personal data from anyone, including children.

---

## 8. Changes to this Policy

This privacy policy may be updated to reflect feature additions or architectural improvements. All revisions will be posted directly to this repository.

---

## 9. Contact

For inquiries or feedback regarding privacy or data practices, please open an issue on GitHub:

[https://github.com/milk120106/AetherLyric-app/issues](https://github.com/milk120106/AetherLyric-app/issues)
