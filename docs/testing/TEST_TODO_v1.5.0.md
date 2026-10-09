# HyperModifier 1.5.0 功能测试 TODO

版本：1.5.0（38）。此清单按模块设置页面和子页面排列。未勾选表示没有在本文记录通过结果，不代表你一定没测过。只在有实际验证结果时勾选；不适用项目写明 N/A 和原因。

## 测试环境与记录

| 项目 | 填写 |
| --- | --- |
| 设备/分辨率/字体缩放 | 待填 |
| HyperOS/Android/系统设置/SystemUI 版本 | 待填 |
| LSPosed 或其他框架版本、Root 方案 | 待填 |
| 七个目标应用版本、已启用的其他模块 | 待填 |
| 安装方式（全新/升级）、测试日期 | 待填 |
| APK 版本及签名、截图/日志保存位置 | 待填 |

结果记录建议：`项目编号 — 通过/失败/N/A — 版本 — 操作 — 实际结果 — 截图或日志`。
真机操作影响目标进程，保存设置后按页面提示重启相应进程。涉及连续启动保护的测试放到最后，在有恢复手段的设备上操作。

## 本轮验证状态

- Release 编译及签名：1.5.0（38）构建通过，apksigner 校验通过（v2）。
- 单元测试：本轮未运行。
- 以下所有真机场景与字段：初始未勾选，待人工填写。

## A. 安装、升级与设置应用

- [ ] A01 从 1.4.3 覆盖安装：签名兼容、正常启动、既有显式配置保留。
- [ ] A02 全新安装：141 字段与工作台预设一致；管理器位置显示“设备信息分组”，内部值 device。
- [ ] A03 配置保存后退出/重开模块、强停目标应用、重启设备，值与效果均保留。
- [ ] A04 主页柔光玻璃/系统界面切换、所有子页进入/返回、滚动恢复与返回键正常。
- [ ] A05 浅色/深色、长标题、大字体与横屏下无裁切、错位或点击区域重叠。
- [ ] A06 框架未连接/模块未启用/作用域缺失时，状态提示准确且设置应用可用。

## B. 主页 → 柔光玻璃

- [ ] B01 底栏额外抬高 0/2/48dp 在手势和三键导航下生效，旋转、键盘与系统 inset 不重复叠加。
- [ ] B02 小米运动健康：四个标签路由正确；原生/MIUIX/单色图标设置分别有效。
- [ ] B03 应用商店：游戏/排行/我的显隐分别有效；隐藏当前标签能安全切换；角标按设计保持隐藏。
- [ ] B04 米家：标签、图标、角标正确；设备控制页面返回后底栏高度稳定。
- [ ] B05 高德地图：地图内容可透过玻璃显示，地图拖动正常；隐藏长按说话入口后其他标签正常。
- [ ] B06 Spotify：底栏与 Capsule 同时显示，标题文案为“柔光玻璃悬浮底栏”，切换 Tab 和返回正常。
- [ ] B07 Spotify：播放/暂停/切歌、封面/标题/进度更新正确；拖动进度、未播放、无媒体会话时无误操作。
- [ ] B08 Spotify：系统媒体卡片喜欢/随机播放按钮分别开启/关闭；不可用动作不误显示为可操作，动作反馈正确。
- [ ] B09 哔哩哔哩：首页/动态/关注/会员购/我的/发布显隐分别有效；角标、点击和发布触控正确。
- [ ] B10 小米社区：路由、原生/MIUIX/单色图标与角标设置分别有效。
- [ ] B11 七个应用逐一冷启动、后台恢复、二级页面返回、旋转及键盘出现/收起：无双底栏、闪烁、空白或触摸遮挡。
- [ ] B12 七个应用逐一关闭悬浮底栏并重启：原生布局恢复，保存的样式值保留。
- [ ] B13 不支持的应用版本/识别失败时不误隐藏原生底栏，不因单个可选 Hook 失败中断整个应用。

## C. 主页 → 系统界面

### 通知/控制中心 → 卡片柔光玻璃 → 全局材质模糊

