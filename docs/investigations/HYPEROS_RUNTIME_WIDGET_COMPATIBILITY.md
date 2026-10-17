# HyperOS Runtime 桌面：原生小组件堆叠与柔光玻璃

调查日期：2026-10-11。范围是现有设备的桌面
`RELEASE-8.01.02.7722-260904-09221533-R`，不是所有 HyperOS 版本的通用结论。
首次调查只实现设置菜单整理，没有安装堆叠或玻璃实验 Hook，也没有修改桌面数据库。
下述调查结论保留；后续首版实验实现见文末。

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

## 首版 Deadliner 白名单实验

用户明确选择 Deadliner 作为测试对象后，新增两个默认关闭的设置：
「系统界面 → 系统桌面 → Deadliner 小组件实验」中的堆叠和玻璃背景开关。
没有改 Deadliner 仓库、安装包或桌面数据库。

### 桌面端

`widget_hooks.cpp` 使用已验证的 schema C API，而非硬编码 Dart 函数地址：

- 跟踪 `ActivityInfo_get_meta_data` 与 `PackageItemInfo_get_meta_data` 返回的借用 Bundle。
  通过对应 package/name getter 的 24 字节 Option<借用字符串> 返回值匹配
  `com.aritxonly.deadliner` 的 CompactTaskGlanceWidgetReceiver；Capsule 和其他应用不匹配。
- `Bundle_clone` 将标记传递给独立副本；owner drop 释放借用标记，Bundle drop 释放副本标记。
  共享借用 Bundle 以引用计数追踪。原始元数据不被写入或合成。
- 只对白名单 Bundle 的 `Bundle_contains_key` / `Bundle_get_boolean` 覆盖
  `miuiWidget` 和 `supportBackgroundBlur` 读取结果。
  已检查 Result<bool> ABI：最低字节 0 表示 Ok，第 2 字节 1 表示 true。
- 堆叠实验请求 `miuiWidget=true`；玻璃实验请求这两个标记均为 true。
  **玻璃也需要 MIUI 资格，因此开启玻璃时也可能获得堆叠资格。**
  没有绕过桌面现有的尺寸、容量、拖放和模型构建限制。

注入前核对 `libhyper_os_schema_public.so` 文件大小 601400 字节和整文件
FNV-1a 64 位指纹 `f519ae5224ebc666`。这是版本匹配指纹，不是安全认证。
未知二进制或任一 Native Hook 安装失败时，不激活能力覆盖。
SystemUI 配置广播同时携带两个开关，并缓存到桌面自己的 cache 中供重启读取。
需要重启桌面，才保证重建已有 provider/model 的资格信息。

首次范围按 provider 白名单划定，不限某个 appWidgetId；建议先以现有 2×2 实例测试，
同一个 Compact provider 的其他实例也会得到资格标记。

### Deadliner 端

新作用域 `com.aritxonly.deadliner` 的 `DeadlinerWidgetGlassHooks` 在
AppWidgetManager 完整/部分 RemoteViews 更新入口工作。只有全部目标 ID 均属于
Compact provider 或明确指定 Compact ComponentName 时才处理，混合 provider 批次不处理。

基于本机 Deadliner 源码确认背景是 `TaskWidgetLayeredCard` 的独立 Image；
Glance 1.2.0 的 LayoutSelection 确认 `.appWidgetBackground()` 将其 ID 设为
`android.R.id.background`。Hook 克隆更新对象，遍历 portrait/landscape 与 responsive-size
RemoteViews 的叶子布局，在原动作之后追加 `setImageAlpha(0)`。
不隐藏整个组件、不移除图片视图，不修改文字/按钮或 PendingIntent。
因此组件刷新时也不会把背景重新画回来；原 provider 对象未被修改。
改变玻璃/模块开关时，从 Deadliner 进程请求 Compact provider 重绘以恢复/切换背景。
若 RemoteViews 结构无法处理，交回原始更新对象。

### 验证与测试流程

Native 白名单、开关组合及注册表的共享 owner/副本生命周期、分配器地址复用、
并发访问断言通过；使用 clang AddressSanitizer 和 UndefinedBehaviorSanitizer 检查。
Debug 与签名 Release 构建通过。Java/Kotlin 回归仍为 214 项通过、1 项已有
Bilibili 默认预设断言失败。本实验尚待用户安装验证真实堆叠与桌面材质。

1. LSPosed 保留系统界面、系统桌面，玻璃测试额外勾选 Deadliner。
2. 先只开堆叠实验，重启桌面；若旧实例资格未刷新，重新添加一份 Compact 2×2。
   拖到同尺寸已支持卡片上，确认加入/展开/切换/点击/更新/移出与重启恢复。
3. 再测试玻璃，首次安装后重启手机以加载 Deadliner Hook；开启玻璃并刷新组件。
   确认背景是否采用桌面材质，而非只有背景透明；检查明暗模式与壁纸变化。
4. 关闭堆叠资格前先把 Compact 移出堆叠；关闭玻璃后刷新/重启 Deadliner
   检查原图片背景恢复。

日志命令：`adb -s 5c7da359 logcat -v threadtime -s MHMDesktopWidget MHMDesktopClock`。
关键消息：`HOOK metadata capability bridge ready`、`METADATA Compact provider observed`、
`CAPABILITY miuiWidget=true`、`CAPABILITY supportBackgroundBlur=true`、
`BACKGROUND Compact alpha=0 layouts=...`。
若资格已覆盖但仍有拒绝/unknown widget type，继续定位 Dart 模型入口；
若背景透明但无桌面玻璃，继续定位 host 材质容器及 provider options 协商。
目前实现是可测试原型，不代表这两条后续路径已全部完成。


## 通用 Android 小组件版本（2026-10-11）

用户已确认上述 Deadliner 实验可用，继续将堆叠扩展至全部 Android AppWidget，
玻璃配置扩展至应用或 provider 粒度。上节是首版实验的历史记录，本节描述当前实现。

### 范围与配置

- “系统界面 → 系统桌面 → 允许安卓小组件堆叠”统一放开 Android provider 的 MIUI 资格。
  不改动尺寸、容量、拖放或实例持久化限制；关闭前先移出堆叠。
- 玻璃具有独立总开关；列表来自当前用户的 `AppWidgetManager.installedProviders`。
  支持应用和组件名称搜索、按应用全部选择、展开单独选择 provider。
  应用规则会包含以后新增的 provider；取消其中一个时转换成其余已安装 provider 的具体规则。
  同一 provider 的所有实例共享选择，不提供 appWidgetId 级别配置。
- 选择保存在 `android_widget_glass_targets`，每行一个包名或完整 `package/class`。
  原两个开关的 preference key 保留以兼容升级；旧玻璃配置自动迁移到 Compact provider。
  空的新配置明确表示无选择，不回退为 Compact。
- 列表标注所选应用的 LSPosed 作用域状态；主界面也显示动态应用作用域。
  系统界面和系统桌面继续作为必需作用域。新增应用需勾选对应作用域并重启应用；
  安装新版本后重启手机，配置改变后重启桌面以重建模型。

### Native 桥

元数据必须由原始 `Bundle_contains_key` 确认存在 `android.appwidget.provider`，
才注册为 Android provider。注册表保留 package/component 身份并传播到 Bundle 副本；
应用级 metadata、普通 Activity、MAML 等不因此被统一改写。
堆叠覆盖这些 provider 的 `miuiWidget` 布尔能力；玻璃匹配应用或完整组件后
覆盖 `supportBackgroundBlur` 和 `miuiWidget` 的布尔能力。Flutter 的后续尺寸与材质读取
也必须获得能力值，不能只允许 Rust 模型构建点。
`contains_key("miuiWidget")` 始终委托原函数，避免向分类读者虚构 MIUI 元数据键；
仅所选玻璃 provider 的 `supportBackgroundBlur` 键存在性允许合成。
因此玻璃选中的组件仍可能独立获得 MIUI 堆叠资格。

仍要求 schema 全文件指纹匹配。`Bundle_get_string` 返回的是拥有所有权的 40 字节
Option<RString>，不能复用此前 24 字节借用字符串 ABI。
因此配置广播使用已验证的 `Bundle_get_i32` 标量 API：0x5717c 反汇编确认结果低 32 位
为 Ok/Error tag，高 32 位为 i32 内容。规则为 ASCII，每 4 字节一个 word extra，
`mhm_widget_rules_size` 给出总长度，`mhm_widget_rule_N` 给出 words；上限 16384 字节。
只接受既有认证的 SystemUI 载体，缺失、超长或非法配置保持上一个有效版本。
本地缓存带版本头，原子替换；旧两字节缓存仍可迁移。

### Provider 背景处理

`AndroidWidgetGlassHooks` 对所选应用安装 AppWidgetManager 完整/部分更新 Hook。
混合 provider ID 更新批次按选择分开，未选 ID 使用原对象且跳过背景 Hook。
原 RemoteViews 不被修改；按横竖屏、自适应尺寸和 ViewGroupActionAdd 嵌套布局遍历副本。

通过资源 XML 与 RemoteViews 的动态 root ID 识别实际视图：
- 外层布局有明确 root ID 时，只将其 View background 变透明。
- `android.R.id.background` 的背景节点清除 View background；仅 XML 确认为 ImageView 时
  追加 `setImageAlpha(0)`。这覆盖 Glance 动态 root ID 的 appWidgetBackground 图片层。
