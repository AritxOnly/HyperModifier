# 本地 4.0.4.0 标记包：状态栏电池图标静态分析

分析日期：2026-10-21。仅分析 APK、Smali 和资源，没有修改模块运行时代码或安装到设备。

## 样本边界

`reference/MiuiSystemUI-OS4.0.4.0.XPCCNXM.apk` 与 `reference/MiuiSystemUI-OS4.0.2.0.XPCCNXM.apk` 的 SHA-256 均为 `d3628f6c3b85d16b1e74aef13ee227b41b6a855a01873c7f50364e274637f571`，两者字节内容相同。因此以下结论适用于本地标记为 404 的样本，不能据此认定 404 相对 402 有电池实现变化，也不能仅凭文件名独立确认提取来源。

代码路径均相对 `reference/systemui-4.0.4.0/`。原目录通过不解码资源的方式反汇编，XML 是二进制；本次另用 apktool 2.12.0 仅解码资源到 `/private/tmp/myhypermodifier-systemui404-res/`，没有覆盖基线。

## 层级与模式

实际小米状态栏路径是 `com.android.systemui.statusbar.views.MiuiBatteryMeterView`，不是包中同时存在的 AOSP BatteryMeterView / unified battery 类。

`res/layout/battery_digital_view.xml` 的结构：

```text
MiuiBatteryMeterView (LinearLayout)
├─ battery_icon_container (FrameLayout)
│  ├─ battery_image: MiuiBatteryMeterIconView
│  └─ hollow_battery_image: MiuiHollowBatteryMeterIconView
├─ battery_charge_out_image (ImageView，外部充电标识)
└─ battery_percentage_container (LinearLayout，外部百分比)
   ├─ battery_percentage_view (TextView，数字)
   └─ battery_percentage_mark_view (TextView，百分号)
```

`MiuiBatteryMeterView.onBatteryStyleChanged`（Smali 第 1705 行）选择图形；`updateChargeAndText`（第 3613 行）选择外部数字和充电标识。

| style | 图形 | 外部百分比 |
| --- | --- | --- |
| 0 | MiuiBatteryMeterIconView，无内部数字 | 隐藏 |
| 1 | MiuiHollowBatteryMeterIconView，有内部数字 | 隐藏 |
| 2 | 图标容器隐藏 | 隐藏（历史顶部电量条模式需结合 BatteryIndicatorView 分析） |
| 3 | MiuiBatteryMeterIconView，无内部数字 | 显示 |

这指有效的 `mBatteryStyle`。原始设置另存于 `mStoreRealStyle`；刘海、超级岛、AOD 等分支可能临时改变有效模式，例如有刘海时将 style 2 转为 0。因此做后续功能时不能只依据设置整数假设当前显示形态。

## 无内部数字：描边外壳 + 独立电量条

`MiuiBatteryMeterIconView`，默认 `battery_meter_use_legacy_drawable=false`。

- `battery_meter_bg*.xml`：圆角横向外框和右侧凸起电池正极。
- `battery_meter_progress_*.xml`：内部独立的实心圆角电量条；与外框之间留有空隙。
- `battery_meter_progress_radius.xml`：移动圆角遮罩；通过 DST_IN 合成电量区域。
- `onDraw`（第 1702 行）：画缓存的 `mProgressBackgroundBitmap`，然后叠加 `mTintBgDrawable` 外框。
- 电量从左向右增长，横向遮罩位移为 `-16.599976 × (1 - level/100)` dp，再按设备密度取整；0% 有额外位移处理。并非简单把整个图标缩窄。

默认 View 及矢量画布为 28 × 20 dp。外轮廓约 x=2.6..25.212、y=4.116..15.884；内部电量条约 x=4.954..21.515、y=6.47..13.531。这些是矢量坐标，不是截图测量。

## 内部数字：实心电池形状 + 电量分区 + 镂空数字

`MiuiHollowBatteryMeterIconView`。Hollow 指数字镂空效果，不代表它采用普通模式的细描边外框。

- `firstBackground`：`hollow_battery_meter_normal*.xml`，完整电池轮廓，普通状态 fillAlpha=0.4，表示剩余未填充区域的底色。
- `secondBackground`：`hollow_battery_meter_progress_*.xml`，与完整电池同形状的实色电量层。
- `hollow_battery_meter_radius.xml`：圆角遮罩；默认位移 `-23 × (1 - level/100)` dp，0% 特殊处理。
- `updateFirstBitmap`（第 1810 行）：将实色电量层与移动遮罩用 DST_IN 合成。
- `updateSecondBitmap`（第 2421 行）：再用 SRC_OUT 合成未填充的底色部分。
- `onDraw`（第 884 行）：依次绘制两个缓存 Bitmap、性能模式标记（如适用）、数字。

