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
| **Internet access**<br>`INTERNET` | Network Connection | Connects to online lyrics providers, local Google Translate endpoint, custom JSON plugins, or user-specified self-hosted servers. |
| **Accessibility Service**<br>`AccessibilityService` | Optional | Assists media detection and playback progress tracking on video platforms (such as YouTube or Bilibili) that do not implement standard MediaSession protocols. **Does not log keystrokes or record screen contents**. |
| **Shizuku access** | Optional | Performs elevated media detection without accessibility services via user-authorized Shizuku installations. Purely inter-process communication on-device. |
| **Custom External Lyrics Permission**<br>`com.airlyrics.app.permission.ACCESS_LYRICS` | Custom Permission | Protects the external broadcast receiver (`AetherLyricIntentReceiver`) against unauthorized broadcasts from untrusted third-party apps. |
| **File picker**<br>`ACTION_OPEN_DOCUMENT` | Storage Access | Allows users to manually select local audio files for AI transcription, import custom font files, or import/export local lyrics files. |

---

## 3. Local Data Storage & Hardware-Backed Encryption

- The app stores the following data locally on the device:
  - Application settings and UI preferences (theme colors, light/dark mode, compact mode status).
  - Self-hosted AetherLyric server configuration profiles (profile names, URLs, engine mode, WebSocket toggle, and Proxy settings).
  - **Android Keystore Encrypted Credentials**: Server authentication secrets (`APP_ACCESS_TOKEN`, PIN code, Challenge Answer) and Proxy passwords are encrypted via Android Keystore (`AES/GCM/NoPadding`) and are never stored in plaintext.
  - Lyrics provider ordering, enabled states, custom JSON plugin definitions, and custom API tokens (BYOK).
  - Locally cached lyrics, multi-version lyrics libraries, and custom timeline offset records.
  - Runtime execution logs (AppLogger, retained in memory and temporary local cache).
- **All data listed above remains strictly on the user's device**. Unless the user explicitly initiates an export, share, or backup action, AetherLyric-app never transmits this data to any remote server.

---

## 4. Online Lyrics Search, Local Translation & Self-Hosted AI Server

- **Parallel Multi-Source Lyrics Search**:
  - When searching for lyrics online, the app transmits required query metadata (song title, artist, album, and duration) to enabled providers or user-installed custom JSON plugins.
  - When **Server-Only Mode** is selected, the app disables all direct device-side requests to third-party providers and routes all queries exclusively through your self-hosted server.
- **Local Google Translate**:
  - When "Enable Local Google Translate" is turned on and a song lacks existing translations, only the lyric lines are sent to the translation endpoint to generate bilingual lines, without any personal identifiers.
- **Self-Hosted AetherLyric Server**:
  - When server collaboration is enabled, user-selected audio files, reference plain-text lyrics, or video URLs are transmitted directly via HTTP(S) or WebSocket (`/ws/tasks/{taskId}`) to the **user-specified self-hosted server**.
  - **The AetherLyric development team does not host or operate any central servers** and cannot access, monitor, or intercept audio files or text processed by self-hosted instances.

---

## 5. External Control & Broadcast Intent API Security

- AetherLyric-app supports automation via standard Android Broadcast Intents and two-way `PendingIntent` callbacks.
- For system security:
  - Requires callers to hold the `com.airlyrics.app.permission.ACCESS_LYRICS` permission.
  - Verifies the authentic caller package via `PendingIntent.getCreatorPackage()`.
  - Requests from unrecognized applications are rejected by default, with a dedicated App Permissions dialog for granular allow/deny control.

---

## 6. Contact

For inquiries or feedback regarding privacy or data practices, please open an issue on GitHub:

[https://github.com/milk120106/AetherLyric-app/issues](https://github.com/milk120106/AetherLyric-app/issues)
