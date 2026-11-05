# 蔚来汽车 6.9.6 悬浮底栏

## APK 与静态证据

- 用户提供 `base(3).apk`，包名 `cn.com.weilaihui3`，versionName `6.9.6`，versionCode `60906`。
- SHA-256：`ed64527d874ef94704c82309705743dc39f9594ba8fda3c80e105c1a832e8e4a`。
- APK 使用 SecNeo 包装，业务类未能从壳 dex 静态还原；不据此猜测私有字段、混淆方法或登录跳转。
- Manifest 中主页为 `cn.com.weilaihui3.app.ui.activity.HomeActivity`。
- `activity_home.xml`：`home_bottom_tab_layout` 为底部 FrameLayout；`home_content_layout` 使用 RelativeLayout 的 `layout_above` 预留底栏；另有 `login_guide_bar`。
- `home_custom_tab_layout.xml`：`cn.com.weilaihui3.widgets.scrolltab.ScrollTab`，ID `home_bottom_scroll_tab`。
- `home_custom_tab_view_layout.xml` / `view_tab_lottie.xml`：原生 `LottieTabView`、`custom_tab_img`、`custom_lottie_view`、`custom_tab_tv`。
- APP 包含 `lg_widget_core_icon_{discover,friend,car,surprised,mine}_28_{normal,focused}` 原生静态矢量图标，以及 `assets/tab/lottie_bottom_*.json` 动画。模块直接读取前者，不提交 APK、反编译结果或图标副本。

## 实现与边界

设置入口：柔光玻璃 → 蔚来汽车。使用原生双态图标，不提供 MIUIX 替代图标。模块预设默认开启，系统预设关闭；已接入本地/远程保存、备份键集合、作用域状态和重启选择。

只在蔚来主进程安装 Activity 三个生命周期回调，并立即过滤非 HomeActivity。这样不需要启动时解析壳内业务 Class，也不安装全局资源 Hook、View/触摸 Hook、桌面桥接服务。子进程和嵌入库的包回调均提前退出。

扫描范围限定原生底栏，识别包含标签与图标的最内层可点击 View，点击仍交由 APP 自己处理。选中项优先读取原生子树 selected 状态，其次使用唯一标签颜色差异；无法判定时保留最近一次原生点击索引（初始为 0）。由于业务代码加固，此回退对特殊深链、非点击切页、登录拦截的正确性仍需真机确认。

只有至少三个且至多七个入口全部能匹配原生静态图标时才替换；未知入口或图标资源缺失时保留原底栏。角标暂不镜像。配置变化在下一次主线程合并任务重建宿主及图标缓存，避免沿用旧配置颜色；APP 自定义主题如果不更新 Resources.Configuration，仍需真机检查。

替换仅移除内容区的 `RelativeLayout.ABOVE` 规则，保留其他规则、边距；关闭、暂停、销毁恢复原规则及原生底栏 alpha/无障碍状态。滚动内容的底部避让复用共享可恢复策略。暂停取消迟到挂载任务并释放宿主，恢复后重新挂载。

## 性能检查

完成代码审查和结构性开销检查，尚无真机前后对照数据。

- 启动只安装三个生命周期 Hook；资源 ID 查找及矢量光栅化限于有界挂载/配置更新阶段。
- 每个入口仅缓存两张 84×84 位图，五入口像素存储约 276 KiB；复用同一位图作为无需使用的单色字段，不生成额外遮罩。缓存不持有原生 Drawable、Activity 或 Context。
- 每帧退出只检查前台、总开关、原生可见性、会话状态和配置；底栏子树/选中项同步间隔 48 ms，点击强制同步。不在同步中重复解析 JSON、反射查找、IPC、磁盘访问或创建图标位图。
- 未识别入口的恢复检查仅由 APP 自身绘制驱动，间隔 250 ms，没有常驻计时器。启动重试沿用共享运行时的有界策略。
- 复用现有 `NativeGpuBackdrop` 与树内宿主；不另建 PixelCopy、软件截图或 Lottie 重播通道。GPU 记录、模糊面积和实际耗电仍需设备测量。
- 设置监听仅在模块/蔚来开关的有效值改变时排队，并按宿主合并任务。无关设置不会重建蔚来底栏。材质参数由现有 NavigationGlassRuntime 管理。
- 全局索引只通过弱引用联系 Activity/View；销毁清理回调和索引。短生命周期任务复用会话 Runnable。