- [ ] C01 通知圆角开关、0/默认/最大值；通知展开、折叠、清除及分组卡片无异常。
- [ ] C02 控制中心整体与细节圆角分别生效；磁贴/卡片/主滑块/详情滑块/媒体/外部入口逐项核对。
- [ ] C03 MiLink 卡片开启/关闭及圆角修改；控制中心跟随 MiLink 背景材质选项可恢复。
- [ ] C04 材质兼容开关停用卡片 Glass Hook 后原生可用，编辑玻璃参数不会意外重新启用。
- [ ] C05 卡片 CSV 预设、单项修改、JSON 导入/导出可往返；旧版 JSON 不意外覆盖共享模糊值。
- [ ] C06 共享 Glass 比例 0/100/200%、Glass 半径 0/默认/最大值分别有效，其他未开启的参数不被重置。
- [ ] C07 全局背景模糊/压暗在通知栏、控制中心和 MiLink 上分别有效；关闭后恢复，展开动画不跳变。

### 悬浮通知 → 悬浮通知柔光玻璃

- [ ] C08 通知底部提示条显隐、底边距 0/13/32dp，无文本裁切或拖拽异常。
- [ ] C09 悬浮通知亮色/暗色参数分别生效；模块/系统预设及参数导入导出正确。
- [ ] C10 高级半径参数按当前实际 UI 和兼容策略生效；未暴露字段只核对持久化，不计作可见功能通过。

### 超级岛与媒体组件 → 高级布局编辑

- [ ] C11 系统/紧凑/标准/自定义预设分别切换，普通媒体与超级岛布局一致，四组高度和 XML 互不覆盖。
- [ ] C12 展开/折叠/完整 AOD 高度与 AOD 动作、无缝切换选项分别有效，退出 AOD 恢复。
- [ ] C13 超级岛高度、进度条、下拉横条与内容底边距分别有效，不影响非媒体岛。
- [ ] C14 白名单控制开启/关闭后对支持的目标生效，原生权限与其他服务无异常。
- [ ] C15 普通媒体/岛 XML 编辑与预览、合法导入、空 XML/错误 XML 反馈正确，失败可回退。

### 锁屏

- [ ] C16 时钟冒号、HyperMusicCover 兼容开关、渐进避让分别开启/关闭；封面/通知变化后时钟不重叠、不残留偏移。
- [ ] C17 AOD 字重 100/200/700与开关，切换时钟样式后仍正常。
- [ ] C18 锁屏通知指纹避让、锁屏隐藏指纹与 AOD 显示指纹组合分别验证，不影响解锁。
- [ ] C19 密码页下移、背景模糊/透明度/跟随混色、PIN 软玻璃/圆角/纵向间距分别有效；指纹/密码切换无卡住。

### 状态栏

- [ ] C20 Wi-Fi 连接/断开时移动类型显隐正确；移动/Wi-Fi 活动箭头和 Wi-Fi 标准标识显隐独立。
- [ ] C21 移动/Wi-Fi 箭头 X/Y 偏移与网速右间距不改变其他图标位置，切换网络后保持。
- [ ] C22 独立 4G/5G 标识大小/字重/偏移，双卡、无 SIM、飞行模式、网络切换时不重复或残留。

### 手势提示线

- [ ] C23 总开关与全部预设、应用规则优先级；新增应用、搜索与系统应用列表可用。
- [ ] C24 触摸显现、松手后延时、四向滑动和触控带 0/16/32dp；跨应用切换不破坏返回/主页手势。
- [ ] C25 预设切换清空旧规则；JSON 文件/粘贴导入、剪贴板/文件导出、旧 system 预设与错误数据处理正确。

### 系统设置 → 设置主页入口 / 手机型号图片

- [ ] C26 关于手机双卡片的设备名、存储与入口点击正确；关闭后恢复系统布局。
- [ ] C27 三倍预览、自动/预设/自定义来源、平移/缩放等变换保存/恢复正确，不拉伸机型图。
- [ ] C28 在线清单刷新/缓存、无网络/错误链接/损坏图片有可理解反馈；更换图片来源无旧图残留。
- [ ] C29 HyperModifier、LSPosed 和外部模块独立开关/位置；四个位置、同位置顺序、分组和重复刷新无重复项。
- [ ] C30 快捷入口与“我的设备”图标/标题起点/箭头对齐：34dp 占位、28dp 图形，包含长标题、浅深色及大字体截图。
- [ ] C31 外部应用大图标、无设置页模块、卸载/禁用模块的列表与入口处理正确。
- [ ] C32 模块设置点击正确；独立/寄生管理器分别启动正确；Root 拒绝/缺失/超时有提示，不误打开错误报告页。
- [ ] C33 旧位置配置及 headline 别名升级后兼容，未知位置可安全回退。

