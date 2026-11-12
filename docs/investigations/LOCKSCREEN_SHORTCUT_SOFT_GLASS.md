# 锁屏快捷圆盘与通知底部预留

2026-11-08 替换上一版失效实现。原版本错误地向快捷按钮 FrameLayout 添加背景子项；它没有迁入 HyperMusicCover 的控制器取按钮、根层级挂载及变换跟随路径。此前仅有构建/JVM 测试，不能证明实际设备挂载成功。

## 迁入来源

依用户明确授权，从本地 HyperMusicCover（HEAD `7ba08d0`，以当前工作区源文件为准）迁入其用户贡献的快捷圆盘路径：

- `MiniPlayerSqueeze.kt` 的 `ShortcutDisc`：生成 ID、原生圆盘轮廓、MaterialElement、setShape/dress/placeElement/onLayout。仅剔除给小岛使用的文字、封面和 live-view 分支，避免为两个快捷圆盘额外创建文字容器。
- `MaterialElement.kt` 原实现，仅改包名。它保证原生配方不能把圆盘轮廓改回卡片圆角。
- `MiniPlayerRuntime.kt` 的 resolveShortcuts/findShortcuts、layerSlot/discParent/discInPlace、drawnCentre/drawnZoom/chainFade/setIfChanged/resizeSmallIsland，以及 updateDiscs 的快捷圆盘流程。
- material 的通知配色路径：先建立原生 blur container，设置大小模糊半径与 MiBlurWinType，再把通知混色、材质和玻璃应用于元素。
- updateStackingBottomForMiui/showingKeyguardNotifications/checkStackFloor/restack 路径，用于新的锁屏底部预留。

必要适配：模块入口和设置不同；移除音乐播放器/通知小岛依赖及其 squeeze/holdButtons 行为；按钮自身变换仍是唯一动画源。反射元数据改为安装时缓存；不在每帧重新查方法。材质独立于音乐播放器，首次用原生锁屏通知资源初始化，后续按原实现的 native 方法清单，顺序记录锁屏通知 effect 内对背景的完整材质调用，保留嵌套调用去重。记录项的 View/Context 参数保存为占位，重放时再注入目标；相同调用复用快照，仅内容变化才拷贝数组。隐藏 transitionAlpha 访问缓存为 MethodHandle。原项目的 250ms 重试为一次有界重试，不轮询。

源文件快照 SHA-256：

- MiniPlayerSqueeze.kt: `47f374e2afaecb6447caf8daaeb4c342cf8a76400344d8e7a0b703891adf759e`
- MaterialElement.kt: `9a9a95133d0769de9b8dfb6b52b958fb8057e20982b68c268cb7f8dc555bd8ca`
- MiniPlayerRuntime.kt: `f2ff8022c9fb778ec18d6d440e3ef61d6ca08ccaa9ab1b42e772b0abc09ae9b9`

文件位于 runtime/systemui/shortcut/。`MaterialElement` 保留原文件 SPDX 标识；本段快捷圆盘迁入由代码贡献者在本次任务中授权。

## 挂载与生命周期

优先由 `MiuiShortcutController.onSystemUIAction$1(Bundle,"getShortcutView")` 取得左右实际按钮；fallback 按资源名递归寻找普通 View，不限制 FrameLayout。

圆盘插入 `keyguard_root_view` 内、按钮所在分支之前，避免被壁纸主体遮挡，也保留通知中心/控制中心的系统遮挡关系。若不存在该根节点，按原实现回退到 window root。圆盘保留生成 ID，避免 ConstraintSet.clone 对无 ID 子项崩溃。

启用且挂载时使用一个 pre-draw 监听，按原实现同步坐标、缩放、alpha/transitionAlpha；不触发周期性 invalidate。矩阵/点数组和材质回调复用，属性未变不写入；非锁屏先退出，不计算矩阵。只有材质代际变化才重放配方。关闭时移除监听与圆盘；源视图或窗口卸载后清理控制器。注册表为弱键、弱值，不通过 map value 强引用宿主。

## 新设置

“锁屏 → 锁屏快捷方式”：玻璃直径 32–96dp，默认 54dp。54dp 外框保持原始图标大小；图标按外框直径 / 54 同比缩放，不替换原图标或修改布局/触摸区域。已保存的尺寸（例如 56dp）继续保留，不强制覆盖用户值。

“锁屏 → 锁屏通知”：自定义通知底部距离默认关闭，支持屏幕底边或快捷玻璃上沿，间距 0–480dp、默认 24dp。几何使用未动画的布局位置，避免解锁/滑动时边界随动画抖动。选择玻璃上沿但圆盘未开启/没有有效按钮时保留系统边界。目标边界限制在系统通知容器的可用范围内；此设置定义通知堆叠/滚动的底界，不强行拉伸最后一张通知到底。

