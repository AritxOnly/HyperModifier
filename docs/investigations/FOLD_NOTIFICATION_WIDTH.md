# 折叠屏通知中心与锁屏宽度核对

2026-11-08。正式兼容基线仍为 `OS4.0.2.0.XPCCNXM`；另以本地 `MiuiSystemUI-OS4.0.26.0.XPNCNXM.apk` 与匹配的 `systemui-fold-4.0.26.0` 反汇编核对折叠屏逻辑。不混用资源 ID，所有资源名均通过各自 APK 的 aapt2 dump resources 对照。

## 确认的两层宽度

`NotificationPanelViewControllerInjector.updateResources$1()` 按 `barState == 1` 选择 `notification_panel_width_lockscreen`，否则选择 `notification_panel_width`，写入通知栈的 ConstraintLayout.LayoutParams.width。`SharedNotificationContainerInteractor` 的配置维度 Flow 也按锁屏通知状态选择相同两项资源。

两包中的资源值相同：

| 资源配置 | notification_panel_width | notification_panel_width_lockscreen |
| --- | --- | --- |
| 默认窄屏 | match_parent | match_parent |
| sw600dp-port | 504dp | 661dp |
| sw600dp-land | 504dp | 432.882812dp |
| 一般 land | 420.664062dp | 未单列，按资源匹配规则回退 |

这些是面板/栈宽度，不是卡片最终内容宽度。`NotificationStackScrollLayout.onMeasure` 再减去两倍 `mMinimumPaddings`；一般由 `notification_side_paddings`（14dp）提供，迷你屏横屏另有专用值。随后原生 `onLayout` 居中放置子项。因此常规无额外资源覆盖情况下，504dp 容器的通知卡片约为 476dp，661dp 容器约为 633dp；不能把资源表值直接宣称为真机卡片测量值。

## 外屏特殊资源不等于普通通知列宽

另外存在 `notification_panel_width_outer_port = 397.593750dp`。`NotificationConfigManager` 根据 `use_outer_screen_portrait_dimension` 选择该尺寸并在配置变化时失效缓存。

`ExpandableNotificationRow.onMeasure` 仅在 `isHeadsUpState()` 且此开关为真时改用该宽度；`PlayerIslandConstraintLayout` 与 `DynamicIslandController` 也有使用分支。APK 默认布尔值为 false，实际设备可能叠加资源覆盖。不能把 397.59dp 当成外屏普通通知中心或锁屏的固定宽度。

## 对模块的结论

系统本来就区分资源配置和锁屏场景。当前模块的“通知中心最大宽度 / 锁屏最大宽度”是叠加于原生测量后的上限，默认关闭，并不替换上述面板资源。对折叠屏只提供这两个共用上限，无法分别表达内外屏的偏好；后续若实现独立设置，应至少按“内屏通知中心、内屏锁屏、外屏通知中心、外屏锁屏”分开，并保留系统原始上限、横竖屏资源切换和悬浮通知专用途径。

本轮按用户“先从 SystemUI 看看”的要求只做调查，未将宽度设置扩展成四组，也未改动 APK/反汇编。以上是静态反汇编及资源表证据，尚无真机内外屏测量或 RRO 清单佐证。
