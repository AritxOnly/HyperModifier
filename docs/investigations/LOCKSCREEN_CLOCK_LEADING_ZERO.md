# 锁屏时钟小时前导零

设置：锁屏 → 锁屏时钟 → 24 小时制隐藏小时前导零，默认关闭。

当前适配 SystemUI 中的 `com.miui.clock.allInOne.AllInOneBase`。
本机参考 `/private/tmp/hmc-systemui-aod-reference` 的 smali 已确认：

- `refreshFullTimeStrings()` 先调用 `ClassicClockTimeUtils.getTimeString(int, boolean)`
  格式化小时，再调用同一方法格式化分钟。
- 小时字符串用于设置 `mHourView`、`mAllTimeText`、`mAllTimeTextNoColon` 和
  `mColonIdx`，随后系统根据这些值计算布局。
- `updateTime()` 中的 `HHmm` 用于四位 `mCurrentTimeArray`，不能直接改为 `Hmm`。

Hook 仅在启用设置且 `m24HourFormat` 为 true 的刷新调用内，通过线程局部状态
将第一次 `getTimeString` 的补零参数改为 false。分钟和其他调用保持原样。
使用 finally 恢复作用域；关闭设置后，下次原生时钟刷新恢复原格式。
不改绘制、字形路径或时间数组。缺少对应方法时在 Hook 诊断中报告不兼容。

真机检查：开启后依次确认 `00:03 → 0:03`、`09:03 → 9:03`、`10:03 → 10:03`、
`23:03 → 23:03`；切换 12 小时制和关闭开关，确认原格式恢复。
检查横向/双行布局、冒号开关和 HyperMusicCover 模式下的对齐。

## 桌面组件定位

先保留目标组件在桌面，运行 `adb shell dumpsys appwidget`，查找 Widgets 区的
目标 widget ID、provider 组件和 host。provider 表示内容提供者，host 表示承载者。
再用 `adb shell dumpsys package <provider 包名>` 查看 receiver/service 的进程配置，
并用 `adb shell ps -A` 核对正在运行的进程。provider 未必常驻。

标准 RemoteViews 组件由 provider 提供布局和数据，由桌面 host 展示；若使用
TextClock，时间格式也可能由 host 内的视图处理。仅凭点击后打开的应用或
界面层级中的桌面包名，不能确定应 Hook 的位置。

如果 `appwidget` 中没有目标项，可能是厂商自有组件。下一步应结合桌面进程的
视图类名和桌面 APK 实现继续定位，不能直接认定属于时钟应用。


## 2026-10-11 真机定位结果

通过 USB ADB 读取设备 pudding（25113PN0EC）的 AppWidget、进程、桌面活动和日志。
首页时钟对应 Launcher 记录 `id=428`：

- 标题：默认经典时钟（与锁屏同步）。
- `itemType=19`，`screenId=1`，`cellX=0`、`cellY=0`，尺寸 `4x2`。
- MAML `appWidgetId=101390041`，`appWidgetProvide=None`。
- `mamlTagCategory=theme_clock`，`appPackage=com.android.deskclock`。
- 组件 productId：`52b8238d-848f-4608-a0e6-28458d4b1f2c`，资源版本 `40127`。
- 资源路径：`/data/user_de/0/com.miui.home/files/maml/res/0/52b8238d-848f-4608-a0e6-28458d4b1f2c/40127/52b8238d-848f-4608-a0e6-28458d4b1f2c/widget_4x2`。

此项不在标准 `dumpsys appwidget` 的 Widgets 列表中。承载活动为
`com.miui.home/.launcher.Launcher`，本次 PID 为 `6894`；同 PID 日志持续输出
`rust_maml_sdk` 的 ScreenElementRoot 定时器和 Flutter MAML 图像诊断信息。
这些证据将显示侧定位到桌面进程。关联时钟应用包名不能证明其负责最终绘制。

当时 ADB shell 无可用 `su`（设备使用 KernelSU），尚未读取私有组件资源，不能确定小时前导零来自具体哪个
XML 表达式或格式化函数。后续实现应先检查该组件资源及桌面的 MAML/经典时钟
桥接逻辑，不能直接沿用 SystemUI 的 AllInOne Hook 或认定传统 Java MAML 类可用。


## HyperOS Runtime 注入支持核查

