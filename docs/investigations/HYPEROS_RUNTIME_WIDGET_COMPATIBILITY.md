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
