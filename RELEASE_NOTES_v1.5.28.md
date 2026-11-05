# HyperModifier v1.5.28

## QQ 音乐

- 修复 QQ 音乐柔光底栏的横向错位：仅对该目标启用原生底栏屏幕边界，不再套用通用
  16dp 内边距；五个 tab 的中心与原生首页底栏逐项对齐。
- 修复纵向错位：QQ 音乐目标整体下移 7dp，使替换后的图标和文字中心落到原生图标、
  文字所在位置。
- 通用底栏运行时新增 `matchNativeBarHorizontalBounds` 与
  `navigationContentOffsetY` 参数，其他应用保持原布局路径不变。

## 验证

- Release 编译、R8 和 Lint Vital 通过。
- APK v3 签名验证通过，版本号为 `1.5.28 / 73`。
- ASCII 构建路径下完整 JVM 回归 474 项通过，0 失败、0 错误、0 跳过。