数字不是 TextView，也不在电量图片内预先烘焙。由同一个图标 View 调用 `Canvas.drawText(String.valueOf(level), ...)`，显示 0..100，不带百分号。

正常状态先使用 `hollowTextPaint` 的 CLEAR 混合模式擦出数字，再叠加 `textPaint` 的低透明度黑/白文字（资源为 #1A000000 / #1AFFFFFF）。因此数字的主要可见颜色来自后面的背景；它并非固定黑色数字贴在白色电池上。充电、快充、省电、性能模式会跳过这一步镂空，使用白色数字；低电量本身不在跳过镂空的判断中，但会令文字颜色切成白色，需要单独验证其最终效果。

字高默认 9.599976 dp；水平位置 `(viewWidth - batteryLevelTranX - measuredTextWidth)/2`，`batteryLevelTranX` 默认 1.3399963 dp；垂直基线根据 ascent/descent 居中。优先采用系统 MiSans 可变字体，字重 620，并保留 mipro-bold 回退。

完整电池轮廓约 x=2.6..25.594、y=3.88..16.12，比无数字外框略宽、略高。视觉上为完整圆润电池色块，剩余区为较淡底色，电量区为实色，数字居中。

## 颜色、充电和兼容路径

原包提供浅/深色及 tint 分支。默认浅色图标颜色白色，深色图标颜色 #BF000000；不要把深色模式误认为完全不透明黑色。状态颜色资源包括充电 #1DCD3A、低电量 #FA382E、省电 #FF9F05、性能 #3482FF；实际哪些层采用哪个颜色由各自状态分支决定。

内部数字模式充电时还可能显示独立的外部充电 ImageView（style 1/2 且未加入电池岛）。无内部数字模式的充电/快充标记由其图形资源分支处理；不能概括为两种模式都只有一个统一的闪电层。

旧式路径由 `battery_meter_use_legacy_drawable` 控制：从 `res/raw*/stat_sys_battery*.webp` 图集生成 LevelListDrawable，BatteryIcon 持有资源及等级缓存。默认现代路径使用上述矢量与位图合成，后续修改需保留旧式与主题覆盖边界。

## 外观对照

本地预览：`reference/systemui-4.0.4.0/battery-analysis/comparison.png` 及同名 SVG。使用 APK 原始矢量路径重建 20/50/80/100% 普通状态，放大显示。字体为替代字体，透明度合成、Android 像素取整和运行时 tint/主题覆盖可能不同；该图用于辨认组成与形状，不是真机截图或逐像素复现。

## 性能检查范围

本次仅做静态审查，没有改变系统进程启动、Hook 安装、主线程任务、IPC、GPU 层或耗电行为。

已有实现将 Drawable 与中间 Bitmap 缓存，尺寸不变时复用；合成时仍会新建 PorterDuffXfermode。内部数字的 onDraw 每次执行数值转字符串、measureText 和 getFontMetrics()，后者会返回 FontMetrics 对象，是后续若高频刷新需要关注的分配点。两种 View 都有 attach/detach、布局变化与资源刷新路径，新增功能应复用这些状态和缓存，避免在 draw 中反射、读设置、重建图形或引入计时器。

上述只是潜在热点的静态定位，尚无频率、CPU、分配采样、帧率或功耗前后对照，不能据此宣称性能问题或性能改善。未运行构建测试，因为未修改运行时代码；未做真机外观回归。

## 模块实现：无数字电池使用实心框

设置入口：状态栏 → 状态栏电池 → 无数字电池使用实心框，默认关闭。键为 `status_bar_solid_battery_frame`，已接入模型、预设复位、SharedPreferences、设置 Bundle、备份 schema 和 SystemUI 修改状态标识。

仅 Hook `MiuiBatteryMeterIconView` 自己的绘制、状态、资源刷新和 attach/detach 路径；不改变 `mBatteryStyle`，不操作外部充电 ImageView，也不影响原内部数字 View。这样无内部数字的 style 0/3 使用新框，style 3 的外部百分比仍由系统显示。启用时不再回退到主题的旧式图集或竖向图形，始终使用模块内置的 base SystemUI 系统电池几何。

