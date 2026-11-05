# 支付宝经典模式柔光底栏

## 分析基线

- 用户提供的 `base(1).apk`：`com.eg.android.AlipayGphone`，`12.12.36.8000 / 212370`。
- SHA-256：`96eccbc1150332d2afc7190c02f29ee274d74ad4106d33527157199d58fd252c`。
- 用本地 aapt2 核对包版本，apktool 反汇编全部 21 个 DEX；APK、smali 和资源清单仅留本地，不加入 Git。

## Lottie 与原生静态图标的证据

经典入口由 `com.alipay.android.launcher.TabLauncherFragment` 管理：

- `tabWidget` 是 `android.widget.TabWidget`，资源为 `android.R.id.tabs`。
- `tabBarContainerView` 是底栏容器，`tabcontent` 是内容 FrameLayout。
- `TabWidgetItemView` 及其子类提供原生点击和选中状态；`TabHost.currentTab` 是当前选中索引。
- `badge.TabBarLottieView` 内有 `normalPlayer`、`darkPlayer`、`guidePlayer`，并持有 `TabWidgetItemView`；其 `playInternal`、`playLottie` 等方法证明该底栏支持 Lottie。`TabIconGifView` 则是另一条动态图标路径。不能据此推断每个标签的基础图标都是 Lottie。
- `TabWidgetItemView.getTabDefaultIcon()` 返回 `Pair<Drawable, Drawable>`：first 为普通态，second 为高亮态。实现读取原生图标资源或构造原生 `AUIconDrawable`。`refreshWithDefaultIcon()` 正是把这对 Drawable 传给 `setTabIcon(normal, highlight)`；后者以 `state_selected` 构造 StateListDrawable。

因此无需解析 JSON、运行 Lottie、截动画中间帧或重画支付宝标识。适配器直接使用应用提供的默认两态图标，保留造型、颜色及识别性；活动推广动画、GIF 和临时返回顶部箭头不会复制到新底栏。

## 接入与恢复边界

设置：柔光玻璃 → 支付宝 → 柔光玻璃悬浮底栏。默认模块预设开启，系统预设关闭。偏好进入本地存储、远程快照和备份 schema；作用域面板和重启选择器包含支付宝。首次在 LSPosed 勾选支付宝并重启；后续开关及底栏额外抬高可实时刷新。

仅支付宝主进程加载专用入口，子进程和嵌入库回调提前退出，不安装通用资源、Context、全局模糊 Hook 或桌面桥接。Hook 限于支付宝自有的 Launcher Fragment 生命周期、TabBarManager 样式更新、默认图标刷新，以及 TLCommonUtils 的内容 margin 写入。不再安装触摸采样 Hook。

通过 `TLCommonUtils.isTabBarNormalMode()` 和 `AiTabManager.JinniModeBarStyle()` 识别普通经典底栏并排除 AI 底栏；`YoungStyle` 是图标外观策略，也用于经典模式，不能用它判断用户模式。找不到已分析的结构或无法读取任一默认图标时保留原生底栏。点击交回原 View 的 `performClick()`，选中索引取自原 TabHost，保留支付宝路由。

复用共享材质和底栏隐藏逻辑；原生独立背景 FrameLayout 在替换期间隐藏并恢复 alpha。内容原有 bottomMargin 在替换开始时移除；应用随后通过 `TLCommonUtils.setViewMargin(View,int,int,int,int)` 写入该内容 View 的 bottomMargin 时，保存应用请求值并将有效值置零，避免先抬高再在后续帧清零。退出后恢复最近请求值，再调用原生 `setTabContentBottomMargin()` 重算当前页应有间距。进入后台、关闭功能、切换 AI/非普通模式或销毁 Fragment View 时释放替换窗口与采样器。

## 性能审查范围

这是代码审查与结构性开销控制，没有真机帧率、功耗或 Perfetto 前后对照：