### 音量面板

- [ ] C34 默认 24dp、0dp 及其他圆角，展开/折叠、音量键操作正常。

## D. 兼容与恢复

- [ ] D01 关闭模块总 Hook 后重启目标进程/设备，所有作用域遵循关闭状态，配置仍保留。
- [ ] D02 SystemUI 兼容模式跳过系统界面和插件，其余应用 Hook 可独立工作；关闭模式并重启可恢复。
- [ ] D03 20 秒内三次启动保护、20 秒稳定运行重置、手动重试和重启后持续保护按说明工作。
- [ ] D04 功能安装结果区分成功/失败/未检测，后来的成功覆盖旧失败；不把安装状态当可见效果。
- [ ] D05 日志复制/清空、7 天自动清理、120 条上限与关闭自动清理分别有效，不阻塞宿主主线程。
- [ ] D06 与其他圆角/玻璃/状态栏/媒体/时钟模块逐个组合：记录冲突特征，关闭对应功能或兼容模式后可恢复。

## E. 关于 → 性能分析

- [ ] E01 模块信息、作用域状态、更新检查、项目入口正确；连续点击 LSPosed API 5 次显示性能区域。
- [ ] E02 15/30 分钟、Root 同意/拒绝、基线就绪提示、提前结束/自然到期和返回页面补齐正确。
- [ ] E03 后台/休眠/进程退出、强停/重启场景不产生虚假的完整区间；延迟与数据缺失有说明。
- [ ] E04 报告时长、电量计、深度休眠、亮屏增量、CPU 快照口径正确；查看/导出 ZIP、最近三轮保留可用。
- [ ] E05 相同 AOD/音乐/网络/温度条件下做模块开/关对照，记录实际功耗与采样活动；不从单次 CPU 快照推断整段耗电。

## F. 电脑 HTML 工作台

- [ ] F01 直接打开 tools/module-defaults/index.html，离线导航、搜索、类型筛选和所有字段编辑可用。
- [ ] F02 导入预设、导出/复制 JSON、恢复预设往返一致，共 141 个字段，数值/布尔/对象类型保持。
- [ ] F03 错误 JSON、缺失/未知字段与取消导入按页面规则处理；Spotify 标题和模块层级一致。
- [ ] F04 Hook 风险与原理说明可读，能区分参数修改与可选 Hook 开关；页面操作不写入手机设置。

## G. 全字段覆盖表（141 项）

下面用于逐项勾选，顺序沿用设置页面分节。可见字段检查“编辑 → 保存 → 重开 → 目标效果 → 关闭/恢复”；开关检查两种状态，数值检查默认和边界，文本/对象检查合法与错误数据。隐藏、遗留、固定策略字段只核对其保存/兼容行为，并标注原因。字段已勾选不能替代上面的场景验证。

### 主页 / 柔光玻璃

**底栏额外抬高**

- [ ] G001 底栏额外抬高 · `hyperGlassifyHiddenNavigationLift` · 默认：2

### 主页 / 兼容与恢复

**模块恢复**

- [ ] G002 启用模块 Hook · `moduleHooksEnabled` · 默认：true

**自动恢复**

- [ ] G003 SystemUI 兼容模式 · `systemUiCompatibilityMode` · 默认：false
- [ ] G004 SystemUI 重试代数 · `systemUiRetryGeneration` · 默认：0

### 主页 / 系统界面 / 通知/控制中心

**材质兼容性**

- [ ] G005 停用卡片玻璃 Hook · `disableShadeGlassHooks` · 默认：false

**通知中心**

- [ ] G006 通知圆角 · `notificationsEnabled` · 默认：true
- [ ] G007 圆角大小 · `notificationRadius` · 默认：28

**控制中心**

- [ ] G008 控制中心圆角 · `controlCenterEnabled` · 默认：true
- [ ] G009 控制中心圆角大小 · `controlCenterRadius` · 默认：28
- [ ] G010 控制中心跟随 MiLink 背景材质 · `controlCenterFollowMiLinkBackgroundMaterial` · 默认：true

