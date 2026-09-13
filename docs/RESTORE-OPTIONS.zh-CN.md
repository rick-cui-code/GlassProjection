# 0.3.15：手指滑动恢复与悬停时间

在 App 的「恢复正常画面」卡片中：

- **悬停等待时间**：1～10 秒、整秒调整，默认 3 秒。整段时间内铰链最大摆幅不超过 10° 时触发；修改时间重新计时。
- **滑动恢复正常画面**：默认关闭。开启后直接检测单指触点位移，不依赖页面是否滚动。静态空白处也有效，轻点和小幅抖动不触发。

两种触发都使用约 220 ms 的平滑投影回放，随后保持正常画面；再次开合超过 10° 时恢复跟随。设置自动保存。「恢复默认」将等待时间还原为 3 秒、关闭滑动恢复，保留全局作用范围选择。不会因恢复动画而释放切屏角度控制。

## 触摸实现与兼容边界

0.3.15 删除了 0.3.14 的 TYPE_VIEW_SCROLLED 近似检测。由已经授权的 Shizuku shell 助手检查 ACCESSIBILITY_MOTION_EVENT_OBSERVING 权限，为本应用自己的无障碍连接一次性设置 motion sources 与 observed sources 两个掩码，并读取系统结果验证都是 SOURCE_TOUCHSCREEN。失败即清空订阅，不降级为消费触摸事件的监听。

应用使用 HiddenApiBypass 6.1 访问本进程自己的无障碍连接及回调过滤字段，以适配 SDK 未公开的接口；不修改系统隐藏 API 策略或授予系统权限。系统权限检查仍由 Binder 服务执行。没有启用 MONITOR_INPUT，也没有常驻 UiAutomation 测试服务。

滑动模型跟踪单指起点到当前位置的距离，阈值为 20 dp 与两倍系统 touch slop 中的较大者；同一次手势仅触发一次。多指、取消、过期事件、显示切换、非主显示器事件不触发。只在内存中处理触点，不保存坐标、屏幕内容或输入文字，不消费、复制注入或重放手势。关闭选项、助手断连、停用服务时清空订阅。

需要设备系统实现观察接口且 Shizuku shell 已具有相应权限；不支持时悬停计时仍然可用。Android 14 以下不会请求该接口。

## 验证

2026-09-13：系统读取结果 sources=4098、observed=4098；实机日志记录 FINGER_RESTORE 与 RETURN_TO_NORMAL，用户确认应用内和桌面滑动均能触发。原有应用触控保持可用。JVM 测试覆盖单指阈值、轻点抖动、重复手势、多指取消、过期输入、显示切换，以及 1/3/10 秒悬停计时、平滑回放和再次开合恢复。

本次只更新本地源码与手机安装包，GitHub 已发布的 v0.3.12 未变。

参考：[Android onMotionEvent](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#onMotionEvent(android.view.MotionEvent))、[AOSP 观察接口与说明](https://android.googlesource.com/platform/prebuilts/fullsdk/sources/+/refs/heads/androidx-core-telecom-release/android-35/android/accessibilityservice/AccessibilityServiceInfo.java)、[HiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass)。