- 不改变任意内容 ImageView 的 alpha，不清除文字/按钮、点击或 PendingIntent。
  没有可识别背景、布局包含未解析 include、背景与内容合并为位图等情况保留原输出，
  可能遮挡桌面玻璃，仍需具体 provider 适配。
- 配置修改后请求本应用各 provider 更新，包括取消选择的 provider，以恢复其原背景。
  某些 provider 忽略 APPWIDGET_UPDATE 时需要它自身的刷新或重新添加组件。

背景透明发生在 provider 更新侧，对该 provider 的其他 host 实例也可能生效；
柔光玻璃材料由 HyperOS 桌面提供，模块没有替代第三方 host 的材料实现。

### 验证

通用 native 策略测试覆盖应用/provider 匹配、规则替换失败、旧缓存迁移、新空选择、
元数据共享 owner/副本身份、地址复用及并发访问，ASan/UBSan 通过。
Java 选择策略新增 5 项测试，验证应用范围、组件范围、包名前缀隔离、非法输入、
确定性保存及空选择。Debug 与签名 Release 编译通过。
通用版本用户已反馈可用；本次二级菜单及选择器分类修复仍待安装后真机验证。

日志仍使用 `MHMDesktopWidget`，消息为 `METADATA provider=...`、
`CAPABILITY ... provider=...`、`CONFIG stack=... glass=... rules=... bytes`、
`BACKGROUND package=... layers=...`。layers=0 表示没有识别到可清除的背景层。


### 添加页过滤回归与二级菜单（2026-10-11）

用户反馈开启全局堆叠后，“智能助理 → 添加小组件 → 安卓小组件”缺少应用组件。
连接手机确认 `dumpsys appwidget` 中 Deadliner Compact / Capsule provider 均存在，
添加页截图仍显示时钟、计算器、笔记、天气等内置项目；此次相关 Hook 日志为空，
所以不能把静态调用路径分析视为已完成的真机验证。

首个选择器修复仅允许 `Bundle_get_boolean` 的两个 Rust 调用点获取合成 MIUI 资格：
`0x6c6c78`（`MiuiWidgetUtils::is_miui_widget`）和 `0x6f7ef0`
（`MiuiWidgetCompat::create_item_info`），同时停止伪造 `miuiWidget` 键存在性。
用户安装并重启后确认：添加列表恢复，但尺寸不再统一、柔光玻璃失效。
这个方案不能保留 Flutter 端的尺寸与材质读取，已撤掉调用地址白名单。

第二次修订恢复完整布尔能力，仅保留原始 `miuiWidget` 键存在性。
用户确认尺寸与玻璃正常，但添加列表再次缺失，证明键存在性不能单独解决过滤。
该试验记录保留，不能将它当作有效的列表修复。

第三次修订保留第二版完整能力桥，另在 Dart 添加列表局部处理分类读取：

- 支持的 `libapp.so` 全文件长度 29382056，FNV-1a64 `a9c299ea503f9171`。
- `0x1a83dd8` 从当前 ItemInfo 读取 `isMiuiWidget`，随后解压 bool 指针；
  `0x1a83de0` 为 `tbnz w0, #4, 0x1a83f20`，对应 Android 分支。
- 仅在这次分类读取处使用 Dart 的 false（`NULL_REG x22 + 0x30`）；
  不修改 ItemInfo / provider metadata，也不修改任何全局 Dart getter。
- 局部入口在兼容功能开启、schema 能力桥就绪、精确 Dart 指纹和 16 字节入口匹配，
  且 native detour 安装成功后才启用。关闭开关继续执行原分支。
- 无 C ABI 调用，不改 SP / Dart x15，不改 SIMD / NZCV；仅修改局部 x0，
  使用该位置非活跃的 x16/x17 作跳板 scratch。主机 ARM64 直接执行生产汇编入口，
  96 项 enabled / bool / NZCV 组合验证参数、shadow stack、NULL_REG 和 SIMD 保留。
- 安卓列表可包含带 MIUI 标记的 AppWidget；现有列表去重和隐藏规则继续执行，
  桌面尺寸、类型构建与材料读取继续获得完整 MIUI 能力。

分析记录：Dart Runtime 自报 3.10.1，snapshot hash `5d85d85296400e9b3992193deed317c9`。
`dae v0.1.13` 产生 parse drift，弃用其输出。使用 `unflutter v0.5.9` 的相邻 Dart 3.10
布局做本地分析试验获得对象池/指令数据，再用 NDK llvm-objdump 与 ELF 原始 bytes
核对目标读取和分支。跨版本不能套用该地址，必须重新核对；本次局部 Hook
用户安装第三版后确认列表仍未恢复；入口的运行状态及实际过滤语义尚未得到真机验证。

第四次修订修复原生库指纹校验对 APK 内库路径的支持。真机只读检查确认：

- 手机已安装的第三版 APK SHA-256 与本地第三版完全一致。
- 桌面 `extractNativeLibs=false`，`lib/arm64` 目录为空，Dart 库位于 APK 内。
- 原实现对 `base.apk!/lib/arm64-v8a/libapp.so` 直接 fopen，无法校验这种路径。
  这是确定的实现缺陷；日志为空，尚不能声称已观察到真机具体拒绝记录。
- 校验器解析 ZIP 中央目录及 local header，限定未压缩、未加密的条目，
  对条目原始 bytes 校验完整长度和 FNV 指纹。普通文件路径仍受同样校验。
  不放宽 Dart 指纹和入口机器码要求。
- ASAN/UBSAN 测试用实际桌面 APK 校验成功，并覆盖普通文件、缺失条目、
  修改内容、压缩条目和截断 ZIP 的拒绝；生产汇编 96 项 ABI 测试继续通过。

该版本只修复加载校验缺陷，不能据此认定列表问题已解决。
后续需先确认 PICKER 安装记录，再验证候选分支与列表实际过滤的关系。

“玻璃应用与小组件”改为系统桌面页内的二级菜单，参考手势提示线页面的胶囊搜索栏、
应用图标列表、下拉刷新、边距与返回行为。按应用展开具体 provider，保留应用全选、
取消单个 provider 时转换规则、不可用旧选择清理及作用域提示。
模拟器已验证系统桌面入口可进入独立页面，搜索栏、应用图标和 provider 数量正常显示；
Release 编译与 APK v2 签名验证通过，Java 5 项选择策略测试无失败。


### 默认全部开启与可选排除

柔光玻璃总开关保留原值；开启时所有应用及未来新增的小组件默认匹配。
二级菜单保留应用/具体 provider 粒度，改为关闭例外项，去掉 APP 作用域文案。
首页不再把这些 provider 当成必需作用域。桌面材质仅要求 SystemUI 设置桥与桌面
作用域；provider 进程内的背景清理仍只会在用户已经勾选的作用域内运行。

新规则键 `android_widget_glass_rules_v2` 默认为 `*`，`-package` / `-component`
排除对应应用或 provider，排除优先于通配符。旧版选择白名单不转成排除，升级后
所有应用恢复默认匹配；总开关不自动打开。原生缓存版本 3 保留新规则，旧缓存
版本 1/2 在校验成功后迁移为 `*`。Java 选择策略、SystemUI 传输、原生匹配和
模块设置导入/导出使用一致的新键与规则。

验证：7 项 Java 策略测试无失败，原生规则与缓存迁移 ASAN/UBSAN 测试通过，
Release 构建及 APK v2 签名校验通过。模拟器可以进入模块系统界面页，但界面
检查期间出现 ANR / UIAutomator null root，未完成二级菜单的可视验证；不能据此
认定新二级菜单已经通过运行验证。真机玻璃效果及选择排除仍待安装验证。


### 两种视觉材质的进一步调查

用户观察：当前安卓组件玻璃与桌面 2×2 时钟玻璃的色调表现不同。
现有模块只合成 `supportBackgroundBlur` 能力，未选择材质类型或修改色调参数。
提取桌面包存在 `GlassWidget` / `HyperMaterialWidget`、`usingFolderGlassStyle`、
`notifyAllWidgetBlurOptionsForMaterialChange` 及 `getMaterialStyle` 相关字符串。
Rust 符号 `MaterialStyleSettingsCallback::get_material_style` 位于 `0xbaa7a8`；
原始 ELF 指令确认它调用 `settings_secure_get_i32`，键地址 `0x28b076`、长度 14，
该地址原始 bytes 为 `material_style`。这是桌面支持材质样式协商的证据，不能
仅凭这些名字断言恰有两种预设，也不能确认 2×2 时钟对应哪个枚举或参数。

下一步应对比 2×2 时钟（可能为 MAML）与 AppWidget host 的玻璃构造参数，
确认局部样式入口后再增加选择项；不应修改全局 `material_style` 来影响整个桌面。


### Deadliner 玻璃样式实验开关

新增默认关闭的 `deadliner_clock_glass_experiment`，系统桌面页内可启用，仅在
柔光玻璃总开关已开启时可操作。实验采用现有经过指纹校验的 schema bridge：
仅对 `com.aritxonly.deadliner` 的已选 Android provider，向
`usingFolderGlassStyle` 的 contains 返回 true、boolean 返回 Ok(false)。
其他包、其他 metadata 键以及未匹配/已排除的 provider 不改动；关闭开关恢复原读取。
不修改全局 material_style，不增加 Dart 地址 Hook，不增加实验命中日志，不抓真机 log。

