# 系统设置主页模块入口

基于 reference/Settings.apk 的 DEX 合约检查：`com.android.settings.MiuiSettings.updateHeaderList(List)` 原地按本机硬件、地区、运行模式和系统功能过滤条目，并补齐部分标题；`MiuiSettings$HeaderAdapter.mHeaders` 共享同一列表。入口点击通过 `onHeaderClick(PreferenceActivity$Header, int)`。Header 来自 `com.android.settingslib.miuisettings.preference.PreferenceActivity$Header`，有 id、title、titleRes、iconRes、groupId、intent、extras 字段。

## 2026-10-09 滑动崩溃和隐藏入口回归

用户设备日志保存于 build/diagnostics/settings-home-crash.log。崩溃为 `String.contentEquals` 参数为空，在 `MiuiSettings$HeaderAdapter.onBindViewHolder(SourceFile:1974)` 触发，发生在 RecyclerView 滚动或预取条目时。

旧实现把 updateHeaderList 的原列表复制后传给原方法，且再次复制 Adapter 的构造参数。原生代码过滤的是副本，显示列表却可能仍持有原始未过滤条目。这导致不属于本机的硬件入口被显示，其中未初始化标题的条目使 Header.getTitle 返回 null，引发上述崩溃。

修复移除 Adapter 构造 Hook，不替换 updateHeaderList 的参数或列表。原方法执行前，仅在原列表去掉本模块上次注入的专用 ID；原生筛选完成后，再往同一列表插入完整的模块 Header。列表原有对象和顺序保持，入口标题、资源 ID、摘要、extras 与 key 显式初始化。回归测试覆盖共享列表原地筛选、重复更新去重、隐藏功能不被恢复、关闭入口后的清理。

## 原生分组、管理器与安全情况

入口位于 `xiao_mi_hyperos_ai`（小米澎湃 AI）之后、`personalize_title`（系统个性化）之前，顺序为 LSPosed、HyperModifier。使用原生空白 category Header 作为分组边界，标题为空字符串，fragment/intent 为 null；复用相邻的现有空白 category，避免重复间距。入口和分隔有四个专用 ID，重复更新前全部清理，列表身份不变。入口默认值和既有开关保持兼容，管理器仍可选。

原生图标尺寸是 28dp。`HeaderAdapter.setIcon(HeaderViewHolder, Header)` 仅对本模块两个 ID 加载模块包的 VectorDrawable，不把模块资源 ID 交给宿主 Resources。LSPosed 保留官方白色图形与 #F48FB1 粉色；HyperModifier 的蓝底浅色三面魔方图形重新绘制，源 SVG 位于 design/settings-icons。复用原生连续圆角轮廓。