- 元数据在安装时解析，缓存 Method/Field；不在绘制或触摸中重复查找反射成员。
- 默认两态图标只在首次使用、资源 Configuration/主题变化或原生默认图标刷新后重建。缓存值仅为配置和图片，不保留 Drawable、View、Activity 或 Context。最大 96×96 像素，直接保留原色，不生成不用的单色掩模；五项两态像素存储上限约 360 KiB，不含对象及 GPU 纹理。
- 已移除 LauncherActivity 触摸 Hook，GPU 路径跟随系统渲染，不依赖触摸采样请求。
- 原生状态同步间隔 100ms，逐帧只保留可见性及开关判断。使用专用图标和红点读取路径，跳过共享适配器中图标树扫描和缺失方法探测。
- 与抖音/Spotify 一样使用 `NativeGpuBackdrop`，引用原 `tabcontent` 的 RenderNode；Compose 底栏放在同一个窗口的旁支，源内容不含替换底栏，防止递归采样。完全停用 PixelCopy/Bitmap 截图、GPU 像素回读和软件 View.draw 回退；不能创建 GPU 背景时使用现有无背景材质兜底。同窗口不额外触发源帧重绘或持续轮询。
- 原生状态同步保持 100ms；支付宝不再接入通用滚动避让，不扫描页面滚动 View 树、不追加 scroll padding，也不改变 clipToPadding；其他应用保持原策略。
- 原生图标使用直接的 tab -> Session 弱键索引确定主题，防止按祖先猜测归属失败时在不同主题缓存间反复重建图标。
- 相关偏好通过签名比较并合并主线程任务；无关偏好变化不重放底栏。
- 会话使用弱引用；共享 host 索引改为弱值，避免 WeakHashMap 的 value 通过 Activity 反向保活 key。附着的 View listener/composition 持有活动 host。
- 不修改支付、网络、登录或安全控件；原生 Lottie 的业务状态保持由应用管理，不能声称原生所有动画开销已消除。

## 验证与待办

- 已确认 APK 包版本、上述类/字段/方法、默认图标 Pair 顺序及选中状态约定；脚本逐项核对 7 个类的 18 个 Hook/反射方法签名。
- JVM 回归：350 项，0 失败、0 错误、0 跳过；包含模块/系统预设及打包作用域一致性。Release/R8 构建成功。
- 完整 Lint 能正常运行，但未通过：17 个错误来自现有桌面桥接、小组件、HyperMusicCover、抖音纹理、Spotify 私有 API 和 QUERY_ALL_PACKAGES 等代码；支付宝适配没有 Lint 错误。未屏蔽检查或修改这些独立功能。首次 Lint 的内部类型推断崩溃已通过显式声明 WeakReference 类型规避。
- 首版验证时无 ADB 设备。本轮设备可连接但处于锁屏，未执行可比的支付宝操作序列；遵照用户要求，不进行截图验证。尚未验证理财页实际连续滑动、页面覆盖关系、红点、横屏、无障碍和多个模块共存。
- 真机需要检查：首页/理财/生活/消息/我的（以账号实际配置为准）、重复点击首页、二级页面往返、后台恢复、经典/AI/长辈或学生模式切换、日夜主题、实时开关、系统预设恢复、内容末项是否可滚动露出。
- 应在相同设备、刷新率、温度和设置下检查 FrameTimeline/CPU/分配采样；当前不作帧率、卡顿率或耗电改善声明。

## 用户卡顿反馈后的修订

用户报告采样非常卡、理财页出现原生底部抬高。静态复查确认旧版有 PixelCopy 像素路径，并在每次状态同步中遍历滚动内容；原生 `TabBarManager.setTabContentBottomMargin()` 会通过 `TLCommonUtils.setViewMargin` 写入非零 bottomMargin，与模块后续清零形成重复布局。这些是代码层面的热点与冲突证据，尚未用真机 CPU/分配采样确认各项在用户卡顿中占比。

本轮改为上述同窗口 RenderNode 路径、事件触发的滚动避让和原生写入时的 margin 替换。margin Hook 只匹配已登记的内容 View，其他 View/左右上边距/关闭状态继续走原生实现，不替换框架 View.setLayoutParams。仅替换状态中、该内容 View 的有效四边 margin 与当前值完全相同时跳过原工具函数的重复 setLayoutParams，防止 layout → 原生预留重算 → 相同 layout 的反馈循环；实际变更仍沿原调用链，左右上边距变化不会被吞掉。会话和 View 索引维持弱引用，保留启停、后台、模式切换时的恢复。

