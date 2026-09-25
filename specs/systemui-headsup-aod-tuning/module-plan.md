# SystemUI Heads-up Handle and AOD Clock Weight Module Plan

## 模块拆分

- `settings`：在 `ModifierSettings` 和 `ModuleSettings` 之间持久化与同步两个独立选项。
- `runtime/heads-up`：单独的 Java Hook 类负责定位横条视觉层并处理其刷新。
- `runtime/heads-up-margin`：独立 Hook 在通知行状态更新后修改该行内容容器的底边距，并保存原值供退出状态时恢复。
- `runtime/aod-clock`：单独的 Java Hook 类负责 AOD 时钟动画终态目标值。
- `ui`：在通知与锁屏页面分别配置这两个选项；大设置页拆成入口、通知页、其他系统功能页三个 Kotlin 文件。

## 平台映射

- iOS：无，本功能仅针对 HyperOS SystemUI。
- HarmonyOS：无。
- Android：`app/src/main/java/com/aritxonly/myhypermodifier/{runtime,settings,ui}`。

## 文件拆分策略

- 单个核心文件尽量不超过 1000 行有效代码
- 若必须逃逸，先申请开发者批准
- 避免继续扩充 `SystemUiRuntimeHooks.java`；入口只调用独立安装器。
- 原 `MyHyperModifierSettingsApp.kt` 超过 2000 行，已拆为 `MyHyperModifierSettingsApp.kt`、`NotificationSettingsPages.kt` 和 `SystemFeatureSettingsPages.kt`。
- `core_suffixes.txt` 将 Java/Kotlin 纳入规格中的 1000 行规模检查。

## 风险点

- 横条视觉元素与触摸入口可能共享对象，不能通过删除整行或禁用触摸来隐藏。
- AOD 字重可能以动画参数多次写入，Hook 必须只修改目标终值或最终状态。
