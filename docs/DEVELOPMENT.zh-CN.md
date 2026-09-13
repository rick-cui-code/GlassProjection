# 开发、构建与发布说明

## 目录

```text
projection-lab/                 Android 应用，保留原包名
  src/main/java/               UI、无障碍、显示宿主、Shizuku 连接
  src/main/aidl/               手机端助手 Binder 接口
  src/main/assets/helpers/    当前实机验证的两份 DEX
  src/main/assets/*.png       原创离线测试图及模糊层
tools/helpers/                 实时 GPU 渲染器、切屏控制器 Java 源码
tools/tests/                   原有纯 JVM 模型测试
tools/build_helpers.py         可跨平台重建 / 校验助手
tools/test_models.py           测试入口
tools/bake_demo.py             可选：重建编号测试素材
```

仓库从 TabFold 工作区抽出当前独立模块。未包含旧「折叠玻璃」应用、调试日志、手机截图、设备配对资料、本地 SDK、编译缓存或历史试验包。包名仍为 `io.github.sixzleo.tabfold.projection`，保留已安装版本的升级关系。

## 构建

工具版本：JDK 17、Gradle 8.7、Android Gradle Plugin 8.6.1、SDK Platform 35、Build Tools 35.0.0。`minSdk=33`、`targetSdk=35`。Gradle Wrapper 校验分发包 SHA-256。

设置 `JAVA_HOME`、`ANDROID_HOME`。仅构建 APK 时，也可在不提交的 `local.properties` 中配置 `sdk.dir`。安装 SDK 后：

```sh
python tools/test_models.py
python tools/build_helpers.py --check
./gradlew :projection-lab:assembleDebug :projection-lab:lintDebug
```

Windows 使用 `gradlew.bat`。Python 脚本仅需 Python 3.10+ 标准库。`--check` 在临时构建目录编译助手并对比内置 DEX，不改资产；校验不一致时应先查明编译器或源码差异，不要跳过校验后宣称与测试版本相同。

修改助手后运行 `python tools/build_helpers.py`，它会生成并替换本仓库的两份内置 DEX，然后重建 APK。没有只提供二进制却缺少对应源码的助手。

编号素材已包含在仓库中，普通构建不需要重烘焙。若要修改测试图，可安装 NumPy / Pillow，为 `GLASS_DEMO_FONT` 指定本机合法可用的 TTF 字体，再运行 `python tools/bake_demo.py`。原图在 Windows Segoe UI 下生成，换字体会改变图案；项目不分发字体文件。

## 当前 Release 的二进制来源

v0.3.16 发布当前已安装、已验证的 debug APK，不重新签名。SHA-256：

```text
e26d713a62955bde52f7cd486d753425d9a0f8399602e43f616f6f1a08892f29  GlassProjection-0.3.16.apk
8e75e0a5acac99c53162cb59432f24c23adfe1dad217059de3a5a73d39a84f3d  live.dex
2da8789e5157c684c6e4a594b673425411850e43c413df3be0a2a458fcbea470  controller.dex
```

`python tools/audit_publication.py --apk dist/GlassProjection-0.3.16.apk` 扫描待发布文件，并比较附件与本机构建的运行时代码、资源和清单。APK ZIP 时间戳、构建环境及签名密钥会影响整包字节；不承诺不同机器重建的 APK 与附件逐字节相同。私钥不纳入 Git。

早期 v0.3.12 Release 保持原样；v0.3.16 统一无障碍服务名称为「玻璃投影」，同步应用提示与使用教程；保留此前的全局范围、悬停恢复、真实手指滑动恢复及按钮反馈。详见[恢复选项](RESTORE-OPTIONS.zh-CN.md)。

## 运行路径

`ProjectionService` 负责作用范围判断、铰链数据、实时输出宿主和助手心跳。`MobileHelper` 在服务连接后绑定 Shizuku UserService；`MobileHelperHost` 以 shell UID 启动 APK 内置的两份 DEX，并检查它们是否存活。

`LiveMirrorWindowProbe` 取得实时镜像，将它送入 GPU 模糊金字塔，再按铰链角度投影。当前默认路径不按每个桌面页做静态截图缓存。`MirrorPreview` 通过 `SurfaceControlViewHost` 和显示器级无障碍挂载提供输出，避开此前窗口层级参与的部分系统淡入动画。输出不接收触控。滑动恢复由 TouchObservation 为本服务配置被动观察，FingerSwipeGate 判断位移；FoldHoldGate 统一管理悬停和滑动恢复状态，渲染器中的 FoldReturnMotion 平滑回放投影。

`EarlyDisplayHelper` 从受 UID 限制的 provider 读取状态，以进程绑定的 DeviceStateRequest 控制切屏。心跳过期、场景不允许或进程退出时释放覆盖；用户暂停会停用无障碍并停止助手。

## 设备适配边界

当前固件的状态编号 `0` 对应外屏、`2` 对应内屏；输出采用 1182 × 1182 缓冲与 2 倍缩放，逻辑画布覆盖 2364 × 2364。内屏常用逻辑尺寸 2364 × 1672，外屏 1168 × 1712。方向映射、屏幕识别及这些假设分布于 MirrorPreview、ProjectionService、实时渲染器和 EarlyDisplayModel。

这些不是 Android 各厂商通用约定。适配新设备时需先核对传感器、显示布局、系统状态、隐藏 API 权限和坐标，再调整实现。不要把 `minSdk` 当作兼容清单。项目没有使用 root，也没有修改系统框架或刷机。

## 权限与诊断

Provider 仅允许本应用 UID 或 shell UID 2000 调用。Shizuku 宿主只启动内置且设为只读的 DEX，不接受任意 shell 命令。本版本要求 Shizuku 以 shell 模式运行。

兼容截图代码与少量诊断入口仍随当前 APK 保留。CacheProbeActivity 只处理内置编号图。实时助手在开发者主动请求 GPU 导出、或手动启动限时预览模式时，可把一帧画面写入 `/data/local/tmp/tabfold-live-projection.png`；正常桌面模式不主动启用这些导出。调试时应自行检查日志/图片并脱敏，不能直接上传用户桌面、锁屏通知等内容。

普通 `uiautomator dump` 会建立 UI 自动化会话，可能暂时抑制普通无障碍服务，导致本应用销毁、重连。验证后台常驻时不要同时执行它；优先读取 dumpsys、受限 provider 状态和必要的截图。

## 已完成的验证

- Android debug 构建与 lint。
- 几何、中轴锚定、场景门控、开合方向与角度模型测试。
- 测试设备上的桌面/锁屏、内外屏投影与自定义切屏。
- Shizuku 手机端启动助手，退出设置页后继续运行。
- 单独终止渲染子进程后，手机端助手自动重启并恢复画面。
- 配置小米后台保护后执行一次一键清理，无障碍和原进程保持运行，用户确认动画仍在。

这些记录不代表所有设备适配、长期后台稳定性或持续 120 fps 的保证。