**控制中心圆角细节**

- [ ] G011 独立圆角 · `advancedControlCenterCorners` · 默认：false
- [ ] G012 快捷图标圆角 · `controlCenterTileRadius` · 默认：28
- [ ] G013 卡片（含二级菜单）圆角 · `controlCenterCardRadius` · 默认：28
- [ ] G014 主滑杆圆角 · `controlCenterSliderRadius` · 默认：28
- [ ] G015 详情滑杆圆角 · `controlCenterDetailSliderRadius` · 默认：28
- [ ] G016 控制中心媒体圆角 · `controlCenterMediaRadius` · 默认：28
- [ ] G017 外部入口圆角 · `controlCenterExternalEntryRadius` · 默认：28

**小米互联服务**

- [ ] G018 融合设备中心卡片圆角 · `miLinkMainCardsEnabled` · 默认：true
- [ ] G019 融合设备中心圆角大小 · `miLinkMainCardRadius` · 默认：28

### 主页 / 系统界面 / 悬浮通知

**悬浮通知小窗**

- [ ] G020 隐藏底部提示横条 · `hideHeadsUpMiniBar` · 默认：false
- [ ] G021 自定义小窗通知底边距 · `headsUpBottomMarginEnabled` · 默认：false
- [ ] G022 通知内容底部边距 (dp) · `headsUpBottomMarginDp` · 默认：13

### 主页 / 系统界面 / 悬浮通知 / 悬浮通知柔光玻璃

**使用方式**

- [ ] G023 启用自定义参数 · `headsUpGlassParametersEnabled` · 默认：false

**预设**

- [ ] G024 亮色柔光参数 · `headsUpGlassParameters` · 默认：预设内的完整值
- [ ] G025 暗色柔光参数 · `headsUpGlassDarkParameters` · 默认：预设内的完整值

**高级设置**

- [ ] G026 自定义通知背景模糊半径 · `headsUpBackgroundBlurRadiusEnabled` · 默认：false
- [ ] G027 通知背景模糊半径 (px) · `headsUpBackgroundBlurRadius` · 默认：60

### 主页 / 系统界面 / 通知/控制中心 / 卡片柔光玻璃

**使用方式**

- [ ] G028 启用自定义参数 · `shadeCardGlassParametersEnabled` · 默认：false

**高级参数**

- [ ] G029 卡片玻璃参数 · `shadeCardGlassParameters` · 默认：预设内的完整值

### 主页 / 系统界面 / 全局材质模糊

**共享 Glass 材质模糊**

- [ ] G030 全局 Glass 模糊 · `globalGlassBlurEnabled` · 默认：false
- [ ] G031 材质模糊比例 (%) · `shadeCardBackgroundBlurPercent` · 默认：100
- [ ] G032 自定义 Glass 模糊半径 · `shadeCardGlassBlurEnabled` · 默认：false
- [ ] G033 Glass 模糊半径 (px) · `shadeCardGlassBlurRadius` · 默认：20

**全局背景材质**

- [ ] G034 全局背景模糊 (%) · `globalBackgroundBlurPercent` · 默认：100
- [ ] G035 自定义背景压暗 · `globalBackgroundDimEnabled` · 默认：false
- [ ] G036 背景压暗程度 (%) · `globalBackgroundDimPercent` · 默认：20

### 主页 / 系统界面 / 超级岛与媒体组件

**媒体布局预设**

- [ ] G037 启用媒体组件修改 · `mediaEnabled` · 默认：true
- [ ] G038 展开高度 (dp) · `expandedHeight` · 默认：152
- [ ] G039 媒体布局预设 · `mediaLayoutPreset` · 默认："standard"
- [ ] G040 系统媒体高度 (dp) · `systemMediaHeight` · 默认：168
- [ ] G041 紧凑媒体高度 (dp) · `compactMediaHeight` · 默认：84
- [ ] G042 标准媒体高度 (dp) · `standardMediaHeight` · 默认：150
- [ ] G043 自定义媒体高度 (dp) · `customMediaHeight` · 默认：150

**锁屏媒体**

