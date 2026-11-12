# 外屏应用适配实验版

## 范围与使用

需求来自小米 18 Fold 外屏约宽高 `1:1.4` 的布局设想。尚未取得该设备的
框架包、精确面板参数或真机测量，设备名称不作为已验证兼容性声明。

在「折叠屏专区」启用「外屏适配」，选择 LSPosed 的 Android 系统
（`system`）和系统界面作用域后重启手机。首次启用需重启；关闭后会实时退出，
已经安装的 Hook 可在本次进程中重新启用；启动时被跳过的 Hook 需要重启安装。

进入普通应用，通过「外屏适配」快捷磁贴或桌面长按快捷方式切换模式。
支持选择 Activity 的手势工具可绑定
`com.aritxonly.myhypermodifier.CoverAdaptationActivity`。
侧栏底部有「换边」和「退出」；换边位置在本次系统进程生命周期中保留。
快捷方式用于绑定应用内可触发的手势等入口，以触发时的应用为目标；从桌面直接触发
不会自动选择应用，会提示先打开普通应用。

- 应用窗口以全高计算 `宽 = floor(高 × 9 / 16)`，余下区域交给侧栏。
  外屏 `1:1.4` 时，理论比例约为应用 78.75%、侧栏 21.25%。
- 系统必须确认处于折叠状态。默认内置逻辑屏、竖屏、标准全屏任务、未启用
  size-compat 的 Activity 才可适配；侧栏至少留出 64dp。
- 侧栏显示时间、电量、充电状态、默认网络类型及最多四个最近应用，网络暂不显示
  实时信号强度，暂不镜像完整通知/状态图标。只查询当前用户的任务，过滤其他用户。
- 最近应用从已有任务恢复，切换时先恢复上一任务的配置，再适配新任务。
  同任务 Activity 跳转保留已适配的底层窗口；左右换边只更新一次配置，避免先全屏
  再收窄引发两次重排。嵌入式 TaskFragment 和请求横屏的 Activity 暂不适配。
- 返回桌面、展开、熄屏、切换用户、关闭功能、进入不支持的窗口时退出。
  通知/控制中心等无 Activity 的系统窗口取得焦点时暂时隐藏侧栏并释放状态栏覆盖。
- 主应用区保留系统导航栏和显示挖孔的 inset 分发，功能只覆盖状态栏可见请求。
  「9:16」指窗口边界，扣除导航栏、挖孔、键盘后的内容区不保证仍为 9:16。
  键盘显示期间暂时移除侧栏，收起后恢复，避免 ROM 的全宽键盘被侧栏遮挡和吞触摸。
- 侧栏优先采用任务描述中的不透明状态栏颜色，否则使用深浅色纯色兜底；文字跟随
  SystemUI 原生状态栏明暗回调。透明栏背后内容不做截图取色。
  同任务的颜色变化通过已有 TaskStackChangeListeners 的任务描述回调刷新，
  不在绘制或状态栏动画回调中重新查询任务。

## 实现与恢复边界

`CoverAdaptationHooks` 只在开机时已启用功能、模块总开关打开且不处于
SystemUI 兼容模式时安装。能力探测完整解析 WM 反射访问计划，任何必需签名
不存在就放弃安装，不对系统接口做猜测式多签名扫描。

`ActivityRecord.resolveOverrideConfiguration(Configuration)` 原生计算结束后，
只为当前任务写入 resolved bounds / app bounds / max bounds，并调用原生
`Task.computeConfigResourceOverrides` 重新计算资源尺寸。不修改 Activity 的
requested override、全局显示分辨率、密度、应用清单或其他应用进程。
退出清除覆盖目标，再对弱引用记录的 Activity 调用原生 `recomputeConfiguration()`。

状态栏通过当前适配 Activity 对应的 `WindowState` 可见请求进行覆盖，原始请求值
保持不变。其他窗口、导航栏请求不受覆盖。WM 更新与恢复沿用原生 global lock；
广播、任务图标加载不在锁内执行。