点击 LSPosed 直接进入管理器；独立管理器用包的启动 Intent，寄生管理器沿用[官方 LSPManagerService](https://github.com/LSPosed/LSPosed/blob/master/daemon/src/main/java/org/lsposed/lspd/service/LSPManagerService.java)的 shell 同进程 Activity 与 `org.lsposed.manager.LAUNCH_MANAGER` 分类。模块 UI 未连接框架时不会尝试普通 shell Activity。移除旧 ModuleHubActivity、模块枚举页面和相关路由，继续使用既有 settings_modules_entry_enabled 开关保存用户选择。

`updateHeaderList` 对 `security_status` 按国际版/安全中心条件移除。模块总开关开启时，保留筛选前的这一原生 Header 和后续条目位置，在原生筛选完成后仅恢复该项到下一个存活邻居前。保留其原生 fragment、titleRes、iconRes，不绕过其他设备/地区判断。总开关关闭时完全遵循原生筛选。

回归测试覆盖：共享 adapter 列表、隐藏硬件入口仍被过滤、仅恢复安全情况、分组位置及顺序、既有 category 复用、重复更新和关闭入口清理、缺失锚点处理。

按用户已有要求不运行模拟器。此次抓取的是用户真实设备的现存日志；修复验证包括 release 编译、列表回归及默认入口开关单元测试、APK 签名。未替用户安装修复包或宣称设备复测通过。

## 2026-10-09 分组横线与位置选项

上一版本给空白分组 Header 和入口设置相同的正 groupId。`ProxyHeaderViewAdapter.getItemViewGroup` 直接返回此字段，导致上下 category 也参与入口卡片的绘制，出现额外留白与横线。进一步核对原方法尾部：非 HyperOS 1 会移除 id=-1 的空白 Header 及 global_feedback_category，由 groupId 直接划分卡片。上一版本在原生筛选后重新插入专用 ID 的空白 Header，把 category 背景里的分割线重新显示出来。现在非 HyperOS 1 不创建或插入 category，仅插入共享独立正 groupId 的两个入口；HyperOS 1 保留原生 category 分隔兼容，分隔 groupId 为 -1。不改变其他原生条目的分组或背景。

新增 settings_home_entry_position，middle（默认）/top。模块 UI 的系统设置页提供顶部、中部单选，保存到本地及远程设置 Bundle；早期远程读取及常规运行时读取都加载该值，未知值回退 middle。顶部以下方 wifi_settings 为锚点，保留“我的设备”整组（包括已恢复的安全情况），复用无线组之前的 category；中部沿用 personalize_title / xiao_mi_hyperos_ai 锚点。切换后重启系统设置应用。图标未改动。

## 当前配置：安全情况遵循系统、支持合并到我的设备

撤销 security_status 的强制恢复：updateHeaderList 完全遵循原生地区及设备筛选，不再保存或重新插入安全情况 Header。

位置增加 device（UI：“合并到关于本机分组”）。以 my_device 为锚点，读取其真实 groupId，定位该组连续条目的末尾，再按 LSPosed、HyperModifier 顺序插入已启用的入口并继承 groupId，不增加空白 Header 或新卡片，保留该组其他本机功能。锚点缺失或分组无效时跳过。顶部及中部仍可选；默认值及已保存的位置不变。

图标改为用户选择的魔方方向：三面各 3×3，浅色块面和纯蓝底，无字母；设置入口与应用启动图标共享路径。SVG 位于 design/settings-icons/hypermodifier.svg。

## 四个位置与所有模块的独立入口配置

用户可选头条（device，合并我的设备）、顶部（top，我的设备整组之后/WLAN之前）、底部（bottom）和中间（middle，AI/系统个性化之间）。旧的位置键不变，既有保存值自动兼容。

底部两侧锚点为 feedback_services_settings 与 other_advanced_settings（更多设置），按实际列表中两个条目的先后顺序选择较后者作为插入边界；只剩一侧时以存活条目为边界，两侧都不在时不向无关位置注入。

settings_module_entries 保存按包名索引的已启用条目，含设置 Activity、启动分类、标题和位置。模块 UI 在 IO 线程枚举当前用户应用，识别 xposedmodule、assets/xposed_init 和 META-INF/xposed/java_init.list/native_init.list，包括 split APK。仅允许未禁用、导出且权限可访问的设置 Activity，优先[LSPosed 官方设置分类](https://github.com/LSPosed/LSPosed/blob/master/app/src/main/java/org/lsposed/manager/adapters/AppHelper.java)，再 INFO、LAUNCHER。无界面的模块不列出。返回模块设置页或手动刷新更新列表，各模块默认不注入；HyperModifier 和管理器沿用既有内置开关。

宿主不扫描全量 APK，仅读取已选描述并复核包及 Activity 可访问性；卸载、禁用或不可访问的入口跳过。使用已安装模块图标，明确 Intent 保留设置分类。每个包使用稳定 48 位 FNV 派生的专用 Header ID，同位置条目共享一个 groupId 和一对旧版分隔，新版不注入空白 Header。头条全部继承我的设备 groupId。原生设备/地区筛选及共享 adapter 列表继续保留。

## 独立入口页、位置弹出菜单与图标尺寸修复

入口配置统一为三级页面“系统设置 → 设置主页入口”。HyperModifier、LSPosed 管理器及检测到的其他模块共用一个 Section，每行读取对应图标、显示开关和已选位置；启用后点击行打开锚定该行的 `WindowListPopup`，提供四个位置并标记当前选择；选择后、点击外部或按返回键关闭菜单。第三方图标在 IO 扫描时生成预览位图，绘制使用固定尺寸与圆角裁切。

新增 settings_manager_entry_position，管理器位置独立于 settings_home_entry_position。旧配置缺少新键时回退原位置，保存后分别更新；早期远程偏好、常规 Bundle 与本地存储同步支持。运行时仍把同位置的不同入口合并成一个卡片。

用户截图里外部模块图标撑大，因为宿主 ImageView 的 wrap_content 受应用 Drawable 的较大固有尺寸影响。设置 Drawable 前固定 width/height 为 28dp，关闭 adjustViewBounds，设置 FIT_CENTER 和圆角 outline 裁切，避免扩展到卡片边缘。原生其他条目的绑定与筛选保留。


### 管理器入口误打开错误报告界面

手工选取 Shell Activity 的兜底会在框架未接管 Intent 时显示系统错误报告。
管理器入口现在优先启动实际框架对应的独立管理器；寄生模式通过 LauncherApps
调用 Shell 发布的官方桌面快捷方式，查询包含其他桌面固定的快捷方式，以保留
框架的完整启动参数。无法获取快捷方式时只显示提示，不再猜测 Shell Activity。
框架名称通过 libxposed getFrameworkName 获取，支持 LSPosed 与 Vector 的各自命名空间。
当前未连接真机；Release 编译与纯逻辑回归测试不代表已验证实际快捷方式访问权限。


### 无常驻通知的直接启动方案

已移除通知读取路径。独立管理器先使用常规 Intent，寄生 LSPosed 管理器由
Settings 进入受 WRITE_SECURE_SETTINGS 保护的模块启动 Activity，在模块自身 UID
下通过通用 su 提交固定的官方拨号码广播。用户已明确允许尝试此 Root 方案。
此方式不依赖常驻通知或桌面快捷方式，支持提供标准 su 的 Magisk、KernelSU、APatch。
授权拒绝、超时或 su 不可用时显示失败提示，不猜测 Shell Activity，不开启通知。
细节见 LSPOSED_MANAGER_DIRECT_LAUNCH_PLAN.md；广播提交成功不等于已验证界面打开。


### 原生图标占位与标题对齐

位置选项 device 现显示为“设备信息分组”。核对 Settings.apk 的
miuix_preference_main_layout 和 Widget.PreferenceIcon.Avator：原生 ImageView
为 34dp，占位后另有 13dp 间距，内部 ic_my_device 图形为 28dp。
旧 Hook 把 ImageView 改为 28dp，造成文字起点左移 6dp。
现在先执行原生 setIcon，仅替换图形，保持原生 LayoutParams、padding 和
scaleType；DrawableWrapper 使用宿主 header_icon_size 限制固有尺寸，
保留外部应用图标的尺寸限制与圆角裁切。尚需用户设备实际复测。