`BatteryFrameGeometry` 采用本地样本的内部数字电池轮廓；原系统普通充电、快充及双箭头性能标识经过缩放或位移完全放进电池主体，并从主体中镂空。性能与充电可以同时显示；快充标识优先于普通充电。电量层保持系统状态颜色优先顺序：充电/快充 → 省电 → 性能 → 低电量 → 普通；tint 与 MIUI 优化关闭分支另按系统语义处理。基色跟随资源及系统 tint，动态值在系统状态/取色回调后刷新。

静态图形预览为 `reference/systemui-4.0.4.0/battery-analysis/solid-frame-preview.png`（及 SVG），用于检查标识排列；不等同于设备主题、取色、透明度和抗锯齿的最终效果。六种单独/组合标识通过离线光栅边界检查，均完整落在电池轮廓内。

性能边界：路径在首次启用时解析一次；主体及 6 种标识组合静态缓存；电量分区仅在电量、标识或增长方向变化时更新。绘制回调只有开关判断、弱引用表查询、缩放与两次 drawPath，没有反射、字符串/路径解析、模块主动对象分配、IPC、位图、saveLayer 或定时任务。状态回调缓存反射字段与 MethodHandle，取色调用使用 invokeExact，避免反射 varargs 数组。弱键的值仅持有绘图对象与资源 ID，不持有 View/Context。设置刷新合并队列并比较有效启用值，无关设置不遍历图标。

启用时系统原有的主题 drawable、位图和 tint 资源刷新会被短路，绘制直接使用缓存的系统电池路径；关闭开关、停用模块或进入兼容模式时重新执行系统资源刷新并直接走原 onDraw，无需回写或重建系统设置。冲突边界：其他模块若同时完全替换该 onDraw，最终行为仍受 Hook 顺序影响；本实现不修改其字段或挂全局 Canvas Hook。

验证：390 项 Debug JVM 测试通过（0 失败/错误/跳过），包括新增的电量边界、双方向、主题兼容与性能/快充组合选择，默认设置关闭检查；Debug APK 构建通过。未进行真机 Hook 命中、实时启停、深浅色、系统主题、充电/性能切换、超级岛动画或功耗/帧率测试。

补充构建验证：最终源码再次运行 `:app:testDebugUnitTest :app:assembleRelease --offline` 成功，390 项测试通过，Release lintVital/R8/打包通过；`apksigner verify --verbose` 确认 APK v2 签名有效。产物为 `app/build/outputs/apk/release/app-release.apk`。尚未安装到设备。


## 2026-10-21 外观细调（最终方案）

根据用户真机截图与后续更正，仅将图形高度压到上一版的 98%，宽度与 View 占位保持不变，缩放中心为画布 (14, 10)。

普通与快充闪电直接沿用原版 `battery_meter_bg_charge`、`battery_meter_bg_quick_charge` 的第一条 pathData，已逐字比对一致；保留原版形状，居中后分别适配到 94% / 92%，性能与充电组合时采用 70% 充电标识，双箭头在左、闪电在右。图标填色取自与电池基色相同的系统资源 / DarkIconDispatcher tint，跟随状态栏切换深浅及透明度，不固定为深色。

镂空间隙沿用系统数字电池的 CLEAR 合成语义：缓存 Paint 使用 `BlendMode.CLEAR`、`FILL_AND_STROKE` 与 0.85 矢量单位宽的圆角描边，擦除标识及其外围，再以状态栏取色绘制标识填充，形成真正透明的间隙而非白色描边。

静态证据：`MiuiHollowBatteryMeterIconView` 构造函数为 hollowTextPaint 设置 PorterDuff CLEAR；`MiuiBatteryMeterIconView.onAttachedToWindow` 原生设置 LAYER_TYPE_HARDWARE。原版无数字闪电周围的留白主要已写入资源矢量，没有单独可复用的描边计算方法。本实现沿用其镂空机制与当前 View 已有的隔离层，不调用原方法冒充对任意 Path 的支持，也不新增 saveLayer。若其他模块改变层类型，退回系统绘制，避免 CLEAR 擦到父背景。

性能检查：复用路径和标识缓存；电量分区只在电量或方向变化时计算。普通状态两次 drawPath，有标识时追加 CLEAR 与填充两次 drawPath。无新增离屏层、位图、轮询、反射查找或主动每帧对象分配。原有系统状态回调与兼容/关闭恢复边界保留。

