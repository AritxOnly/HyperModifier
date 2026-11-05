# HyperModifier v1.5.8

## QQ 分身

- 真机确认 QQ 9.1.25 分身底栏实际为 `com.tencent.biz.qui.quipagetabbar.QUIPageTabBar`。
- 精确接管 `QUIPageTabBar` 的 `mTabContainer` 子项，并使用 `getCurrentPosition()` 读取选中项。
- 移除微信/QQ 适配对当前进程名的过滤，覆盖 XSpace `u999` 克隆用户进程。
- 增加一次性启动日志，可直接确认 QQ 分身进程是否完成模块注入。

## 回归

- 保留 `QQTabWidget`、`QQTabLayout` 和通用底部 tab 行兜底。
