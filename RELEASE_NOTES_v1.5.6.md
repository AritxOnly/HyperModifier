# HyperModifier v1.5.6

## QQ

- 同时支持旧版 `QQTabWidget` 与 Material `QQTabLayout` 两种底栏实现。
- 在 QQ 底栏 View 构造完成时直接触发挂载，不再只等待 Activity 回调。
- `QQTabLayout` 使用自身选中索引，并通过 Tab 的实际 View 转发官方点击。
- QQ 改用宿主 Decor 内的浮层，绕过其窗口策略可能拒绝独立应用面板的问题。

## 抖音极速版

- 修正图标下方文字过度上移：仅极速版将文字基线向下调整 5dp。
- 极速版不再依赖未命中的视频 Surface hook，改用窗口级硬件 PixelCopy 采样，确保柔光玻璃能取得背景。
- 普通抖音保持原有文字布局。

## 验证

- QQ 结构解析、动画状态和默认设置相关单元测试通过。
- 编译和 release 构建通过。
