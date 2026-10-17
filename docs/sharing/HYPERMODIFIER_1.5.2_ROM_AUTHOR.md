# HyperModifier 1.5.2 改动说明（供官改系统作者参考）

这次主要围绕 HyperOS 4.0.2.0 正式版兼容、桌面组件和界面材质做了调整，另外修了隐藏锁屏指纹误伤支付/应用锁的问题，加了状态栏显秒。下面把实现位置和适配边界一起列出来，方便对照源码或移植到系统。

- 仓库：[AritxOnly/HyperModifier](https://github.com/AritxOnly/HyperModifier)
- 比较基准：[v1.5.1 / 1fff270](https://github.com/AritxOnly/HyperModifier/commit/1fff270)
- 本次版本：`1.5.2` / versionCode `40`，整理日期：2026-10-17。
- 范围：全部 1.5.2 工作区改动，包括锁屏指纹修复和状态栏显秒。

## SystemUI 适配基线

以 `OS4.0.2.0.XPCCNXM` 正式版为基线：

| 包 | 版本 | APK SHA-256 |
| --- | --- | --- |
| SystemUI | `17.03.260226.r` / `202602260` | `d3628f6c3b85d16b1e74aef13ee227b41b6a855a01873c7f50364e274637f571` |
| 系统界面插件 | `18.3.2.57.0` / `183025700` | `a7abb534697ad0638bd82e932de9c622b5f2f124ffe717e5342fc4011a1ded2f` |

APK 和反汇编只在本地保留，仓库不附带系统包。其他 ROM 版本需要重新核对类、字段、资源及调用链，不能直接套资源 ID。

## 这次的功能和修复

| 项目 | 改动与边界 |
| --- | --- |
| 锁屏指纹 | 原来直接在共用 `MiuiGxzwIconView` 的显示方法之后隐藏图标，支付和应用锁也受影响。现在先检查该 View 的 `mKeyguardAuthen`，只在锁屏认证时隐藏。息屏保留图标的逻辑同样收窄到锁屏；保留密码页主动切回指纹的例外。 |
| 状态栏显秒 | 增加“状态栏 → 状态栏时间 → 时间显示秒数”，默认关闭。针对 `MiuiClock` 中 ID 为 `clock`、模式为普通时间的文本，不改大锁屏时钟和日期标题。跟随系统 12/24 小时制、语言和时区；关闭时调用系统 `updateTime()` 恢复。 |
| 桌面小组件兼容 | 处理 4.0.2.0 下原生接口来源变化。按接口校验实际代码、ABI 和可达 helper，支持已验证的 appwidget 替代来源；不通过简单放宽版本号来兼容。增加兼容状态查看、诊断导出及安全拒绝路径。 |
| 本地安卓组件目录 | 接入澎湃组件目录，完善预览、添加流程和尺寸识别。修正版本硬门槛以及 2540371 真包的协程/Job 混淆适配，完整契约验证成功后才启用。 |
| 抖音柔光底栏 | 针对抖音 `40.7.0 / 400701` 增加柔光玻璃悬浮底栏与独立发布入口，复用原生点击和路由；处理主题、底部沉浸、视频展开、字幕/控件及进度条避让。依赖该版本的内部结构，不承诺所有版本兼容。 |
| 共用底栏与 Spotify | 调整同窗口底栏的生命周期、背景取样和恢复路径，同步修改 Spotify 悬浮底栏与播放胶囊。 |
| 通话界面材质 | 全局背景模糊、压暗及 Glass 半径增加通话界面适配；专用进程路径保留原生材质生成完成后的处理，以及停用时恢复。 |
| HyperMusicCover | 完善 0.9.x APK/DEX 接口解析、冷启动准备和生命周期绑定，减少对固定混淆名称、时钟 View 挂载时机的依赖。 |
| 超级岛通知背景 | 移除通知中心超级岛背景不透明度自定义和专用 Hook，保留展开面板、下拉小窗及其他仍支持的材质设置；旧备份中的两个退休键在导入时跳过。 |
| 设置和文档 | 增加捐赠页、收款码查看/保存，补充正式版基线、性能记录、兼容调查和发布检查清单。 |

通知中心背景这项是撤下失效功能，并非已定位并修复了全部失效原因。4.0.2.0 中标准超级岛通知从专用配方切到普通通知配方是已确认差异，但不足以单独解释所有外观异常。

## 锁屏指纹：移植时最需要注意的点

`MiuiGxzwIconView` 是共用的屏下指纹视图，不能根据“这个类在 keyguard 包里”就把它当作锁屏专用视图，也不能只根据锁屏当前是否可见来决定隐藏。

基线中，`MiuiGxzwManager.setKeyguardAuthen(boolean)` 会把认证归属同步到 `MiuiGxzwIconView.onKeyguardAuthen(boolean)`；实际字段 `mKeyguardAuthen` 继承自 `GxzwNoRotateFrameLayout`。本次在显示后先读这个字段，值为 `false` 时直接保留系统行为，不调用模块的隐藏方法。

字段和方法在安装时解析并缓存，认证归属每次读取当前值，不缓存成可能过期的全局“是否锁屏”。未知字段或读取失败时保留系统图标。此修复不改指纹识别和支付认证本身。

源码：[LockscreenHooks.java](https://github.com/AritxOnly/HyperModifier/blob/master/app/src/main/java/com/aritxonly/myhypermodifier/runtime/LockscreenHooks.java)。

## 状态栏显秒：刷新与生命周期

基线状态栏实际使用 `com.android.systemui.statusbar.views.MiuiClock`，继承 `TextView`；它与 AOSP `statusbar.policy.Clock` 是不同实现，后者的 `mShowSeconds` 开关不能直接作用于它。

本次保留系统时钟更新入口，用缓存的格式器给目标文本增加秒数。多个目标共用一个主线程 Handler 任务，对齐墙钟整秒，使用 uptime 队列而非唤醒型 Alarm。开启、亮屏、附着、可见且非演示模式时才持续调度；息屏、关闭、隐藏或卸载后停止秒级更新。屏幕事件监听只在开启且存在目标时登记，目标用弱引用，设置刷新只在相关启用状态变化时回放。时区、语言和 12/24 小时制在系统更新事件中刷新，不在每秒回调里查询设置 Provider。

每秒文本更新仍有格式化、文字绘制和可能的布局成本，长时间亮屏下不能称为零功耗；没有前后对照性能数据。系统原生集成时可以把这部分直接放进已有时钟控制器的可见性和屏幕生命周期中。

源码：[StatusBarClockSecondsHooks.java](https://github.com/AritxOnly/HyperModifier/blob/master/app/src/main/java/com/aritxonly/myhypermodifier/runtime/systemui/StatusBarClockSecondsHooks.java)、[调度与格式规则](https://github.com/AritxOnly/HyperModifier/blob/master/app/src/main/java/com/aritxonly/myhypermodifier/runtime/systemui/StatusBarClockSecondsPolicy.java)。

## 性能处理范围

- 指纹路径：缓存反射元数据，关闭和非锁屏认证时廉价退出；不增加截图、模糊层、轮询或 IPC。
- 显秒路径：只有可见亮屏期间有必要的每秒任务，复用 Date、格式器和格式化缓冲；不增加每帧回调、绘制 Hook 或后台常驻定时任务。
- 桌面 ABI/HMC：解析和校验放在安装准备或后台阶段，避免在绘制、触摸和动画回调中扫描 ELF/DEX。
- 本地组件目录：独立作用域只装目录相关功能；工作线程空闲回收，取消和迟到结果受代际检查约束。
- 通话材质：专用适配，按相关配置变化合并刷新，不轮询原生位图生成状态。
- 视频底栏：优先复用 RenderNode/Texture 渲染路径；SurfaceView 回退仍有低分辨率截取成本，需要设备测量。
- 移除通知中心专用背景 Hook 后减少了 Hook、目标缓存及刷新分支。这是结构性开销减少，不等于已测得帧率或耗电改善。

## 验证结果与待验范围

本轮 327 项 JVM 单元测试全部通过，签名 Release（R8/Lint）构建和 APK v2 签名校验通过；新增用例覆盖显秒的全部生命周期开关组合、整秒对齐、分钟/午夜切换和 12/24 小时格式。同时重跑桌面兼容和实例 ABI 原生专项，128 组 picker 及 SDK 拒绝分支通过（ASan/UBSan）。

已有 1.5.2 调查记录包含原生 ABI/结构突变、ASan/UBSan、实际桌面包及组件目录契约验证；组件目录条目恢复有用户确认。这些历史验证不替代当前 APK 的全部设备回归。

仍需在目标 ROM 上确认：

- 锁屏隐藏、息屏保留、密码页切回指纹，以及支付/应用锁图标是否正常；包括锁屏上方弹出的应用认证。
- 显秒实时启停、息屏/AOD 不再秒级刷新、全屏隐藏后恢复、横竖屏、多用户、语言/时区/12/24 小时制切换，与其他时钟模块共存。
- 桌面组件完整预览/添加/配置/取消，通话材质和抖音/Spotify 页面切换及关闭恢复。
- 同设备、刷新率、温度和操作下的 Perfetto、FrameTimeline 与分配采样。当前没有帧率、卡顿率或耗电改善的前后对照结论。

详细资料：[性能审查](https://github.com/AritxOnly/HyperModifier/blob/master/docs/investigations/RELEASE_1.5.2_PERFORMANCE.md)、[发布检查](https://github.com/AritxOnly/HyperModifier/blob/master/docs/testing/RELEASE_1.5.2_CHECKLIST.md)、[SystemUI 基线](https://github.com/AritxOnly/HyperModifier/blob/master/docs/investigations/SYSTEMUI_4.0.2.0_BASELINE.md)、[桌面组件兼容调查](https://github.com/AritxOnly/HyperModifier/blob/master/docs/investigations/HYPEROS_RUNTIME_WIDGET_COMPATIBILITY.md)。
