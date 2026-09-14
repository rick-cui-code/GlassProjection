# v0.4.27 恢复默认小窗应用配对

- 修正 v0.4.26 对需求的误解：默认「配对」恢复原来的 WirelessPairingActivity 小窗应用，包含独立任务、自动申请 freeform、手动转小窗提示及端口自动发现。恢复原实现及 manifest 配置，不需要悬浮窗权限或通知权限。
- 页面底部「更多配对方式」提供通知配对。通知授权与返回通知设置后的续接只进入通知配对，不影响默认小窗流程。
- 仅删除 WirelessPairingService 中的悬浮配对面板、悬浮窗授权入口及 SYSTEM_ALERT_WINDOW 权限。
- 保留管理无障碍直达「已下载的应用」的入口，以及 APK 更新等已有功能。

验证：assembleDebug 与 lintDebug 通过；默认小窗 Activity 与原实现完全一致；已覆盖安装 0.4.27（versionCode 84），原配对身份和设置保留，动画服务已恢复且报告 alive=true。未清除现有配对来重测首次配对流程。

APK：dist/GlassProjection-0.4.27.apk
SHA-256：ebfd9b7942c4ae4fedb45d17e2ff8a821e5891b24c6e726b71457e1a62d8a4f7
