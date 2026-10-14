# 系统桌面性能分析（2026-10-13）

## 范围与结论

用户反馈桌面滑动、文件夹，以及打开应用、返回桌面、最近任务动画偶发掉帧。已检查模块代码并通过 ADB 录制 30 秒 Perfetto。用户确认在录制期间进行了操作，但没有明显卡顿。因此这次数据是基线，不能代表严重卡顿时的表现，也不能据此确认所有问题的根因。

当前最明确的优化入口是 SystemUI 手势条绘制中的同步 IPC；桌面组件元数据 Hook 的锁和规则解析开销是第二优先级，尚无运行时耗时归因。

## 真机测量

- 桌面版本：`RELEASE-8.01.02.7748-260904-10091435-R`。
- 桌面渲染管线：Impeller / Vulkan，GPU：Adreno 840。常规 HWUI gfxinfo 不提供适用于此管线的完整帧统计。
- FrameTimeline 记录桌面 Surface 226 帧：223 帧准时、3 帧晚呈现；应用侧 `App Deadline Missed` 为 0。3 帧均标记 `SurfaceFlinger GPU Deadline Missed`，集中在录制约 28.09–28.12 秒的转场。
- 222 个桌面帧同时标记 `Buffer Stuffing`，但呈现准时；不能将其直接当成 222 次肉眼掉帧。
- SystemUI 记录多个 Surface，共 393 条帧记录，17 条晚呈现，11 条包含应用侧超时。不同 Surface 可属于同一显示帧，不能将这些计数视为独立掉帧次数。
- 导航栏 `draw-VRI[NavigationBar0]` 内有 228 次同步 Binder transaction，目标均为 `system_server`，transaction code 为 `0x1e`；累计耗时 59.237 ms，单次最大 17.782 ms。
- 28.078 秒的导航栏绘制耗时 26.730 ms，其中两次上述 Binder transaction 累计 26.335 ms。这明确显示该次绘制的大部分时间花在同步 IPC 上。
- trace 未报告丢失或 overrun 错误。

FrameTimeline 的 Surface 持续时间包含排队与呈现等待，不能直接当作 CPU 绘制耗时。嵌套 slice 的总耗时也不能相加当作总 CPU 时间。

## 模块热路径

### 1. 手势条：绘制中重复查询锁屏状态（优先）

`GestureHandleHooks.draw()` 先调用 `appliesTo(view)` 判断隐藏状态；开启滑动跟随时又调用一次判断位移。`appliesTo()` 每次通过 `KeyguardManager.isKeyguardLocked()` 查询状态。

这与录制中的每次导航栏绘制通常包含两次同步 IPC 的模式相符。由于录制没有模块函数级标记、设备 transaction code 到方法的直接解码或关闭模块的对照实验，不能将全部 228 次调用完全归因于模块。

建议：首先每次绘制复用一次判定；进一步使用 SystemUI 锁屏状态回调维护状态，避免绘制线程同步查询。不要仅使用 SCREEN_OFF / USER_PRESENT 永久缓存：它们不能完整覆盖锁屏出现、取消、遮挡和配置变化。

`pillRect()` 在输入事件路径每次调用 `getMethod("getPillRect")`；OS3 不提供该方法时会反复抛出反射异常，再访问字段。建议按 View Class 缓存方法或字段访问方案，包括不存在的结果；几何值仍实时读取，以适应旋转和尺寸变化。

### 2. 桌面组件：扩大了通用 Bundle 查询的开销

`widget_hooks.cpp` 的 `booleanHook()` 对任意 boolean key 都先查询注册表、复制身份字符串、获取配置锁、匹配材质规则，然后才判断是否为需要覆盖的 key。即使功能关闭也会进入部分路径。`containsHook()` 在玻璃启用后，对无关 key 仍会先查询身份并获取配置锁。

建议：先判断功能开关与 key，仅让 `miuiWidget`、`supportBackgroundBlur`、`usingFolderGlassStyle` 等相关查询进入匹配路径。保留原有 metadata 观察、clone/drop 和生命周期注册，避免实时切换功能或地址复用时关联错误。

`configureAndroidWidgets()` 在判断内容变化前解析三套规则，并持有能力查询也使用的 `configMutex` 执行缓存文件写入与 rename。建议去重后再解析，并将持久化移出热路径共享锁；需要保持并发更新的顺序与缓存一致性。

目前 trace 没有这些 native Hook 的独立 slice 或采样堆栈，不能确定它们是否参与这次转场延迟。

### 3. 设置桥接和组件提供方（次要）

`DesktopClockSettingsBridge.PUBLISH` 在 SystemUI 主线程读取、解析、打包规则并广播，启动期重试、之后约一分钟心跳。`desktop_clock.cpp` 收到时钟设置后会写缓存，即使时钟值没有变化。适合做 payload 复用和持久化去重，但频率不支持直接解释持续滑动掉帧。

`AndroidWidgetGlassHooks` 在组件提供方更新时查询组件信息、解析规则、反射 RemoteViews 并读取布局 XML。它可能增加组件刷新压力，但用户描述的连续桌面/转场场景需要进一步对照才能归因。

时钟格式 Hook 只做短字符串判断与原子开关读取，组件选择器汇编入口只做短分支。当前没有证据优先修改这两处。native 安装和 schema 指纹校验主要发生在启动阶段。

## 下一轮验证

1. 优先减少手势条绘制 IPC，缓存反射访问方案；验证锁屏、解锁、旋转、滑动返回、横向切换最近任务。
2. 分别录制仅关闭手势条功能、仅关闭组件堆叠/玻璃功能的同场景对照，保持刷新率、温度和操作一致。
3. 若再次出现明显卡顿，追加模块函数级耗时标记或 native CPU 采样，并检查 SurfaceFlinger 的 GPU/合成等待。当前记录不足以判定 GPU 超时由某项模块材质效果造成。

本次仅分析，没有修改桌面或手势条实现，没有安装 APK、切换功能或重启系统进程。

## 分析材料

本机 `/tmp/mhm-launcher-analysis/` 保存 `launcher.perfetto-trace`（约 64 MiB）、trace 配置、gfxinfo、三组 SQL 与输出。该目录为临时目录，不进入 Git；trace 包含进程和系统运行信息。
