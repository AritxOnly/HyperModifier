# HyperMusicCover 扩展

设置入口：锁屏设置 → HyperMusicCover 扩展二级页。绕过时钟避让、展开通知/指纹、自定义字体高度和宽高比集中于此；设置键和值保持原样。

## 1.5.1 可用性检测

- 只查询安装包，不检测注入状态。页面进入/恢复时在 IO 线程查询官方 `com.github.zyl6932.HyperMusicCover` 与旧包名 `com.os4.musiccover`；正式包名依据：[上游构建配置](https://github.com/zyl6932/HyperMusicCover/blob/main/app/build.gradle.kts)。
- 未检测到安装包时扩展设置置灰。“强制启用”可忽略包名检测，手动解锁设置，不自动打开其他功能；Android 14 的最低版本条件仍保留。
- 强制启用默认关闭，键为 `hmc_force_enable`，随配置保存、备份、恢复和预设重置。关闭它不会清空功能设置值。
- 移除此前新增的 SystemUI 检测广播、接收器、等待超时、采样状态和对应测试；全关闭时不再为 UI 检测额外采集调用栈。功能启用后的既有 HMC 绑定路径保留。
- 无常驻轮询或新增系统渲染层。页面状态、强制启用及包卸载/重装仍待设备回归。

## 兼容依据与范围

本功能直接 Hook SystemUI 进程中 HMC 实际运行的模块，不修改或重新打包 HMC。
使用现有调用栈发现机制取得 HMC 的真实 ClassLoader，再验证 `Main` 的方法、字段类型及静态属性。
不创建第二份 HMC 实例，不依赖 `ClockCollapse`、`LockHold` 或它们的混淆名称。

2026-10-09 下载并解码以下官方发布 APK，均通过 13 个方法、4 个字段的静态签名检查：

| 官方产物 | SHA-256 |
| --- | --- |
| v0.8.1 | `88f4e14a35cbda80125ac0037a9502766d3707436e043b50d158900b6501145b` |
| nightly-20261008-f932eac | `74e0f94cfe324b0547e1002f08500fd63484396ab7ab40e4276d42e7a2cc69e6` |

上游这两个版本的 R8 规则保留 `com.os4.musiccover.Main { *; }`；官方 APK 已验证这些签名仍存在。
未来版本可能改规则、成员或行为。接口检查失败时停用对应扩展并记录 Hook 诊断。
静态签名通过不代表 ART Hook 已执行，也不代表真机 UI、其他 SystemUI 版本或未来 HMC 已兼容。

复核其他官方 APK：

```sh
python3 tools/verify-hmc-release.py /path/to/HyperMusicCover.apk
```

脚本需要 apktool，仅检查入口、成员和签名，不检查私有类的映射。

## 展开时下沉通知并隐藏指纹

从 HMC `clockHeld()` 读取展开背景 owner 的并集，不写 HMC 的 `sFpAvoid`、`sHideFp` 或状态文件。
仅在可见且锁定的锁屏全屏岛场景生效，包括 HMC 持有该场景的 AOD；不以屏幕交互状态为前提。
通知中心、控制中心、编辑器、解锁和全部卡片 LIST 展开时恢复原策略。
指纹 painter 和动画帧分别处理，遵循 HMC 的 FOD 场景判断，避免影响应用内认证。
记录原动画帧，展开时重绘为空帧，收起后经 OEM draw 队列恢复；HMC 或 HyperModifier 手动隐藏优先。

官方 v0.8.1 / nightly 没有新主线的通知策略刷新接口。因此扩展独立定位 SystemUI
通知边界 combine，在首次 collect 前追加一个 StateFlow 输入，展开、收起及策略修改都会触发
原协程重新计算。源数组保留七个 OEM 输入，以及新主线可选的第八个 HMC 策略输入。
只克隆当帧参数，不修改 combine 保留的参数数组。

首次启用后重启系统界面：通知 Flow 通常只收集一次，后装 Hook 无法保证加入既有收集。
之后开关与高度可动态更新。未捕获首次收集时，应按诊断重启，不手工重入 invokeSuspend。

## 原生字体高度

Hook `TimeView.onDraw`，临时调整原生 `height` 字段，由 OEM 的
`TextMeasureUtils.applyFontVariation` 将其转换成 `HGHT` 字体轴；finally 恢复字段。
直接输入任意有限的原生 HGHT 轴数值，支持负数和小数，默认 28（TimeView 初始化值）。
提供的 SystemUI font_set.xml 采样表覆盖 10..100，但采样范围不代表字体轴边界；低于 10 的字形效果需真机确认。
进入前由 setHeight/onDraw 捕获并固定原生高度基准，按 HMC cardProgress 过渡至输入值；
不会以每帧正在被 OEM 压缩的高度重新作为插值起点。尺寸补偿也使用同一固定 HGHT 基准，
避免 OEM 字形尾动画使时钟先缩小再长大；退出后恢复 OEM 字段。
不支持 HGHT 的字体、轴边界和轮廓变化均以原生实际输出为准，不使用纵向拉伸补偿。

复制 Paint 测量同一文字、字号、字体在原轴与新轴下的真实轮廓。
Hook HMC 保留的 `Main.glyphUnit(RectF)` 返回原轴对应的单位，使其原有等比姿态映射
保留 HGHT 引起的字形变化，通知避让及歌词下边界继续读取实际 ink box。
高度轴按 HMC 媒体卡片进度过渡；HMC 放开 `sHoldY` 后恢复原轴，保持小时钟的 AOD 沿用轴。
只处理 HMC 所拥有时钟树里的 TimeView，编辑器不应用自定义高度。

测试请使用官方 HMC。此前本地 HMC 开发版也实现了高度参数；若两个实现同时启用会叠加。

## 验证

- 单元测试：展开/收起和场景恢复、HMC 三种原策略、全局下沉、输入数组保留、轴过渡和无效值。
- 验证字体实际轮廓在等比尺寸映射后仍保留高度变化，非线性轴变化不能被目标高度抵消。
- 反射适配测试：内部 helper 使用模拟混淆名称；缺失或签名变化的 Main 被拒绝。
- 官方 APK 签名复核与 HyperModifier 自身 R8 Release 构建。

仍需真机验证：两种官方 HMC、多种 AllInOne 字体、指纹静态帧与脉冲、通知出现/消失、
控制中心/通知中心/编辑器/解锁、AOD、快速展开收起、多个 owner 交接及设置重载。

原百分比键 `hmc_clock_font_height` 保留为读取兼容，使用初始化值 28 × 旧比例迁移。
新值保存到 `hmc_clock_font_height_axis`，优先于旧键；旧比例曾依赖 OEM 当帧高度，迁移无法精确复刻所有状态。

## 全部卡片展开与全屏岛场景

对照本地 codex/lockscreen-island-backdrop：原实现由 LockHold 的 COVER/IMMERSIVE 联合所有权
和 MiniPlayerScene 的 KEYGUARD 场景控制，通知模式随场景变化经 StateFlow 恢复。
移植前仅复制 held + KEYGUARD，缺少 OEM 卡片列表状态的排除条件。
当前从 NotificationContainerViewModel.getCurrentsStackState 的 SynchronousStateFlow 读取状态；
LIST（上滑展开全部卡片）或未知状态时禁用自动隐藏/下沉；NUMBER/STACK 不单独表示全屏，仍必须有 LockHold。
PRE_DRAW 与通知 transform 都重新读取状态，退出 LIST 后按当前全屏岛所有权恢复。
HMC/Main 注入签名未增加，避免依赖 R8 混淆后的 MiniPlayerScene/LockHold 类名。

## 2026-10-10：全屏退出后的下沉残留

真机反馈：展开全部卡片时指纹已恢复，通知仍下沉。说明自动场景已退出，
而通知源输入仍可能保留此前的 false。检查发现移植额外把自动展开策略注入
useExtraShelfSpace 的 invokeSuspend；此 Flow 没有订阅扩展 StateFlow，
场景退出无法使其重新发出 OEM 值。原 HMC 的 NotificationSink 只改通知边界
combine 的克隆输入，未使用这条额外覆盖。

移除自动模式对 useExtraShelfSpace 的覆盖，保留独立的全局手动下沉设置。
HMC adapter 已绑定时跳过旧 invokeSuspend/L$1 修改，自动模式只经
HyperMusicCoverNotificationFlow 的克隆输入和 StateFlow 驱动。
升级后重启 SystemUI，让被上一版污染的 shelf Flow 从原始输入重新收集。
实际展开/收起的屏幕表现仍需真机确认。

## AOD 与配置验证结果

用户确认：HMC 通知避让设为“跟随系统”后，全部卡片展开时能正确恢复。
HMC 的手动下沉策略与 HyperModifier 的全屏自动策略互相独立；测试自动恢复时应让 HMC 跟随系统。

后续 AOD 反馈显示息屏展开后要等亮屏才下沉：移植版多出的 screenOn 条件关闭了自动策略，
原分支的 backdropSurfaceVisible 没有这个条件。现移除交互屏幕要求，保留 LockHold、可见锁屏、
KEYGUARD 与 NUMBER/STACK 场景约束，并在 setIsDozing 后刷新；进入 AOD 时按原分支重置
被打断的 keyguardGoingAway。不可见的插件 AOD 不因屏幕息屏而单独启用策略。

## 原生字形宽高比实验

高度模式保留；另增“按宽高比调整字体”，默认关闭，需要启用字体高度扩展。
输入值是两位数字 00 的轮廓宽高比，默认 2.4，正数越大越扁，不是百分比。
参照 AllInOneBase.setClockViewRect 的三轴加字号路径：在实际 Typeface 上测量 00，
优先在当前 wdth 下搜索 HGHT 10..100，触及边界后搜索 wdth 10..100。
通过 textSizePx 调整字号保持 00 的原生轮廓宽度，不设置 View.scaleY、Canvas.scale 或 Paint.textScaleX。
使用当前字体实时测量而非硬编码 font_set.xml 表，超出字体范围时停在可达端点。
每个 TimeView 缓存展开基准的字重、宽度与求解结果，参数或 Typeface 改变时再求解；
按 HMC cardProgress 过渡三轴，每帧按当前 OEM 字重测量宽度并匹配字号，finally 恢复三项字段。
补充捕获 setClockParamsInternal 等批量 setter，避免漏掉 setClockViewRect 写入的自然基准。
此处复用求解思路，没有调用 setClockViewRect 改写系统布局；不同字重的终态比例仍需真机比较。

绘制 Hook 采用最低优先级，原生 onDraw 返回后先恢复临时字体字段，再返回外层
HMC onGlyphDrawn 的重新定位回调，避免 finally 覆盖该回调新写入的 OEM 动画参数。

### AOD wake size and delayed font-weight correction

The experimental drawing path now snapshots HGHT, wdth, wght and textSizePx together for the held scene. OEM setters still run and are restored after drawing, but their AOD/wake or trailing Folme values cannot replace this reference. Scene ownership accepts retained Main.clockHeld even while sHoldY is temporarily absent. The native AllInOneClockAnimation.updateLockScreenStyle(ClockResult) supplies the lockscreen target timeWeight per mAllContainer; wght is interpolated using HMC cardProgress alongside HGHT/wdth. An unavailable target retains the entry weight. The axis solver uses the endpoint weight.

Main.glyphUnit receives the maximum reference ink height directly, matching HMC's own per-digit local unit convention; the previous shaped/reference ratio division could combine stale draw data with freshly measured native units. This prevents compensation from depending on OEM point-size/weight changes through sleep/wake. Device validation is still required for the complete animation and AOD transition.

### Shared scene reference and colon layout correction

Per-TimeView snapshots are replaced by one reference for the complete retained HMC clock-hold session. Hour, minute and colon, and newly introduced AOD/foreground copies, use the same native axes and point size. Temporary font inactivity does not release the reference while clockHeld or sHoldY still owns the scene. It is cleared only when that ownership ends.

AllInOneBase.setClockViewRect measures mTextMeasurePaint and writes segmented advances and draw origins before our drawing hook applies the font. These origins therefore described a different font. The draw hook now recomputes segment advances with the actual shaped Paint, keeps the row centered in mClockViewRect, and updates hour/minute/colon draw origins, advance widths and baseline together. The colon prefix of the minute string and the separate colon copy intentionally share an origin as in OEM code. Both must move past the newly measured hour width. No extra View/Canvas scaling is introduced. Device checks for AOD size and layout remain outstanding.

### Complete clock frames and visible unlock handoff

getTextBoundsWithPosition now publishes a prepared snapshot for all hour/minute/colon views before HMC computes its placement. The snapshot is prepared once using AnimationUtils' Choreographer-locked animation time and reused by native onDraw and HMC's after-draw remeasure. Native font setters still run, but a late OEM layout cannot move one digit within a frame prepared using the previous layout. Original native baseline coordinates are kept instead of computing a second baseline from the modified Paint font metrics. Duplicate font invalidation was removed. Failed preparation resets its frame gate for retry.

keyguardGoingAway captures the last held clock pose before native/HMC release. The existing prepared font and coordinates remain during the visible unlock fade; only the captured time_group's scale/pivot/translation setters are held, while native requested values are recorded and replayed after the clock or any ancestor becomes invisible/transparent. Notification/FOD policy still releases at unlock start. New keyguard presentation or an interrupted unlock into AOD clears the exit capture; closing the held font scene also clears old pose observations. No timer decides when a visible clock may return to its native font. Device motion and unlock verification remain necessary.