SystemUI 创建一个 `TYPE_STATUS_BAR_SUB_PANEL` 的纯色窗口，仅侧栏矩形接收点击，
不取得应用输入焦点。模块入口命令使用自定义签名权限；SystemUI 使用独立的
`MANAGE_ACTIVITY_TASKS` 权限通道，不假设其为 UID 1000 或与模块签名相同。
状态回传限制发送方系统权限，使用显式目标包。SystemUI 提供 Binder lease，进程死亡后 framework 退出会话；
侧栏创建失败会主动请求退出，避免长期留下无人控制的适配窗口。

Android 窗口逻辑以官方 `android16-release` 源码检查接口和配置调用顺序。
SystemUI 回调签名以本地 `OS4.0.2.0.XPCCNXM` 参考包检查：
`CommandQueue.onSystemBarAttributesChanged(II[AppearanceRegion;ZIIString;[LetterboxDetails;)V`。
同时探测该基线的 `CommandQueue.setImeWindowStatus(IIIZ)V` 和
`TaskStackChangeListeners$Impl.onTaskDescriptionChanged(RunningTaskInfo)`。
这不等于已验证目标 ROM 的 services.jar，也不等于真机可用性结论。

源码：

- [ActivityRecord](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/wm/ActivityRecord.java)
- [TaskFragment](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/wm/TaskFragment.java)
- [DisplayPolicy](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/wm/DisplayPolicy.java)
- [DeviceStateManager](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/hardware/devicestate/DeviceStateManager.java)

## 性能检查

本轮为代码审查和结构性开销控制，没有微基准、真机帧率/卡顿率/功耗对照结果。

- 关闭功能启动时不安装额外 WM / 可见性 Hook，不注册折叠与侧栏监听。
- 已安装但无会话时，配置 Hook 读取空目标立即退出；可见性覆盖只检查弱引用和
  缓存字段。没有调用栈、异常探测、磁盘、JSON、同步 IPC 或反射查找。
- 有会话时只在配置事件计算窗口，配置备份仅在受影响任务配置变化时分配；
  不挂绘制、触摸、Choreographer 或动画帧回调。
- 焦点事件只缓存目标并合并更新；无会话时不额外请求布局。状态改变才广播。
- 最近任务 Binder 查询、包信息及图标加载在单个可超时退出的工作线程完成；
  图标缓存上限 16，前后台代次检查阻止过期结果写回。
- 电池、网络、TextClock 监听只在侧栏可见时活动。无截图采样、新模糊、轮询或
  常驻定时任务，无额外应用进程作用域。
- 不能静态确认的热点：窗口重排是否引发目标应用重建、首次图标读取、WM 锁内
  资源计算，以及新系统侧栏 Surface 的 GPU 合成成本，需要真机采样。

## 回归验证与真机待办

2026-11-06 本地验证：

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --offline
```

576 项测试全部通过（含本功能 14 项）；Debug / Release 构建、Release lintVital
及 R8 通过。使用 aapt2 检查了磁贴、快捷方式目标、签名权限及 SystemUI 现有权限。
ADB 无已连接设备，未进行安装、视觉验收或性能实测。

本地测试使用独立 WM 测试桩，不模拟真实 Surface、输入派发或 ROM 扩展。
覆盖比例/最小宽度、左右边界、requested/parent/max bounds 恢复、任务隔离、
通知面板暂停、桌面退出、内外屏切换、SystemUI lease 死亡、模块关闭及设置备份。

真机验证前仍视为实验功能，需检查：

- 普通 View / Compose / WebView / SurfaceView 应用的实际窗口比例和触摸坐标。
- 软键盘弹出收起、输入框滚动、支付/权限弹窗、横屏视频、PiP 和分屏切换。
- 同任务 Activity 跳转、最近任务动画、返回手势与底部导航边界。
- 左右换边后的挖孔避让、大小字体、浅深色与透明状态栏、应用内主题切换。
- 展开/折叠、锁屏解锁、用户切换、SystemUI 被杀、总开关和兼容模式实时恢复。
- 多模块共同控制状态栏/应用比例时的共存和退出恢复。
- Perfetto / FrameTimeline、CPU 和分配采样，对照保持设备、刷新率、温度、设置和
  操作一致；不以累计 gfxinfo 推断模块引起的性能改善。