新增回归覆盖：理财页反复写回不同 margin 时有效值始终为零，关闭后恢复当前页面值；重复启用不把零误记为原值；后台往返和原生零预留；1200 次稳定帧判断只触发一次内容发现，页面变化/延迟测量/inset 变化仍会刷新，既有应用连续刷新策略保持。

本轮最终验证：350 项 JVM 测试通过，Release/R8 构建及 APK v2 签名校验通过。完整 Lint 报告仍有 17 个既有错误，本轮支付宝/共享内容刷新改动无错误；未屏蔽检查。没有真机连续操作的前后对照数据，不声称帧率、卡顿率或功耗已有量化改善。

## 末尾继续上拉时预留区灰块

用户进一步明确灰块在“底栏所在的抬高区”，首页/理财滚到底继续上拉时偶现。不能仅凭这一现象认定共享 GPU 采样有问题，本轮不修改 NativeGpuBackdrop、抖音或 Spotify 的背景路径。

静态定位到通用 `InjectedScrollableContentBottomPadding`：它会为支付宝原生滚动容器增加整段底栏高度的 padding，并强制 `clipToPadding=false`。这改写了应用自定义刷新/回弹容器的可见区域，额外预留段可能在末尾继续拖动时显露为矩形底色。此为与反馈吻合的结构性原因，尚无不截图条件下的真机复现证据，不能将其表述为唯一已证实根因。

支付宝现在不再提供通用适配器的 `resolveContentHost`，因此不创建滚动 padding 管理器，不追加 padding，也不修改原生裁剪或过度滚动模式。内容 View 仍独立提供给 `resolveGpuSource`；上一轮在原生 margin 写入时抑制抬高和无效重复 layout 的逻辑保留。移除已经不需要的页面切换 Hook。无新逐帧操作、反射、截图、像素回读或计时任务。

验证重点：首页与理财末尾连续拖动、回弹结束、切换页签、末项可见性、启停及后台恢复。遵照用户要求不截图；灰块是否彻底消失仍需实际操作确认。

本轮预留区修订验证：350 项 JVM 回归通过，Release/R8 与 APK v2 签名校验通过；Lint 报告仍有 17 个既有错误，支付宝代码无错误。未改共享 GPU 背景实现，未截图；没有实际末尾拖动复现的确认结论。


## 深色模式底部 edge-to-edge

2026-10-20。用户指出支付宝深色模式没有 edge-to-edge。适配此前设置
`manageWindowImmersion=false`，隐藏原生底栏、移除 bottomMargin 并不会改变窗口对
系统导航栏区域的布局。现在对经典底栏启用共享 `InjectedBottomNavigationImmersion`，
同时设置 `requestImmersiveInsets=true`。

仅在底栏替换期间请求 `LAYOUT_STABLE | LAYOUT_HIDE_NAVIGATION`，透明导航栏及分隔线，
关闭导航栏对比度遮罩；首次应用和退出恢复时请求 inset 重新分发。使用底部专用路径，
保留支付宝状态栏原布局。明暗主题都启用，不依赖应用原先是否主动沉浸。
关闭功能、后台、模式切换或销毁时沿现有生命周期恢复窗口原有布局标志、导航栏颜色、
分隔线、对比度策略和图标明暗。此前对滚动 padding、clipToPadding 和 margin 的边界保留。

性能检查为静态代码审查：复用既有共享宿主和廉价差异判断，不新增 Hook、监听器、轮询、
IPC、截图或采样层；只在应用/恢复阶段额外请求 inset，不逐帧 requestApplyInsets。
深色模式首次打开、二级页往返、经典/AI 模式切换、启停和导航方式切换仍需真机验证。

本次 edge-to-edge 调整验证：381 项 JVM 回归通过，Release/R8 和 APK v2 签名校验通过；
Lint 仍有 17 项已有错误、205 项警告。未新增专项窗口运行测试，未进行真机性能测量或截图。
构建日志 `/private/tmp/hmc-alipay-dark-e2e.log`。

## 二级页面返回崩溃与原生底栏闪现