- [ ] G044 息屏收起高度 (dp) · `collapsedHeight` · 默认：120
- [ ] G045 息屏常显高度 (dp) · `fullAodHeight` · 默认：80
- [ ] G046 息屏时隐藏操作按钮 · `hideAodActions` · 默认：true
- [ ] G047 息屏时隐藏设备切换 · `hideAodSeamless` · 默认：false

**超级岛媒体**

- [ ] G048 启用超级岛媒体调整 · `islandEnabled` · 默认：true
- [ ] G049 超级岛高度 (dp) · `islandHeight` · 默认：160
- [ ] G050 进度条光效 · `islandProgressBar` · 默认：true

**超级岛权限**

- [ ] G051 关闭超级岛白名单 · `superIslandWhitelistDisabled` · 默认：false

**超级岛交互**

- [ ] G052 隐藏下拉横条 · `superIslandHidePullBar` · 默认：false
- [ ] G053 自定义内容底边距 · `superIslandContentBottomMarginEnabled` · 默认：false
- [ ] G054 内容底边距 (dp) · `superIslandContentBottomMarginDp` · 默认：8

### 主页 / 系统界面 / 超级岛与媒体组件 / 高级布局编辑

**普通锁屏媒体 XML**

- [ ] G055 启用自定义媒体布局 · `customMediaConstraintSetEnabled` · 默认：false
- [ ] G056 普通媒体布局 XML · `customMediaConstraintSetXml` · 默认：""
- [ ] G057 系统媒体 XML · `systemMediaXml` · 默认：""
- [ ] G058 紧凑媒体 XML · `compactMediaXml` · 默认：""
- [ ] G059 标准媒体 XML · `standardMediaXml` · 默认：""

**超级岛媒体布局**

- [ ] G060 启用自定义超级岛布局 · `customMediaIslandConstraintSetEnabled` · 默认：false
- [ ] G061 超级岛媒体布局 XML · `customMediaIslandConstraintSetXml` · 默认：""
- [ ] G062 系统超级岛媒体 XML · `systemMediaIslandXml` · 默认：""
- [ ] G063 紧凑超级岛媒体 XML · `compactMediaIslandXml` · 默认：""
- [ ] G064 标准超级岛媒体 XML · `standardMediaIslandXml` · 默认：""

### 主页 / 系统界面 / 锁屏

**锁屏时钟**

- [ ] G065 24 小时制显示时钟冒号 · `forceLockscreenClockColon` · 默认：false
- [ ] G066 绕过 HyperMusicCover 时钟避让 · `bypassHyperMusicCoverClockAdjustment` · 默认：false
- [ ] G067 时钟渐进避让通知 · `progressiveLockscreenClockAvoidance` · 默认：false

**息屏时钟**

- [ ] G068 自定义息屏时钟字重 · `aodClockWeightEnabled` · 默认：true
- [ ] G069 息屏时钟字重 · `aodClockWeight` · 默认：200

**锁屏通知**

- [ ] G070 锁屏通知下沉 · `sinkLockscreenNotificationsForFingerprint` · 默认：false

**锁屏指纹**

- [ ] G071 隐藏锁屏指纹图标 · `hideLockscreenFingerprintIcon` · 默认：false
- [ ] G072 息屏时显示指纹图标 · `showLockscreenFingerprintIconOnAod` · 默认：false

**密码输入界面**

- [ ] G073 密码页下沉与指纹切换 · `lowerLockscreenPasswordPage` · 默认：false
- [ ] G074 锁屏密码背景模糊 · `lockscreenPasswordBackgroundBlurEnabled` · 默认：false
- [ ] G075 锁屏密码背景不透明度 · `lockscreenPasswordBackgroundOpacity` · 默认：0
- [ ] G076 跟随通知中心背景混合 · `lockscreenPasswordBackgroundFollowShadeBlend` · 默认：true
- [ ] G077 数字按钮柔光玻璃 · `lockscreenPinKeySoftGlassEnabled` · 默认：false
- [ ] G078 按钮额外半径 (dp) · `lockscreenPinKeyGlassExtraRadius` · 默认：6
- [ ] G079 按钮上下间距 (dp) · `lockscreenPinKeyGlassVerticalGap` · 默认：16

### 主页 / 系统界面 / 状态栏

**状态栏信号**

