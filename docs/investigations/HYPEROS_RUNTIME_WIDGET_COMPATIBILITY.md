# HyperOS Runtime 桌面：原生小组件堆叠与柔光玻璃

调查日期：2026-10-11。范围是现有设备的桌面
`RELEASE-8.01.02.7722-260904-09221533-R`，不是所有 HyperOS 版本的通用结论。
本轮只实现设置菜单整理，没有安装堆叠或玻璃实验 Hook，也没有修改桌面数据库。

## 初步结论

Android 原生小组件有望复用现有堆叠与材质路径。
桌面已经承载带 MIUI 扩展声明的 Android RemoteViews 组件；问题首先在资格识别、
拖放/模型构建和组件背景协商，而不是需要将所有 Android 组件转成 MAML。
普通组件是否能仅通过白名单资格适配进入现有堆叠，仍需原型验证。

柔光玻璃也有现成线索：MIUI RemoteViews provider 声明
`supportBackgroundBlur`，桌面 Dart AOT 中有材质能力与样式通知路径。
目前不能认定该标记足以让任意第三方背景正确透明化，也不能认定它必然开启
与文件夹完全相同的折射/混色效果。

## 设备上的对照对象

ADB `dumpsys appwidget` 确认以下实例均由 `com.miui.home` 的 hostId 1024 承载。
桌面活动 dump 与 APK 清单提供独立对照：

| 组件 | appWidgetId | 桌面 itemType | 尺寸 | itemFlags | provider 扩展声明 |
| --- | --- | --- | --- | --- | --- |
| Deadliner CompactTaskGlanceWidgetReceiver | 52 | 4 | 2×2 | 0 | 只有 android.appwidget.provider；未声明 miuiWidget/supportBackgroundBlur |
| 小米运动健康 WidgetRainbowProvider_2_2 | 61 | 4 | 2×2 | 160 | miuiWidget=true、supportBackgroundBlur=true |
| 默认经典时钟（与锁屏同步） | 101390041 | 19 | 4×2 | 160 | MAML，无标准 AppWidgetProvider |

itemFlags 差异是观测结果，本轮没有确认各 bit 的完整含义，不能直接将 160
当作通用“允许堆叠”掩码写入数据库。

运动健康声明还包含 `miuiWidgetRefresh=exposure`、刷新间隔和
`miui.appwidget.action.APPWIDGET_UPDATE` action。
这些扩展说明 provider 与桌面有附加契约，不应复制其元数据后假定普通 provider
实现了相同生命周期与背景行为。

## 堆叠限制证据

桌面 APK 无 Java DEX；主要逻辑分布在 Rust `libapp_launcher.so` 和
Dart AOT `libapp.so`。解析 launcher 的 `.gnu_debugdata` 后找到：

| Rust 函数 | 本版 RVA | 证据与作用 |
| --- | --- | --- |
| LauncherUtils::is_support_stack_widget | 0x6c635c | 反汇编读取 debug_force_stack_widget、检查系统版本，并读取 supportStackWidget 元数据；属于全局能力门禁 |
| MiuiWidgetUtils::is_miui_widget | 0x6c6a00 | 反汇编从 provider ActivityInfo 获取 metadata，调用 Bundle_get_boolean("miuiWidget") |
| MiuiWidgetCompat::create_item_info | 0x6f49cc | 符号与诊断字符串包含 provider、MAML、STACK 的模型构建分支 |
| MiuiWidgetCompat::create_stack_card_items | 0x6f80e4 | 堆叠卡片构建入口，仍待逐字段确认 |
| LauncherModelInternal::load_pending_app_widget | 0x79d140 | 原生 provider 加载路径；诊断允许堆叠子项处于非 desktop container |

上表地址仅用于当前二进制分析，不是可直接复用的 Hook 配置；函数调用 ABI 和
入口指纹仍须逐项验证。

`libapp.so` 的 WidgetStackDropHandler 包含明确的拒绝诊断：

- dragItem / targetItem is not miui widget；
- dragItem or targetItem is 1x1；
- service delivery / gadget 排除；
- target stack full；
- device is not hyperOS4+、PA stack widget disabled。

还有 `WidgetStackGetXController addWidgetToStack---unknown widget type`、
`ADD_WIDGET_TO_STACK`、`INSERT_OR_UPDATE_STACK_WIDGET` 与堆叠移除/动画路径。
因此更改全局能力返回值不足以完成普通组件适配；也不能只绕过拖放检查，
忽略后续模型构建及持久化。

## 原生组件在 Runtime 中如何显示

