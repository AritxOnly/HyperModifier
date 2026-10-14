# 通知/控制中心卡片柔光玻璃

入口：通知/控制中心 → 通知/控制中心卡片柔光玻璃 → 高级参数调整。

## 参考与实现

参考 HyperChanger 提交 `467d0463608194294d8a2a771b12344c63292788` 的
[installShadeMaterialHooks](https://github.com/ColdP/HyperChanger/blob/467d0463608194294d8a2a771b12344c63292788/app/src/main/java/btm/m/os4/systemuihook/HyperSystemUiModule.kt#L4813)。
它在 `View.setMiGlass(float[])` 处根据调用栈区分通知与控制中心，并对原有参数增加偏移。
本模块独立实现同一入口，不强制替换 material type、混色或玻璃布局。
参数名称参考其 MainActivity.kt 的 MaterialOverrideAdvancedPage；中文说明同时依据原有
framework 参数映射补充，HyperChanger 并未提供全部 42 项的完整说明。

参考基准来自 9 月 24 日设备 SystemUI 的 `notification_glass_params_normal`，共 42 项。
此资源没有夜间覆盖。编辑页现只保留一套共用值，亮暗模式应用相同偏移。
运行时计算为 `原有参数[i] + 编辑值[i] - 参考基准[i]`，非负槽位下限为 0。
未编辑的槽位完全保持原值；系统全零清理数组、长度不同的数组、非有限数组不处理。
这能保留通知卡片、控制中心已开启/未开启按钮、动画配方的原生差异。
基准中的数值不是每一种控制中心卡片实际使用的绝对值。

## 范围

- 通知：从通知材质 Effect / NotificationUtil 等通知调用路径更新的 NotificationBackgroundView，并要求找到所属通知行；
  锁屏通知、已固定的悬浮通知、HeadsUp 路径不处理。
- 控制中心：`miui.systemui.controlcenter.*` 调用；只有材质工具动画回调时，额外要求
  View 的父链存在控制中心组件，避免动画刷新把修改覆盖回原值。
- **42 项配方**排除 ShadeBlendBlurController 以及识别出的整页背景容器。
  **Glass 半径**按 HyperChanger 的调用栈路由包含 shade 命名空间/原生 BlurProvider，不能复用配方的排除条件。
- 不影响 MiLink、PIN 页和锁屏快捷方式；不新增任何玻璃/模糊层。
- 调整系统已有的柔光玻璃与卡片背景模糊；传统模糊或非玻璃主题不会被强制切换成玻璃。

## 设置、预设与恢复

材质参数默认关闭。原生基准按钮只重置共用配方，系统默认按钮只关闭配方调整；
两个按钮都不再修改共享模糊。模糊的独立开关及数值由全局材质模糊页面管理。
悬浮通知与卡片使用同一套 UI 实现，但保存到独立键。卡片 JSON 格式为
`myhypermodifier-shade-card-glass`，当前 version 为 3，mode 为 `relative-to-system`，
仅导出单个 parameters 数组。兼容旧 version 2 的配方，校验旧模糊字段但不应用；
旧 version 1 校验两个数组后采用 regular。导入任何材质预设都保留全局模糊设置。
旧应用配置同样采用已保存的亮色配方；原有暗色 preference 保留但不再参与运行时渲染。
悬浮通知 JSON 不接受导入到卡片页面，反之亦然。

记录系统实际应用过的卡片配方与原生 Glass 半径，设置变化后分别在卡片/半径所属 View 的主线程重放。
卡片再次可见时只重放材质配方，不再向继承模糊的子 View 注入半径。
通过独立的持久设置观察器响应每次 remote preferences 快照；原 onLoaded 回调是一次性的，
不能用它代替滑杆变化后的刷新。其他原有一次性加载观察器保持不变。
系统更新容器 Glass 半径时直接在 setter 修改参数，不遍历子卡片。关闭覆盖时恢复最后观察到的原生半径，
不拿已修改值重复缩放。清理、进入悬浮通知或锁屏材质时丢弃旧卡片配方，避免重放旧场景的材质。
应用内重启提示范围为 SystemUI 和控制中心插件；本次开发不执行任何设备安装或重启。

## 验证边界

本地单元测试覆盖：零偏移、修改量、清理调用、参数验证、范围排除、动画父链判定、
共用参数、恢复保留、模糊清理、范围验证以及 JSON 隔离。编译与签名不能证明设备实际 View/调用链命中。
新增用例覆盖半径比例、自定义半径与比例组合、零值、通知 utility 调用范围，以及
“配方排除 BlurProvider、半径包含 BlurProvider”的回归用例。
手动测试时可先在亮色模式下增加共用的混合亮度（槽 06），检查通知卡片与控制中心卡片均变化，
随后核对暗色模式采用同样偏移。半径测试需单独检查共享 Glass 缓冲的影响，不应声称是独立的每卡片模糊。
日志标记为 `MyHyperModifier`，首次命中会输出 `Card glass matched` 和具体范围。

## 卡片模糊

HyperChanger 对卡片的 Glass 半径 Hook 位于 setMiGlassBlurRadius(int,int)，将两个半径
设为同一值（其原实现只在值大于 0 时覆盖）。仅拦截此 setter 在本机参考包并不完整：
NotificationRowGlassEffect.apply 先应用 NotificationRowBlurEffect，再调用 setMiGlassCompat，
整个普通通知行应用流程没有设置半径。控制中心的 MainPanelItemViewHolder 同样先设置
View blur mode，再应用 MiBackgroundStyle；多数卡片不单独设置半径。父级 BlurProvider
才负责通知面板/控制中心容器的背景和 Glass 半径。

9 月 27 日第一次适配：在 setMiGlass 后给子 View 设置半径，并尝试取父链半径作为比例基准。
用户反馈依旧无效，此方案已移除，不再以“子采样半径不能消除背景模糊”解释该功能无效。

用户明确确认 HyperChanger 的**通知 Glass 模糊**可用后，逐行对照发现：
其 isNotificationCenterCall 包含 com.android.systemui.shade / com.miui.systemui.shade，
setMiGlassBlurRadius Hook 因而能够命中 BlurProvider。旧实现的 targetKind 既排除
ShadeBlendBlurController，又排除 NotificationPanelView / NotificationShadeWindowView，
恰好过滤了真正拥有 Glass 缓冲的入口。

当前适配：配方筛选与 Glass 半径筛选分离。半径使用 HyperChanger 的 notification/shade/CC
命名空间路由，直接替换原生 setter 的两个 int 参数，不要求这是 NotificationBackgroundView。
reference 验证路径：BlurProvider.applyGlassBlurBlurRatio -> view.setMiGlassBlurRadius(small,big)。
控制中心与通知共用设置，直接的 HeadsUp、PIN View、MiLink 调用不进入半径匹配。
开启绝对半径时，用指定值同时替换大小半径，再乘比例，因此 20px × 50% = 10px。
与 HyperChanger 一样，绝对值覆盖不再把入参 (0,0) 自动当作不可修改的清理调用；
是否启用捕获仍由原生 material / blur mode / pass blur 生命周期管理。比例模式零入参仍为零。
有自己的背景半径的卡片仍缩放其原生值；没有的卡片不创建独立背景捕获、不启用新 blur mode。
普通 setMiBackgroundBlurRadius 的面板容器仍由全局背景设置控制，不改变其缩放/压暗。
注意：Glass 缓冲由容器共享，这不是每张卡片独立的背景层；窗口级共享缓冲也可能改变
其他使用者的 Glass 采样，包括从同一窗口取样的通知。直接的悬浮通知材质参数仍保持独立。
设备尚未连接，本轮不能声称实机已生效。日志新增 Native Glass radius matched，输出实际
View 类、系统两个半径与改写值；Card settings refresh 同时输出 nativeGlassTargets 数量。

## 系统默认与范围扩展

使用 aapt2 读取 reference/MiuiSystemUINew.apk 与 MiuiSystemUI.apk 的资源，并对照
ShadeBlendBlurControllerImpl.updateMaxBlurRadius 中的 getDimensionPixelSize 调用，确认：

| 缓冲 | 资源 | 最大半径（dp） | 440dpi 示例（px） |
| --- | --- | ---: | ---: |
| 通知小 Glass | notification_glass_small_blur_max_radius | 14.55 | 40 |
| 通知大 Glass | notification_glass_big_blur_max_radius | 181.82 | 500 |
| 合并窗口/原生控制中心 Glass | combined_blur_max_radius | 40 | 110 |

悬浮通知生效且启用仿生材质时，通知小半径另有 3.64dp 的 heads_up_extra（440dpi 约 10px）。
这些是资源最大值，真实 px 由设备 density 与 glassBlurRatio 决定，不应把 dp 当作 px，
也不能把 440dpi 的换算示例当作未连接设备的实测值。控制中心统一 MiLink 选项还会将
其 provider 的 Glass 最大半径改为 110px，属于已有功能，不代表所有设备原生值均为 110px。

半径可编辑范围扩为 0–1000px，滑杆 5px 一档；UI、偏好读写、Bundle、运行时与 JSON
共用 MAX_GLASS_BLUR_RADIUS。Glass 不再复用普通背景半径的 500px 截断；叠加 0–200%
比例后运行时最大值为 2000px。普通背景半径的旧安全上限保持不变。高半径可能增加渲染负担。
旧设置值和 JSON 预设继续兼容，不自动将原有自定义值改为新的范围上限。

## 全局材质模糊页面与设置归属

用户实测确认：原通知 Glass 模糊入口本身能够控制悬浮通知，所以不应再次拆分半径。
独立 HeadsUpGlassBlurHooks / HeadsUpGlassBlurPolicy 及安装入口已移除，
不再读取 headsUpDelegate 或面板状态来隔离共享缓冲，也不再单独写悬浮通知半径。

新增首页“系统界面 → 全局材质模糊”二级页面：
- 共享 Glass：独立启用开关、0–200% 比例、半径覆盖开关与 0–100px 半径。
- 全局背景：通知中心/控制中心/MiLink 的背景模糊比例与混色压暗。
- 通知/控制中心、悬浮通知、两类材质编辑页面均显示同一跳转入口与当前数值摘要。

保留原 BlurProvider / 原生 View.setMiGlassBlurRadius Hook 匹配方式，仅解耦启用条件，
不新增模糊层，不新增 PIN / MiLink 的 Glass 入口，也不扩展独立悬浮通知 Hook。
新增 global_glass_blur_enabled；旧配置无该键时继承 shade_card_glass_parameters_enabled，
使此前已启用的比例/半径不失效。比例与半径继续使用旧键，原有数值不会被覆盖。
启用条件不再依赖卡片或悬浮通知配方开关；整页背景的设置与 Hook 保持原样。

材质页面的启用、原生基准、系统默认、JSON 导入仅影响各自配方。
卡片导出升级 v3，仅存配方；v1/v2 仍可导入，但旧模糊字段不改写共享设置。
悬浮通知导出仍为 v1，不再包含独立半径字段；旧字段仅作兼容校验，不参与运行时。
废弃 heads_up_background_blur_radius* 存储暂保留，避免无关数据清理，但不再生效。
没有将废弃悬浮通知数值迁移到全局半径，以免覆盖用户已确认有效的全局数值。

UI 沿用项目现有 MIUIX SettingsScrollPage / SettingsSection / NavigationSettingItem，
复用根主题、滚动与 inset 宿主；未增加新主题或嵌套 Scaffold。
本轮本地回归检查预设不会覆盖共享设置，未执行设备安装或重启，界面实机验证待用户测试。

### 半径上限收窄与主页入口样式

按用户要求，自定义 Glass 基础半径现限制为 0–100px，滑杆每档 1px。
偏好加载/保存、Bundle、运行时与旧 JSON 校验共用该上限；旧自定义值超过 100px
时收敛到 100px。比例仍为 0–200%，只收窄自定义半径，不将系统原生的大半径截成 100px，
也不修改整页背景模糊或压暗范围。
主页“全局材质模糊”移除摘要，复用其他系统项的 HomeSystemIcon 左侧图标样式；
相关二级页面的数值摘要与跳转入口保持不变。

## 2026-10-13 全系统 Glass 半径入口

共享区精简为 `global_glass_blur_enabled` 开关与 `shade_card_glass_blur_radius`（0–100px）。
旧比例和额外半径开关键继续保存/导出，但运行时不再读取，也不再修改卡片的背景模糊。
`GlobalGlassBlurHooks` 在 PackageLoaded 安装到每个 LSPosed 选中的应用进程；
不再按 View 类型、父链、通知状态或调用栈过滤 `View.setMiGlassBlurRadius(int,int)`。
因此锁屏、悬浮通知、桌面、设置及其他选中的应用，只要使用该原生 API，就会统一大小半径。
作用域外的进程、独立 shader 和其他模糊 API 不会因此改变；扩大覆盖需在 LSPosed 勾选对应系统应用并重启。

每个目标 View 使用弱引用保存最新系统调用的原始大小半径；设置刷新在 View 线程重放，
重放不会覆盖原值，关闭全局开关恢复最近的系统半径。兼容停用开关仍暂停所有写入，
重启后将不安装 Hook。卡片配方继续由 `ShadeCardGlassHooks` 独立处理。

## 2026-10-13 通知中心清除按钮补充入口

用户确认音量条半径可用，不再对音量路径追加修改。通知中心 × 的
`NotificationDismissViewGlassEffect.apply(Object, Context)` 只设置 Glass 配方，
没有直接设置采样半径；新增 `NotificationDismissGlassHooks` 在该方法完成后
通过 `GlobalGlassBlurHooks.onMaterialApplied` 补设现有全局半径。
同时 Hook `clear(Object)`，在玻璃效果退出时恢复原值并移除跟踪，
防止待执行的设置回放将普通材质重新加上 Glass 半径。

原始半径优先取之前观察到的系统 setter 入参；没有系统调用时采用受支持设备
libhwui 原生 `BackgroundBlurBlendProperties` 的默认值 0/0。核对的 native getter
位于 0xa323c0，读取字段 0x304/0x308；初始化路径 0x5d955c/0x5d9560 将两字段置零。
该默认值是此设备构建的静态证据，其他 ROM 仍需验证，不能当作全部系统版本的保证。

该补充 Hook 只安装在 SystemUI，使用精准的材质 apply/clear 方法，
复用原有弱引用状态、设置去重及合并回放，不扫描 View 树、不采集调用栈、不轮询，
无关设置变化不额外刷新。只输出一次实际匹配日志，便于真机确认入口。
未增加独立开关、模糊层或修改 Glass 配方。
本次开发时 ADB 设备已断开，无法完成按钮视觉/帧耗时实测；编译和测试不能替代真机验证。

验证：35 项 Glass 相关单元测试通过，签名 Release 构建成功，APK v2 签名验证通过。

## 2026-10-14 超级岛 Glass 半径

核对设备插件代码：`DynamicIslandWindowView.updateWindowBlur(boolean)` 在柔光模式中
通过 `MiBackgroundStyle.setBackgroundBlurRadius(view, background, 50, 500)` 设置窗口采样，
其中 Glass 分支最终调用 `View.setMiGlassBlurRadius`，已被全局 Hook 覆盖。
`DynamicIslandBaseContentView.updateBackgroundBg(View, boolean)` 对展开内容只应用
`EXPANDED_GLASS_TOKEN` 配方，没有设置该背景自身的 Glass 半径。

新增 `DynamicIslandGlassHooks`，在展开背景材质更新结束后复用全局半径覆盖。
父节点存在、系统允许背景材质且插件处于 Bionics 模式时才处理 Glass；
普通材质/关闭材质路径恢复原值并移除跟踪，旧排队回放通过状态身份检查失效。
不强制将非 Glass 的收起岛或传统材质转成 Glass，不修改转场内容/应用的 self blur。

通过现有插件加载器工厂获取真实 ClassLoader，复用 `installSystemUiPluginCornerHooks`
安装入口；不增加 ClassLoader 全局拦截器，不在 loadClass 的锁内加载其他类。
只 Hook 材质更新方法，不 Hook 绘制/动画逐帧回调，不遍历 View 树。
模式反射 Method 在安装时缓存；背景材质允许状态读取 Resources.Configuration，
不是 Settings/Binder IPC；设置回放沿用现有去重、合并和弱引用状态。
无需新开关，超级岛窗口和展开背景都使用“全局材质模糊 → Glass 模糊半径”。
本次未安装到设备或进行真机效果/前后帧率验证。

验证：35 项共享 Glass 回归测试通过，Release 构建成功，APK v2 签名验证通过。

## 2026-10-14 展开面板未生效后的实测修正

已比对安装 APK SHA-256 与构建一致；模块更新后 SystemUI 已重启，
全局开关 true、半径 48、兼容停用 false。用户持久化诊断截图确认安装成功。
定位版实际回调条件 7：target/parent 均为 FrameLayout，材质允许与 Bionics 均 true，
viewBlurMode=1、backgroundBlurMode=0。之前的实现修改了消费者，而没有直接注册其采样容器；
“已安装”不能作为效果覆盖的证据，先前完成声明未经过视觉验证。

修正为通过 getRootView 精确识别 DynamicIslandWindowView，要求其 backgroundBlurMode=1；
直接复用现有背景采样容器，不给消费者新增背景模糊模式或采样层。
原生 setter 被观测时保存其入参；若窗口初始化早于注入，则使用受支持插件
updateWindowBlur(true) 的 50/500 原值作为基准。展开材质事件才激活覆盖，
对应窗口的常规原生半径 Hook 默认透传，收起小岛和修改全局半径时的收起状态保持系统值。
真实内容和 fake 内容的 close-end 事件各执行一次清理，延后一帧检查当前是否仍有展开内容，
在确已收起后恢复并移除跟踪，避免关闭旧卡片时覆盖新展开卡片。

消费者与采样容器映射使用弱键/弱引用，重复材质更新复用映射；只在材质/收起事件处理，
不扫描完整 View 树、不增加动画逐帧回调或 IPC。诊断每种条件最多记录一次并复用后台队列。
修正后的实际视觉效果须在新 APK 安装、重启并展开后验证。
