# HyperModifier 功耗采集

本模块的 hook 在目标进程中运行；电池统计可能归到 SystemUI、Spotify、哔哩哔哩等宿主，而不是设置应用。需要比较相同场景下模块启用与禁用的结果才能判断模块的增量开销。尚未采集真机数据，不能据代码确认耗电原因。

## 内置监测入口

打开「关于」，连续点击「LSPosed API」5 次（相邻点击间隔不超过 2 秒），显示隐藏的「性能分析」区域。入口默认隐藏，仅在当前页面解锁，不保存为永久设置；已有监测和数据不受隐藏影响。选择 15 / 30 分钟（默认 15 分钟）。开启「读取整机统计（Root）」后开始测试，先完成 Root 授权及基线采集。看到「基线就绪，请熄屏」再锁屏，拔掉充电线，期间不操作手机。时长从基线完成后计算，Root 授权与准备时间不占用测试时长。到时重新打开本页，会补齐结束样本；也可提前结束。

短时测试只采集首尾两次，不再每 15 分钟唤醒采样，不常驻前台服务或持续持有唤醒锁。WorkManager 仅负责一次结束任务，可能因系统休眠而延迟；返回页面也会完成已到期的测试。报告显示实际覆盖与结束延迟，不能把延迟后的区间硬当作设定的 15 / 30 分钟。进程退出后结束任务由系统恢复；强制停止、重启或厂商后台限制可能中断测试，重启后的时间不连续时不计算消耗/休眠。

基础数据包括电量、电量计、电流、温度、充电、采样时亮屏状态，以及 elapsedRealtime / uptimeMillis 首尾时钟。两种时钟的增量之差用于估算区间深度休眠；未休眠不表示 CPU 一直满载。

Root 模式在首尾保存 `dumpsys batterystats --charged`、`dumpsys cpuinfo`、`dumpsys power`。`--charged` 跳过多日历史，保存累计统计摘要；不使用会处理旧统计的 `--checkin`，不清空电池统计。每条命令最多等待 15 秒，单文件超过 16 MiB 时标记采集失败，不把截断输出当完整统计；`capture-before/after.json` 记录每轮实际采集时间与成功状态。首尾统计的起点不一致或格式不支持时，不计算亮屏增量。结束命令有少量额外开销，亮屏操作和采集开销会在报告中说明。

报告显示整机电量计减少量、实际时长、深度休眠比例、累计亮屏时长增量与真实 CPU 快照窗口。超过一分钟亮屏时提示不能当作纯熄屏测试。统计估算不等于硬件实测，CPU 快照不等于整段 CPU。模块列表仅表示检测到的已安装模块，不验证启用状态，也不自动停用其他模块。

本机保留最近三轮记录，不上传；支持查看与导出最新一轮 ZIP，前两轮作为本机备份。对照测试使用相同监测配置并固定 AOD、网络、通知和音乐等条件。

参考：[WorkManager 延迟执行](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)、[Android 时钟口径](https://developer.android.com/reference/android/os/SystemClock)、[BatteryManager 电量计单位](https://developer.android.com/reference/android/os/BatteryManager)。

## 工具与准备

电脑需要 Python 3、Android platform-tools 的 `adb`；手机打开调试并授权。多设备连接时附加 `--serial DEVICE_SERIAL`。工具不需要 root，不重置电池统计、不切换模块、不改手机电源设置。系统未开放的数据源可能没有数据或返回权限错误。

在项目根目录运行，输出默认保存在 `power-captures/`。用 `--output /path/to/folder` 改变输出位置。报告记录设备属性、进程及电池统计；共享前检查其中的设备标识和应用信息。

## 亮屏：定位 CPU 与渲染开销

```sh
python3 tools/power/capture.py trace --label scroll-on --seconds 60
```

出现 Recording 后，在一个目标应用内复现滑动、切换标签等操作。另录一次停留静态页面，再录通知栏/控制中心场景，每次只测一种操作。将 `trace.perfetto-trace` 打开到 https://ui.perfetto.dev ，观察宿主主线程、RenderThread、CPU 调度/频率、帧时间与电池计数器；支持的设备还会提供 power rails。

通过 LSPosed 禁用模块并重启对应宿主（SystemUI 场景建议重启手机），待启动稳定、温度接近后，用同样时长和操作录制 `scroll-off`。固定亮度、刷新率、网络、音量和内容，每组至少重复三次，避免用单次峰值作结论。

电池电流/电量计测的是整机；连接充电器时不能当作放电功耗。优先用无线调试并拔掉 USB，保持每组调试条件相同。无线调试也有开销。`dumpsys battery unplug` 仅模拟拔电状态，不会切断物理供电。

## 熄屏：检查长时间待机

```sh
python3 tools/power/capture.py snapshot --label idle-on-before
```

随后断开 USB，停止调试流量，熄屏自然待机 1–2 小时，记录起止时间、电量、是否有音乐/AOD/通知。结束后重新连接，尽快执行：

```sh
python3 tools/power/capture.py snapshot --label idle-on-after
```

禁用模块并重启手机后，以相同条件执行 `idle-off-before` 和 `idle-off-after`。每对 before/after 必须处于同一次开机与连续电池统计周期内；不要跨重启直接相减。如果系统自动重置统计，该组重新测量。连接取数的时间也会进入统计，需要尽量短且两组一致。

比较 `batterystats.txt` 中宿主 UID 的 CPU、partial wakelock、唤醒与网络活动的前后增量；`power.txt` 只是采集时刻的状态，不能说明整个待机期间。系统的 mAh 归因往往是估计值。长时间待机先用快照，若仍不明确，再录短时熄屏 Perfetto 定位唤醒，避免持续高频采样影响自然待机。

## 报告应该区分的结论

| 数据 | 能得出的结论 | 限制 |
| --- | --- | --- |
| 电量计 charge counter 的前后差 | 整机消耗多少 mAh | 依赖设备支持和分辨率，充电影响结果 |
| 电流 + 电压 | 整机功率随时间的变化 | 单点不代表均值；电流符号、单位要核对 |
| Power rails | 支持的硬件分组能耗 | 厂商未开放时无法得到，不等于单个 hook 耗电 |
| batterystats | UID 的估算耗电、wakelock 等累计活动 | 估算值不能冒充精确硬件测量 |
| Perfetto 调度与帧数据 | 哪些进程/线程持续工作或渲染 | 本身不等于瓦数，也不直接识别每个 hook |
| 相同场景模块 on/off 对照 | 模块带来的增量开销 | 重复实验，控制温度与其他后台活动 |

项目中优先检查背景 PixelCopy/硬件采样及其引发的重绘，随后检查后台/熄屏后采样是否停止、SystemUI 周期回调和媒体更新。共享采样器支持跟随显示刷新率，采样间隔策略最高按 120 Hz 计算，这只是排查线索，需要真机 trace 证明实际频率与开销。若要归因到具体方法，下一步根据 trace 热点加入少量 `android.os.Trace` 标记，再复测。

参考：[Perfetto 电池与功耗数据源](https://perfetto.dev/docs/data-sources/battery-counters)、[Android dumpsys 电池统计](https://developer.android.com/tools/dumpsys#battery)、[Android Studio Power Profiler](https://developer.android.com/studio/profile/power-profiler)。
