# 电商应用悬浮底栏与 PiliPlus 液态柔光修复

## 适配基线

- 拼多多 8.23.0（82300），`com.xunmeng.pinduoduo`
- 淘宝 10.65.10（855），`com.taobao.taobao`
- 京东 15.7.50（102312），`com.jingdong.app.mall`
- 闲鱼 7.28.10（518），`com.taobao.idlefish`
- 抖音商城 40.1.0（400101），`com.ss.android.ugc.livelite`
- 菜鸟 8.11.508（464），`com.cainiao.wireless`
- PiliPlus 2.1.6，Flutter 3.47.7，`com.example.piliplus`

## 电商适配

六款应用共用 `EcommerceHooks` 生命周期适配器和
`NativeViewBottomBarNavigation` 原生 View 路由。模块不替换应用点击监听，所有悬浮入口最终调用原生 Tab View 的
`performClick()`，因此页面路由、账号状态、埋点和子进程边界仍由应用负责。

已定位的原生底栏：

- 拼多多：`com.xunmeng.pinduoduo.ui_home_activity.widget.tab.PddTabView`
- 淘宝：`com.taobao.tao.navigation.TBFragmentTabHost`
- 京东：`com.jingdong.common.unification.navigationbar.newbar.NavigationGroup`
- 闲鱼：`com.taobao.idlefish.maincontainer.MainNavigateTabIndicator`
- 菜鸟：`com.cainiao.commonlibrary.navigation.NavigationView`
- 抖音商城：先使用准确类名，未命中时按底部标签行与预期角色回退定位

生命周期 Hook 只安装在对应应用的 Activity 主进程。其他 LSPosed 回调在
`PackageLoaded`/`PackageReady` 阶段直接退出，不安装通用资源 Hook、桌面桥接服务或全局 View Hook。宿主每
160 ms 最多同步一次标签元数据；可见性仍由已有逐帧廉价检查控制，不新增定时器、持续全树扫描或同步 IPC。

## PiliPlus 修复

原实现的底栏依赖 Flutter Android 侧语义快照，仅调用 `FlutterJNI` 并只读取
`SemanticsNode.label`，同时点击只依赖 Accessibility Bridge。当前实现做了三项修正：

1. 同时尝试 `FlutterRenderer.setSemanticsEnabled(true)`、`FlutterJNI` 和
   `AccessibilityChannel.onAndroidAccessibilityEnabled()`，兼容 Flutter 3.47.7 的语义启用路径。
2. 依次读取 `label`、`value`、`hint` 和 `identifier`，并接受“首页，已选中”等无障碍后缀。
3. `performAction(ACTION_CLICK)` 失败时，使用同一语义节点的屏幕坐标向 FlutterView 转发一次
   Down/Up 触摸，避免底栏可见但无法切换页面。

液态背景继续使用 `ViewBackdropSampler` 的 PixelCopy 路径实时采样 Flutter 窗口，只把采样结果发布到同窗口
外的模块玻璃层。硬件采样失败时不递归绘制 Flutter 内容，保留柔光材质兜底。语义树为空、当前位于详情页、
键盘弹出、Activity 失焦或功能停用时均隐藏悬浮底栏。

## 验证

- `testDebugUnitTest`：448 项，0 失败、0 错误、0 跳过。
- `assembleRelease`：Release、R8、Lint Vital 通过。
- APK Signature Scheme v2 验证通过，证书 SHA-256：
  `1e369c6e447e3c003fb3ac7d90bfe85e90473ca769a244b476df83da7f68ecd3`。
- 本轮没有连接 Android 设备，因此尚未完成六个应用和 PiliPlus 的真机视觉、点击、前后台及性能复测。

## commerce-piliplus.2 修正

- 底栏定位增加不依赖文字和图标的底部等距点击行几何回退；抖音商城同时使用
  `MainBottomTabContainer` 限定搜索范围，避免在页面内容中误选商品卡片。
- 京东点击目标下探到 `NavigationButton` 内真实可点击的覆盖层，选中态按
  `getCheckId()` 返回的导航 ID 映射到 Tab 位置，不再把该 ID 当作位置。
- 闲鱼直接使用 `MainNavigateTabIndicator.getCurrentIndex()` 的当前页索引；未知选中态
  返回空值并保留用户刚点击的位置，避免灰色指示器被错误重置到首页。
- 淘宝通过 `TabWidget.getTabCount()/getChildTabViewAt()` 枚举真实 Tab，并把电商
  原生底栏延迟发现窗口从 8 秒延长到 30 秒。
- 回归验证：451 项 JVM 测试全部通过；Release、R8、Lint Vital 通过；APK v2 签名
  证书与上一版一致。真机四款应用仍需在安装后复测。

## commerce-piliplus.3 修正

- 淘宝的导航根从全屏 `TBFragmentTabHost` 改为其内部 `TabWidget`。前者同时承载
  页面内容，不能作为底栏替换或隐藏目标；当前直接按 Tab 数量读取原生入口，并通过
  `TabHost.currentTab` 同步选中态。
- 抖音商城优先绑定原生 `BottomTab`，不再依赖底部几何位置猜测点击项。点击通过
  `BottomTab.getView()` 路由到宿主真实内容视图。
- 抖音商城关闭模块窗口沉浸标志竞争与背景采样，并改用 MUIX 矢量图标，避免宿主与
  注入窗口持续互相改写 Insets/SystemUI 标志，同时消除高频位图和 PixelCopy 开销。
- 版本号提升至 `1.5.3.1`（versionCode 42）。回归验证：452 项测试全部通过；
  Release、R8、Lint Vital 及 APK v2 签名验证通过。