候选来自 Dart AOT 的 `usingFolderGlassStyle` 字符串读取附近，缺省值指令形似
Dart true；试验目的是观察 false 是否能切换外观。尚未验证实际读取是否经过
该 schema bridge，也未确认此分支与 2×2 时钟材质的对应关系。不能将设置保存、
测试或编译成功当成材质切换已经生效。

开关通过现有 SystemUI 认证设置广播传递，桌面使用独立私有缓存保留状态。
设置读取、保存、重置和导出均包含开关；原生策略测试覆盖 Deadliner 两类 provider、
其他应用、总开关/实验关闭、其他键及无效 identity 的隔离。


实验 1 用户反馈：外观与此前无变化。不能认定材质已切换。
实验 2 将识别来源改为 `AppWidgetManager_get_app_widget_options` 的返回 Bundle，
通过同一个 manager + widget ID 查询 provider，仅为 Deadliner 登记独立的 options
identity；普通 provider metadata 能力桥保持原有规则。Bundle clone/drop 同时维护
独立 options registry，不修改原生 options 内容、不持久化系统 widget options。
关闭实验/玻璃后仍原样读取，重新启动桌面会重新取得 options。

静态 ABI 证据：SDK `0x11d2b0` 调用 get_options(manager, int32 ID)，返回 x0 Bundle；
`0x11cd3c` 调用 get_info，同步检查 x0 bit0，再从 x1 取 owned info。
桌面 `0x6c6f4c..0x6c6fc0` 同样验证该 Option 返回值，get_provider 返回 owned component，
get_package_name 返回 borrowed (pointer,length)，并明确调用 ComponentName_drop 和
AppWidgetProviderInfo_drop。schema 的 ComponentName_get_class_name 原始导出代码
也验证 borrowed RStr 两寄存器 ABI。SDK 长度 2679408、FNV `67fdc4bf9c2e1b24`，
仅精确匹配后安装 options Hook。没有新增命中日志，没有抓真机日志。

此修订验证的是更合适的 options 读取入口；与时钟材质对应关系仍待用户观察。


### 实验 2 无效果后的对象池校正（实验 3）

用户确认实验 2 仍没有材质变化。本轮静态核对发现 unflutter 的 PP 注释用
`byteOffset / 8` 直接当条目索引，但导出的 ObjectPool entry 0 offset 为 0x10。
因此此前 Dart PP 字符串注释整体错移 2 项，不能继续按旧标签解释调用。
新增离线 `tools/analysis/correct-dart-pool-labels.py` 按导出 entry offset 重建注释，
保留原始反汇编不覆盖。它只校正标签，不声称验证了全部 snapshot class layout。

可直接核对的例子：`0x18fb92c` 的 LDR 偏移加上前一条 ADD 等于 0x29380，
对应导出 entry 21102（offset 0x29380）的 usingFolderGlassStyle；其默认分支
`0x18fb93c` 是 `add x0, x22, #0x30`（false），随后存入解析结果字段 27。
前一处默认 true 的 `0x18fb8fc` 属于 miuiAutoScale；此前的解释错误。
校正后的错误分支文字为 parseMetaInfoFromAppWidgetProvider error: meta is null，
确认这是 provider metadata 解析，第二版改到 options 的判断也不成立。

实验 3 撤销独立 options Hook / registry / SDK 安装等待，回到经过 schema 指纹
校验的 metadata bridge，仅对已启用玻璃的 Deadliner provider 将
usingFolderGlassStyle 的 presence 返回 true、布尔结果返回 Ok(true)=0x100。
关闭实验恢复原读取。前两次实验强制 false，与实际默认 false 相同，不能用
它们的无变化反推系统没有第二种材质。此版采用非默认 true；外观效果及是否
对应 2×2 时钟，仍需肉眼对比，不新增或抓取日志。

此前所有依赖旧 PP 注释的 Dart 命名/功能映射应重新校正；尤其不能仅凭旧的
ImageFilter::initGlass 标签判断函数是渲染构造入口。


### 已验证材质扩展到全部应用

用户确认实验 3 的材质变化正常后，将 `usingFolderGlassStyle` 选择开放给全部
Android AppWidget provider。设置名称为“时钟样式柔光玻璃”，保留旧偏好键与
广播 bool 键以延续已经开启的实验状态；新增 `android_widget_clock_glass_rules`
独立规则，默认 `*`。开启后所有应用默认使用时钟样式，用户可在“玻璃应用与
小组件”中排除整个应用或具体 provider，切回默认材质。该选择按 provider
类型生效，不支持同一 provider 的不同 widget ID 使用不同材质。

玻璃背景与材质规则独立。只有模块、玻璃总开关、该 provider 的玻璃规则、
时钟样式总开关全部允许时才覆盖材质 metadata：选中材质返回 Ok(true)，
排除材质返回 Ok(false)，presence 为 true；关闭玻璃或时钟样式总开关则恢复
原读取。不改变现有 miuiWidget、supportBackgroundBlur 或 picker 路径。

SystemUI 使用相同的已验证 i32 packed carrier 发布两组规则，每组上限 16 KiB。
桌面先验证两组规则，再统一更新，避免非法规则导致部分配置更新；材质缓存
使用临时文件与 rename，支持旧版单字节实验缓存迁移为默认全部选择。偏好
保存、导出、恢复支持新规则；不新增材质日志，也不抓真机日志。修改设置后
需重启桌面重建 provider 模型。全应用实机外观仍待安装后验证。


### 应用下拉、自定义类型弹窗与已添加实例

“玻璃应用与小组件”使用两个页签。应用页每行提供不启用柔光玻璃、色调柔光
玻璃、通透柔光玻璃、自定义四项。选择自定义弹出受控草稿 Dialog，按 provider
类型选择前三项；保存统一提交，取消/返回不改规则。实例页只显示当前用户
com.miui.home 的 1024 host 已绑定且有 provider 的 Android AppWidget，每行提供
前三项，并允许移除实例覆盖恢复继承。服务中的绑定记录可能含桌面尚未清理
的绑定，不能据此声明读取了桌面格子位置或渲染截图。

新偏好 `android_widget_glass_styles` 按 target\tmode 分行编码，0=关闭、1=色调、
2=通透、3=应用自定义标记。target 为 package、完整 provider 或 provider#ID。
优先级为实例 > provider > 应用 > 旧玻璃/样式规则；自定义标记不覆盖未指定
的新 provider。统一应用选择清除其 provider 覆盖，保留实例覆盖。UI、Java
provider 清理与 native metadata 使用同一语义；旧两组规则继续作为升级回退。
材质值 false 对应旧默认（色调），true 对应用户已验证的通透样式。

SystemUI 挂载只接受模块真实包名与 UID 的带发送者身份广播，通过已有 DUMP
权限调用 appwidget binder 的 dump，在后台读临时文件后立即删除。只返回筛选
后的 ID/provider，不返回完整 dump，不新增日志，不调用 su，不写桌面数据库。
模块请求有 8 秒超时，失败作为错误状态展示，不伪装成空桌面。解析测试同时
覆盖主用户/其他用户、智能助理 host、未绑定 ID、权限失败与两个同类型实例。
Android 16 以下不注册或调用此入口。

实例信息沿 AppWidgetManager_get_app_widget_info → AppWidgetProviderInfo 克隆 →
AppWidgetProviderInfo_get_providerInfo → ActivityInfo 克隆 → metadata Bundle 传递。
Option 返回寄存器 ABI 在桌面 0x6c6f4c/0x6e0fb4 和 SDK 0x11cd3c 校验为低位
错误 tag + x1 owned info；SDK 0x1231d4/0x12957c 使用 get_providerInfo 的 x0 指针，
并交给 owned ActivityInfo 包装/释放。无 Rust 字段布局读取，也不篡改 Bundle。
只在原 schema 指纹与 SDK 全文件指纹均匹配时安装实例入口；生产 ID 的 Hook
最后安装，部分安装不能产生实例标签。各 owned drop 删除标签，未知来源
clone 清理复用地址。若两个实例共享同一 borrowed metadata，则退回类型规则，
不把其中一个 ID 覆盖泄漏给另一个。原生测试覆盖 ID 传递、释放、复用与共享
metadata 隔离；是否所有桌面实例路径都经过这条读取链仍需真机验证。

应用 UI 复用项目的 Miuix 0.9.4-rc01 WindowDropdownPreference 与玻璃 Dialog
封装，保持原 Scaffold、主题和 inset。色调/通透是产品名称，未新造渲染层。


本轮验证：15 项相关 Java 测试通过，原生策略/metadata 生命周期测试在 ASan 与
UBSan 下通过，Release 编译与 APK v2 签名校验通过。独立只读 Android 16 模拟器
验证应用四项下拉、类型三项下拉、取消不落盘、保存两种类型的不同材质、进程
重启后选择恢复；最终弹窗显示可用的 targetCellWidth/Height，不为名称已区分
的小组件重复展示实现类名。真机实例 inventory 权限和材质链路尚未验证。


### 2026-10-12：运行后安卓添加列表再次缺失

