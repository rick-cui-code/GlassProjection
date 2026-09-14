# v0.4.23 内外屏交接淡入淡出提速

用户明确反馈折叠开合时内外屏交接的变暗、变亮偏慢。本次调整 `ScreenFade`，以已通过实机跟手和稳定性测试的 0.4.22 为基线。

| 阶段 | 之前 | 现在 |
| --- | --- | --- |
| 接近切屏角度时变暗 | 切屏前 12° 开始 | 切屏前 8° 开始 |
| 新屏画面就绪后变亮 | 180 ms | 120 ms |
| 反向或保持时取消变暗 | 120 ms | 80 ms |

变暗仍由实际开合角度驱动，变亮与取消沿用平滑曲线。应用状态计算和 GPU 辅助进程共用相同的时长常量，避免两端采样不同步。新屏有效画面确认和 900 ms 等待上限保持原值；上述阶段缩短三分之一不代表硬件切屏总耗时固定缩短三分之一。

全部模型测试通过，包括内外屏双向交接、配置阈值边界、新帧确认、旧确认拒绝、反向取消、慢开停住和超时恢复，以及已有闭合保护、跟手和性能策略回归。两个辅助 DEX 已重建，Debug 构建与 Lint 通过（0 errors / 25 warnings）。

已在测试手机覆盖安装 0.4.23 / versionCode 80，保留配置、配对和其他无障碍服务。启动后确认服务存活，闭合时 angle=0、projectionBlocked=true、screenDarkness=0；GPU 日志进入 CAPTURE_PAUSE。交接速度的主观体验待用户实机反馈。

后续采样期间系统在 21:57:46 终止应用，退出记录为 `OTHER KILLS BY SYSTEM` / `[DA] umms_selfcheck {744 frozen-foreground}`，随后后台进程有一次 `LOW_MEMORY` 回收记录。已重新启用本应用服务，复核 alive=true、霍尔样本新鲜、闭合透明及采集暂停。此次未采到完整交接，因此不将安装后日志视为交接视觉体验通过；退出记录与恢复状态保存于 `build/diagnostics/fade-0423-exit-info.txt` 和 `fade-0423-restored-state.txt`。

分发包：`dist/GlassProjection-0.4.23.apk`。

SHA-256：`ac0909ce06d71def30055030e98a9f8cf47bab10ca3f1f55765013877eda8541`。

0.4.22 分发包保留，便于对照。现场初始状态位于 `build/diagnostics/fade-0423-state.txt`。
