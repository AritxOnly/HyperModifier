# HyperModifier v1.5.5

## QQ 修复

- 不再只依赖固定的 `QQTabWidget` 类名，改为同时从 `QQTabHost` 获取实际 TabWidget。
- 增加底栏挂载、未找到底栏和延迟挂载诊断。

## 抖音极速版

- 适配抖音极速版 40.4.0（`com.ss.android.ugc.aweme.lite`）。
- 复用抖音的柔光玻璃底栏、视频背景采样和快速切换打断动画。
- 抖音与极速版分别提供独立开关，共用同一套渲染与生命周期实现。
- 内部底栏改为通过 `root_view` 资源结构识别，不再依赖可能变化的 R8 类名。

## 回归

- 新增 QQ TabHost 结构解析测试。
- 完整单元测试、R8 压缩和 release 构建通过。