2026-10-24。从已连接设备的 crash buffer 读取到 22:56:21、22:56:44、23:07:33
三次相同主线程崩溃：系统请求 AssistStructure，遍历 Compose 的虚拟无障碍节点，
`ComposeAccessibilityNodeProvider.createAccessibilityNodeInfo` 查询字符串
`0x7f090090` 时进入支付宝 `BundleResources.getString` 并抛出 NotFoundException。
修复前本地 Release 的资源表确认该 ID 为 Compose 的 `string/tab`；R8 mapping
也将日志中的 `x5.u` / `z1.createAccessibilityNodeInfo` 对应到 Compose/AndroidX
无障碍提供器。用户补充同 Activity 内二级页面普遍触发，另开 Activity 的小程序不触发。
原始日志留在 `/private/tmp/hmc-alipay-crash.txt`，不提交包含设备应用信息的完整日志。

支付宝注入的 ComposeView 现在单独使用模块包 Resources、Assets 和主题，窗口等
服务仍委托给原 Activity。保留标签的无障碍语义，不关闭 AssistStructure，也不 Hook
支付宝/框架的全局资源读取。模块资源上下文只在 host 创建时获取，没有全局 Activity
缓存；其他适配器继续使用各自原有 Context。

原实现 Launcher Fragment onPause 直接 dispose host，会在转场期间恢复原生底栏，
返回再创建 Compose、GPU backdrop 和宿主。现在暂停复用 host、隐藏替换栏并停用
背景采样，沿共享 retainNativeSuppressionDuringCover 策略继续隐藏原生栏；恢复时
复用现有 host，去掉一次重复强制同步。暂停仍恢复窗口沉浸参数和内容 margin，
关闭功能、退出支持模式、销毁 View 时仍完整 dispose 并恢复原生 alpha/无障碍状态。

性能检查为代码审查与结构性分析：减少页面往返时的 Compose/背景对象重建，不新增
Hook、轮询、定时任务、IPC、像素回读或渲染层。Resources 查找不进入逐帧路径。
没有 Perfetto、FrameTimeline 或耗电前后对照数据，不声称量化性能改善。

新增 Robolectric 回归模拟宿主缺失 Compose tab 字符串，确认原宿主读取抛错、模块
上下文读取成功，且窗口服务仍由 Activity 提供；修订后的测试同时模拟包名查询
抛 NameNotFoundException，确认注入上下文不会进行额外的包名查询。
真机页面往返、原生栏闪现、主题/实时启停及其他模块共存仍需安装新包后复测。

最终验证：429 项 JVM 回归全部通过，Release/R8、Lint Vital 和 APK v2 签名校验
通过；未运行完整 Lint。构建日志 `/private/tmp/hmc-alipay-return-fix-build.log`。

### 包可见性导致的新底栏无法挂载

同日用户反馈修复包底栏空白，稍后回退官方底栏。设备日志明确记录
`Could not attach Alipay classic navigation`，异常为 `createPackageContext` 查询
模块包时抛出的 `PackageManager.NameNotFoundException`。上一轮 JVM 测试未覆盖
宿主看不到模块包的条件，不能以其通过代替实际注入验证。

改为使用 libxposed `getModuleApplicationInfo().sourceDir` 提供的 APK 路径，通过
公开的 ResourcesProvider/ResourcesLoader 加载编译资源，注入 View 的 Resources
仅包含系统资源与模块资源。APK provider 在进程内复用，不保留 Activity/Context；
包文件只在首次构建资源时打开，不查询宿主 PackageManager。每个 host 持有独立
Resources，宿主配置变化时按差异更新，主题和窗口服务边界保持。
日志保存在 `/private/tmp/hmc-alipay-missing-bar-logcat.txt`。

修订后 429 项 JVM 回归通过，Release/R8、Lint Vital、APK v2 签名验证通过；
构建日志 `/private/tmp/hmc-alipay-apk-resources-build.log`，最终测试日志
`/private/tmp/hmc-alipay-apk-resources-tests.log`。修复包已安装并重启支付宝。
本轮自动启动后未取得新进程的明确挂载日志，不能声称日志验证成功；随后用户在
手机上复测并反馈“没问题了。算是修好了”。这是用户实测反馈，没有执行截图、
系统化页面覆盖回归或性能量化测试。