- [ ] G080 Wi-Fi 下隐藏信号角标 · `statusBarHideMobileTypeOnWifi` · 默认：false
- [ ] G081 隐藏移动网络上下行箭头 · `statusBarHideMobileActivity` · 默认：false
- [ ] G082 隐藏 Wi-Fi 上下行箭头 · `statusBarHideWifiActivity` · 默认：false
- [ ] G083 隐藏 Wi-Fi 网络制式 · `statusBarHideWifiStandard` · 默认：false

**移动网络上下行箭头位置**

- [ ] G084 移动网络箭头水平偏移 · `statusBarMobileActivityOffsetX` · 默认：0
- [ ] G085 移动网络箭头垂直偏移 · `statusBarMobileActivityOffsetY` · 默认：0

**Wi-Fi上下行箭头位置**

- [ ] G086 Wi-Fi 箭头水平偏移 · `statusBarWifiActivityOffsetX` · 默认：0
- [ ] G087 Wi-Fi 箭头垂直偏移 · `statusBarWifiActivityOffsetY` · 默认：0

**实时网速**

- [ ] G088 实时网速与右侧的距离 · `statusBarNetworkSpeedRightGap` · 默认：0

**状态栏网络类型**

- [ ] G089 独立 4G/5G 标识 · `statusBarNetworkTypeEnabled` · 默认：false
- [ ] G090 网络类型文字大小 (sp) · `statusBarNetworkTypeSize` · 默认：13.5
- [ ] G091 加粗网络类型文字 · `statusBarNetworkTypeBold` · 默认：true
- [ ] G092 网络类型水平偏移 (dp) · `statusBarNetworkTypeOffset` · 默认：0

### 主页 / 系统界面 / 手势提示线

**启用手势提示线**

- [ ] G093 启用手势提示线修改 · `gestureHandleEnabled` · 默认：false

**手势线预设与手势行为**

- [ ] G094 选择预设 · `gestureHandlePreset` · 默认："stock"
- [ ] G095 触摸时显示 3 秒 · `gestureHandleTouchReveal` · 默认：false
- [ ] G096 滑动时跟随 · `gestureHandleSwipeMotion` · 默认：false
- [ ] G097 底部响应距离 (dp) · `gestureHandleTouchAreaDp` · 默认：16

**应用列表**

- [ ] G098 应用显示规则 · `gestureHandleAppModes` · 默认：{}

### 主页 / 系统界面 / 系统设置

**关于手机布局**

- [ ] G099 设备名称与存储双卡片 · `aboutPhoneCardsEnabled` · 默认：true

### 主页 / 系统界面 / 系统设置 / 手机型号图片

**自定义图片调整**

- [ ] G100 自定义图片变换参数 · `aboutPhoneImageTransforms` · 默认："{}"

**预设管理**

- [ ] G101 预设图片清单地址 · `aboutPhonePresetUrl` · 默认：预设内的完整值

**图片来源**

- [ ] G102 图片来源 · `aboutPhoneImageSource` · 默认："auto"
- [ ] G103 自定义图片数据 · `aboutPhoneCustomImage` · 默认：""

### 主页 / 系统界面 / 系统设置 / 设置主页入口

**模块设置入口**

- [ ] G104 HyperModifier · `settingsHomeEntryEnabled` · 默认：false
- [ ] G105 LSPosed 管理器 · `settingsModulesEntryEnabled` · 默认：false
- [ ] G106 主页入口位置 · `settingsHomeEntryPosition` · 默认："bottom"
- [ ] G107 入口位置 · `settingsManagerEntryPosition` · 默认："device"（编辑器旧别名 headline）
- [ ] G108 已安装模块入口 · `settingsModuleEntries` · 默认："{}"

### 主页 / 系统界面 / 音量面板

**音量键弹出面板**

- [ ] G109 volumePanelRadius · `volumePanelRadius` · 默认：24

### 主页 / 柔光玻璃 / 小米运动健康

**小米运动健康**

- [ ] G110 柔光玻璃悬浮底栏 · `xiaomiHealthFloatingNavigationEnabled` · 默认：true
- [ ] G111 使用系统风格图标 · `xiaomiHealthMiuixIconsEnabled` · 默认：false
- [ ] G112 使用单色图标 · `xiaomiHealthMonochromeIconsEnabled` · 默认：true

