# HyperModifier v1.5.27

## QQ 音乐

- 以腾讯官方 Android `20.9.7.9` 安装包为基线，确认启动 Activity 仍为 `AppStarterActivity`，撤销上一实验版本中扩大作用面的 Activity 回退 Hook。
- 底栏改为优先精确匹配 `com.tencent.qqmusic.ui.minibar.navigation.MainDeskNavigateContainer`，不再依赖中文标签才能生效。
- 精确底栏容器下优先使用其直接可点击子项作为五个 tab；类名识别失败时才使用文字和几何兜底。
- 媒体卡改为只匹配 `MinibarRootLayout`，并以 `ClipBottomConstraintLayout` 作为同一模块内的兼容目标；不再扫描任意“带图片和点击控件的宽容器”。
- 媒体卡根据底栏实际边界上移并保留 10dp 间距，布局稳定 120ms 后复核；退出时恢复原位置并移除监听。

## 验证

- 新增精确 QQ 音乐导航容器不依赖标签的测试，以及精确媒体卡上移与恢复测试。
- 完整 JVM 回归 474 项通过。
