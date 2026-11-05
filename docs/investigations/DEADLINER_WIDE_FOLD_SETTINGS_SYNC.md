# 设置应用阔折叠组件同步

来源：本机 ProjectDeadliner/Deadliner，HEAD `3b561656e1ddca43220a39763f12aaa0c67fdaba` **加工作区未提交修改**，2026-11-05。
目标依赖 MIUIX 0.9.4-rc01。组件放在 `myhypermodifier/ui/adaptive`；原第三方注入组件保留，避免引入宿主资源表和无障碍兼容回归。

## 集成

完整同步浮动 Tab、手势运动、阴影、横向文字 Tab、侧栏与 WideFoldTopAppBar 源码，保留侧栏原许可证。复用现有 SoftGlassSurface、GlassMaterialSpec、主题 CompositionLocal 和材质实现。
SettingsAdaptiveChrome 接入上游紧凑底栏的 6dp 方向累计、弹簧收起，以及侧栏的展开动画/宽度/竖屏遮罩。以窗口尺寸而非机型名单判断：宽度 >=840dp 切换横向导航/侧栏；<600dp 且满足阔折叠比例时底栏滚动收起。切换状态由 rememberSaveable 保留，不使用 Deadliner 的 GlobalUtils。
应用集成差异：使用 HyperModifier 图标资源、路由和独立重启操作；不复制 Deadliner 计时器覆盖层和业务屏幕。侧栏/顶栏/底栏采样已有页面 backdrop，保持 chrome 在捕获层之外。
折叠屏专区位于系统界面列表最后，仅呈现修改 wm density；保留原配置字段和运行时实现，不静默改写用户旧值。

## 验证范围

运行 JVM 回归、Debug 构建与 diff whitespace 检查。新增上游窗口阈值回归测试。尚未做模拟器/真机截图、内外屏切换和触摸验收，也未做 Perfetto 或帧率/耗电测量。
性能静态检查：无新 Hook、IPC、磁盘读取、轮询或常驻任务；滚动观察只累计有限浮点状态，尺寸读取使用 LocalWindowInfo；复用材质捕获，导航展开使用布局/图层动画。

## 源文件 SHA-256

- `navigation/MiuixFloatingTabBar.kt`: `bf350f78c1203b4fd4a0e5517eb7e159a15c68247bdaf1da45b173546651c730`
- `navigation/FloatingTabMotion.kt`: `0218c71268599b1cad185b116ab19314a9599d82f98e71cb28a2efca6d835bf3`
- `navigation/FloatingNavigationShadow.kt`: `ede857bf8de11d344df9fd50b8026b19c145143b481e8053c647d55af07562d8`
- `navigation/ImmersiveTextTabRow.kt`: `f985ba983f4fed0912aa4698b22753f2cb42b2ebb39d7e42ff3e62a38f6bc786`
- `navigation/TabletNavSideBar.kt`: `082e5a29b8c20db8b3efeafdb90fd7f1a7e38b49cd1f3c473500649f000b5da2`
- `base/WideFoldTopAppBar.kt`: `12614a8eb393e5893104e2785f9bd70b361a8e5638463197a66605ce9c8f93cd`
- `base/ImmersiveMiuixCompactNavigationScaffold.kt`: `4ab080e834404e6b8a9f26e5ea00aeb5f2beb8c7655a40b0d28596292b4b6382`
- `base/TabletNavigationScaffold.kt`: `c879737165d6fb855f1015b8e074889b228ff8f453d63ea1d7df19d039e56db7`

## 后续布局对齐

严格采用上游 TabletNavigationScaffold 层级：全窗口 backdrop 记录背景与内容，仅内容和顶栏前景应用侧栏 inset；横向导航作为独立 TopCenter 层，保留 5dp 顶距与淡入/水平滑动；宽屏重启操作位于 TopBar，窄屏保留独立底部操作。主页仅在 >=840dp 宽屏移除双 Tab，参考 Deadliner ModernHost 显示柔光玻璃和系统界面两个等宽、独立滚动的列表，外侧留白和栏间距均为 8dp；窄屏保留双 Tab，两类内容各自保存滚动位置。关于页宽屏采用共用一个滚动容器的双列，左侧头图与作者致谢，右侧模块设置和作用域信息；窄屏保持顺序排列。折叠屏专区使用独立折叠屏矢量图标且无副标题。
本轮 Debug 构建、JVM 回归及 whitespace 检查通过；视觉和真机性能仍未实测。

外屏滚动收起时，独立重启按钮复用 LocalDockScrollFraction 和底栏 Height，同步缩放容器与图标，不新增滚动观察器或动画状态。

本轮性能静态检查：双列复用现有背景与滚动状态，不添加模糊层、Hook 或轮询。已安装应用列表在 IO 线程查询，仅首次进入及相关应用安装变化时刷新；更新请求也在 IO 线程执行并串行化。尚未进行真机帧率、分配或耗电对照测量。

宽屏页面列表不再置于 Scaffold 内：全窗口内容捕获层、渐进玻璃顶栏和透明弹窗宿主分别作为同级层。顶栏高度作为滚动内容的顶部留白，让列表可滚至状态栏与顶栏后方；主页双列各自滚动，关于双列整体联动。导航选择异步保存到独立 UI preferences，不触发模块设置刷新。作者头像本地打包、IO 解码；赞赏码保留原图并列展示与分别保存。