静态深浅色预览：`reference/systemui-4.0.4.0/battery-analysis/final-clear-preview.png`。它用于检查图形排列、透明间隙和主题色变化，不是真机 Hook / GPU 合成验证。此前的白色描边预览为迭代过程资料，不代表最终实现。未做真机帧率、耗电对照或外观回归，不宣称性能改善。

最终验证：390 项 JVM 测试通过（0 失败/错误/跳过），Release lintVital、R8 与构建通过，APK v2 签名验证通过。普通/快充闪电路径与原版资源逐字相同。测试包 `app/build/outputs/apk/release/HyperModifier-v1.5.2-battery-frame-height98.apk`，尚未安装设备验证。


## 2026-10-21 真机反馈后的高度与镂空调整

按用户反馈扩大透明间隙，将 CLEAR 描边宽度从 0.85 提到 1.25 矢量单位，单侧留白由 0.425 增至 0.625。基于原始矢量几何，将电池高度比例从 0.98 改为 `(15.884 - 4.116) / (16.12 - 3.88)`，即约 0.961438，缩放中心仍为 (14, 10)。实心框的可见高度由此对齐原版无内部数字外框的 11.768 dp，宽度、View 占位和标识原始形状不变。标识随整体垂直变换一并收低，颜色继续跟随状态栏。

这是根据 APK 路径尺寸的静态对齐，不保证跨主题、设备像素取整或抗锯齿后逐像素一致。继续复用缓存路径、CLEAR Paint 和系统已有绘制层，仅改变常数，没有新增绘制次数、缓存、Hook、IPC 或轮询。

本次验证：原始几何上下边界对齐计算通过（4.116..15.884），390 项 JVM 测试通过，Release 构建与 APK v2 签名验证通过，`git diff --check` 通过。产物：`app/build/outputs/apk/release/HyperModifier-v1.5.2-battery-stock-height.apk`。未进行本次改动的真机外观或性能测量。


## 2026-10-21 继续试调

按用户指定，CLEAR 镂空描边宽度改为 2 矢量单位，图形高度缩放 94%、宽度缩放 98%，中心仍为 (14, 10)，View 占位不变。闪电形状、状态栏取色、缓存、实时启停与兼容回退逻辑不变。仅修改绘制常数，不新增绘制次数、层、Hook、IPC 或轮询；本次仍无真机帧率/功耗测量。

本轮验证：390 项 JVM 测试通过（0 失败/错误/跳过），Release 构建、APK 签名及 `git diff --check` 通过。测试包：`app/build/outputs/apk/release/HyperModifier-v1.5.2-battery-h94-w98-gap2.apk`，尚未进行本轮真机外观验证。


## 2026-10-21 充电标识上下穿出

经用户确认，普通闪电采用原始路径的 110%，快充采用 105%，均围绕 (13.25, 10) 等比放大。性能组合状态保持同样的闪电高度，仅沿用横向偏移，保留左侧性能双箭头。电池主体高 94%、宽 98%、中心 (14, 10)、CLEAR 描边宽度 2 及状态栏占位不变，不增加整体上下位移或改变横向间距。

静态几何估算：普通闪电上下各穿出电池主体约 0.34 dp，快充约 0.29 dp；含镂空间隙的范围仍在 28 × 20 View 内。路径生成仍仅在缓存初始化时发生，没有新增每帧计算、绘制次数、绘制层、对象分配、Hook、IPC 或定时任务。尺寸与裁切检查属于静态验证，尚无本轮真机外观与性能测量。

本轮验证：普通/快充原版路径逐字一致检查、静态上下穿出与 View 范围检查通过；390 项 JVM 测试通过（0 失败/错误/跳过），Release 构建、APK 签名及 `git diff --check` 通过。测试包：`app/build/outputs/apk/release/HyperModifier-v1.5.2-battery-bolt110-quick105.apk`。静态深浅色预览见 `reference/systemui-4.0.4.0/battery-analysis/bolt110-quick105-preview.png`。尚未进行本轮真机验证。

## 2026-10-22 控制中心展开时外显电量

新增独立设置：状态栏 → 状态栏电池 → 控制中心展开时外显电量，默认关闭。键 `control_center_show_battery_percent` 已接入模型、预设复位、持久化、设置 Bundle/备份与 SystemUI 修改状态标识，不依赖实心框开关。