真机故障截图只剩内置 gadget，`dumpsys appwidget` 中第三方 provider 仍存在。
在同一桌面进程返回智能助理、重新进入安卓列表，日志记录 `open_classic_picker`，
未记录重新枚举/构建列表；堆叠里的 Deadliner 仍收到 MIUI 更新。用户手动重启
桌面后确认列表恢复。重启日志确认 metadata bridge 与 Dart picker detour 均
安装成功。提取当前桌面 APK，Dart/Rust 文件大小和 FNV 与已有精确配置一致。
这说明组件未从系统注销，但不能仅靠现有日志确认后续过滤的具体调用点。

测试修复：让 Rust `MiuiWidgetUtils::is_miui_widget` 在 `0x6c6c78` 的布尔
读取保留 provider 原始声明，避免把桌面兼容能力当成应用原生分类。只在
Rust launcher 全文件指纹与该位置 8 字节指令均匹配时启用。
`MiuiWidgetCompat::create_item_info` 与所有 Flutter metadata 读取继续获得
完整尺寸/堆叠/玻璃能力；既有 Dart picker 分支保留。与早先仅对两个 Rust
调用点提供能力的失败方案不同，本次只排除一个分类调用点，不限制 Flutter。

原生策略 ASan/UBSan 与 picker 汇编 96 项 ABI 验证通过。此修复仍须用户安装
后验证添加、绑定、堆叠、材质，以及运行一段时间后重新打开列表；当前不能
声称间歇性缺失已被真机验证修复。故障/恢复日志临时保存在
`/private/tmp/mhm-picker-reopen-live.txt`，未写入仓库。

### 2026-10-12：第二次复现采集结果

用户反馈上述测试修复仍会在运行一段时间后丢失列表，因此尚未解决问题。
本地源码撤销 `0x6c6c78` 的分类读取例外；手机仍保留已安装的测试版本，
本轮没有覆盖安装。13:55 手动重启后的桌面 PID 为 3880，日志确认该测试版本
的 metadata bridge 与 picker Hook 安装成功。之后多次打开安卓列表，用户反馈
暂时无法再次复现，未捕获从完整到缺失的转换，不能判定问题消失。

本次采集已停止，日志保留在 `/private/tmp/mhm-picker-refresh-v2-live.txt`
和 `/private/tmp/mhm-picker-refresh-v2-focused.txt`；故障时系统 provider 状态
保留在 `/private/tmp/mhm-picker-refresh-v2-appwidget.txt`。现有日志没有明确的
列表重建计数，不能以缺少枚举日志推断必定没有重新枚举。后续复现应在重启
前保存日志及 provider 状态，再检查刷新和过滤路径；当前不发布新的修复包。


### 2026-10-15：改从澎湃组件目录接入的调查方向

用户反馈桌面更新后安卓组件列表再次缺失，提出让所有 Android AppWidget 进入
澎湃组件目录，放弃在安卓列表中维持分类修补。当前 reference 只有 7722 版本
APK，未获得此次更新后的二进制，不能核对新版本准入条件或发布适配 Hook。

现有实现已经给普通 provider 提供 MIUI 堆叠/尺寸/材质资格；目录接入与资格
覆盖是不同问题。旧版已定位的 buildAppWidgetsItems 分支是安卓列表的分类读取，
不是已确认的澎湃目录白名单。现有局部分支 detour 严格匹配旧 Dart 全文件和
入口指令，不能复用到更新后的未知版本。是否因版本拒绝、目录过滤或缓存
失效导致本次缺失尚未判定；不放宽指纹来猜测兼容。

旧 Dart 对象池中 “DROPPED (not in whitelist)” 与 Action_ReflectionAction /
ViewWidgetCovert 相邻，指向 RemoteViews 动作支持，不能作为目录应用白名单的
证据。另有 “not in MAML white List, skip MAMLIcon” 文案，属于 MAML 图标路径；
Rust set_white_list / KILL_SKIP_HOME_WHITELIST 属于进程/最近任务，也不是组件目录。
目前没有确认只去掉一个统一应用白名单便能列出全部 provider。

优先路线是定位新版澎湃目录的数据源和本地 AppWidget 条目构建入口：若目录
枚举本地 provider 后再过滤，局部放开准入并保留系统分组/预览/去重；若目录
仅接收商城或云端描述，需要将本地 provider 转成系统已有的 AppWidget 目录项，
合并数据源而非仅将白名单判断改为 true。不得将 RemoteViews 伪造成 MAML，
须保留 provider、用户/profile、配置 Activity、appWidgetId、绑定/更新/删除语义。

实现后需验证首次进入与返回目录、provider 安装/卸载/变更后的刷新、运行一段
时间后目录保留，以及添加/配置/点击/更新/堆叠/玻璃/移除和进程重启。目录路径
只在初始化及 provider 变更时构建/增量更新，不加入轮询或每次打开时的 IPC 全量
重建；动态数据不永久缓存。现有资格桥依赖 pickerReady，切换路线时必须同步
调整激活条件及关闭后的原值恢复，不能只删掉安卓 picker Hook。

本轮只有旧样本与代码检查，未修改生产 Hook、编译或安装 APK。


### 2026-10-15：7748 更新包与前台适配提示的实机证据

用户提供 Downloads 中 7748 APK，并连接手机停在适配提示页。本轮只读保存
activity、appwidget、package、当前 logcat 与截图，未清日志、重启、安装或改库。
材料目录 `/private/tmp/mhm-launcher-7748/`。实机桌面 versionCode 801027748，
版本名与用户提供文件一致；未对实机 APK 全文件做同一性确认。

| 文件 | 长度 | FNV-1a64 |
| --- | --- | --- |
| 新包 libapp.so | 29378704 | 440922566a4fe1f5 |
| 新包 libapp_launcher.so | 16220456 | 53c28844c689a8a1 |
| 新包 libnative_widget_sdk.so | 2679408 | e3273af9c63e9922 |
| 实机 /system_ext/lib64/libhyper_os_schema_public.so | 601400 | f519ae5224ebc666 |

APK SHA-256 为 70c986b426b2319ba1bbb75f6a3903a95f63b27804017f91e046e5e6a30136c0。
新包不包含独立 schema 库；该库由系统提供且指纹仍匹配原桥。Dart/Rust/SDK
则均变化：旧 Dart picker 指纹必定拒绝此文件，SDK 实例标签入口也不能按旧
指纹激活。metadata bridge 的 active 与 pickerReady 独立，不能仅由 picker
拒绝就推断所有 MIUI 能力都已关闭；这种组合有造成分类不一致的风险。
当前 logcat 窗口没有启动安装记录，未直接观察本次进程安装结果。

unflutter 解析新包自报 Dart 3.10.7、compressed pointers、snapshot hash
5d85d85296400e9b3992193deed317c9，生成 102348 个函数。buildAppWidgetsItems
含国际负一屏过滤与 needsHideFromPicker 路径；旧 MIUI 分类读取的候选对应新
0x1a86990/0x1a86994，分支为 0x1a86998 的 TBNZ W0,#4。LLVM 原始 bytes
核对该分支与随后 12 字节；此为研究地址，未安装 Hook，也不是目录白名单证据。

截图对应智能助理 PickerHomeActivity，内部 PickerDetailFragment 的
pa_picker_detail_app_widget_in_dev（0x7f1303e5）显示“正在努力适配中”。
静态路径为 appDoUnableInMarket(false)，由详情状态错误码 32006/32007 对应
下架/未在商城上架的分支进入。PickerDetailService.getPickerDetail 使用 Retrofit
POST component/store/impl/tail，Repository 将 ApiException.getCode 写入响应状态。
不能将这一页面直接等同于桌面 Dart 安卓列表为空；日志没有保留触发该页面的
网络响应，因此未确认本次具体错误码或排除其他前置原因。

系统 dumpsys appwidget 中 Deadliner Compact/Capsule provider 仍注册。智能助理
25.40.34.00-09031710 的目录详情依赖商城响应；已找到 LocalWidgetCountHelper
和 provider metadata 读取，但没有证明放开一个本地白名单可自动生成商城条目。
MaMlAppWhiteList 仅列出部分系统应用，尚无它控制此 AppWidget 目录的证据。

因此新的实现方向为本地 provider -> 现有目录项适配 -> 复用 AppWidget 添加链路，
需继续核对智能助理目录/详情模型与桌面交接参数；若需要在智能助理注入，只
安装该功能精确 Hook，不顺带安装 SystemUI/桌面通用 Hook 或桥接服务。目录枚举
放在既有后台加载阶段，按 provider/profile 区分并去重，在包变更时失效，不用
轮询或绘制回调做扫描。原有远程组件仍保持原路径。

本轮只完成二进制差异、页面归属与目录数据源调查，没有发布功能修复或将候选
地址加入生产配置。旧列表间歇消失与本次商城详情提示不能未经验证合并为同因。


### 2026-10-15：本地 provider 接入澎湃目录首版

新增 LocalWidgetCatalogHooks，复用“允许安卓小组件堆叠”开关，新增智能助理声明
作用域及作用域面板项。仅智能助理主进程安装六个精确目录/点击/详情/添加 Hook；
不启动 Context/resource 通用 Hook、全局玻璃适配或桌面广播桥。目标模型取自实机
智能助理 versionCode 2540340，后台读取包版本不匹配时不生成本地目录。

首页原有 PickerCardListResponseCallbackWrapper 的 shouldCacheFirstPage=true 路径
代理实际 PagingResponseCallback：在系统缓存处理之后、交付首页之前合并本地
AppGroup Card。所有本地 Card/详情使用 mhm-local-widget: 标识，重复交付时去重，
云端条目保留；本地项不进入原商城磁盘缓存。网络失败且本机 provider 可用时
交付本地目录，空目录或适配失败保留原失败回调。未修改搜索、分类或后续分页。

