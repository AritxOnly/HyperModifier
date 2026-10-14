# 桌面状态栏取色（2026-10-13）

## 已检查的系统路径

桌面 Rust 库通过 `WindowOperation_set_light_status_bar` 提交状态栏明暗请求。
本地 SystemUI 反编译样本的最终决策入口是
`LightBarControllerImpl.updateStatus(AppearanceRegion[])`：普通模式读取 appearance
regions；夜间模式且非锁屏时读取 `StatusBarRegionSamplingInteractor.isRegionDark`
和 `samplingBarBounds`。所以仅覆盖桌面 appearance 请求不足以覆盖该采样路径。
最终通过 `DarkIconDispatcherImpl.setIconsDarkArea` 和
`LightBarTransitionsController.setIconsDark(ZZZ)` 更新现有图标及动画。
SystemUI 样本版本为 `17.03.260226.r`（versionCode 202602260）。本次手机未连接，
未核对当前实机版本或执行实机操作。

## 设置与边界

“系统界面 → 系统桌面 → 桌面状态栏图标颜色”提供跟随系统、浅色图标、深色图标。
默认跟随系统。新键 `desktop_status_bar_icon_mode` 的 0/1/2 分别表示上述选择；
非法导入值回退 0，保存、加载、设置 Bundle 和系统预设覆盖此项。

只在主屏幕的真实 `onSystemBarAttributesChanged` 回调中跟踪窗口归属。
回调所属显示和事件显示都必须为 0，packageName 必须为 `com.miui.home`。
使用回调所属 repository 身份匹配 LightBarController，避免作用到外接屏。
锁屏读取 SystemUI 已有 isOnKeyguard flow；SEMI_TRANSPARENT 状态保留系统决策。
这些是基于样本的范围保护，仍须真机验证通知栏、控制中心和应用转场。

不改 appearance region 缓存或采样 flow。启用时直接执行系统现有 dispatcher
决策；关闭、模块停用及离开桌面时重新执行原始 updateStatus。仅用弱引用保留
controller/repository，不持有 View、Activity、Context。缺少精确方法/字段时通过
既有诊断报告不兼容，运行时异常只报告一次并回退系统，不做异常循环探测。

## 性能检查范围

本次是代码与结构性开销检查，尚无真机前后对照测量。新增两个事件级 Hook：
窗口属性回调与状态栏明暗更新，未挂绘制、触摸或动画逐帧回调。跟随系统或
非桌面时不读 flow、不解析规则、不做 IPC/磁盘访问。反射方案安装时解析；
flow getter 无参调用和清除取色区域的参数数组复用，保留最终决策调用的少量
反射参数封装。没有新增 GPU 层、截图、模糊、采样器、轮询或定时任务。

设置 observer 只在本项、模块总开关或兼容模式变化时合并主线程更新。窗口归属
变化同步执行必要决策；主线程只读取本进程已有状态并调用系统原有图标 API。没有扩展
LSPosed 作用域；功能只安装在 SystemUI。启动期开销为类/成员解析和两个 Hook。
不能据此声称帧率、卡顿或耗电改善。

## 待真机验证

首次更新模块后重启系统界面作用域。两种颜色分别检查浅/深壁纸、深色模式、
文件夹、返回桌面、进入应用、锁屏/解锁、下拉通知/控制中心；选择跟随系统和
停用模块应恢复系统取色。多模块共存仍需实机验证，模块不直接改系统原值。

## 本轮验证

三个相关 JUnit 测试通过（两个范围/非法值测试和一个默认设置测试）；Debug
Java/Kotlin 与 Release 构建通过，包含 lintVital 检查。初次 Lint 被数组描述符
形式的 Class.forName 触发 PrivateApiDetector 解析崩溃，改为 Array.newInstance
构造数组 Class 后检查通过，没有关闭检测器。设置交互复用项目现有
WindowDropdownPreference，参数核对 0.9.4-rc01 固定版本文档；暂无设备 UI 截图。
最终 APK v2 签名校验通过，未安装到手机、未提交 Git。

## 首版实机失败与修正

