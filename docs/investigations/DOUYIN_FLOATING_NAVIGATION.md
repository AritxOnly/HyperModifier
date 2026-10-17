# 抖音 40.7.0 柔光底栏

## 参考与实现

参考包：`reference/Douyin.apk`，从用户提供的 `Downloads/base.apk` 复制并命名，原文件保留。包名 `com.ss.android.ugc.aweme`，versionCode `400701`，versionName `40.7.0`。两个文件 SHA-256 相同：`86ee431640c9fe5035135092079af2dcddf947feb7e02cb78aaf459c821f80c0`。reference 按仓库惯例忽略，不提交二进制。

DEX 和资源静态核对：

- `classes48.dex`：`com.ss.android.ugc.aweme.main.MainActivity` 声明 onCreate/onResume/onPause/onDestroy/dispatchTouchEvent/onWindowFocusChanged。
- `classes23.dex`：`MainBottomTabContainer` 外容器。
- `classes18.dex`：`MPFBottomTabComponent.getContainerView()` 返回 `X.0gnN`，这是实际管理显示、背景和触摸的内底栏。其内容 `root_view` 是 LinearLayout；原生 BottomTab 被按配置顺序加入。
- `BottomTab.getCurrentTabId()` 返回原生 ID；`getTabTitle()` 返回 TextView；`isSelected()` 读取原生选中状态。`homepage_publish` 为独立发布入口。MPFBottomTabComponent 的构建链给原生 BottomTab 安装点击与触摸监听。
- 主页面布局 `0x7f0d167a`–`0x7f0d1680` 使用 `1mw` 内容容器、`cvf` 分隔线和 `cvc` 外底栏。

复用项目 NativeViewBottomBarNavigation 与现有 Deadliner MiuixFloatingTabBar/SoftGlassFloatingActionButton。导航顺序、标签、选中状态从实际原生 View 获取；玻璃控件调用原生 performClick，保持应用路由和发布流程。隐藏外底栏，读取内底栏的原生显示状态；内层 setAlpha 会被抖音防烧屏逻辑忽略，因此不使用它来隐藏。内底栏的直接触摸在它的 dispatchTouchEvent 入口拦截，防止不可见底栏仍触发 OnTouchListener。首页/朋友页根据玻璃底栏的屏幕坐标加 8dp 间隔调整每个 VideoViewHolderRootView 内独立 `i6` 控件层的 bottomMargin；视频层 `pn7` 和页面外壳 `1mw` 保留原有尺寸。离开视频页或停用时恢复。进入视频页时有一次限额扫描，后续新页面由构造/附着事件登记；底栏 alpha/无障碍状态及分隔线 alpha 沿用恢复路径；不修改 native View 的点击监听。

设置页：柔光玻璃 → 抖音 → 柔光玻璃悬浮底栏。模块默认启用，系统默认关闭。新增偏好包含在本地存储、远程快照和备份 schema 中。首次注入需要在 LSPosed 作用域选择抖音并重启抖音；后续设置变化可实时应用。

此适配依赖参考版本的混淆内底栏类 `X.0gnN`，不承诺其他抖音版本兼容。

## 性能审查

检查范围：进程启动、主线程帧回调、反射/对象分配、防烧屏 alpha 策略、设置刷新、生命周期、采样与 GPU 路径。

- PackageLoaded 对抖音直接退出，PackageReady 仅在主进程安装本功能。子进程不连接设置、不安装全局资源/Context Hook、不启动桌面桥接。
- 生命周期 Hook 限于 MainActivity；触摸拦截限于实际底栏类，不挂全局 View 的触摸/绘制 Hook。
- 每帧先检查前台、设置开关、原生显示状态、窗口焦点和 IME；原生标签同步限为 100ms。原生可见性读取缓存的内底栏弱引用；选中状态直接读 View，绕过共用适配器的缺失 getStatus 反射探测。
- 发现共用滚动内容 padding 适配器会在每次同步时全量扫描页面、分配类层级列表和反射查询滚动方向。本适配使用弱缓存的视频控件层与布局事件，数值相同时不请求布局；只在进入视频页时扫描一次已有页面，新页面通过附着事件登记，避免周期性扫描。应用动态更新自己的 margin 时刷新基线；恢复时只有当前值仍属于模块才写回。
- 标签方法按 Class 缓存，分隔线查找只在替换时解析一次；元数据缓存不保存 Activity、Context、View。设置监听仅比较总开关、抖音开关和底栏抬升，按弱 Activity 合并复用 Runnable；无关设置不重放 UI。缓存内底栏 `root_view` 的弱引用，直接读取其最多八个入口，避免同步时递归遍历标签子树和类层级。
- 用户真机反馈上一版严重卡顿。代码复查发现视频页持续 PixelCopy，每次创建位图、在主线程执行 Bitmap.sameAs 并更新 Compose 玻璃纹理。首页/朋友页现在完全停止采样，使用已有柔光材质无背景兜底；消息/我页关闭逐帧采样，只响应页面变化、滚动、触摸，最小间隔 250ms（最多约 4Hz），静止后停止。软件 View.draw 截图仍禁用。视频页目前不提供实时背景折射，这是性能优先的明确取舍。
- 关闭和销毁恢复原值并移除监听、窗口、采样任务；销毁前先恢复分隔线，再移除 Activity 的弱状态。

以上是代码审查和结构性开销限制，未做微基准或真机前后对照。不能据此宣称帧率、卡顿率、耗电已经改善。