本机已注册 provider 按当前用户、完整 ComponentName 去重，按应用分组，每卡
最多四个应用。使用系统 AppGroup 布局/图标及详情布局，不新增 renderer 或模糊层。
点击本地卡片期间仅向既有 detail URI 增补局部 groupId 标记；真实 appPackage
不变，不替换远程应用详情。对应 Repository.getPickerData 由后台生成真实
PickerDetailResponseWidget 与 Wrapper，并在原 launchOnUI 路径的主线程恢复
Continuation；取消的 Job 不恢复，过期/关闭后的结果不继续覆盖有效目录。

添加仍使用原 getAddTargetItemInfo 与添加/配置/绑定链；只为本地记录补回精确
providerInfo/profile 和 MIUI 资格。不伪造 appWidgetId、MAML、更新 action 或暴露
刷新契约。标准绑定权限、provider 配置 Activity、更新和删除继续由系统完成，
是否所有 provider 都能正常添加/配置仍需真机回归。

第一版仅枚举当前用户，不混合工作资料/其他用户的相同组件。优先使用
AppWidgetProviderInfo.targetCellWidth/Height；缺省时从像素 minWidth/Height 转 dp
后估算网格。目录样式复用系统固定尺寸表；非标准尺寸使用 2x2 目录卡片，保留
真实 providerInfo 交给原 host，不能声称任意尺寸已完整适配。

性能边界：provider 枚举、应用标签/图标读取、JSON 模型转换与分组均在单线程
后台执行；线程按需创建、空闲十秒退出，没有轮询。只缓存不含 View/Context 的
provider 数据，不缓存已经绑定 View 的 Drawable/Card。Context 为弱 application
引用；包安装/卸载/变更及相关设置改变使数据失效，失效时不主动做全量枚举。
回调去重并丢弃被后续请求替代的交付；UI 热路径仅做本地标识和缓存字段读取。
关闭开关立即停止后续目录注入，已显示列表在下一次目录加载时恢复原数据。

新版桌面 Dart profile 新增 29378704 / 440922566a4fe1f5、局部分支 0x1a86998，
保留旧 profile 的精确全文件/入口校验。新增 SDK 指纹 e3273af9c63e9922 允许实例
标签链，已核对旧文档三个 ABI 调用点机器码与旧包一致；schema 指纹不放宽。
SDK 两个允许指纹一次读取校验，避免相同长度库重复计算整文件指纹。未知版本
仍拒绝地址 Hook。保留安卓旧目录的局部保护，但本地澎湃目录不依赖它返回候选。

验证范围：目录标识/尺寸策略与声明作用域三项 JUnit 通过，native 策略及
metadata 生命周期 ASan/UBSan 通过，picker 汇编 96 项 ABI 通过；新旧实际 SDK
指纹接受、错误指纹拒绝和新 Dart 指纹验证通过。APK ZIP/压缩/损坏拒绝回归通过。
这些是主机测试与静态 ABI 检查，不等同于手机目录显示、配置、堆叠或材质验证。


最终 Release/lintVital 与 APK v2 签名验证通过，相关 JUnit 三项通过。反编译最终
APK 确认 target 的 kotlinx.coroutines.n1/n1$a 名称保留，CoroutineSingletons 名称
仍在运行时拼接，没有重演模块 Kotlin 类名被 R8 重写到目标 ClassLoader 的问题。
随后用独立 app_process + PathClassLoader 在手机只读加载已安装智能助理：真实
构造器、Continuation/Job 接口、Gson Card/AppGroup 模型及 provider 字段校验全部
PASS；运行后删除临时 dex。没有安装模块、运行真正目录 Hook、重启系统进程或
改变现有 widget。类/模型校验不等同于界面/生命周期验证。

交付 `HyperModifier-local-widget-catalog-7748.apk`，SHA-256：
3cf14f1d85b8da9f521fdf28192a3e60575a5b195a7d5a4e47ac041e3fb593dd。
安装后需新增智能助理作用域、保持系统桌面/系统界面作用域，开启已有堆叠开关并
重启智能助理与系统桌面。检查澎湃目录首页“本机小组件”应用分组、Deadliner
两类 provider 的详情/预览/添加，随后配置、点击、更新、堆叠、两种玻璃和移除。
联网/离线、退出重进、安装卸载应用与关闭开关重开目录也需回归。未提交 Git。


### 2026-10-15：点击崩溃与目录没有合并的修正

用户截图表明首版插入了独立 AppGroup 卡片，未进入原有“支持小部件应用”扁平
列表。实机 crash buffer 的两次智能助理异常（11:10:49 / 11:10:55）均为
NullPointerException -> v8.a.a(ArgumentParser.kt:188) -> g8.a.s(AppGroupCard.kt:206)。
原因是自建 AppGroupEntity 没有 BasePropertyEntity.title，点击解析强制非空。
之前仅验证构造器和模型反序列化没有覆盖点击协议，不能据此声称目录可用。

撤掉 AppGroup 卡片、首页分页 callback 代理及 URI groupId 标记。接入点改为
PickerAppListRepositoryKt.traversalAppListDataSetLocalIcon 的现有后台加载阶段：
首页请求在 List 内携带局部分页标记，后台图标协程准备原生 PickerListAppData，
仍由 PickerMainViewModel.convertAppListToCards 生成 cardType=20 的标准扁平行。
标题、行布局、点击及分页展示均复用系统原链路，原商城缓存不写入本地条目。

分页原始数据计数与待转换行独立保留，避免本地应用数量改变远程 hasNext。
首个空云端页面有本地数据时用一个真实本地记录进入原标题/成功分支；后续
远程页面排除首页已经列出的本机 provider 应用，减少跨页重复。仅首页请求
适配，搜索及其他组件分类不额外插入分组。重复 package 合并，当前用户边界保留。

详情使用真实 appPackage，应用概览返回后按完整 provider 身份合并本地记录；
远程失败且本地可用时提供本地详情。已有 MAML/付费记录及授权字段保持原数据，
指定 implUniqueCode/productId/groupId 的单项请求不拦截。相对/短 provider 类名
规范化后去重，已有远程 provider 不重复添加。数据恢复通过目标自身 intercepted
Continuation 的 dispatcher，取消也交回 CancellationException 让原 finally 清理，
不再简单丢弃取消的 continuation。异步请求有设置/包变化 generation 保护。

智能助理 AppWidgetItemInfo.parseWidgetMetaData 只为本机登记的 provider 补充
isMIUIWidget=true，getAddTargetItemInfo 补回精确 providerInfo/profile；不改安装包
metadata、商城/服务器资格、更新 action、PendingIntent 或已分配 appWidgetId。
这是智能助理本地模型的 MIUI 资格兼容，不能等同于服务端收录或任意 provider
实现了全部 MIUI 专用接口。原桌面 native 堆叠/尺寸/材质桥继续复用。

性能检查：去掉额外卡片构造、Gson 模型转换和首页 callback 额外重放。枚举、
图标、详情合并继续在原 IO 阶段/按需后台线程；UI 转换只传递准备好的列表。
冷枚举在后台串行并复用 snapshot，主线程及资格读取不等待该锁。新增资格覆盖
只是 cached component 查询与布尔字段写入，没有绘制 Hook、轮询或常驻采样。
这是结构检查，未测量帧率、耗电或函数耗时，不能报告真机性能改善。

实机独立 app_process 校验扩展为调用真实 convertAppListToCards 生成扁平 Card，
再调用此次崩溃的 v8.a.a 点击参数解析器，PASS；构造器及协程 ABI 仍 PASS。
这些不运行应用真实 Activity、目录 Hook 或配置流程，仍需安装后的端到端回归。
新增远程分页计数/空首页/尾页与 provider 别名去重回归，相关 JUnit 共五项通过。


最终扁平列表修复 Release/lintVital、五项相关 JUnit 与 APK v2 签名校验通过。
最终 APK 反编译确认 BaseContinuationImpl/ContinuationImpl/Result$Failure 等目标
Kotlin 类名仍动态拼接，原生列表与 metadata 方法名保留。编译类时间晚于最终源码。
独立进程验证完成后删除手机临时 dex。未安装模块、未提交 Git；真实目录注入、
实际点击详情和添加仍需用户安装后验证。

交付 `HyperModifier-local-widget-unified-list-fix.apk`，SHA-256：
59d2a0e3e708d1de23500be648232df6154c10852d551bd6958a5c87cf432c08。
安装并重启智能助理/系统桌面后，检查原有“支持小部件应用”扁平列表包含本机
provider 应用、同一应用不重复分组，点击后可进入合并详情并完成添加/配置。


### 2026-10-15 第二次点击崩溃：修正详情 Flow 返回值约定

用户安装扁平列表版本后仍崩溃。此次 11:31:54、PID 30185 堆栈为
PickerDetailResponseWrapper cannot be cast to kotlinx.coroutines.flow.d，发生于
PickerDetailViewModel$getPickerDetailList$2.invokeSuspend。上次仅检查构造器、
点击参数和协程 ABI，未覆盖 getPickerData 实际返回类型，验证范围不足。