静态依据：`MiuiBatteryControllerImpl` 向 BatteryStateChangeCallback 分发 `updateControlCenterExpandState(boolean)`，新建 View 注册 callback 时也会收到当前展开状态。`MiuiBatteryMeterView` 保存为 `mIsControlCenterExpanding`。该类的 `updateChargeAndText`、`onMeasure`、`onLayout` 都按有效 `mBatteryStyle == 3` 处理原生外部百分比，因此仅把百分比容器设为 visible 不足以保证正确测量与排版。

实现通过五个专用方法 Hook（attach、detach、展开状态、电池样式、文本刷新），仅在功能启用、控制中心展开、非 AOD 且当前原生有效样式为 0（图形电池且隐藏数字）时，临时把 `mBatteryStyle` 改为 3，并调用系统 `updateChargeAndText`。复用原 TextView、百分号、取色、字体、测量、布局和充电标识策略，没有新建文字 View，也不改系统电池设置或 `mStoreRealStyle`。

收起、关闭开关、停用模块、兼容模式或 detach 时恢复模块接管前的有效样式。系统电池样式回调执行前，先移除自己的临时覆盖，使系统仍能正确比较原始状态；回调后重新记录系统决策并按当前条件处理，避免把展开前的旧样式强行覆盖用户新选择。内部数字（1）、历史样式（2）、已外显（3）、超级岛临时样式及 AOD 等均尊重系统有效样式；其中若系统已自行把历史样式转为 0，则按实际显示状态处理。展开期间收到电量事件，系统原有文本更新继续生效。

性能检查是静态结构审查：仅 SystemUI 中解析一次元数据，使用缓存 Field/MethodHandle；不 Hook 绘制、测量、布局或动画帧，不引入全局 View Hook、IPC、位图、绘制层或定时任务。只在实际覆盖状态变化（或原生回调跳过但需要纠正可见性）时重放系统文本刷新。设置通知合并入主队列，并比较有效启用值，无关设置不遍历 View。弱键表中的值只存原生状态整数和标志，不持有 View/Context。

边界：其他模块若同时覆盖 `mBatteryStyle` 或完全替换这些方法，效果仍受 Hook 顺序影响；本实现只撤销自身接管的有效样式，不写 SharedPreferences 或系统设置。尚无本轮真机控制中心展开/收起、样式切换、重建、AOD、充电/性能组合、取色或功耗/帧率实测。

验证结果：397 项 JVM 测试通过（0 失败/错误/跳过），包含新增的 7 项状态转换/恢复策略测试与默认关闭检查；Release lintVital、R8、构建及 APK v2 签名验证通过，`git diff --check` 通过。产物：`app/build/outputs/apk/release/HyperModifier-v1.5.2-control-center-battery-percent.apk`。尚未安装到真机验证，本次性能结论仅限结构审查。

## 2026-10-22 修复展开后消失、收起时短暂出现

用户真机反馈：控制中心展开后外显百分比短暂显示约两秒后消失，收起时也短暂出现。静态分析确认上一版状态依据有误：`MiuiBatteryControllerImpl$$ExternalSyntheticLambda0.accept` 仅在参数等于 `ControlCenterContent.ExpandState.EXPANDING` 时令 `mIsControlCenterExpanding=true`，`EXPANDED` 时反而为 false。这个布尔量描述过渡动画，不代表控制中心持续打开；关闭过渡同样会触发 EXPANDING。因此上一版在动画完成时撤销样式、收起动画时又重新接管，与两个现象均吻合。这里确认的是静态状态逻辑错误，没有把用户报告的“两秒”当成精确计时或真机 trace。

移除旧的布尔状态 Hook，改为观察系统侧 `ControlCenterContainerController$onExpandChangeListener$1.onExpandStateChanged(ExpandState)` 的完整事件。在原方法发布 Flow 值前更新本模块的状态，保留最近的稳定阶段以区分两个方向：

| 阶段序列 | 外显策略 |
| --- | --- |
| COLLAPSED → EXPANDING | 打开，显示 |
| EXPANDING → EXPANDED | 保持显示，不再次重放 UI |
| EXPANDED → EXPANDING | 收起，恢复隐藏 |
| EXPANDING → COLLAPSED | 保持隐藏 |
| 打开取消 → COLLAPSED | 隐藏 |
| 收起取消 → EXPANDED | 恢复显示 |

同时观察 onContentAttached / onContentDetached，初始化插件当前阶段及卸载时清理状态，避免 View/插件重建沿用过期状态。不改系统自身的动画布尔量、原 Flow 或原始电池样式设置。

