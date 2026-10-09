# AllInOne 锁屏时钟渐进避让

设置：`progressive_lockscreen_clock_avoidance`，默认关闭，入口为锁屏时钟设置。

32dp 是避让边界。原始布局与通知之间已有足够间距时不动，不把时钟拉向通知，也不放大时间字体。以当前设备密度 520dpi 计算，32dp = 104px。

## 当前系统的依据

连接设备 OS4.0.0.32.XPCCNXM 的 MIUISystemUI 与 `reference/MiuiSystemUINew.apk` 相同。`KeyguardClockNotifInteractor.calculateClockSqueezeInternal()` 先更新适配布局参数，再根据通知预留区域计算位移与字体尺寸。其预留通知顶部不等于短通知卡片的实际顶部。

原生空间分支按顶部和底部比例分配位移；超过可用空间后，直接将位移设为顶部剩余空间的负值，再压缩字体。因此居中的大时钟可能提前移动到顶部。

## 实现

`LockscreenClockAvoidanceHooks` 在原生计算之后替换返回的 `ClockResult`，保留原生缓存及 `AllInOneClockAnimation` 的 Folme 动画、拖动状态和 AOD 流程。

- 用 `maxEditRect`、适配顶部比例、完整时钟高度和杂志高度重建无通知的布局，保留原生杂志布局位移。
- 从通知堆栈中读取可见通知行的实际顶部，并用 `ViewState.mYTranslation` 校正到行的目标位置。忽略隐藏行和折叠后透明的行。
- 侵入量 = `max(0, 原始底边 + 32dp - 实际通知顶部)`。
- 先仅按侵入量上移，顶部空间用完后才压缩时间字体。高度、宽高比与字重缩放沿用系统的尺寸边界。
- 达到系统最小字体高度后仍不足时，沿用原生极限分支的额外上移方式；此时顶部安全区域可能无法同时满足。
- 预绘制监听只在通知目标顶部或开关状态变化时重算，处理通知布局变化但系统预留顶部没有发出新事件的情况。关闭时重新应用原生结果。
- HyperMusicCover 的 `sHoldY` / `sSelfDriving` 表示音乐封面接管时跳过调整。渐进避让开关也会启用现有 HMC 类发现流程，但不会自行打开“绕过 HyperMusicCover”开关。

范围为 Android 14+、当前 AllInOne 可变字体动画；普通时钟、AOD、无法识别的通知堆栈和异常布局保留原生结果。反射失败记录兼容性诊断并停止替换。

## 验证

纯计算测试覆盖原始大间距、104px 边界、逐像素连续避让、先移动后压缩、最小尺寸、通知尚未布局和杂志原始位移。构建测试不能替代装机后的两模块动画及通知折叠实测。