完整 smali 确认 suspend getPickerData 返回 Flow<PickerDetailResponseWrapper>，
而不是单个 Wrapper；正常、空数据和 ApiException 分支均构建不可变
kotlinx.coroutines.flow.q（flowOf），其 final Object a 为负载，d(collector,
continuation) 负责原生 emit，并原样传播 COROUTINE_SUSPENDED。

新增 LocalWidgetCatalogFlow：绑定时校验目标 Flow/collector/continuation 与
单值实现布局；仅对已验证的精确 q 实例读取负载，在按需后台任务中合并详情，
重新构造同类型 Flow。原 Flow 不修改，无变化不分配新 Flow。未知 Flow（含其他
模块包装）与 Result.Failure/取消原样透传；不代理 collect/emit，不替代其挂起
状态机。远程成功 Wrapper 的付费/MAML 字段仍保留。另处理远程 responseList
为 null 的情况。关闭或包变化时，异步结果仍按 generation 回退原 Flow。

验证：Release/lintVital 构建成功，5 项目录/作用域 JUnit 通过，APK v2 签名通过。
在手机独立 app_process 中加载当前安装的智能助理 APK，运行仓库内
`tools/tests/LocalWidgetCatalogFlowDeviceTest.java`（与真实生产 adapter 同编译）：
实际 Flow 立即/挂起 collect、Wrapper 与 continuation 身份、收集器异常、取消、
未知 Flow 透传、无变化不分配和原 Flow 不变全部 PASS。已有原生扁平 Card 转换
与 v8.a 点击参数解析也再次 PASS。此测试未运行真实 Activity/LSPosed Hook，
不等同于用户页面点击和添加小组件端到端通过；未代用户安装或重启作用域。

性能为代码结构检查：反射访问方案在绑定时缓存；新增操作仅详情请求完成时
运行，合并在原有按需 worker，保留空闲 10 秒退出；不增加常驻任务、绘制/动画
Hook、GPU 层或周期性 IPC。未测量帧率、功耗或前后耗时，不声称性能改善。
手机临时诊断 dex 已清理。

交付 `HyperModifier-local-widget-detail-flow-fix.apk`，SHA-256：
fe53d7bfb33d4acc47507f9e1f38fb9d4231aa9a0cee3f7c570b9468e06f1e78。


### 2026-10-15 第三次点击崩溃：本地详情的默认付费状态

11:39:15/11:39:27 新堆栈已通过 Flow.collect，崩于
PickerDetailFragment.updateShow:184。完整 smali 指向
getPriceInCent() 后 Intrinsics.checkNotNull，而该分支条件为 isPay && !isBought。
手机已安装 APK SHA-256 与前次交付 fe53d7bf…06f1e78 完全一致。

根因是系统 PickerDetailResponseWrapper 五参数简化构造器默认 isPay=true、
priceInCent=null。本地目录此前只设应用和列表，遗漏了这个与 UI 耦合的默认值。
这不是上次 Flow 类型错误重现；独立 Flow 协议验证并未覆盖详情 UI 模型语义。

新增 LocalWidgetCatalogModels 缓存完整 19 参数构造器及本地行状态 setter，
为新建本地 Wrapper 显式传 isPay=false、priceInCent=0、isBought=false、
非空安装提示和下载列表；本地行同样设免费、零价格，并为已登记本地 provider
设置可添加状态。仅作用于新建的本地行，不修改现有商城记录或其授权信息。
云端空记录 Wrapper 采用完整本地状态；非空远程记录仍复制其所有字段后合并
provider，保留 MAML/付费状态。没有给远程付费产品补授权或绕过购买流程。

tools/tests/LocalWidgetCatalogFlowDeviceTest.java 已扩展，直接加载真机目标
APK 并与生产 Models/Flow 类同编译：先断言旧短构造器的默认付费/空价格组合，
再验证修复模型免费/零价格/安装提示/下载列表、本地行可添加且未伪造购买状态、
旧对象未被修改；原 Flow collect 的立即/挂起/异常/取消等回归仍 PASS。
验证仍为独立进程模型与协议测试，未运行真实 Fragment/LSPosed 页面，不能
宣称安装后详情、预览或添加流程已经端到端通过。

性能检查为静态结构检查：方法/构造器仅绑定时查找；增加的字段初始化只在
详情按需 worker 执行，不增加启动进程、主线程 Hook、动画/触摸 Hook、IPC、
GPU 层、持有界面的缓存或定时轮询。未进行功耗/帧时间前后测量。

Release/lintVital 构建成功，5 项目录/作用域 JUnit 通过。交付已签名
`HyperModifier-local-widget-detail-state-fix.apk`，SHA-256：
16eaf685e4d6b09c8c6c7a51087dcff592bcf880d6540747e288431a4b9d9f6a。
手机临时诊断 dex 已清理。未代安装、未提交，真实详情页和添加流程待安装回归。


### 2026-10-15 详情空白预览与“加载中，请稍后添加”

用户能打开 Deadliner 详情但预览为空，添加弹出 pa_picker_detail_preview_load_fail_can_not_add_widget。
定位字符串 0x7f130403 到 DragHelper（d9.a.b/c）：ObserveGlideLoadStatusImageView.a
未就绪时阻止添加。PickerItemContainer.showPreviewImage 使用应用条目的 light/darkPreviewUrl，
经 picker.util.l0.i → u6.c.f → Glide asBitmap；空 URL 仅走占位符分支，未完成实际预览加载。
本地条目此前两个 URL 均为空，因此不是继续等待就会完成的网络请求。

改为在详情后台构造时填入 android.resource://package/resourceId：优先 provider.previewImage，
然后 provider.icon、applicationInfo.icon；均不存在时使用 android 的默认应用图标资源。
light/dark URL 同源，Widget 构造器第三个 String 是 desc，第四/第五个才是 light/dark。
没有新增加载状态 Hook，没有直接写成功布尔值，没有移除 DragHelper 检查。
继续由原 Glide 异步解码、缩放与缓存，原成功/失败回调负责是否允许添加。
资源选择仅读取已有 provider/AppInfo 整数并构造 URI；不在主线程解码或查询 provider，
不新增截图、文件缓存、View 持有、实时小组件宿主、轮询或更新广播。

从手机读取 Deadliner APK 并用 aapt2 检查两个 provider XML，均未声明 previewImage，
只有 Glance initialLayout 与配置 Activity。因此此应用先显示图标作为静态兜底，
不声称显示了实时内容或已配置后的实际预览。添加后的实际 widget/configure 链路保持系统行为。

tools/tests/LocalWidgetCatalogPreviewDeviceTest.java 在手机独立 app_process 加载当前
智能助理 APK 和 Deadliner 已安装资源，用目标 s2.e ResourceDrawableDecoder 与目标
ResourceBitmapDecoder 解码出真实 Bitmap，再调用原生 ObserveGlideLoadStatusImageView
成功监听器和实际 d9.a.b 检查。Deadliner 自适应图标 URI 与 android 默认图标 URI 均 PASS。
测试为隔离环境：未附加/渲染 View，未运行真实页面或 LSPosed Hook，也未分配、绑定或
添加 widget。测试初次因目标 BitmapPoolAdapter 构造器被裁剪失败；改用有已验证构造器的
LruBitmapPool(0) 后通过。不能把隔离检查等同于安装后的完整页面添加验证。
新增本地预览来源优先级及空资源兜底回归；原 Flow/模型修复保持。
性能结论为代码结构审查，未取得帧时间或功耗前后对照。

Release/lintVital、6 项目录/作用域 JUnit、APK v2 签名通过。交付
`HyperModifier-local-widget-preview-fix.apk`，SHA-256：
03a474b1ef2c47c61b5777c07885f665a6134a726ca06482dc10148d47a9f0f6。
未代安装/重启作用域、未提交。实际页面添加及配置仍需安装后回归。


### 2026-10-15 修复 4×2 本地组件被压成 2×2

用户反馈唯品会等原本 4×2 的组件被注入为 2×2，转换后的 MIUI 小组件不能
自由缩放，导致内容异常。根因在本地目录的固定 70dp 格数估算：唯品会未声明
Android targetCellWidth/Height，minWidth/minHeight 推出的 6×3 等格数不在
MIUI 枚举内，随后此前的 style==0 → style=1 回退把它永久变成了 2×2。
“保留 providerInfo 就能保留尺寸”的原判断不成立，因为 getAddTargetItemInfo
从 originStyle 转换 span/configSpan，而不是从 provider minWidth 重新计算。

移除固定 dp 格数估算及强制 2×2 回退，新增 LocalWidgetCatalogSizes：明确声明
且系统支持的 targetCell 优先；缺省轴按智能助理实际 pa_widget_size_type_* 的
像素宽高选能容纳 provider 最小尺寸的最小支持容器。显式但不支持的格数仅允许
向上匹配；无对应容器或缺少有效尺寸不注入，避免添加不可调整的错误大小。
8×4 为特殊缩放/平板样式，不用作普通手机尺寸估算的“大容器”。Widget 内格数、
style、originStyle 使用同一个选择结果，不再出现预览样式和添加格数不一致。
既有非空远程 MIUI 记录及授权状态不改。既有已添加错误尺寸未自动修改桌面数据库，
安装后需要移除再重新添加，才能让新 ItemInfo 尺寸生效。