## 验证与真机待验项

初版 Debug/Release 编译与 R8 构建、全量 JVM 回归通过（282 项、0 失败）。本轮 Release/R8/Lint 构建与签名验证通过，284 项 JVM 测试全部通过；新增页面主题、播放避让坐标/原生预留保护回归。新增回归覆盖配置重排后的原生索引、独立发布入口、无发布入口、系统预设关闭和模块作用域声明。

本轮 ADB 已连接设备，但前台正在通话，未切换界面或重启应用。读取到抖音历史累计 gfxinfo（556 帧、43 个 deadline jank），该数据包含过去操作且进程已在后台，不能归因于模块或用作修复前后对比。以下行为尚未真机验证：视频 Surface/Texture 的 PixelCopy 背景、启动广告/同 Activity 覆盖层、发布/登录流程、账号下发的标签变化、旋转/主题、键盘、实时启停及多模块共存。

真机验证应固定设备、刷新率、温度、设置与操作，对比启用/关闭的首页连续视频、商城列表、消息、个人页、发布与返回、前后台切换、键盘和广告。用 Perfetto/FrameTimeline 加 CPU 与分配采样检查主线程、GPU、PixelCopy；同条件记录持续采样的耗电。不得用不同视频渲染管线或累计 gfxinfo 直接归因模块。

## 页面主题规则

首页（homepage_home）和朋友页（homepage_familiar）强制底栏深色。消息和我等其他页面读取抖音原生标签当前文字颜色的对比度来跟随应用主题，避免只跟随系统 uiMode；原生文字透明或不可读时回退应用资源 Configuration。改变颜色不修改抖音原生主题，也不改写系统全局深色状态。

## LiquidTab 采样对照（2026-10-14）

核对源码提交：`e54cdeabb18df07b0943b49b6275ca060ae9fe7d`，仓库 https://github.com/big-dictator/LiquidTab 。本次仅研究方案和核对参考 APK，没有复制或运行上游 Hook。

关键源码：

