# SystemUI Heads-up Handle and AOD Clock Weight

## 背景

- 创建日期：2026-09-24
- 功能标识：`systemui-headsup-aod-tuning`
- 需求来源：用户提出隐藏悬浮通知底部横条但保留下滑小窗手势，并调整 AOD 时钟动画终态字重。

## 目标

- 在设置页提供独立开关，隐藏本机 HyperOS 悬浮通知的底部提示横条。
- 横条隐藏后，通知卡片的下滑小窗手势保持可用。
- 可单独自定义有小窗横条的悬浮通知内容底部边距，未启用时保持原布局。
- 在设置页提供 AOD 时钟终态字重自定义，默认值保持系统行为。
- 以 `reference/MiuiSystemUI.apk` 和必要的插件 APK 为依据定位实际资源、视图和动画入口。

## 非目标

- 不改变通知手势判定、滑动阈值、小窗启动流程或通知内容布局。
- 不改变锁屏常亮态之外的时钟字重，也不替换整段原厂过渡动画。
- 不为未知 HyperOS 版本猜测资源 ID；缺失时保持原厂行为。

## 用户场景

1. 用户关闭底部横条后，悬浮通知不再画出该提示，但仍可从通知卡片下滑打开小窗。
2. 用户选择 AOD 终态时钟字重后，时钟进入 AOD 时到达所选字重；退出 AOD 恢复原厂目标。
3. 功能关闭时两处均保持原厂行为。

## 验收标准

- 设置可持久化、重启 SystemUI 后可用；范围经过校验，不接受非法数值。
- 底部边距为独立的 0–32dp 选项，仅在原厂判定小窗横条应显示的通知行生效；行退出该状态时恢复原边距。
- Hook 限定于已核实的视图或方法；可选类缺失不妨碍其他 Hook 安装。
- Java/Kotlin debug 编译通过；新核心文件低于 1000 有效行。
- 在规格文档记录从参考 APK 得到的类名、方法、资源及手势所有权证据。

## 风险与约束

- SystemUI 在目标设备上的时序敏感；安装在对应包的早期生命周期，但运行时找不到目标时安全退化。
- 仅做可见层隐藏，保留横条所在父容器及通知卡片的触摸链。
- APK 可能与其他设备固件不同，采用类/资源存在性检测与有限范围的 Hook。
- 这两个功能属于同一 Android 模块，无 iOS/HarmonyOS 改动。

## 参考 APK 证据

- `reference/MiuiSystemUI.apk` 的 `res/layout/heads_up_mini_window_bar.xml` 定义独立 `@id/mini_window_bar` View，`status_bar_notification_row.xml` 将其包含在通知行中。
- `ExpandableNotificationRowInjector.updateMiniWindowBar()` 计算 `canSlide` 后设置该 View 的 `VISIBLE/INVISIBLE`，并更新提示图标；因此删除视图或改写 `canSlide` 会改变原厂语义。
- 普通、长文本、收件箱和大图通知模板在内容容器使用 `miui_notification_content_margin_bottom`（此 APK 为 13dp）；大基础模板在无 ID 的内层容器上使用同一尺寸。该尺寸不是小横条 View 的高度。
- `NotificationContentView.onMeasure()` 还把收起态通知重新测量至 `mMinContractedHeight`（此 APK 的 `min_notification_layout_height` 为约 73.45dp），因此单独把模板边距调为 0 不一定能缩短整张卡片。
- `AppMiniWindowRowTouchHelper.onInterceptTouchEvent()` 从通知行 `ExpandableNotificationRowInjector.canSlide` 判断手势资格，不读取横条 View 可见性。隐藏 View 绘制可保留手势。
- `AllInOneClockAnimation.fontAnimState(String, ClockStyleParams)` 从 `timeWeight` 生成 `FONT_WEIGHT` 动画属性；标签分为 `all_in_one_lock_screen`、`all_in_one_aod` 和 `all_in_one_full_aod`。
- `AllInOneClockAnimation.getTargetStyle()` 返回相应 AOD 样式并在完成时同步时钟。两个 AOD 样式需要同时修改，锁屏样式不应修改。

## 实现范围

- 横条只改变 `mini_window_bar` 的视觉透明度和自身高度，不改可见性、父布局和触摸路径；关闭选项时恢复原高度。
- 底边距 Hook 在 `updateMiniWindowBar()` 原逻辑之后运行，只对 `miniBarVisible` 为真的通知行调整内容容器的底边距；默认关闭，退出该状态时恢复每个容器原值。
- 时钟字重适用于原厂 `AllInOneClockAnimation` 的联动 AOD 与完整 AOD；其他使用 SVG 或不同动画器的时钟样式保持原厂行为。
- 字重范围采用 100–700；原厂 AOD 权重计算将输入上限限制为 700。设置关闭时保持原厂目标值。

## 实现结果与限制

- 通知行 Hook 修改横条 View 的 `alpha` 和自身高度，保留系统自身的 `visibility` 与 `canSlide`；默认关闭时不写入视图属性。
- 该 View 叠放在 `ExpandableNotificationRow` 中，原始高度为 3.5dp；通知模板自身另有 13dp 底部边距。收起横条高度不保证消除所有底部留白，因此不擅自改动所有通知共用的内容边距。
- 新增的独立开关允许把这部分内容边距设为 0–32dp；只修改当前符合小窗横条显示条件的通知行，不替换全局尺寸资源。
- 用户反馈边距为 0 时出现“先缩小、动画结束又留白”；修复同步调整目标行收起态内容的最小测量高度，并在内容测量与通知出现动画完成后重新应用，避免布局刷新覆盖自定义值。
- AllInOne 动画 Hook 只针对 `all_in_one_aod` 与 `all_in_one_full_aod` 标签修改 `ClockStyleParams.timeWeight`，并记录原值以便开关关闭时恢复。
- 新增选项默认关闭，存储与目标进程均校验字重范围；需要重启 SystemUI 应用新配置。
- 参考 APK 为本地 `reference/MiuiSystemUI.apk`；若目标设备版本缺失上述类或方法，Hook 安全跳过。其他时钟动画器不在本次覆盖范围内。
- 当前无连接设备，真机视觉效果和手势仍待验证。