采用系统本身的 isShowingKeyguardNotification 同步状态判断，仅修改锁屏，通知中心仍执行原生分支。重放最后的系统输入并执行原生方法，随后调整 stackingBottom；关闭或兼容模式时重新执行原输入恢复系统计算。当前实现对这个输出字段作最后写入，不拦截其他模块的 Hook；其他模块也修改同一字段时，仍需按实际 Hook 顺序验证共存。

## 检查范围与限制

新增高频路径主要是启用期间的 pre-draw 几何同步，以及 HMC 原生材质调用录制入口的极短门禁。录制 Hook 延迟到首次启用快捷玻璃时才安装；默认关闭不会在进程启动时安装这些通用入口。未录制时直接退出，不扫描调用栈或 View 树；录制状态仅存在 effect 调用作用域，finally 清空目标。未启用不安装 pre-draw，关闭后没有残留轮询、定时器、截图或独立模糊动画。两个圆盘确有原生 GPU 材质成本，不能宣称没有额外 GPU 开销。

通知底部修改和按钮几何变化合并排队；无关设置不重放。关闭时也只在之前确实覆盖过边界的情况下请求系统重排，不对原生常规边界更新额外触发动画。

JVM 回归覆盖根级兄弟挂载、普通 View 按钮、变换跟随、材质代际去重、轮廓保护、改大小、非锁屏隐藏、关闭清理、设置/Bundle/备份往返，以及屏幕与快捷上沿坐标换算、缺失目标回退和边界夹取。构建与测试只证明代码路径和离线几何，不证明真机渲染命中。ADB 检查当前无已连接设备；尚未进行真机壁纸层级、AOD/解锁、通知滚动、Perfetto/FrameTimeline 或耗电对照。

最终离线验证：616 项 JVM 测试通过（0 失败/错误）；直板正式基线与折叠屏参考包各通过 23 个原生成员契约静态检查；Release 构建成功，APK v2 签名验证通过。交付 APK SHA-256：`48341fdbb3ec278f9db86286c2b7fb9a5bc3c6f16a0edf41e374ca60f9eea5ea`。安装新版本后需重启 SystemUI 加载替换后的 Hook；新代码加载后的设置支持实时调整。


## 继续失效后的时序修正

用户在小米 18 Fold（报告版本 4.0.0.26）反馈上一版快捷玻璃和底距均无效、诊断无报错。本轮没有 ADB 设备连接，因此未把静态发现认定为手机端已验证根因。

代码中确认的缺口：`MyHyperModifier.scheduleDeferredSystemUiHooks` 明确在首帧后 300ms、后台 worker 安装完整 SystemUI Hook，以避开启动 ANR；原快捷玻璃只依赖安装之后的 `addShortcutViews`，底距只依赖之后的 `updateStackingBottomForMiui`。若两个回调都已发生，sources/BINDINGS 为空，设置刷新只遍历空注册表，不会触发异常，也无法作用于现有界面。

新增 `LockscreenTargetBootstrap`，不提前完整 Hook 安装，不增加全局 View/构造器 Hook，也不轮询窗口或扫描整棵 View 树。通过本地两套参考包均确认存在的依赖链，在主线程一次性查找现有对象：

`MiuiStub.INSTANCE.mSysUIProvider.mCentralSurfaces.get()` → `mStackScrollerController.mNsslControllerInjector` → `keyguardPanelViewController.get().miuiShortcutControllerLazy.get().shortcutContainerView`。

仅功能已开启才访问现有实例；仅启用屏幕底距时不初始化最后两段快捷依赖。相关设置变化时如仍没有登记目标，合并一次接管请求，仍无定时循环。保留原生未来回调来接管后续重建。

迟接管通知底界只知道当前原生输出，不能把它当成原始输入再次调用系统方法，否则导航栏 inset 可能被重复扣除。`NotificationBottomState` 分开保存原始输入与有效输出：未知输入时直接协调已存在的有效边界；第一次真正的原生回调后再采用原始输入重放。关闭、卸载和失败恢复时保留原值；如果另一个模块在本模块写入后又改了该值，则以其新值为基线。

增加独立的运行阶段证据：锁屏目标接管、快捷玻璃创建回调/目标/材质应用、通知底界目标/原生回调/应用。运行时异常也进入现有诊断页；绘制路径只在首次命中时记录，后续帧不发送广播或格式化日志。显式设置变化可重试一次失败的渲染实例，不作异常轮询。

新增回归覆盖界面早于 Hook 创建、后续对象重建、快捷插件未就绪仍取得通知控制器、不初始化无关快捷依赖、未知输入接管后的原值恢复、真实原生回调接续和其他模块后写值保护。仍需手机端验证实际挂载、可见像素、AOD 与通知滚动，不能把 JVM/签名通过等同于真机修复完成。