用户选择浅色图标后，桌面仍显示自动深色。只读提取实机已安装模块，SHA-256
与首版交付包一致。provider 返回 mode=1、模块启用、兼容模式关闭。实机
SystemUI 的 LightBarController 与 repository 回调 smali 和样本一致。桌面前台
时 appearance 为 NONE、statusBarMode 为 TRANSPARENT，但最终 darkIntensity=1。

用户重启 SystemUI 后的启动日志明确记录本功能安装失败：ClassNotFoundException:
z52。已安装 APK 的 smali 确实将 Class.forName 的 StateFlow 字符串改为 z52；
R8 mapping 同时确认 kotlinx.coroutines.flow.StateFlow 被映射到此名。因此是
跨 ClassLoader 的反射名称被模块自身 Kotlin 依赖混淆污染，尚未进入覆盖逻辑，
不能把首版失败归因于采样覆盖、窗口归属或内联。

修正为从 ROM 的 keyguard/statusBarMode Field.getType() 分别获取 getValue Method，
不再按模块依赖类名查找目标类。没有全局关闭 R8、扩大 keep 范围或 deoptimize。
两种 getter 安装时缓存；热路径和渲染路径保持原设计，不增加高频日志。
日志采集已停止，原始材料在 /private/tmp/mhm-statusbar-color-*，不进入仓库。
修正版仍需安装后验证成功注入及实际图标颜色。

修正版三个 JUnit 测试、Release 构建（含 lintVital）及 APK v2 校验通过。
反编译最终签名 APK，验证 getter 获取位置使用两次 Field.getType/getMethod，
已没有原先的 z52 Class.forName 查找。交付文件为
`HyperModifier-desktop-status-bar-color-fix.apk`，SHA-256：
`1e6ecc55719a4ce110d7f8eee302302ad9f85b4ea8589339f465d54c293c2f28`。
未覆盖安装手机、未提交 Git；没有真机性能对照数据。

## 2026-10-14：返回桌面迟滞及先黑后白

用户反馈修正版能覆盖颜色，但返回桌面存在明显迟滞和先黑后白。静态检查发现
窗口归属变化会排队一次 refresh，并且固定色继续调用 animateChange 后进入
setIconsDark 的动画路径。ROM 的动画路径可等待 pending/deferring transition，
也可创建 ValueAnimator 和延迟 Runnable。这提供了迟滞的实现依据；尚没有
模块函数级测量，不能据此认定所有实际延时均来自此处。

本轮改动：窗口归属回调在主线程同步执行必要的决策，不再为普通进出桌面
排队；只有设置变化仍合并排队。关闭或兼容模式下且没有待恢复覆盖时，归属
回调不触发额外决策。固定色取消正在运行的系统 tint animator 和延迟 Runnable，
将已存在的 pending tint target 改为固定色，再调用 setIconsDark(animate=false)。
不能仅传 false：ROM 的非动画分支不会取消旧 animator，也不修正 pending target。
保留系统 transition 的生命周期标记，不清除 mTransitionPending 等状态。

相同 current/next intensity 不重复分发颜色；空 tint area 不重复调用区域清理。
getter/字段/方法在安装时缓存，固定颜色参数数组复用，没有新增动画、逐帧
Hook、IPC、磁盘访问、轮询或渲染层。锁屏、下拉面板、应用、停用和兼容模式
继续走原决策；设置恢复使用未修改的 appearance region 缓存重新计算。

首次 30 秒 Perfetto 未覆盖用户操作，仅捕获少量静态帧，不能用于解释故障。
用户开始操作后重新录制 `/private/tmp/mhm-statusbar-before-active.perfetto-trace`。
此为旧版基线；修正版尚未安装，不能报告前后性能改善。

重新采集的 process.name 为 NULL，最初按进程名称过滤未命中；改按 layer 名称
确认旧版桌面 Surface 有 1206 帧，其中 3 帧晚呈现且有 App Deadline Missed；
状态栏 Surface 6 帧，没有晚呈现或 App Deadline Missed。trace 未报告错误或丢失。
不同 Surface/渲染管线不可直接比较，也没有模块函数级 slice 或修正版对照，
不能将迟滞归因于主线程耗时或声称已改善帧率。仍需安装后实机验证。
最终三个 JUnit 回归、Release/lintVital 和 APK v2 校验通过，交付
`HyperModifier-desktop-status-bar-immediate.apk`，未安装手机或提交 Git。

