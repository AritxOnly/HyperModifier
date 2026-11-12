# 快手双包与 QQ 音乐柔光底栏

## 参考包

- 快手：`com.smile.gifmaker 14.8.10.50114`，SHA-256 `ACB81288C126B159B85D8BC6AF4405255ED9F3B5E061FD86FFA5F44D05008878`。
- 快手极速版：`com.kuaishou.nebula 14.9.10.11957`，SHA-256 `1A736683760158DC8B7A11B5D5B35EFDC291B6D2CB6FEE800DD01F02292E095C`。
- QQ 音乐：`com.tencent.qqmusic 20.9.5.8`，SHA-256 `72E261BA3A1AD23DD4E9DECC5C8D725EF3D7ADF44FFF50BC00224F9064F74224`。
- QQ 音乐官方 Android `20.9.7.9`：versionCode `7559`，SHA-256 `78C98E93F760D42E9C4D59EEDAD6F7BBBC3F8F68BE027EFE35ECBDB8A0470393`。

## 快手

两个包共用 `HomeActivity`，底栏 tab 为
`com.yxcorp.gifshow.homepage.kcube.actionbar.bottom.widget.BottomTabView`。
实际底栏是四个 tab 的最近公共父容器；中间的拍摄/任务入口由独立的
`BottomBarShootView` 或 `BottomBarCenterTaskView` 绘制。

适配会保留四个原生 tab，把原生拍摄/任务 View 作为 detached action 转发点击，并同步隐藏其原生绘制。
之前仅按第 3 个 tab 判断动作入口，会误删“消息”；现在按真实类名识别。

## QQ 音乐

首页底部导航的稳定容器是
`com.tencent.qqmusic.ui.minibar.navigation.MainDeskNavigateContainer`，它直接继承
`LinearLayout`。适配优先精确匹配该容器，并使用其直接可点击子项作为五个 tab；
标签和几何顺序仅作为类名发生变化后的兜底。点击坐标重新交给原生底栏的
`dispatchTouchEvent`。

官方 `20.9.7.9` 的启动 Activity 仍为 `AppStarterActivity`，因此入口只保留该
Activity 的精确生命周期 Hook，不安装跨 Activity 的通用回退 Hook。

固定媒体播放卡的精确根类是
`com.tencent.qqmusic.ui.minibar.music.view.MinibarRootLayout`。适配只移动该类或同一
QQ 音乐迷你播放器模块的 `ClipBottomConstraintLayout`，根据悬浮底栏实际屏幕边界保持
10dp 间距；布局稳定 120ms 后复核一次。不扫描普通页面容器，也不修改列表、广告、
原生播放中控和路由；停用时恢复原始位置。

## 深浅色

快手与 QQ 音乐的玻璃底栏使用系统 `Resources.getSystem().configuration.uiMode`，
不读取应用内页面或宿主主题，避免应用强制深色时与系统设置不一致。

## 性能

- 只在目标 `HomeActivity`/`AppStarterActivity` 安装生命周期与触摸监听，不安装通用全局 View Hook。
- tab 同步间隔为 160–200 ms，高频触摸回调只更新有限采样窗口。
- 背景优先复用独立窗口 GPU 捕获；失败时回落到有限裁剪的 PixelCopy，不做全屏 CPU 采样。
- 快手原生动作层、透明度和无障碍状态在停用或销毁时恢复。

## 验证

- 最终 Release/R8/Lint Vital 构建通过，APK v2 签名有效。
- 新增快手双包、QQ 音乐底栏识别、默认值、作用域和备份 schema 回归。
- 小米社区与淘宝共用紧凑原生底壳识别，排除全屏页面容器和 `TabHost`。
- 全量 JVM 回归通过 174 项。
- QQ 音乐真机视觉与点击仍需安装 `1.5.22` 后复核。

## 2026-11-05 两入口模式重复首页与我的

用户截图显示悬浮底栏出现“首页、首页、我的、我的”。QQ 音乐部分账号或实验开关会把
`MainDeskNavigateContainer` 收敛为“首页、我的”两个直接入口；旧逻辑只接受 3 至 6 个
直接入口，于是继续执行语义兜底。兜底同时命中入口容器的 `contentDescription` 和内部
同名 `TextView`，每个入口被计数两次。

精确容器现在接受 2 至 6 个可见且可点击的直接入口，两入口模式直接复用原生入口，
不进入语义树重复收集。两项入口仍使用原生 `contentDescription/selected`、点击路由和
图标标签映射；五入口模式与类名缺失时的标签/几何兜底不变。

新增 Robolectric 回归覆盖两入口容器必须返回两个原生 View，不得返回描述层与文字层
重复项。本轮专项类执行时既有的未知标签几何兜底测试仍在 Robolectric 返回 0 个候选，
该失败与本次两入口分支无交集；新增两入口测试及精确五入口测试通过。