### 主页 / 柔光玻璃 / 应用商店

**应用商店**

- [ ] G113 柔光玻璃悬浮底栏 · `marketFloatingNavigationEnabled` · 默认：true
- [ ] G114 使用系统风格图标 · `marketMiuixIconsEnabled` · 默认：false
- [ ] G115 使用单色图标 · `marketMonochromeIconsEnabled` · 默认：true
- [ ] G116 显示底栏角标 · `marketNavigationBadgesEnabled` · 默认：false
- [ ] G117 隐藏“游戏” Tab · `marketHideGamesTab` · 默认：false
- [ ] G118 隐藏“榜单” Tab · `marketHideRankingsTab` · 默认：false
- [ ] G119 隐藏“我的” Tab · `marketHideProfileTab` · 默认：false

### 主页 / 柔光玻璃 / 米家

**米家**

- [ ] G120 柔光玻璃悬浮底栏 · `miHomeFloatingNavigationEnabled` · 默认：true
- [ ] G121 使用系统风格图标 · `miHomeMiuixIconsEnabled` · 默认：false
- [ ] G122 显示底栏角标 · `miHomeNavigationBadgesEnabled` · 默认：true

### 主页 / 柔光玻璃 / 高德地图

**高德地图**

- [ ] G123 柔光玻璃悬浮底栏 · `amapFloatingNavigationEnabled` · 默认：true
- [ ] G124 使用系统风格图标 · `amapMiuixIconsEnabled` · 默认：false
- [ ] G125 使用单色图标 · `amapMonochromeIconsEnabled` · 默认：true
- [ ] G126 隐藏“长按说话”按钮 · `amapHideLongPressVoiceTabEnabled` · 默认：true

### 主页 / 柔光玻璃 / Spotify

**Spotify**

- [ ] G127 柔光玻璃悬浮底栏 · `spotifyFloatingNavigationEnabled` · 默认：true
- [ ] G128 系统媒体控制中显示收藏按钮 · `spotifyFavoriteButtonEnabled` · 默认：true
- [ ] G129 系统媒体控制中显示随机播放按钮 · `spotifyShuffleButtonEnabled` · 默认：true

### 主页 / 柔光玻璃 / 哔哩哔哩

**哔哩哔哩**

- [ ] G130 柔光玻璃悬浮底栏 · `bilibiliFloatingNavigationEnabled` · 默认：true
- [ ] G131 显示底栏角标 · `bilibiliNavigationBadgesEnabled` · 默认：true

**底栏功能区**

- [ ] G132 首页 · `bilibiliHomeTabVisible` · 默认：true
- [ ] G133 动态 · `bilibiliDynamicTabVisible` · 默认：true
- [ ] G134 关注 · `bilibiliFollowTabVisible` · 默认：true
- [ ] G135 会员购 · `bilibiliMallTabVisible` · 默认：true
- [ ] G136 我的 · `bilibiliMineTabVisible` · 默认：true
- [ ] G137 发布按钮 · `bilibiliPublishButtonVisible` · 默认：true

### 主页 / 柔光玻璃 / 小米社区

**小米社区**

- [ ] G138 柔光玻璃悬浮底栏 · `xiaomiCommunityFloatingNavigationEnabled` · 默认：true
- [ ] G139 使用系统风格图标 · `xiaomiCommunityMiuixIconsEnabled` · 默认：false
- [ ] G140 使用单色图标 · `xiaomiCommunityMonochromeIconsEnabled` · 默认：true
- [ ] G141 显示底栏角标 · `xiaomiCommunityNavigationBadgesEnabled` · 默认：true

## H. 发布前汇总

- [ ] H01 所有必测场景已标记通过或记录失败；N/A 项有设备/应用版本原因。
- [ ] H02 141 字段覆盖表与实际 UI/持久化核对完成，未测字段已列明。
- [ ] H03 已收集入口对齐、Spotify、锁屏/AOD 与控制中心的前后截图，异常附 LSPosed 日志。
- [ ] H04 将剩余缺陷、受影响版本与临时关闭项整理后，再决定发布说明中的兼容范围。

| 待修/待补测 | 所属项目编号 | 复现与证据 | 处理结果 |
| --- | --- | --- | --- |
| 待填 | 待填 | 待填 | 待填 |