## 2026-10-14：固定取色 Hook 性能专项复查

检查对象为本功能两个 Hook 与设置 observer，不能据此评价整个桌面模块的功耗。
本轮 ADB 没有检测到手机，未执行新版静置或转场实测，也没有电量、功率或能耗
对照数据。用户随后明确本轮只做静态分析，不继续采集手机。结论是代码结构上
未发现本功能新增持续工作源；实际功耗仍未量化。

| 范围 | 检查结果及剩余开销 |
| --- | --- |
| 启动 | 类/字段/方法仅安装时解析，两个精确 Hook；没有新增作用域、线程或桥接服务。默认跟随系统仍安装 Hook，以保持实时切换。 |
| 桌面静置 | 无轮询、定时器、帧回调、主动采样或唤醒锁。系统原有采样/flow 变化仍可能触发 updateStatus，事件频率需要真机确认。 |
| 决策热路径 | 禁用、跟随系统、兼容模式、非桌面廉价退出；仍保留 repository 身份和弱引用读取以支持主屏范围及实时启用。启用时两次缓存 flow getter 与若干 Field 读取，没有成员查找、IPC、磁盘、规则解析或每次日志。 |
| 图标渲染 | current/next intensity 一致且区域为空时不分发颜色；变化时复用系统 dispatcher。区域和颜色同时变化可能各触发一次系统分发，没有绕过系统 API 做批量字段修改。 |
| 动画 | 不创建本功能动画；取消遗留 tint animator/延迟回调并更新已有 pending target。后续系统转场仍由原生命周期处理。 |
| 分配与内存 | 固定颜色参数数组/无参 getter 复用；WeakReference 只在目标身份改变时创建，refresh 的反射 varargs 在归属/设置事件时有少量封装。libxposed 拦截框架和系统真实分发本身的分配未实测，不声明零分配。无 View/Activity/Context 强引用缓存。 |
| 锁与设置 | 热路径没有新增 synchronized；设置 observer 的短锁只去重相关值，单个 Runnable 合并刷新，不受无关设置变化触发。样本 ReadonlyStateFlow.getValue 委托到 StateFlowImpl 的 AtomicRef.value 读取，无 monitor；Hook 框架成本仍需运行时采样确认。 |
| GPU/耗电 | 无新增 Surface、模糊、截图、合成层或逐帧 invalidation。仍保留 ROM 的取色采样，不把覆盖颜色等同于关闭系统采样或保证降低功耗。 |

本轮进一步收紧两个路径：锁屏 getter 为 true 后立即返回，不再读取 status mode；
pending target 已等于目标色时不重复写入。没有缓存锁屏/面板动态状态，也没有
省略动画检查或区域检查，避免颜色被旧任务覆盖及影响系统原值恢复。

旧版 30 秒 trace 按已识别 PID 的 sched running duration 汇总，SystemUI 主线程
326.18 ms（2294 个调度片段）、桌面主线程 55.85 ms（186 个片段）。这是整个进程
主线程的运行时间，包含所有模块与系统功能，且只覆盖记录窗口内存在的对应
PID；不能归因于此 Hook，不能与不同管线帧数换算，也不能用于推算耗电。

后续实机对照应使用同一签名版本，比较“跟随系统”和“浅色图标”，保持其他
模块功能、壁纸、刷新率、亮度、温度与操作一致。分别录制桌面静置和多次返回
桌面，检查主线程 sched/CPU 调用栈、FrameTimeline、分配与 GC、Binder、唤醒及
可用的 power rails。设置切换后的初始化阶段不计入静置窗口。设置 A/B 只能衡量
覆盖逻辑增量；两组共有的 Hook 安装/拦截成本需另做相同配置下有无 Hook 的对照。
功率数据不可用时，只报告 CPU/唤醒/渲染指标，不用短时电量百分比代替能耗。

本轮三个范围/默认设置 JUnit 测试通过，Release 构建包含 lintVital 通过。测试不覆盖
真机动画取消、下拉面板恢复或功耗，不能当作运行时性能验证。