只在接管/恢复的实际切换时，使用 `MiuiBatteryMeterView.showView/hideView` 原生静态辅助方法取消百分比容器已有 alpha/blur 动画并同步显隐。这些方法已有 cancel + setTo + setVisibility 路径，避免旧的动画完成回调再延迟隐藏。Folme 尚未建立时仅对该容器同步设置 alpha/visibility；不新增全局 visibility Hook 或逐帧补偿。

性能范围：新增的完整阶段与内容生命周期事件替代动画布尔事件，合计七个专用方法 Hook；没有逐帧 Hook、轮询、延时重试、IPC 或额外绘制层。只有显隐策略变化才遍历弱引用 View 表；系统原生显隐辅助方法在切换时有自身的短期对象分配，未宣称零分配或帧率/耗电改善。状态、样式、生命周期和原值恢复仍为缓存字段 / MethodHandle 与基本值，不强引用 View/Context 或插件对象。

修复验证：402 项 JVM 测试通过（0 失败/错误/跳过），新增五项完整状态转换及与有效样式覆盖联动回归，覆盖动画结束持续显示、关闭动画不重现、开关动画取消、内容重建及两个旧布尔量反向场景。Release lintVital、R8、构建与 APK v2 签名验证、`git diff --check` 均通过。测试包 `app/build/outputs/apk/release/HyperModifier-v1.5.2-control-center-battery-state-fix.apk`。尚未进行修复后的真机回归。

## 2026-11-05 实心框固定使用系统电池素材

按用户要求，开启“无数字电池使用实心框”后不再调用主题电池图标素材，固定使用 base SystemUI 的系统电池几何。启用态在 inflate、资源刷新、`clearTint` 和 `onDarkChangeInternal` 入口短路主题 drawable/位图刷新，仅读取系统状态栏颜色、电量、充电/省电/性能/低电量字段及系统进度方向；因此主题的旧式图集、竖向电池图片和其它电池资源不再参与该开关的绘制。

关闭开关、停用模块或进入兼容模式时，对已跟踪 View 重新执行原 `updateResources$10`，恢复系统主题资源、位图、布局尺寸和原始绘制链路。路径、标识、颜色与电量分区仍复用原有缓存，没有新增每帧反射、资源查找、位图、绘制层、轮询或 IPC；新增 Hook 只位于低频 inflate、状态和资源变化入口。

验证：Debug Java/Kotlin 编译通过，`StatusBarBatteryFramePolicyTest` 2 项通过；`:app:assembleRelease --offline`、zipalign 和 APK v3 签名校验通过。Gradle 9.3.1 在当前含中文的工作区路径下运行 `testDebugUnitTest` 时仍对测试类报 `ClassNotFoundException`，因此策略测试改用同一 JUnit 4.13.2 与 Java 21 直接执行。尚未进行真机主题切换和外观回归。

## 2026-11-05 控制中心电量内部/外部与垂直居中

为实心电池框新增 `control_center_battery_percent_placement` 偏好，默认 `0` 表示电池框外，
`1` 表示电池框内。设置只在“无数字电池使用实心框”和“控制中心展开时显示电量”同时开启时
可选，已接入本地存储、远程设置 Bundle 和备份 schema。原“控制中心展开时外显电量”开关保留，
用于控制电池百分比是否在控制中心展开期间出现。

框内模式不让 `MiuiBatteryMeterView.mBatteryStyle` 临时切到 `3`，因此不会切换到外部
TextView，也不会改用系统空心数字电池。实心框继续绘制系统电池主体和充电/性能标识，并在
控制中心展开时用 CLEAR 合成在框体中央镂空电量数字，再叠加低透明度反差色；数字只在电量
或展开/收起状态变化时重新生成字符串，字体度量和基线在首次启用时缓存。框外模式继续复用
系统百分比 TextView、百分比符号、颜色和 Folme 显隐动画。

框外百分比在 `MiuiBatteryMeterView.onLayout` 完成后，根据电池图标 View 与百分比容器的
屏幕坐标计算 `translationY`，使容器布局中心与电池框图标中心重合，而不是停在底部对齐。
仅当模块拥有外部样式覆盖时设置该位移；收起、关闭功能或销毁时恢复原 `translationY`。
新增布局 Hook 只处理低频布局回调，没有轮询、逐帧测量、额外 Bitmap 或绘制层。

回归覆盖：框内位置保持原生样式 `0`、位置值归一化、外部百分比中心偏移计算、默认位置、
备份往返和原有面板状态转换。尚未进行真机控制中心展开、深浅色、充电阻抗和不同状态栏
高度下的视觉回归。
