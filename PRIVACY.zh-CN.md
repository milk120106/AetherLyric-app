# 隐私政策 (Privacy Policy)

[繁體中文](PRIVACY.md) · [简体中文](PRIVACY.zh-CN.md) · [English](PRIVACY.en.md)

**AetherLyric-app** 是一款专为 Android 设计的开源悬浮同步歌词应用程序。本应用程序秉持严格的隐私保护原则，**绝不会收集、上传、出售或共享用户的任何个人数据**。

---

## 1. 数据收集原则 (Data Collection)

- **不收集个人数据**：AetherLyric-app 不会收集用户的任何个人身份识别信息（如姓名、电子邮件、电话号码、设备唯一标识符等）。
- **无追踪与广告组件**：本应用程序不包含任何第三方统计分析 SDK、广告 SDK、用户追踪 SDK、账号登录系统或崩溃自动上报服务。

---

## 2. Android 权限使用说明 (Permissions)

AetherLyric-app 仅在实现核心功能所需时，才会向系统申请下列权限：

| 权限项目 | 权限类型 | 用途说明 |
| :--- | :--- | :--- |
| **悬浮窗权限**<br>`SYSTEM_ALERT_WINDOW` | 系统悬浮窗 | 在其他应用程序顶层显示同步滚动的悬浮歌词窗口。 |
| **通知访问权限**<br>`NotificationListenerService` | 媒体通知监听 | 在设备本机实时检测播放中的音乐曲目信息（歌名、歌手、专辑、封面与播放状态）。**绝不上传或记录任何私人通讯通知内容**。 |
| **前台服务通知**<br>`POST_NOTIFICATIONS` | 系统通知 | 让 Android 系统维持悬浮歌词前台服务稳定运行，避免后台运行时被系统异常终止。 |
| **使用情况访问权限**<br>`PACKAGE_USAGE_STATS` | 可选权限 | 仅在设备本机用于“显示范围 (Display Scope)”功能，判断用户所勾选之 App 是否正处于前台可见状态。**绝不读取应用程序内容，亦不记录使用数据**。 |
| **网络访问权限**<br>`INTERNET` | 网络连接 | 在用户执行在线歌词搜索、本地 Google 翻译、自定义 JSON 插件查询或服务器转录时发送请求。 |
| **无障碍服务权限**<br>`AccessibilityService` | 可选权限 | 针对未完整实现标准 MediaSession 协议的音视频 App（如 YouTube、Bilibili），在设备本机辅助检测播放进度与状态。**绝不记录任何键盘输入或屏幕隐私内容**。 |
| **Shizuku 访问权限** | 可选权限 | 通过用户已安装并授权的 Shizuku 进行高权限免无障碍的媒体检测。纯本机进程间通信，不涉及任何外连行为。 |
| **外部歌词访问自定义权限**<br>`com.airlyrics.app.permission.ACCESS_LYRICS` | 自定义权限 | 保护外部控制广播接收器 (`AetherLyricIntentReceiver`)，防止未声明权限的第三方应用程序发送恶意广播。 |
| **文件选择器**<br>`ACTION_OPEN_DOCUMENT` | 存储访问 | 允许用户手动选取本地音频文件进行 AI 识别、导入自定义字体或导入/导出本地歌词文件。 |

---

## 3. 本地数据存储与硬件级加密 (Local Storage & Hardware Encryption)

- 本应用程序会在设备本机存储以下数据：
  - 应用程序设置与界面偏好（主题色彩、亮暗模式、简洁模式开关等）。
  - 自建 AetherLyric 服务器配置文件（名称、URL、引擎协同模式、WebSocket 开关、Proxy 设置）。
  - **Android Keystore 硬件加密凭证**：服务器验证凭证（`APP_ACCESS_TOKEN`、PIN 码、问答验证答案）与 Proxy 密码均通过 Android Keystore (`AES/GCM/NoPadding`) 加密存储，不会以明文落盘。
  - 歌词来源之排序、启用状态、自定义 JSON 插件规则与自定义 API 密钥 (BYOK)。
  - 本地缓存歌词文件、多版本歌词库与自定义时间轴偏移量记录。
  - 应用程序运行日志（AppLogger，仅保存在 RAM 与本机临时存储空间）。
- **上述所有数据均完全保留在用户设备本机**。除非用户主动通过系统分享、导出功能或第三方工具进行传输，否则本应用程序绝不会主动将其发送至任何远程服务器。

---

## 4. 联网歌词搜索、本地翻译与自建 AI 服务器 (Online Search & Server Modes)

- **多源并行歌词搜索**：
  - 当用户搜索在线歌词时，应用程序会将查询所需的曲目元数据（歌名、歌手、专辑与歌曲时长）发送至已启用的歌词提供商或用户安装的自定义 JSON 插件来源。
  - 若用户切换为“**仅服务器模式**”，App 将停用所有本机对第三方平台的直接查询，全数交由用户指定的自建服务器处理。
- **本地 Google 翻译**：
  - 当用户主动开启“启用本地 Google 翻译”且歌曲无现成翻译时，应用程序仅将纯歌词文本发送至翻译端点以生成中文对照，不包含任何个人识别信息。
- **自建 AetherLyric-server 引擎**：
  - 当用户启用服务器并设置自建地址时，主动选取的音频文件、纯文本歌词或输入的视频网址，将直接通过 HTTP(S) 或 WebSocket (`/ws/tasks/{taskId}`) 发送至**用户指定的自建服务器**。
  - **AetherLyric 开发团队不维护任何中央云端服务器**，无法访问、截获亦不拥有任何用户发送至自建服务器的音频与文本内容。

---

## 5. 外部控制与广播 Intent API 安全性 (External Control Security)

- AetherLyric-app 支持通过标准 Android Broadcast Intent 接收外部自动化指令与双向 `PendingIntent` 查询。
- 为了保障系统安全性：
  - 强制要求调用端具备 `com.airlyrics.app.permission.ACCESS_LYRICS` 权限。
  - 通过 `PendingIntent.getCreatorPackage()` 验证真实调用方包名。
  - 未知应用程序的调用默认采取“拒绝”处置，并提供独立的应用白名单与黑名单权限管理界面。

---

## 6. 联系与反馈 (Contact)

若对本隐私政策或数据安全有任何疑问、建议或漏洞报告，请通过 GitHub Issues 与我们联系：

[https://github.com/milk120106/AetherLyric-app/issues](https://github.com/milk120106/AetherLyric-app/issues)