2026-10-11 用户确认设备使用 LSPosed 2.2.0 + Zygisk Next 1.5.0。
LSPosed 官方 2.2.0 公告明确新增 HyperOS Runtime 应用注入，要求 Zygisk 实现
兼容 Zygisk Next API：https://t.me/s/LSPosed/326 。
Zygisk Next 官方文档说明该 Runtime 独立于 ART，不提供 JNI 或 Java 对象：
https://github.com/LSPosed/ZygiskNext/blob/main/docs/hyos_runtime.md 。

现有原生模块实现可参考 SwipeGate 的开发者技术文档，它通过 Modern API 102
的 native_init 在 com.miui.home 内定位并 Hook libapp_launcher.so：
https://github.com/PzHown/HyperOS4SwipeGate/blob/main/docs/TECHNICAL.md 。
其手势地址/特征不适用于本项目的时钟功能。

reference 已包含系统桌面 RELEASE-8.01.02.7722-260904-09221533-R.APK。
初步检查：无 classes.dex；arm64 库包括 libapp_launcher.so、libapp.so、
libhyper_os_flutter.so、libnative_widget_sdk.so 和 libresources_frb.so。
Rust launcher 库存在 set_use_24hour_format 字符串，Dart AOT libapp.so 存在
HH:mm 字符串。字符串命中本身不足以认定为当前经典时钟的最终格式化点，
需要调用链/资源表达式证据后才安装功能 Hook。

本次实现前项目使用 libxposed API 102，但只有 Java 入口且 scope 尚未包含
com.miui.home；原生功能仍需 NDK 库、Modern Native 入口、桌面 scope 和配置桥接。
无 su 仅限制 ADB 直接读私有资源，不意味着 LSPosed 无法在 Runtime 进程中注入。

## 桌面 Native Hook 实现

通过组件下载地址取得上述 productId 的 40127 版资源，确认 `widget_4x2/manifest.xml`
的 `formatDate` 和 `DateTime` 使用 `HH:mm`，独立小时使用 `HH`。
从设备取得 `/system_ext/lib64/librust_maml_sdk.so` 并解析 `.gnu_debugdata`，
定位 `Calendar::format`，RVA 为 `0x3fcb7c`；`DateFormatter::get_text` 的调用点
确认其输入格式为 x2 指针、x3 长度，x0 输出、x1 Calendar 对象。

新增 Modern Native 入口 `libmhm_desktop_clock.so`，仅在 `com.miui.home` 安装。
安装前必须匹配 MAML Build ID `0002d2e16615ebdb6b537b4bf8fede44174bf81d`、
入口 32 字节指令和可执行段；不匹配时输出拒绝日志，不使用旧地址。
汇编入口保留原调用寄存器，开启时仅替换完整格式 `HH`、`HH:mm`、`HH:mm:ss`
中的小时模式为 `H`，不变更分钟、12 小时制、`HHmm` 或日期复合格式。
此 Hook 作用于桌面进程中使用这些完整格式的 MAML 元素，并非仅按 widget ID 筛选。

开关位于「主页 → 系统界面 → 系统桌面 → 桌面时钟」，默认关闭。SystemUI 从模块远程偏好读取开关，
使用带真实发送者身份的显式广播向桌面传递配置。Native 接收器验证 SystemUI
发送者和专用标记后消费控制消息；其他消息交还原接收器。
桌面在自己的 cache 保存启用状态，SystemUI 定期同步以覆盖桌面重启。
广播接口 ABI 参考 HyperOS4SwipeGate，并已附 MIT 许可证。

安装后保留「系统界面」作用域，新增「系统桌面」作用域，重启两者或重启手机。
用 `adb logcat -v threadtime -s MHMDesktopClock` 检查 `INIT`、`BRIDGE install`、
`HOOK Calendar::format`、`CONFIG accepted enabled=1` 和 `FORMAT HH:mm -> H:mm`。
小时大于等于 10 时视觉上不变；实际效果应在 0–9 点确认。

本机格式转换断言通过，签名 Release 构建通过。Java 回归测试 214 项通过，
1 项已有 Bilibili 默认预设断言失败。Native 注入、配置广播和实际组件显示
在首次构建时待真机验证；随后用户安装并确认效果正常，已保存到 `6002d11`。
本轮 ADB 日志缓冲区未保留该次验证的 Native 日志，效果确认来自用户反馈。
升级系统导致 Build ID 变化需重新分析适配。