本轮验证：624 项 JVM 测试通过（0 失败/错误）；两套参考包各通过 31 项成员契约静态检查；Release 构建成功、APK v2 签名通过。交付文件 `app-lockscreen-bootstrap-release.apk` 的 SHA-256 为 `b90ebf5f11b2fd7ca9500cc78e95e9de19fe91c213438dd45a643b3db4283a02`。尚未取得这台 18 Fold 上的新阶段诊断或图形采样，不声称已真机确认两个效果恢复。

## 创建回调命中但仍无目标记录：跨 Kotlin ClassLoader 的静默转换

用户提供的新运行记录包含 `快捷玻璃创建回调: 已命中 addShortcutViews`，却缺少“快捷玻璃目标”与材质准备/应用记录；较早的“锁屏目标接管”记录显示当时快捷容器尚不存在。这表明不能继续只用“没命中创建 Hook”解释现象，也没有依据先修改玻璃参数。

绑定路径中存在 `(stateDelegate.get(controller) as? Lazy<*>)?.value ?: return`：此处 Lazy 是模块自己的 Kotlin 接口，而 delegate 的声明类型来自 SystemUI 的 ClassLoader。即使类名相同，只要两份 Kotlin 运行时的 ClassLoader 不同，安全转换就静默返回 null，既无异常也不会创建圆盘控制器。

`ForeignLazyAccessTest` 使用真实 Kotlin stdlib JAR、parent=null 的隔离 URLClassLoader 及实现外部 Lazy 的代理，复现了名称相同但 `instanceof kotlin.Lazy` 为 false 的情况。通过字段自身的声明类型解析并缓存 `getValue()` Method 后，同一外部 Lazy 能正确返回控制器对象。

修正用 `ForeignLazyAccess(stateDelegate.type)` 取代 Kotlin 强制/安全类型转换；状态对象实际为空时进入诊断，不再无声退出。本轮不改玻璃材质配方、挂载位置和动画。变化只发生在安装/低频绑定路径，方法元数据缓存，不增加每帧反射查找、IPC、轮询或 GPU 层。该错误已在隔离 ClassLoader 回归中确认；其在用户手机上的修复效果仍需新的绑定阶段记录或实际显示验证。

本轮最终验证：626 项 JVM 测试通过（0 失败/错误），包括两份真实 Kotlin stdlib 的隔离加载复现；31 项原生成员契约检查在两套参考包均通过；Release 构建与 APK v2 签名验证通过。交付 `app-shortcut-classloader-release.apk`，SHA-256：`fb2053c8d6e07cce13d05f490d74ae639f6ff7b7c83bb530120c579f811fb447`。当前 ADB 无连接设备，未据此宣称真机快捷玻璃已恢复。


## 快捷图标随外框同比缩放

按用户确认，以外框 54dp 为参考，此时图标保持系统原始大小；81dp 为 1.5 倍，36dp 为 2/3。新默认尺寸设为 54dp，已有保存值不迁移覆盖。

使用 HMC squeezeButton 相同的独立 animationMatrix 路径，围绕按钮平移后中心缩放，不设置系统 scaleX/scaleY，不重排按钮，不改变布局框/点击区域。缩放矩阵与已有矩阵组合；关闭、离开锁屏、隐藏、失败和卸载均恢复自己拥有的矩阵。恢复前检查当前矩阵是否还是本模块写入值，避免擦除另一个模块的后写值；多个模块在活跃动画中共同写同一 AnimationMatrix 的情况仍需真机共存验证。

Android 36 本地 SDK 源码确认 View.getAnimationMatrix 会创建 Matrix，所以不能在每帧读取它。本实现只在取得所有权、尺寸/比例变化和恢复时读取；每个按钮复用 original/applied/work 三个矩阵。空闲帧只比较比例/尺寸/中心，平移动画中重用缓存矩阵，仅新值不同才写入。没有新 Hook、轮询、布局重测、截图层或模糊层。

新增 NATIVE graphics 的 Robolectric 回归检查真实 Matrix/RenderNode：54 基准、放大/缩小、中心、原生 scale/translation 与 transformMatrixToGlobal/getHitRect 不变、原有矩阵组合恢复、后写保护、空闲 20 帧只读写一次，以及控制器隐藏和关闭恢复。属于离线框架回归与静态分配路径核对，不是手机端帧时间、CPU/GPU 或耗电测量。

图标缩放最终验证：635 项 JVM 测试通过（0 失败/错误）；Release 构建和 APK v2 签名验证通过，DEX 包含 54dp 基准提示。交付 `app-shortcut-icon-scale-release.apk`，SHA-256：`a6650dcbb4227c8193480519727e52978ddf662e98c6e7d4f106a82689579583`。尚未做手机端视觉/帧时间/耗电对照。
