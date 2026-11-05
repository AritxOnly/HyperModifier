# HyperModifier 1.5.36

## 米家

- 修复米家「商城」标签图标偶尔空白的问题。
- 保留原生静态 Drawable 作为实时图标，同时在同一图标位置增加同状态的原生位图/矢量兜底。
- 当静态 Drawable 尚未加载、`ConstantState` 为空、源 View alpha 为 0 或复制失败时，兜底图标仍可显示。
- Drawable 复制异常现在降级为空内容，不会中断整个图标层组合。

## 验证

- Debug Kotlin 编译通过。
- Release、R8、Lint Vital 和 APK 签名待本轮构建验证。
- 设备当前未连接，商城标签图标的真机视觉复测尚未完成。