## commerce-piliplus.4 修正

- 淘宝可能在 `PackageReady` 之前创建 `TBMainActivity`。电商生命周期 Hook 现在在
  `PackageLoaded` 提前安装，`PackageReady` 保留为 Activity 类尚未加载时的重试入口。
- 每个候选主 Activity 独立安装 Hook；一个旧类名缺失不再阻断同目标的其他有效类。
- 新增宿主主题解析器，综合宿主暗色方法、选中项文字/图标颜色、系统栏图标明暗、
  Activity 的 `isLightTheme`，最后才回退到系统 `uiMode`。底栏在刷新时会自动同步
  深浅色状态，不需要重启。
- 六款电商应用统一改用主题驱动的 Miuix 矢量图标；PiliPlus 的快照底栏同样改用
  宿主主题状态驱动背景、图标与文字颜色。
- 版本号提升至 `1.5.3.2`（versionCode 43）。回归验证：456 项测试全部通过；
  Release、R8、Lint Vital 及 APK v2 签名验证通过。

## commerce-piliplus.5 修正

- 在精确主 Activity Hook 之外增加框架 `Activity` 生命周期兜底，仅对目标应用自身
  Activity 生效；按需扫描目标底栏后再挂载，避免淘宝和菜鸟受到宿主 Activity 类名
  替换或启动时序影响。
- 抖音商城恢复柔光背景，但改走 `NativeGpuBackdrop` 的 RenderNode 显示列表采样，
  不执行 PixelCopy、Bitmap 回读或软件 `View.draw()`；同时保留此前的窗口沉浸隔离。
- 版本号提升至 `1.5.3.3`（versionCode 44）。回归验证：456 项测试全部通过；
  Release、R8、Lint Vital 及 APK v2 签名验证通过。真机仍需在安装后验证抖音商城
  柔光层和淘宝、菜鸟底栏挂载。

## commerce-piliplus.6 修正

- 以菜鸟 8.11.1023 官方安装包重新核对：`HomePageActivity` 和
  `com.cainiao.commonlibrary.navigation.NavigationView` 仍然有效，但默认 Tab
  已是 `NavigationTabView` 动态集合，旧标签“包裹/取件/寄件”不再可靠。
- 菜鸟现在直接枚举 `NavigationTabView`，并以包内
  `assets/default_tabitems.json` 的当前标签作为第二回退。
- 抖音商城柔光改为 100 ms 节流的窗口级硬件 PixelCopy 实时取样；保留原生 GPU
  图标和窗口沉浸隔离，不启用软件 `View.draw()` 或逐帧高频回读。
- 版本号提升至 `1.5.3.4`（versionCode 45）。456 项测试、Release、R8、
  Lint Vital 和 APK v2 签名验证通过。

## commerce-piliplus.7 修正

- 菜鸟新增 `navigation_tab_view` 资源 ID 定位，并在资源查找后校验
  `NavigationView` 类层级；即使页面刚创建、底栏尚未被 `isShown` 接受，也能先绑定宿主。
- 菜鸟 Tab 改为直接调用 `NavigationView.getTabSize()` / `getTabView(index)`，避免
  `NavigationTabView` 在初始布局阶段因未显示而漏检；点击仍由对应 `NavigationTabView`
  的原生监听器执行。
- 电商框架级生命周期兜底不再要求首次 `onResume` 时底栏已存在。主 Activity 的首次扫描、
  焦点变化和触摸均可启动延迟挂载，覆盖 `PackageReady` 晚于首个页面回调的情况。
- 京东优先使用 `NavigationButton` 的宿主暗色状态；若发布包混淆私有方法，则使用宿主
  窗口配置、系统栏图标和 Activity 主题的多数投票，不再被底栏固定的深色或红色文字误导。
- 闲鱼使用同一宿主窗口多数投票。其 `MainActivityThemeFun` 固定继承
  `Theme.AppCompat.Light.NoActionBar`，不能只读取 `isLightTheme`；当前资源夜间配置和
  状态栏图标外观共同决定悬浮底栏的深浅色。
- 版本号提升至 `1.5.3.5`（versionCode 46）。真机仍需复测菜鸟底栏挂载、京东和闲鱼
  明暗切换，以及切换主题后的即时刷新。

## commerce-piliplus.8 修正

- 真机确认菜鸟悬浮底栏已出现。其主题错误来自共享颜色投票把文字/图标前景色和底栏
  背景色按同一方向计票：白色背景会被当成“浅色文字”，可能反向判成深色模式。
- 已区分前景与背景语义。菜鸟 `NavigationView.refreshTabStripBackground()` 会直接将
  自身背景设为浅色白色或深色黑色，因此该不透明背景现在优先于固定标签色，并在宿主
  切换主题后跟随每次状态刷新。
- 版本号提升至 `1.5.3.6`（versionCode 47），需要真机复测菜鸟浅色、深色及运行中切换。

## commerce-piliplus.9 修正

- 真机反馈背景优先后菜鸟深浅色仍不正确。进一步核对官方 8.11.1023 的
  `com.cainiao.wireless.dpl.utils.DarkModeHelper`：菜鸟底栏最终使用的是
  `DarkModeHelper.duI.isDarkMode(context)`，其中会优先读取应用自己的
  `CN_DARKMODE` 设置，而不是仅跟随 Activity 主题或系统 `uiMode`。
- 菜鸟主题现在直接调用该宿主单例方法，并缓存其稳定的类加载器、字段和方法访问；
  获取失败时才回退到原生背景与窗口证据。运行中切换会随 160 ms 状态刷新同步。
- 版本号提升至 `1.5.3.7`（versionCode 48）。