## 验证

已通过 `:app:assembleDebug` 和 23 项 Robolectric/单元回归（0 失败），覆盖原生可点击包装层、选中子视图/颜色、未知图标回退、RelativeLayout 规则多次启停恢复、作用域声明、设置预设及共享导航策略。构建命令和结果以本次交付为准。

当前 `adb devices` 没有连接设备；未进行运行效果或 Perfetto/FrameTimeline/CPU/分配/功耗测量，不声称帧率或耗电已改善。真机需确认首次启动、已登录/未登录五入口、重复点击、深链、页面覆盖与返回、实时开关、深浅色、手势/三键导航及内容末项避让。加固 APK 的生命周期加载时序也待实机验证。

## Edge-to-edge 补充（2026-10-26）

先前仅有共享底部布局标志/透明导航栏，未显式关闭 Decor 的 Insets 适配或请求重新分发。本轮加入 NIO Home 专用 Window policy：挂载时 `setDecorFitsSystemWindows(false)`，移除窗口导航栏 fit 类型，重新分发 Insets，并强制浅色页面使用深色导航按钮。只对处于替换阶段的目标 Window 拦截原生 decor-fits 写入；其他窗口快速返回，记录 APP 最近请求值供停用恢复。隐藏 getter 仅在挂载时通过一次缓存反射读取，帧回调不做反射。

`home_content_layout`、`rl_home_root`、`android.R.id.content` 三个固定容器只去掉与 navigationBars 对应的底部 padding；保留其他额外 padding、顶部/侧边布局和 IME，APP 重新分发 Insets 回写 padding 时再处理。启停恢复原生 padding 与 RelativeLayout 规则。检查固定三个弱引用目标，无全树遍历、轮询或新截图层；配置和布局变化沿用现有宿主。

新增 Robolectric 回归检查强制 Decor policy、限定 Window 的 APP 回写、最近请求值恢复、底部 Insets padding 刷新与恢复。真机最终内容边界和键盘效果仍需安装新包验证，不能把单元测试当作设备效果或性能测量。

共享 immersion 新增可选的导航按钮明暗覆盖值，仅蔚来传入浅色页面配置。避免系统处于 night 时共享路径和蔚来路径在每次同步中反复写入相反的按钮颜色；其他适配保持默认按系统主题决定。新增 night 模式回归验证固定按钮颜色以及停用后的原值恢复。

设备旧包的窗口快照已带有 `EDGE_TO_EDGE_ENFORCED`（target SDK 的平台强制行为），因此不能把留白只归因于 Decor 开关；APP 容器的 Insets 避让也必须处理。本轮同时处理固定容器的导航栏 padding。若隐藏 getter 不可访问，会话只尝试一次并保留原窗口策略，避免每帧异常探测。

## 底栏页面显隐

新增发现、朋友、爱车、惊喜、我的五个独立显示开关，默认全部显示。设置界面禁止关闭最后一个入口；手工编辑或备份导入全部关闭时，共享 NativeBottomBarPolicy 仍保留第一个原生入口。仅过滤模块底栏的呈现，保持原生索引、点击与深链能力，APP 页面本身不删除。

开关变更仅合并排队一次宿主状态刷新，保留 GPU 宿主和原生图标缓存；总开关变更才走挂载/释放路径。无关设置不触发工作。运行时从布尔快照生成五位掩码，无 JSON 或列表解析。已接入本地读取/保存、远程 Bundle、备份 schema 和系统/模块预设默认值。

新增回归检查逐项显隐、仅保留一个入口、全部隐藏的兜底、原生索引保持、偏好读取以及远程/备份序列化。
