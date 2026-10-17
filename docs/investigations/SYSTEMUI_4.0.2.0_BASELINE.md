# SystemUI 4.0.2.0 基线与超级岛通知背景排查

2026-10-16 从测试设备直接提取，系统版本 `OS4.0.2.0.XPCCNXM`。后续 SystemUI 兼容分析以此版本为基线。

## 参考包

| 默认文件 | APK 版本 | SHA-256 |
| --- | --- | --- |
| `reference/MiuiSystemUI.apk` | `17.03.260226.r` / `202602260` | `d3628f6c3b85d16b1e74aef13ee227b41b6a855a01873c7f50364e274637f571` |
| `reference/MIUISystemUIPlugin.apk` | `18.3.2.57.0` / `183025700` | `a7abb534697ad0638bd82e932de9c622b5f2f124ffe717e5342fc4011a1ded2f` |

版本副本为 `MiuiSystemUI-OS4.0.2.0.XPCCNXM.apk` 与 `MIUISystemUIPlugin-OS4.0.2.0.XPCCNXM.apk`。源路径及哈希记录在 `reference/SYSTEMUI_BASELINE.json`。
旧默认文件按 `*-previous-<SHA256前12位>.apk` 备份，未覆盖既有 `MiuiSystemUI.bak.apk`。旧 SystemUI 哈希为 `1fefd463cbb9…`。
Smali 分析目录：`reference/systemui-4.0.2.0/`、`reference/systemui-plugin-4.0.2.0/`；均使用 `apktool d -r`，只反汇编代码，不混入旧框架资源。`reference/` 沿用 Git 忽略规则，APK 不提交到仓库。

## 已确认变化

`com.android.systemui.statusbar.notification.style.vieweffect` 下：

| 效果 | 旧默认参考包 | 4.0.2.0 |
| --- | --- | --- |
| `FocusNotificationGlassEffect.apply` | 调用 `FocusNotificationBlurEffect`；加载 `focus_notification_glass_params_normal` | 调用 `NotificationRowBlurEffect`；加载 `notification_glass_params_normal`（`0x7f0300ce`） |
| `FocusNotificationGlassOnKeyguardEffect.apply` | 调用 `FocusNotificationBlurEffect`；加载专用锁屏配方 | 调用 `NotificationRowBlurEffect`；同样加载普通通知配方 |
| `FocusNotificationGlassCustomBgEffect` | Drawable 背景 + BloomStroke shader | 同路径；增加 MediaTek 算力分支，仍不调用 `setMiGlass` |

专用正常配方 RGB 槽位 11–13 为 `(0.06, 0.06, 0.06)`、不透明度槽位 14 为 `0.70`。普通通知配方为 `(1, 1, 1)`、`0.10`。二者都仍存在于新包中，但标准超级岛通知路径不再选专用配方。

标准、锁屏、浅色壁纸锁屏、FullAod 四个 Glass 效果的 `apply(Object, Context)` / `clear(Object)` 签名仍在，目标仍是 `getInjector().getBackgroundNormal()`，最终仍调用 `View.setMiGlass(float[])`。原生配方仍为 42 项，槽位 14 保持不透明度语义。

## 真机读取与边界

- 用户报告所有超级岛通知均受影响，设置 65% 时外观接近 0%。
- 通过只读 `ModifierSettingsProvider.get_settings` 已确认：`dynamic_island_notification_opacity_enabled=true`、`dynamic_island_notification_opacity=65.0`，模块启用、Glass Hook 未停用。
- 参考包检查验证四类签名、目标、setter 及旧/新配方槽位。0/30/100% 的离线槽位替换检查通过，只能证明配方计算成立，不能证明真机 Hook 命中。
- ADB 可提取 APK、读取 SystemUI dump；当前 shell 无可用 su，不能直接读取应用私有诊断 XML。ADB input 被 INJECT_EVENTS 权限拒绝，需用户手动打开诊断页后读取。
- 配方切换是已证实的系统差异，尚不足以单独解释“65% 看起来 0%”。未取得完整运行时命中证据；之后按用户决定移除此功能，不再继续此项失效诊断，不能将移除表述为根因修复。
- CustomBg 分支不经过现有四个 Glass Hook，是已有覆盖边界，不能当作本次升级新增的分支或所有通知失效的既定原因。

## 按用户决定移除通知中心背景自定义

- 删除“自定义通知中心背景”及不透明度滑块，删除配置字段、运行时读取与导出。
- 删除 `FocusNotificationGlassTintHooks` 及 SystemUI 的两处安装入口，不再安装四种效果的 apply/clear（共 8 个）专用 Hook。
- `DynamicIslandGlassTint` 仅保留展开面板与下拉小窗：移除通知弱引用表、第二次目标查找和独立刷新遍历，保留展开背景启停、合并排队及系统原值恢复。
- 旧配置残留不再读取；备份解析仅忽略这两个已退休键，其他未知键依旧拒绝，避免旧版全量备份无法导入。
- 保留通知中心系统原生材质、普通通知卡片 Glass 设置及全局 Glass 半径。
- 性能检查属于静态结构检查：减少 Hook、目标缓存和查找/刷新分支，没有新增模糊层、每帧回调或轮询；没有真机帧率、耗电改善数据。


验证：321 项 JVM 测试通过（0 失败/错误），其中包含展开背景原值恢复、动画数组复用及旧备份退休键兼容；Release 构建与 APK v2 签名验证通过。未安装到手机或执行真机界面/功耗回归。
