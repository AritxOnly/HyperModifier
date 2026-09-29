# 通知/控制中心 Glass 材质兼容开关

用户反馈 v1.4.1 与 HyperLight 等模块同时启用时存在通控中心玻璃材质异常；尚未进行双模块设备验证，不能据此确认具体冲突原因。

`disable_shade_glass_hooks` 默认 false，在「通知/控制中心 → 材质兼容性」开启。

开启后的边界：

- PackageLoaded 前从 LSPosed remote preferences 读取开关，不安装 `ShadeCardGlassHooks` 的 setMiGlass、setMiGlassBlurRadius、卡片背景半径和可见性刷新 Hook。
- 已安装进程中的拦截器直接透传；设置刷新、已排队的 UI 回放也停止，不主动写入“系统默认”覆盖其他模块。
- 控制中心跟随 MiLink 的 BlurProvider 不再写入或恢复 maxGlassSmallBlurRadius / maxGlassBigBlurRadius；其普通背景 maxRadius / enableScale 处理仍保留。
- 通知/控制中心圆角、全局背景模糊、背景压暗、MiLink、独立悬浮通知配方及锁屏按钮材质不受影响。
- 共享 Glass 缓冲包含悬浮通知，因此其共享模糊半径/比例也暂停；这不等于关闭独立的悬浮通知配方 Hook。
- 所有参数和预设保留，应用预设或导入 JSON 不会绕过兼容开关。

切换后需要重启 SystemUI（也可重启设备）：已安装的 Hook 不会热卸载，之前写入的原生状态不会通过盲目回放恢复；从停用状态恢复也需要重新安装 Hook。未执行安装或重启设备操作。