- [DouyinPlayerTextureHooks.kt](https://github.com/big-dictator/LiquidTab/blob/e54cdeabb18df07b0943b49b6275ca060ae9fe7d/app/src/main/java/io/github/offlineglass/hook/adapters/douyin/DouyinPlayerTextureHooks.kt)：在 FeedVideoPlayerView 的播放器创建范围内选择应用自带的纹理输出，保留播放器 SurfaceTexture 生命周期。其 eligible 明确限制为 40.6.0。
- [DouyinVideoSampler.kt](https://github.com/big-dictator/LiquidTab/blob/e54cdeabb18df07b0943b49b6275ca060ae9fe7d/app/src/main/java/io/github/offlineglass/hook/adapters/douyin/DouyinVideoSampler.kt)：检测到已注册原生纹理后，停止截图并进入 GPU 场景路径。其他 SurfaceView 通过工作线程异步 ScreenCapture.captureLayers 截取视频 SurfaceControl，裁剪到玻璃区域加模糊扩展，Bitmap.wrapHardwareBuffer 包装结果，没有软件像素遍历。再回退到直接 PixelCopy 视频 SurfaceView（软件 scratch 缓冲复用）或工作线程 TextureView.getBitmap。
- [GlassHostLayout.kt](https://github.com/big-dictator/LiquidTab/blob/e54cdeabb18df07b0943b49b6275ca060ae9fe7d/app/src/main/java/io/github/offlineglass/hook/GlassHostLayout.kt)：缓存场景 RenderNode 引用，按场景/几何变化重新记录，不逐帧重扫整个页面；玻璃通过硬件 Canvas.drawRenderNode 和 RenderEffect 绘制。

与本项目旧路径相比，应优先借鉴“复用应用原生 GPU 内容”和“直接获取视频图层”，而非只调整截图间隔。旧 Window PixelCopy 会生成软件 Bitmap、在主线程执行 sameAs，再把 Bitmap 交给 Compose 上传纹理；SurfaceView 的视频图层也不一定包含在 Window buffer 内。现有 HardwareViewBackdropCapture 仍会通过 ImageReader/ByteBuffer 读回 CPU，不能视为已经具备 LiquidTab 的原生 GPU 场景路径。

40.7.0 参考包进一步静态核对：FeedVideoPlayerView.LJJIJIIJIL 仍存在；创建链改为 `X.0GO1.LJIILIIL(ViewGroup, boolean)`，当 boolean=false 时进入 `X.0Fds(ViewGroup)`，该 wrapper 构造应用自带的 KeepSurfaceTextureView。LiquidTab 使用的 `X.0wMt` 在本版本已经是无关类，没有同名工厂方法。因此方案存在可适配入口，但必须使用 40.7.0 的签名重新实现和验证，不可直接使用 40.6.0 的混淆名。

接入约束：本项目目前是独立 Compose application-panel 窗口，Miuix LayerBackdrop 从 Bitmap 生产图层。要使用保留的原生 RenderNode，必须构建正确的同窗口/GPU 背景桥接，并排除玻璃自身及其他窗口，保留几何、滚动/裁剪和生命周期；单改采样开关或把 source.draw 换为 drawRenderNode 后读回 CPU，无法消除整条读回链。

上游还存在需要按本项目性能原则收敛的部分：视频捕获配置最小间隔 8ms、frameScale=1，连续 frame pump 最大 120Hz；部分 Hook 做 stackTrace、版本查询和磁盘开关探测。不能据其源码宣称性能已经更好，也不应直接照搬这些频率和高开销探测。优先验证原生 TextureView 场景路径；改变 SurfaceView/TextureView 渲染管线后，要在相同设备、刷新率、温度、设置、视频与操作下用 Perfetto/FrameTimeline 比较，同时观察 HDR/色彩、功耗和播放生命周期。

上述源码研究阶段没有改变 APK，随后进行的原生 GPU 桥接试验见下节。尚未完成 GPU 路径真机实验。


## 原生 RenderNode 桥接试验

用户授权试验后新增 `NativeGpuBackdrop`，接入抖音底栏和 Spotify 底栏/原生播放胶囊。复用框架已经产生的 View.mRenderNode，将裁剪区域记录到一个应用自有 RenderNode，再作为 Miuix LayerBackdrop 的内容。原始显示列表保持由目标应用更新；不调用 View.draw 或 updateDisplayListIfDirty，不分配 Bitmap，不运行 ImageReader/CPU 像素读回，也不执行 Bitmap.sameAs。桥接按裁剪、尺寸、来源位置变化重新记录；源 View 与目标 View 只保存弱引用，反射访问计划按框架字段缓存。

同窗口直接复用应用帧，不因源 OnDraw 再调度自身帧；跨窗口仅在源窗口 OnDraw 时合并投递一次消费者 invalidate。消费者尚未附着时不缓存窗口关系，避免将 Spotify 同窗口误判为跨窗口并产生重绘循环。停用/失焦/暂停停止重绘请求，销毁移除观察者、回调和显示列表引用。成功创建桥接时停用旧采样器；Spotify 也不会启动旧硬件采样器的读回线程。反射或硬件条件不支持时沿用已有采样/材质兜底。

原值恢复与播放器生命周期优先：这一轮不改变抖音 SurfaceView/TextureView 的输出选择。已有 TextureView 可参与原生 GPU 场景；独立 SurfaceView 的视频内容不会自动出现在 View 的 RenderNode 中。抖音保留视频视口，只让视频控件避让底栏；底栏复用底下的应用 View 场景。要覆盖独立视频图层，后续仍需异步 SurfaceControl/HardwareBuffer 路径或经验证的原生 TextureView 输出策略。

Spotify 原实现确实是硬件 Canvas，但会将 ImageReader 中的像素逐行复制到 ByteBuffer，再生成 ARGB_8888 Bitmap；它不等同于无 CPU 读回的保留 GPU 场景。本轮其导航与播放胶囊共享一个 GPU 区域，继续使用原有光学配方与几何。

首轮只开启这两个应用。其他应用保留旧路径，避免未经设备验证直接切换 SurfaceView、WebView、原生模糊或自定义渲染器。设备前台仍是通话界面，未安装/启动试验来打断通话；没有真机帧率、功耗、HDR 或跨窗口渲染正确性的结论。原生场景更新是否在所有 RenderEffect 缓存路径中持续传播，需在真机上检查；构建与现有回归不能替代这一验证。

试验版 Release/R8/Lint 构建、签名验证通过，284 项已有 JVM 回归全部通过。尚无本轮真机视觉或 Perfetto 前后对照数据。


## 抖音启动崩溃时序修复

设备 crash buffer 连续两次记录 MainActivity.onCreate → AppCompatActivity.setContentView → AppCompatDelegateImpl.applyFixedSizeWindow，ContentFrameLayout 为 null，尚未出现 Retained GPU backdrop 的创建日志。参考包 classes44.dex 确认 applyFixedSizeWindow 从 sub-decor 查找 android.R.id.content 后立即调用 setDecorPadding；崩溃点在原生内容初始化，日志没有直接指向 RenderNode 绘制。

代码审查发现 DouyinHooks 在 chain.proceed（原生 onCreate）之前调用 prepare，EarlyBottomBarSuppressor 会读取 window.decorView，可能提前触发 PhoneWindow 的 Decor 创建。这是现有实现的初始化风险点，本轮移除前置 prepare：只在原生 onCreate 成功返回后登记并附着底栏。焦点、resume、pause、触摸和 destroy 回调均使用主线程弱 Activity 集合检查创建状态，防止原生创建前的回调提前触及窗口；原生创建异常不额外初始化模块 UI。该集合不强引用 Activity，不添加定时器或 IPC；高频回调增加一次弱集合查询。Spotify 的 GPU 桥接保持原样。

Release/R8/Lint/签名验证与 284 项已有 JVM 回归通过。设备前台仍是通话界面，未自动切换应用进行重现；目前属于根据崩溃栈消除高风险初始化顺序，不能把未验证的因果关系或启动成功当作已确认结论。需安装修复包后重新启动抖音检查；若原生内容初始化仍失败，继续采集新的同次启动日志并区分其他模块/版本因素。


## 闪烁与内容层避让修正

代码审查识别到跨窗口复用整个 Decor、Window PixelCopy 自采样，以及模块隐藏 alpha 与原生显示判断可能形成反馈。抖音改为 Decor 内同窗口 Compose 悬浮层，GPU 来源限定为兄弟内容节点 `1mw`；禁止同窗口截图兜底，GPU 不支持时仅使用材质兜底。不再改写抖音窗口沉浸式标志，原生显示判断不依赖模块正在压低的 alpha。共享 GPU 桥接仅在来源布局改变时重录几何，同窗口 OnDraw 不主动调度自身帧。

原来的 `1mw.bottomMargin` 会缩小整个页面，表现为底部硬性留空。本轮删除该操作。参考包布局 0x7f0d0965/0975/0976 确认 VideoViewHolderRootView → MonitorScrollFrameLayout → PenetrateTouchRelativeLayout (`i6`) 为独立控件层，视频在独立 `pn7` 内。仅对结构匹配的控件层留出底栏空间，不移动底栏、不缩小视频背景或页面壳。避让计算减去每个视频页面在 pager 内的滑动偏移，避免滑动时追逐屏幕坐标；原生自定义 margin 作为下限保留，恢复时只恢复模块仍拥有的值。

控件发现限于进入视频页的一次最多 2048 View 扫描，以及新视频页面附着的一次直接子节点定位。持续更新使用弱缓存、布局事件和合并 Runnable，不增加定时器、逐帧扫描、反射、IPC 或截图。生命周期注销布局观察者并撤销待执行工作。新增构造 Hook 限于抖音主进程的 VideoViewHolderRootView，禁用时廉价退出。

以上为结构性风险消除与静态布局验证；连续闪烁是否消失、实际视频/字幕/触摸几何及 GPU 帧表现仍需设备验证，不能据此声称帧率或耗电改善。

本轮最终 release/R8/Lint 构建及 APK 签名验证通过；284 项 JVM 测试通过（0 失败），包含视频页面上下滑动偏移抵消与应用原有避让量保留。设备前台为 QQ 通话界面，未安装或切换前台，因此视觉、连续闪烁和性能对照仍未测量。


## 同窗口 Compose 生命周期崩溃

10-14 21:39:29 的新 crash buffer 为 `ViewTreeLifecycleOwner not found from DecorView[SplashActivity]`。使用上一轮 release mapping（r8-map-id d1412bb0824552fb05da84d54b396793730cf7c6950ba12c042bff809bf0bc3f）还原：WindowRecomposer_androidKt → AbstractComposeView.onMeasure。此异常来自注入 Compose 的宿主窗口 Recomposer 查找，不是先前的 AppCompat ContentFrameLayout 空指针。

切换为同窗口底栏时，仅在 ComposeView 设置模块 ViewTreeOwner 不够：默认 WindowRecomposer 从宿主 Decor 查找，而宿主没有本模块类加载器兼容的 LifecycleOwner。本轮为 inTreeOverlay 显式设置 `createLifecycleAwareWindowRecomposer(lifecycle = owner.lifecycle)` 的 parent composition context，沿用已工作的 Spotify 路径。模块 owner 销毁时关闭 Recomposer，移除 ComposeView 时也取消其任务；不改宿主 Decor 的 owner，不新增截图、轮询或每帧工作。


验证：最终 release/R8/Lint 与签名校验通过，284 项 JVM 回归 0 失败。通过 adb install -r 安装修复包、force-stop 后重新启动抖音；进程 PID 28724 持续存在，前台为 SplashActivity（40.7.0 的入口活动），截图显示首页视频、字幕和柔光底栏已绘制。crash buffer 最新记录仍为修复前 21:39:29 PID 28151，无本次启动的新崩溃。截图可观察字幕位于浮动底栏上方，不能据静态截图判断连续闪烁或帧率。设备拒绝 adb swipe/tap（INJECT_EVENTS 权限），因此未完成自动滑动/朋友/消息页面切换验证，不改变设备安全设置。没有 Perfetto/帧率/功耗对照结论。


## 底部沉浸、视频展开与独立进度条

用户反馈导航栏不沉浸、控件避让不足、视频未延伸至悬浮底栏下方。上一版只对 `i6` 调整 margin，保留了 feed 内部原生 BottomSpace 预留，并完全关闭窗口沉浸管理；独立 seekbar 不在 `i6` 中，因此没有跟随抬升。

40.7.0 布局 0x7f0d0950/0951 为 DisallowInterceptRelativeLayout(2) → RTViewPager + BottomSpace；普通分支 RTViewPager 使用 ABOVE bottom_space，另一分支使用自定义相对布局规则。本轮保持两类原有相对规则与顶部状态栏间隔，通过将结构匹配的 BottomSpace 高度收为 0 来解除底部约束。仅视频页启用，弱缓存从已确认 RTViewPager 向上至 `1mw` 的容器链及页面根，恢复 MATCH_PARENT、移除底部 margin/padding；不修改播放器 Surface/Texture 的选择或原生宽高比。离开视频页/停用时逐字段仅在值仍由模块拥有时恢复，销毁注销观察者。

窗口沉浸仍由模块 owner 在原生 onCreate 返回后创建，延用底部专用 legacy layout 标志、透明导航栏/分隔线与关闭 contrast scrim。仅在底栏替换开始、页面主题变化和重新获得焦点时维护，停用时恢复；不在每帧或 100ms 同步中重复改写 flags。导航栏图标随首页/朋友强制深色、消息/我原生主题同步。

`i6` 安全间隔从 8dp 增至 48dp（新增 40dp 空间），避让仍扣除页面滑动偏移。独立 DProSeekBar（布局 0x7f0d0cea）以 ProSeekBarView `wb4` 可见轨道为锚点，CustomizedUISeekBar 以自身轨道为锚点，向上 translationY 至玻璃底栏上方 10dp；不强制显示原生自动隐藏的进度条，不改变 track 的测量/触摸监听。进度控件通过构造后附着事件登记，不新增周期性树扫描。

新增开销限于页面/进度条构造的弱集合登记、附着后的直接定位、进入视频页时一次限额扫描、结构匹配容器的布局事件合并处理。稳定几何不写 layoutParams；事件中复用坐标数组，恢复按字段检查所有权，没有每帧反射/IPC/截图。仍需真机检查布局是否被原生不断覆写以及实际视频底部表现；不据静态代码声称卡顿率或功耗改善。


## 开屏导航栏切换与视频层背景采样

用户继续反馈：背景未采到视频，开屏有闪屏，导航栏沉浸会间歇失效。此前同窗口方案复用 `1mw` 的 RenderNode，独立 SurfaceView 的像素不在 View 场景中；把该路径当作视频背景采样是不完整的。之前在每次底栏因焦点/覆盖层隐藏时恢复窗口配置、重现时再次施加，且原生可能继续改写窗口颜色/布局标志，存在相互切换的结构性风险。

沉浸改为原生 onCreate 返回后、第一次模块 UI 测量前开启，并维持到功能关闭或 Activity 销毁。暂时失焦/遮挡只暂停玻璃与采样，不恢复窗口标志。对注册主窗口的 PhoneWindow 导航栏颜色、分隔线、contrast 及 Window.setFlags，以及注册 Decor 的 View.setSystemUiVisibility，拦住底部沉浸相关值被原生覆盖；其他窗口/View 使用弱集合查询立即退出。清除 FLAG_TRANSLUCENT_NAVIGATION、保留 DRAWS_SYSTEM_BAR_BACKGROUNDS；关闭时先注销拦截，再恢复原窗口颜色、标志、图标配置。新增 Hook 仅安装在抖音主进程，无反射/IPC/解析/分配的非目标退出路径；目标写入发生在原生已执行的窗口 setter 中，不新增逐帧维护或轮询。

独立 SurfaceView 使用自己的 SurfaceControl 子树、`captureLayers(args, listener)` 异步截取；capture binder 请求和缓存反射 API 调用都在专用 worker。CaptureArgs 指定本进程 UID，裁剪范围仅悬浮底栏及 32dp 模糊扩散边缘，宽度最多 256 像素，上限 15Hz，最多一个在途请求。结果 HardwareBuffer 用 Bitmap.wrapHardwareBuffer 建立硬件 Bitmap 后关闭原句柄，没有 ImageReader、ARGB CPU Bitmap、像素行复制、同步主线程等待或整窗口 PixelCopy；不包含悬浮玻璃自身及其他应用浮窗。硬件帧进入既有 Miuix LayerBackdrop 材质管线；消息/我等非视频页继续复用 View GPU 场景。

只在播放页可见且真实 Surface 存在时更新，捕获完成后调度下一次，后台/停用/销毁取消帧任务。源丢失时等布局事件重新探测；树扫描只在激活、缓存失效或新 Surface 需要定位时执行，稳定采样不遍历树。隐藏 API 方案缓存到进程级元数据，支持 Consumer/ObjIntConsumer 两种 callback；不支持或权限拒绝时只记录一次失败并停止重试，保留已有材质兜底。已发布的硬件 Bitmap 由 Compose/RenderThread 引用生命周期释放，避免仍在绘制的帧被提前 recycle；丢弃的未发布帧及时回收。

构建期间遭遇 Lint Kotlin UAST 内部异常（Bad parent: KtTypeArgumentList），将反射与泛型 SAM 构造移至 Java NativeLayerCapture 缓存桥接以保留 Lint 检查，没有关闭检查。此处仍必须用设备日志确认真正返回 HARDWARE 帧，不能把编译通过等同于采样已生效。


## 深入比对 LiquidTab：原生纹理、生命周期与 HyperOS 状态

实时日志采集确认先前的 Surface 路径在开屏返回 `256×55 HARDWARE` 帧，设备版本确为 40.7.0/400701。日志缓冲只有 64KiB，普通 `logcat -d` 时启动诊断早已被覆盖；后续诊断改为事先启动仅本模块标签的实时采集。用户报告滑动一次后采样停止，参考项目的 SurfaceHolder 创建/销毁、旧请求取消与超时恢复不能省略。

本轮接入 LiquidTab 优先选择原生纹理的策略，但按本参考 APK 重新核对映射：FeedVideoPlayerView.LJJIJIIJIL 的 `a` 为父 ViewGroup；在该初始化方法的 ThreadLocal 作用域内，X.0GO1.LJIILIIL(ViewGroup, boolean) 的 false 分支使用 X.0Fds / KeepSurfaceTextureView。只在主页 Activity/SplashActivity 上下文且功能开启时替换此次工厂调用的参数，finally 清理作用域；不触及原生播放器表面监听、解码器、宽高比或释放流程。停用后新工厂调用立即沿用原参数；正在播放的原生纹理由播放器原有生命周期处理，不通过强制重建打断播放。

发现并修正实际帧入口：Android 36 TextureView 的 mUpdateListener 调用 `updateLayer(); invalidate()`，`updateLayerAndInvalidate()` 主要用于透明度/可见性改变。首个原生纹理测试包监听后者，故用户不操作时玻璃缺少持续刷新通知。最终改为监听 `updateLayer`，只处理已登记、附着的 feed 纹理，其他 TextureView 立即退出。实际视频帧更新模块 MutableIntState 版本；仅在 GPU 背景绘制作用域读取它，使 Miuix LayerBackdrop 重新记录 GraphicsLayer。没有逐帧重组导航项目、截图、树扫描、反射、IPC 或额外常驻帧泵。订阅者使用弱引用 CopyOnWriteArrayList 的索引循环，消除每帧迭代器分配；生命周期注销，停用/不可见场景不更新状态。Spotify 不订阅此路径。

Surface 兜底保持局部硬件截取，但补齐当前 SurfaceHolder 回调、新 feed page 附着通知、请求 ID 与代次保护、600ms 单次请求 watchdog 和最多两次超时重试。旧来源回调只回收未发布帧，不能释放/占用新来源请求槽；失败后停下，等待原生来源事件重新恢复；缺失来源不常驻探测。底栏暂时因焦点/IME/遮挡隐藏时保留原生底栏抑制和内容几何，避免恢复与重新展开的视频尺寸反复切换；真正停用和销毁仍恢复。

HyperOS 参考链路包含 `getDecorViewImmersiveStub().setForceImmersiveNavBar(true)` 与 `updateColorViews(null, false)`。本轮保存原生 force 状态，原生 onCreate 完成后开启，在注册的 stub 上拦住复写，停用先注销再恢复；反射方法仅在窗口注册时解析，不放入帧路径。标准窗口 flags/颜色作为兼容兜底，顶部状态栏 fitting 保留原生。不会提前创建 AppCompat Decor。

实时日志还显示 WebView 分包 PackageLoaded 在抖音 PID 内安装了全局 Glass 半径 Hook，说明只以 param.packageName 排除抖音不够。本轮 PackageLoaded/PackageReady 同时判断实际进程名，抖音主/子进程中的分包回调直接退出。主进程只安装抖音适配，子进程不装该功能、设置加载器、全局 View/资源 Hook 或桥接服务。

最终 release/R8/Lint、APK 签名验证通过，284 项 JVM 回归 0 失败。设备在安装原生纹理修复前从 ADB 断开，用户选择先拿 APK 手动测试；最终原生纹理/实际帧通知/HyperOS force 状态及滑动恢复没有本轮真机确认。此前成功的 HARDWARE 日志属于 Surface 测试路径，不能冒充最终纹理路径的验证，也不能据此声称帧率、卡顿率或耗电改善。


## 再次启动闪屏：移除视频区域的布局反馈路径

用户报告启动仍闪屏，并怀疑视频播放区域被反复计算。代码审查发现上一版 DouyinFeedExpansion 不仅收起 BottomSpace，还沿视频页、RTViewPager 至 1mw 改写多层高度、bottomMargin 和 paddingBottom，并在布局事件后再次施加。原生重新设置尺寸时存在反复 requestLayout 的反馈风险；这属于静态发现，尚无真机 trace 证明它是全部闪屏来源。

本轮完整删除上述视频尺寸改写、祖先缓存、布局监听及其排队任务。核对 40.7.0 BottomSpace 字节码：没有自定义 onMeasure；setVisibility 与原生调用共享 LIZIZ(int,String)，最终调用 View.setVisibility。改为主页上下文构造后、首次测量前登记这个原生占位 View，在功能开启期间统一使用 GONE，让原生相对布局自行分配 pager 空间。保留原生 LayoutParams 与视频比例，不再修改视频页及其祖先的高度、margin 或 padding。该占位策略不随视频页/消息页切换或暂时遮挡反复开关。

登记使用弱 View 键及弱 Activity 引用；拦截中记录原生最新请求的 visibility，模块自身写入有重入保护。实时停用恢复最新原生 visibility，销毁恢复并移除登记；相同状态直接退出。新增逻辑不引入帧回调、树扫描、IPC、截图或常驻定时任务。字幕/互动控制区的 margin 与独立进度条的 translation 避让继续保留，不操作播放器容器。

验证：release 构建（含 R8、Lint）和签名校验通过，284 项 JVM 回归全部通过。此轮检查覆盖布局反馈、弱引用生命周期、启停恢复和热路径结构；未完成设备启动录像或 Perfetto 前后对照，不能据构建结果声称闪屏、帧率或耗电已改善。


## 场景主题与遗漏的底部占位实例

设备重新连接后，对已安装模块 APK 校验 SHA256 为 3106559ec53c0a16054a4c8b0eed3424ed023bd0a96d5abb68d22056ca0da31a，确为上一轮 stable-viewport 包。读取 Activity View Hierarchy：Decor、1mw、6sd 均为 1220×2656；两个 BottomSpace 仍为 VISIBLE、占据 y=2497..2656 的 159px，RTViewPager 和视频页高度为 2497；第三个 BottomSpace 为 GONE，对应 pager 高度为 2656。说明上一轮仅靠构造时 Context 确认所属主页并不完整，存在遗漏实例。此处没有证据将所有遗漏归因为某一条预加载路径。

本轮对构造时无法识别 owner 的 BottomSpace 也保留弱登记，在附着时根据 root 的 Activity Context 绑定，测量前施加占位策略；同时在原生 onCreate 完成后的 ActivityState 初始化时做一次最多 2048 节点的现有占位登记，覆盖 Hook 安装前预加载的 View。扫描跳过视频根和原生底栏子树，本次层级剪枝后为 1373 节点、3 个占位实例，位于限额内。后续新实例走原生构造/附着事件，不新增扫描定时器；所有权为空或不属于主页窗口时不干预可见性。没有恢复视频页/祖先尺寸重写。

深浅判断改用原生场景状态：40.7.0 MPFBottomTabComponent.LLLFZ(String,boolean) 接收 isDarkMode，LLL(boolean,boolean) 将其传给 X.0gnN.LIZIZ(Integer,boolean,boolean)，后者将参数写入 n。模块只在定位原生底栏时解析一次 n 字段，复用现有 100ms 刷新读取当前实例的布尔状态，不在绘制回调中查找反射元数据或截取背景。取消首页/朋友强制深色，视频页分类只用于字幕、进度条和采样策略；字段不可用时才回退到原生标题颜色及应用配置。测试覆盖原生浅色状态优先于白色标题/深色配置，以及原生深色优先于黑色标题/浅色配置。Spotify 路径未修改。


场景/占位修复验证：release（含 R8、Lint）、签名检查和 284 项 JVM 回归通过；安装后重启抖音，新进程 PID 23712。Activity View Hierarchy 显示当前 BottomSpace 为 GONE，RTViewPager 由 2497px 扩展至完整 2656px。日志确认原生 TextureView 被选择、GPU 场景存在、HyperOS 沉浸开启，采集期间没有该进程的新崩溃；历史 crash buffer 的 10 月 14 日错误不归入本次。用户随后反馈“看起来没问题了”。未完成各子场景主题切换自动测试，也没有帧率/功耗对照测量。

## 视频标题与相关搜索的避让微调

视频 viewport 正确铺满后，用户反馈标题和相关搜索抬升过多。将 i6 控制层相对玻璃底栏的额外安全距离从 48dp 收至用户指定的 8dp，减少 40dp 额外抬升；仍保留原生 margin 下限、页面滑动偏移归一化和相同数值不写 LayoutParams 的处理。不改视频/pager 尺寸，也不改独立进度轨道原有的 10dp 避让。该修改只调整既有计算参数，没有增加监听器、扫描、定时任务或逐帧工作。

8dp 最终包：release/R8/Lint、APK 签名检查通过，284 项 JVM 回归全部通过。该间距微调未再安装到设备，当前设备仍为已验证的场景/占位修复包；不将前一包的布局验证冒充 8dp 间距的真机效果验证。


用户继续指定额外间距为 0dp。本轮将 i6 控制层的 clearance 直接传 0，移除无用的密度换算；保留原生 margin 下限和与玻璃底栏上沿的避让。因此 0dp 指额外间距为零，并非取消全部控件避让。既有滑动偏移归一化测试同步覆盖零间距、正负滑动偏移及原生 margin 下限；没有新增监听或刷新任务。

0dp 最终包 release/R8/Lint 和 APK 签名验证通过，284 项 JVM 回归 0 失败。未安装到设备，实际 0dp 视觉效果待手动测试；没有新增热路径任务，也没有帧率或功耗对照结论。


## 2026-10-16 私信残留底栏与三层间距

用户提供截图：全屏私信输入框上仍绘制主页玻璃底栏，视频标题/相关搜索、进度线和玻璃之间有过大空隙。此轮设备未连接 ADB，以下来源是参考 APK 40.7.0 的字节码、布局和截图，不能冒充真机测量。

原生栏显示判断此前只检查内层附着和 visibility，遗漏原生 alpha=0；改为同时检查 isShown 和 alpha>0，模块自身仍只抑制外层 alpha。针对同一 MainActivity 内覆盖的私信 Fragment，核对 RipsChatRoomFullScreenFragment.onViewCreated(View,Bundle) 和继承的 getActivity，新增该生命周期入口的单项 Hook，登记聊天根 View 的弱引用。复用已有可见性检查，在全屏聊天根附着且显示时禁止玻璃覆盖，移除/隐藏后自动恢复；独立 ChatRoomActivity 仍走原有 MainActivity 失焦/暂停逻辑。只登记主页 Activity，弱键和弱 View 值，Activity 销毁移除；不挂全局 Fragment Hook，不在帧回调里反射或遍历聊天子树。Hook 不可用时保持原生栏状态判断。

参考布局 0x7f0d0965 的 i6 内，cne 包含 0x7f0d095a 的 ctk，其原生 bottomMargin 为 47dp。旧逻辑将 i6 底部直接抬至玻璃上沿，内部 47dp 预留继续生效，即使额外 clearance 为 0 仍有大空隙。现在在登记时缓存 ctk 弱引用，只在布局/已有 dock 几何更新时读取相对位置，用 i6 底部到 ctk 底部的实际 inset 抵扣模块所需抬升；保留原生 i6 margin 下限、滑动偏移归一化和变化才写 LayoutParams。额外监听仅为该 footer 的布局事件，沿用原来的单个排队任务，不修改内部 margin 或视频/pager 尺寸，销毁移除监听、停用恢复模块拥有的值。

ProSeekBarView 继承 AppCompatSeekBar，onDraw 调用父类；0x7f120aad 样式为 18dp 高、paddingTop=6dp、paddingBottom=10dp。旧逻辑用整个触摸盒底边作锚，再额外留 10dp，使实际绘制线更远。改为读取 progressDrawable 已有 bounds 的中心加 paddingTop，兼容无 drawable 时使用内容中心；只平移原生 progress overlay，不修改绘制/拖动监听。进度中心与玻璃上沿距离收至 2dp，ctk 内容底部目标为玻璃上沿 12dp（与进度中心相隔 10dp）。原生内部若另有必要的间距或更高的原值，仍保留。相同几何不重复写，进度 translation 计算扣除上次模块偏移，避免累计抬升。

新增回归覆盖：原生 footer inset 只计算一次、页面正负滑动偏移下间距稳定、原生 margin 下限保留，以及按绘制线中心计算进度位置后多次应用不累计 translation。没有新增帧任务、轮询、同步 IPC、截图或反射查找热路径；本次不声称帧率或功耗改善。

最终验证：release/R8/Lint 与签名校验通过，318 项 JVM 回归全部通过（其中抖音策略 6 项）；设备未连接，未安装或实际复测私信开关及三层间距。APK 保存至 outputs/manual-test，避免后续打包清理带名称的测试文件。


## 合集迟建卡片与旧版进度条补齐

用户反馈合集仍有抬升空隙，并要求进度条离玻璃保留 2dp。参考 APK 核对两条合集创建路径：FeedBottomMixModule.onCreateView(Context,ViewGroup) 和 FeedBottomMixComponentRegister.LJIIIIZZ(X.0rP0,Context,ViewGroup)，均调用 X.04Gw.LIZLLL，创建 0x7f0d08d2 common_feed_bottom_view_layout。实际卡片为 wrap_content 根、内部 tm3 高 40dp。0975 视频页的 i6 初始为空，0976 初始只有 03m，说明仅在 addPage 时查找 ctk 无法覆盖所有延后装配路径。

补齐以上两个原生创建入口的 after Hook，沿用真实返回 View；弱集合去重，在附着后定位所属主页窗口和最多 16 层的视频页祖先。每页 Entry 弱缓存合集卡片及其布局监听，只接受 i6 内的祖先关系。卡片显示且已测量时，使用实际卡片底边替代早期 ctk 作 inset 锚点，扣除卡片以下完整预留；卡片隐藏则回退 ctk。新锚点使用原始 i6 margin 基线重新计算，不沿用旧锚点产生的模块偏移，不改卡片点击、尺寸、文案或视频页高度。监听复用原单一排队任务，销毁移除，不增加周期扫描。

参考布局 0x7f0d097e 还包含 u1k / LineProgressBar，位于 u1r 内，高 1dp、原生底部 margin 2dp。此前只处理 DProSeekBar/CustomizedUISeekBar，没有处理此旧版进度条；用户截图底部的小进度点与这个遗漏路径相符，但此轮无设备连接，尚不能把截图推断视为真机类型确认。新增 LineProgressBar 构造/附着登记，同时在一次性 addPage 时通过 u1k ID 补登记预加载实例；不扫描每帧，也不强制显示原生隐藏状态。

所有进度条改为可见绘制区域下沿距玻璃上沿 2dp：SeekBar 使用 paddingTop + progressDrawable.bounds.bottom，其他进度条使用 height - paddingBottom。此前按中心计算会忽略半条线宽。仍扣除模块自己的 translation、保留原生本已更高的位置，不改原生触摸与拖动事件。回归覆盖合集锚点替换、进度偏移不累计，以及旧版 1px 轨道在非整数密度下的 2dp 边缘间距。新增工作限于原生创建/附着/布局事件，反射仅安装期解析；没有新增帧泵、IPC、截图或固定频率扫描。

本轮首次 release 遇到 Lint PrivateApiDetector 内部异常：Incorrect type X.0rP0 (JDK_25)。原因是 getDeclaredMethod 的常量混淆参数类型名不是合法 Java 源码标识符。改为只在安装期对已确认 Flux 类的方法元数据匹配 LJIIIIZZ / 3 参数 / View 返回类型，缓存 Hook 的 Method；没有关闭任何 Lint 检查，也没有在布局或绘制回调进行方法搜索。

最终 release/R8/Lint 和签名验证通过，319 项 JVM 回归全部通过（抖音策略 7 项）。设备未连接，未安装或真机复测合集及 2dp 视觉间距；性能结论仅为代码路径审查，没有前后帧率/功耗测量。


## 真机定位：普通视频页的迟建 ctk 缓存遗漏

用户在最新合集/2dp 包上仍看到部分视频页抬高。ADB 比对已安装包 SHA256 为 b0a9b791b34287826fca0d7f03de254cb8b731dbd814e199ccaec07564c71f78，排除旧包。用户停留后读取 Activity View Hierarchy，PID 18024：视频页及 Monitor 高 2656px；当前 i6 高 2356px，即 margin=300px，其 cne 的 ctk 底部在 2197px，仍距 i6 底部 159px。相邻页 i6 高 2515px，即 margin=141px，ctk 底部 2356px。两种模块 margin 的差值正好 159px，符合遗漏内部预留时的计算结果。截图为普通标题/位置卡片页，并非合集特有问题。

此前 addPage 只查找一次 ctk，即使 View 后续创建，也不会补绑定；这轮修正根因，不再逐种卡片加规则。Entry 在既有布局事件中沿已验证的两层直接子结构 i6 → cne → ctk 解析/补绑定。正常已绑定时只检查弱引用和直接 parent；迟建或替换时最多检查 96 个 i6 直接子项和 16 个 cne 直接子项，不递归视频子树，不增加帧回调或定时扫描。旧锚点解绑布局监听、新锚点登记监听，停用恢复规则和销毁清理保持。

新增策略回归使用本次设备坐标：无锚点时 margin=300，绑定 159px 原生预留后 margin=141，ctk 底部距玻璃上沿为既定 39px。进度线下沿 2dp 距离未修改。以上坐标证明的是布局预留问题，不是帧率/功耗对照数据。