`libnative_widget_sdk.so` 引用 HyperOS AppWidget API：AppWidgetHost 的监听、
RemoteViews actions、provider 信息和组件 options，向 Dart 发送更新事件。
`libapp.so` 包含 AppWidgetHostView、LauncherAppWidgetHostViewState、
RemoteViews inflate/apply actions、点击绑定、集合视图 adapter 和快照日志。

这些证据支持“原生 RemoteViews 被 Runtime 解析并由 Flutter 侧构建视图”的
工作假设，不能将 Android ART 的 AppWidgetHostView Java Hook 直接搬入此桌面。
但本轮没有证明所有组件/视图都采用同一种合成方式；快照、集合视图和特殊内容
需单独确认。标准 Flutter AndroidView 文档不能代替 Xiaomi 定制实现证据。

Android 的标准 host 契约要求维持原 appWidgetId、配置 Activity、尺寸 options、
更新监听与删除语义；Glance 最终也受 AppWidget/RemoteViews 机制约束。
参考：[Android widget host](https://developer.android.com/develop/ui/views/appwidgets/host)、
[Jetpack Glance](https://developer.android.com/develop/ui/compose/glance)。

## 柔光玻璃证据与限制

`libapp_launcher.so` 存在 `supportBackgroundBlur`、`widget_support_blur`，
`libapp.so` 存在：

- `widgetBlurSupported changed`；
- `usingFolderGlassStyle`；
- `notifyAllWidgetBlurOptionsForMaterialChange usingFolderGlassStyle broadcast`；
- `GlassWidget`、`HyperMaterialWidget` 的玻璃能力初始化诊断；
- 编辑页 view-level PassBlur 的应用/恢复诊断。

定制 Flutter engine `libhyper_os_flutter.so` 有 PassBlur 状态、纹理和
HardwareBuffer 相关实现。编辑页模糊与常驻组件卡片材质不是同一语义，不能
因为一个可用就声称另一个已实现。上述 Dart 字符串的调用关系仍待反汇编定位。

可分成两条实验路线：

1. 对可控制的 provider（先用 Deadliner）实现透明背景和桌面样式协商，
   再验证桌面原生材质容器。此路线最容易分离背景与内容，保留文字和图标。
2. 对无法修改的第三方 provider，由桌面 host 在白名单范围内替换根背景并提供
   材质容器。需要确认背景属于 drawable、RemoteViews action 还是内容 bitmap；
   若文字与背景合成在同一 bitmap，不能通用移除背景而保留内容。

Android 官方 options bundle 能在 host 和 provider 之间传递显示信息并触发
onAppWidgetOptionsChanged，但没有定义 Xiaomi 的上述玻璃 key；完整私有契约
需要对照运行日志和已支持的 provider 实现确认。

## 下一步原型

先以设备上现有 Deadliner 2×2 实例为白名单：

1. 记录 provider 判断和模型生成，确认资格返回值如何进入 Flutter 的模型标记；
   核对 is_miui_widget ABI、库版本与入口指纹后，才实现白名单资格 Hook。
2. 仅与同尺寸的已支持组件尝试堆叠，检查加入、展开、切换、移除及桌面重启后的恢复。
3. 检查更新监听、PendingIntent 点击、集合滚动和尺寸 options；保证堆叠不更换
   原 appWidgetId，也不重复释放实例。
4. 独立验证透明背景的单卡玻璃，再加入堆叠动画。测试明暗模式、壁纸变化、
   滑动时的裁剪、背景采样与性能。

资格实验与材质实验应保留独立开关及按 provider 的配置；只有完成这些实验后，
才能决定是否扩大到第三方组件。本轮未验证任何普通组件的实际堆叠/玻璃效果。

## 二进制身份

研究文件来自 `reference/系统桌面_RELEASE-8.01.02.7722-260904-09221533-R.APK`。
用于上述地址/字符串结论的 SHA-256：

```text
libapp_launcher.so 4dc9fa82371dda8a0a8b9eff48301469d2f84752b52b71635b84e5fc0c47fa6e
libapp.so fb8b1f91eba2d9c43875f49bbded70cca462be341fc379b9864e7c6136d6e463
libnative_widget_sdk.so 1dff73f4729aa79e87e2353a0a81a82b0cb4d45b15d0a99a5946bcb8471908d1
libhyper_os_flutter.so 78769d2aad2dc2f2641b221dd3226e11051642d987572a3eef0e893d2df68cda
```

关键原始分析输出本轮保存在 `/private/tmp/mhm-launcher-debug-symbols.txt`、
`/private/tmp/mhm-is-miui-widget-disassembly.txt`、
`/private/tmp/mhm-deadliner-manifest.txt` 与 `/private/tmp/mhm-health-widget-manifest.txt`；
这些临时文件不是仓库依赖，后续应从对应二进制重新生成并核对。