真机独立 app_process 读取 AppWidgetManager 返回的已换算像素（非 dumpsys 的
complex dimension 编码）：密度 3.25，唯品会 Multi=1063×494、DeskHeler=993×477、
Style4=1027×477，匹配 4×2；其余四个 provider=477×477，保持 2×2。
Deadliner Compact target=2×2 / min=358×358、Capsule target=2×1 / min=358×130，
继续保持 2×2 与 2×1。系统实际 4×2 容器在此配置为 344×160dp。

tools/tests/LocalWidgetCatalogSizesDeviceTest.java 直接调用当前智能助理原生
picker.util.g.a(ItemInfo, style)，逐个断言 spanX/Y 及 configSpanX/Y 与选择结果一致，
9 个真实 provider 全部 PASS。此测试未实际修改桌面、添加/绑定组件或跑真实页面；
仍需要安装后的显示回归。新增 JUnit 覆盖唯品会三种横向尺寸、显式格数优先、
不支持尺寸不缩小、纵向/大组件、无尺寸/超大尺寸拒绝及样式与格数一致。

性能审查：尺寸比较是详情后台每 provider 固定至多 9 次的整数比较；资源 ID 和
像素容器尺寸按配置缓存，Configuration 变化时才重建。无 View/Activity/Context
强引用缓存，不增加主线程反射/IPC、布局/绘制 Hook、轮询或常驻任务。
只是结构性检查及功能回归，未测量耗电/帧时间改善。手机临时 dex 与残留的尺寸
只读诊断进程已清理。

Release/lintVital、11 项目录/尺寸/作用域 JUnit 与 APK v2 签名通过。
交付 `HyperModifier-local-widget-size-fix.apk`，SHA-256：
fbd385406cf48caab24ebc00aee9354f708bf8803028784a2250108e4db12169。
未代安装/重启作用域、未提交。实际显示与重新添加后的布局待用户回归。

### 2026-10-15：schema ABI 兼容与桌面运行状态

用户反馈 7748 在其他 ROM 上无法获得堆叠/玻璃资格。提交的系统 schema 库为
601320 字节、FNV-1a64 `5332d3708a06060d`，旧设备库为 601400 字节、
`f519ae5224ebc666`。此前整库长度/指纹校验会拒绝用户库。

改为保守的 ARM64 ABI 结构契约：19 个实际依赖的导出接口、12 个关联内部辅助
函数（共 8076 字节代码）验证指令、导出尺寸、调用关系和 PLT 的语义导入符号。
地址重定位与内部 Rust panic 源位置 ADR 可移动；返回值编码、对象字段偏移、
局部分支、clone/drop 路径变化均拒绝。安装前另核对实时公开入口与磁盘代码，
检测到先前的 inline 改写时标为安装失败，避免误报为 ROM ABI 不兼容。
两份 schema 可生成完全相同的契约，不再按整库哈希加入逐 ROM 白名单。
这不是通用 ABI 推断：相关指令或依赖 Rust 符号改变后仍需人工审核新契约。

Dart picker 不再以 APK 整库哈希为唯一安装条件。在加载的可执行段中一次性
查找经过审核的 128 字节上下文，仅允许 BL 调用重定位变化，要求唯一命中、
调用目标与两个分支后继均在可执行段内。寄存器、对象池访问和分支距离保持
精确匹配。无匹配、歧义或 detour 失败时不启用 picker override；安装失败尝试
用 Native API 的 unhook 回滚。7722/7748 命中已知位置；提供的 8244 安全拒绝。
本轮不宣称 8244 已获支持，也不把 native 崩溃描述为可被异常捕获兜底。

新增只读本地状态通道：复用原安装 worker，启动期间最多每 500ms 重试、总计
不超过 60 秒；安装结束后阻塞等待诊断请求，无定时唤醒，线程栈设为 256KiB。库加载回调仅合并 eventfd 信号，ABI 校验、Dart 扫描
与 detour 安装全部交给该 worker，不在加载回调线程执行。
状态响应只允许 UID 1000 读取；SystemUI 验证对端为本机桌面 UID，模块请求
继续复用已有 inventory 接收器的包名/UID 校验、异步执行器和 ResultReceiver。
该通道不接受配置命令。schema 未通过验证时也不安装设置广播桥，避免使用
未验证的 getter ABI。未加载/不可达/超时与 ABI 不兼容分别呈现。

桌面设置页按需查询 schema、picker、实例规则、设置桥及配置送达状态。打开
已启用功能的页面自动查询一次，支持点击刷新；确认 schema 不兼容才自动弹出
提取引导，同原因在当前页面保存状态中仅自动提示一次。Dialog 提供电脑 ADB
提取命令、root 文件管理器替代步骤和诊断复制，不自行申请 root 或更改系统库。
状态不可达不会被当作 schema 不兼容。

性能检查为结构审查与主机回归：安装期读取最多 4MiB schema（当前约 0.6MiB），
Dart 可执行扫描限制总计 64MiB，校验缓存为本进程一次；高频 metadata/布尔值/
绘制/动画路径未加入扫描、磁盘访问、IPC 或状态查询。新增成本是一个复用的
阻塞安装线程和按需诊断连接；无真机帧率、耗电或分配采样对照数据。

验证：两份真实 schema、返回值/入口/内部 helper/导入符号破坏、无关 Build ID
变化与截断 ELF；Dart 两套上下文、寄存器破坏、歧义与范围上限；真实 7722、
7748、8244 APK 扫描；原 picker 汇编 96 个 ABI 用例与 widget policy 回归。
Android 编译及状态解析 JUnit 另行执行。尚需真机验证 SELinux 下的状态通道、
Dialog 呈现，以及启停、原背景恢复和实际堆叠/材质行为。

本轮最终验证：Release 构建及 APK v2 签名校验通过；兼容状态、原玻璃匹配与
材质规则共 16 项 JUnit 通过，主机 ASan/UBSan 兼容回归与原 picker 96 个 ABI
用例通过。两份真实 schema 生成的契约文件逐字节相同。交付包为
`app/build/outputs/apk/release/HyperModifier-widget-abi-compat.apk`，SHA-256：
`d227f64f2443725665cf0da1a6c2cc9ff71be1b1dcb8588c8cc0d946880d3280`。
未安装到用户手机，未完成上述真机验收，也没有帧率或耗电改善的测量结论。

### 2026-10-15：8244 与 Dart／SDK 结构定位第二版

8244（RELEASE-8.01.02.8244-260915-09172210-R）Dart SDK 为 3.10.7，
分析得到 103074 个代码入口。小组件列表函数位于 `0x1ac01f8`（1560 字节），
分类读取分支为 `0x1ac047c`。经实际对象池 offset 校正，邻近字符串依次对应
`buildAppWidgetsItems, needsHideFromPicker `、`buildAppWidgetsItems, isMiuiWidget `
及 `WidgetsVerticalList`。其压缩布尔读取仍为 ItemInfo 的 `+251` 字段，x28
解压，x22 为 NULL，false 为 x22+0x30；TBNZ W0,#4 的两个后继未改变。
7748 与 8244 的原 128 字节窗口差异是调用重定位、对象池页/索引及静态字段槽，
不是此处的寄存器 ABI 或分类逻辑变化。

上一轮按两份指令窗口匹配，改为三版共用的一份结构契约：保持操作码、寄存器、
分类字段、帧槽与局部分支精确；解码对象池 ADD/LDR、静态字段 LDR 与 BL。
新增多处独立交叉约束：两条日志路径读取同一静态字段、使用同一 logger 池项，
两条分类日志字面量池项相邻且与 logger 相差五项，support/isPad 调试字面量池项
为另一对相邻项；核对同函数的 false 构造以及条件跳转目标。池项的角色来自
离线反汇编审核，运行时验证操作数关系，不声称在运行时读取了字符串内容。
所有 witness 必须位于同一可读可执行段，调用目标位于可执行段且不能回落到
当前分类块，扫描必须唯一命中，否则拒绝安装。没有加入新的版本 RVA 白名单。

真实 Dart 镜像的同一生产定位器结果：7722 `0x1a83de0`、7748 `0x1a86998`、
8244 `0x1ac047c`。新增 128 组对象池页/索引、静态槽与调用地址变动测试，包括
跨池页边界；破坏分类寄存器/字段、false 构造、静态槽或池项关系、调用越界/
跳入当前块、重复匹配和地址溢出均拒绝。这能自动容纳这些操作数与代码地址
变化；若编译器改变寄存器分配、字段布局或控制流，仍需审核新结构，不能保证
未知 Dart 编译器版本无需维护。

8244 SDK 为 2689096 字节，旧 SDK 哈希门槛会阻止实例 ID 跟踪。改用 SDK 的
实际调用点验证两项外部接口：`AppWidgetManager_get_app_widget_info` 以 x0
低位 tag 判断成功并取 x1 owned pointer；`AppWidgetProviderInfo_get_providerInfo`
直接使用 x0 pointer。PLT 解码后核对 GOT 与实际 dlsym 目标，不能仅凭类似指令
或存在导入符号通过。无需调用这些外部函数或分配系统对象。旧版、7748、8244
SDK 均通过同一契约；错误导入目标、返回 tag 或 pointer 使用方式均拒绝。
此处也移除了整份 SDK 长度/哈希白名单及其启动期磁盘读取。

性能范围：仍在已有后台安装 worker 中一次性执行；Dart 扫描总可执行段上限
64MiB，昂贵 witness 验证只用于 exact bool triplet 候选；SDK 可读映射总量上限
16MiB，快速指令筛选后才解码少量 PLT/GOT。没有新线程、轮询、Hook 或每帧/
每次 metadata 调用的额外逻辑。现有设置热更新、关闭恢复及独立能力降级路径
保持不变。验证是主机结构/ABI 回归与 Android 编译，不是真机帧率或耗电测量。

