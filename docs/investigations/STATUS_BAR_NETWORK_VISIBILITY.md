# 状态栏信号与网络活动指示器

设置入口：系统界面 → 状态栏 → 状态栏信号。四个开关默认关闭，分别控制 Wi-Fi 下隐藏移动信号左上角的 4G/5G 标识、移动网络上下行箭头、Wi-Fi 上下行箭头，以及 Wi-Fi 制式数字（例如 Wi-Fi 7 的 7）。

## 参考包核对

本地 `reference/MiuiSystemUINew.apk` 的资源表中，示例 smali 的 `0x7f0b07a0` 是 `mobile_type`，对应移动信号左上角的网络类型角标。实现按此目标隐藏；保留 `mobile_group` 和 `mobile_signal` 的系统显示状态。`status_bar_mobile_signal_group_inner.xml` 中移动网络箭头是 `mobile_left_mobile_inout`；`status_bar_wifi_group_inner.xml` 中 Wi-Fi 箭头是 `wifi_activity`，制式数字是独立的文字控件 `wifi_standard`。

实现按资源名称解析 ID，不固定数值。只在 `ModernStatusBarMobileView` 和 `ModernStatusBarWifiView` 子树中查找控件；兼容名称包含 `mobile_in`、`mobile_out`、`wifi_in`、`wifi_out` 和 Wi-Fi 子树内的 `inout_container`。其他页面中的同名控件不受影响。未找到的控件直接跳过。

## 行为

- 测量前应用 `GONE`，隐藏 `mobile_type` 角标，不隐藏整个移动信号组。独立网络类型文字 `mobile_type_single` 保持原有设置行为。
- 默认网络回调触发主线程刷新。依据当前活动网络的 `TRANSPORT_WIFI` 判断，与示例 smali 保持一致；仅打开 Wi-Fi 但未连接不隐藏。
- 隐藏期间拦截目标控件的 `setVisibility`，记录系统最新要求的可见性。网络切换或关闭开关时恢复该状态，不强行恢复为 `VISIBLE`。
- 设置加载完成时刷新已观察到的控件，覆盖设置异步加载及后续修改。
- 网络监听注册失败时保留系统原有网络类型角标显示，并写入 Hook 诊断。

## 上下行箭头位置

移动网络与 Wi-Fi 分别提供水平、垂直偏移，范围 -1.5～1.5 dp，步进 0.05 dp，默认 0 dp。点击设置名称或数值可弹窗输入精确偏移，支持小于 0.05 dp 的小数；仅接受 -1.5～1.5 之间的有限数字。示意图已移除。正值向右/向下，隐藏箭头时禁用对应滑块。偏移叠加在系统原有 translation 上，跟踪系统后续更新，不在重复测量中累加；组合容器和内部箭头只在最外层应用一次偏移。非零偏移时关闭箭头到状态栏信号根视图之间的裁剪，归零时恢复原始裁剪属性。设置加载后同步刷新，独立偏移也计入系统界面作用域的修改状态。

## 实时网速与右侧距离

`NetworkSpeedView` 的 `network_speed.xml` 使用独立 `network_speed_container` 包含网速数值与单位；外层有 `status_bar_network_speed_padding_start/end`。`getNetworkSpeedWidth()` 读取外层宽度及 padding，并缓存空文本宽度 `mEmptyWidth`，因此直接修改 padding 会改变图标槽宽且丢失小数像素精度。

新开关位于状态栏的「实时网速」分组。实际为相对原始位置的右侧间距偏移，范围 -2～2 dp，步进 0.1 dp，默认 0。正值将网速内容向左微移、增大右侧间距，负值向右微移。仅平移内部容器，不移动相邻图标、不修改原始图标槽宽；临时关闭网速根视图裁剪，归零恢复。点击名称或数值可输入更精细的有限小数，同样限制在 -2～2 dp。设置加载后刷新已有实例，新实例在首次测量时应用。系统原生实时网速需已开启。

待真机验证网速数值变化、零网速隐藏、左右相邻图标空间、锁屏，以及系统字体和密度变化。

## 验证

单元测试覆盖 Wi-Fi 断开恢复、隐藏期间系统可见性变化，以及四个开关互不影响。通过 Debug 构建和全量单元测试。

待真机核对：双 SIM、Wi-Fi 连接/断开、移动数据上下行、Wi-Fi 上下行、飞行模式、锁屏与深浅色切换，以及与独立 4G/5G 标识同时启用。需要先在 LSPosed 中启用系统界面作用域；首次安装新 Hook 后重启系统界面或设备。

用户提供的 [AnyMount](https://github.com/nakixii/AnyMount) 用于系统目录挂载；本项目通过现有 libxposed 入口实现，不需要替换系统 APK。