本轮交付验证：Release、Debug native 构建通过；相关 16 项 JUnit 无失败。
两份 schema、三份真实 Dart 镜像、三份真实 SDK 镜像、128 组自适应操作数
变动及拒绝路径均通过主机 ASan/UBSan 回归，原 picker 96 个 ABI 用例与
widget policy 测试通过。发布包 `HyperModifier-widget-8244-adaptive.apk`，
SHA-256：`03365e1beb652b5eb409fd1cb1ca41170376caff13b1c7f3e663759be2dd9e60`。未安装至真机，实际 8244 小组件添加、绑定、
堆叠、材质、实时启停与重启恢复仍需设备验收。

### 2026-10-16：OS4.0.2.0 正式版现场诊断（尚未修复）

设备 fingerprint 为 `Xiaomi/pudding/pudding:17/CP2A.260605.016/OS4.0.2.0.XPCCNXM:user/release-keys`。
系统桌面是随 ROM 提供的 7751（801027751，10100945），并非之前的 7748。
实际安装模块包含 `PICKER structural-v2`。诊断材料保存在
`/private/tmp/mhm-widget-os4020/`，现有日志先保存，随后记录完整桌面启动；未清
logcat、未清桌面数据、未安装或变更模块。用户复现反馈没有玻璃效果。

10:24:58（pid 2066）与 10:25:57（pid 4110）的两次启动都记录：
- `INIT launcher uid=10139 api=2`，确认模块 native 注入发生。
- `HOOK rejected: schema ABI incompatible (live-entry:AppWidgetProviderInfo_clone)`。
- `PICKER structural-v2 branch result=0 ready=1 rva=0x1a86e20`，确认 Dart 安装成功。
- MAML build ID / Calendar 指纹拒绝属于独立桌面时钟路径，不是上述拒绝的原因。

导出的 schema 为 601384 字节，现有生产 schema ABI 校验通过。7751 的真实
Dart 与 SDK 镜像也分别通过生产定位器及返回值 ABI probe 的主机回归，包含
拒绝路径与 ASan/UBSan。阻断发生在额外的 live-entry 验证，不能把日志中的
“schema ABI incompatible”直接解释为文件 ABI 不兼容。

新 ROM 的 `libhyper_os_schema_public.so` 和 `libhyper_os_appwidget_public.so`
都导出 GLOBAL DEFAULT 的 AppWidgetProviderInfo_clone/drop。schema 的 clone
为 636 字节、drop 为 168 字节；appwidget 的 clone 为 636 字节、drop 为 24 字节。
桌面和 SDK 的 DT_NEEDED 中 appwidget 库均在 schema 库之前。

现有 matchingSchema 用 ActivityInfo_get_meta_data 的 dladdr 决定单一 schema
base，然后强制要求全部 19 个 dlsym(launcher, name) 等于该 base+schema RVA。
同名导出来自其他合法库时也会被拒绝；这是上一轮实现的错误假设。依赖顺序与
同名导出证据强烈指向该误判，但当前日志将“解析地址不同”和“实时字节变化”
合并成 live-entry，尚未取得实际 resolved 地址的 dladdr 来源，不能据此彻底
排除入口被其他模块改写。

修复应先分开记录符号来源与代码变化，再按实际符号所在库验证相应 ABI，
避免删除 live-code 检查或直接跳过 clone/drop 生命周期验证。本次只诊断和保存
证据，未修改运行时代码或向手机部署修复。日志采集已停止；未做性能对照测量。

### 2026-10-16：正式版同名导出来源误判修复与真机恢复

matchingSchema 不再把 ActivityInfo_get_meta_data 的 schema base 当作全部接口的
唯一来源。逐接口 dlsym + dladdr 取得实际 ELF，并按来源缓存文件和 RX 映射；
每份文件只读取/解析一次，总缓存上限 8MiB（单文件仍为 4MiB）。实际导出必须
满足对应 ABI 结构契约，地址必须等于其自身 ELF 导出地址；公共入口以及该根
可达的所有内部 helper 都与实时 RX 代码比较，入口或 helper 改写仍拒绝。

通用 Image 校验支持选择一个根并遍历完整 helper 图，允许其他合法 DSO 只导出
相关接口，而不要求同时具备所有 19 项 schema 接口。为本机 appwidget 库审核并
生成 clone/drop 的额外结构契约，覆盖两个包装与 8 个内部 helper，包括 4988
字节的内部 clone 路径和 drop 24 字节跳板实际调用的释放路径。不是按库名或
函数尺寸放行，也未简单跳过生命周期接口。ABI 不兼容与 live-origin/address/
range/code 失败分别记录；日志不再一概描述为 schema ABI incompatible。

主机 ASan/UBSan 回归：19 个原 schema 导出、两个真实 appwidget 替代导出均
通过；逐一修改替代根可达函数、模拟 live 改写、错误导出名均拒绝。原 schema
突变/截断检查、7722/7748/8244/7751 Dart 镜像与 128 组操作数变动、7751 SDK
ABI probe 和原 picker 96 个寄存器 ABI 用例通过。Release 与 Debug native
构建通过，修复包与已安装模块签名相同，已覆盖安装并重启桌面。

真机 10:37:27（pid 18601）确认：
- AppWidgetProviderInfo_clone/drop 的实际来源均为
  `/system_ext/lib64/libhyper_os_appwidget_public.so`，其余 17 个接口来自 schema。
- `INSTANCE SDK return/call ABI verified`。
- `HOOK metadata capability bridge ready`。
- `PICKER structural-v2 branch result=0 ready=1 rva=0x1a86e20`。
- `BRIDGE install result=0 ready=1`，并已观察到真实第三方 provider metadata。
用户随后反馈“OK，回来了”，确认功能恢复。此时同名来源误判已由实际 dladdr
日志证实。未对全部堆叠绑定/移除、实时禁用恢复或所有其他模块组合逐项验收；
独立的 MAML Calendar 指纹拒绝仍存在，本次未修改该时钟功能。

性能检查：新增来源解析、契约遍历与 live 比较仅执行于现有后台安装阶段；
没有新增线程、周期轮询、同步 IPC 或高频 metadata/绘制/动画逻辑。源文件缓存
与范围检查均为安装期局部对象，完成后释放。未做帧率或耗电对照测量。
材料 `/private/tmp/mhm-widget-os4020/fix-device-log.txt`。交付包
`app/build/outputs/apk/release/HyperModifier-schema-origin-fix.apk`，SHA-256：
`f9b3747ee5879d4c025b122f95f5ea78432eac4132d4f921d7ef39dae8d5b0e0`。

### 2026-10-16：澎湃目录 2540371 缺失本地组件

用户确认目录完全没有本地安卓组件。实际智能助理为 25.40.37.10-10082009 / 2540371，旧参考为 2540340；新包提取到 `reference/MIUIPersonalAssistantPhoneOS4-2540371.apk`，反汇编目录 `reference/personalassistant-2540371/`。模块和安卓堆叠开关均为开启。

发现两个独立阻断：

1. `Binding` 先按混淆名 `kotlinx.coroutines.n1$a` 解析 Job.Key，新包中此类不存在，独立 app_process 复现 ClassNotFoundException，安装未能完成。新包 Job 为 m1，Key 为 m1$a；n1 已变为其他 Job 实现。
2. 即使越过初始化，load() 仍用 `versionCode == 2540340` 拒绝新版，直接返回 null 且没有说明。

修复不再使用应用版本白名单，也不换成另一组混淆常量：从保留的 `JobCancellationException.job` 字段取得 Job 类型，再校验 CoroutineContext.Element、isActive():boolean、Key 接口及静态 final 单例。元数据在安装阶段一次性解析，后续取消检查复用 Method/Key，无 DEX 扫描或高频反射查找。其余列表/模型/详情签名和字段也先完整解析/校验，全部成功才安装 Hook。安装收集 7 个句柄，中途失败撤销已装句柄、清理监听和 worker 并重置安装标记；运行时失败递增代际、停用本地逻辑并报告，保留原生目录流程。

真实 2540371 APK 的生产 Binding 构造/回滚检查通过；使用无操作诊断 shim，未执行 Hook 安装。原生 Flow 的 collect、挂起、异常、取消及外部 Flow 保留检查通过；9 个安装 provider 的尺寸与原生 ItemInfo/configSpan 转换一致。新包把部分购买/付费 getter 固定为常量，因此测试比较原始私有字段和已有对象状态，不强制覆盖原生 getter；生产代码不修改这些 getter 或远程商品。

实际列表交付增加一次性诊断：只有首页包含本地 provider 且原生转换器返回后，才记录“本地小组件目录已交付”。首次获取真实应用 Context 时接入现有诊断队列，无通用 Context Hook、无新轮询。修复签名包已安装并重启组件目录进程，用户明确确认本地安卓组件已重新出现；未取得自动化完整 UI 列表，恢复结论以用户确认为依据。323 项 JVM 测试及 Release 构建/签名验证通过；详情添加全流程及性能对照不由此推断通过。修复 APK SHA-256：`09072e5353278d9719013fa170c857a634a7338ac8277de8029535a06c55c78f`。
