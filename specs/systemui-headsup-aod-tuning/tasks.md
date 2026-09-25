# SystemUI Heads-up Handle and AOD Clock Weight Tasks

## 阶段 1：规格冻结

- [x] 完成 `spec.md` 初稿及验收边界
- [x] 完成 `module-plan.md` 初稿
- [x] 从参考 APK 确定视图、手势链和 AOD 动画入口并回写规格

## 阶段 2：基础设施

- [x] 确认偏好键、默认值、重载触发点和 Hook 安装时机

## 阶段 3：功能实现

- [x] 实现横条视觉层隐藏并保留手势路径
- [x] 实现 AOD 时钟终态字重配置
- [x] 接入持久化和设置页
- [x] 完成实现批次后统一编译

## 阶段 4：验证与回写

- [x] 执行规格校验
- [x] 执行大文件扫描
- [x] 回写验证结果和限制

## 验证记录

- `:app:assembleDebug` 通过，包含最终 Java/Kotlin 编译与 APK 打包。
- `validate_feature_spec.py specs/systemui-headsup-aod-tuning` 通过。
- `check_large_core_files.py --suffix-file core_suffixes.txt --threshold 1000` 未发现超限 Java/Kotlin 文件。
- `git diff --check` 通过。
- 当前没有连接的 Android 设备，因此下滑小窗和 AOD 动画的真机行为尚未验证。

## 底边距增量

- [x] 核对参考 APK 中底边距的资源和模板范围
- [x] 增加独立开关、0–32dp 设置及目标进程同步
- [x] 仅对小窗横条可显示的通知行应用与恢复原边距
- [x] 增量构建和签名验证（`:app:assembleRelease` 与 `apksigner verify` 通过）

## 动画结束后边距回弹修复

- [x] 根据用户反馈追踪 `NotificationContentView` 的测量下限与动画完成路径
- [x] 在测量前重应用边距，并同步目标行的收起态最小高度
- [x] 在出现动画完成后再次校准通知行
- [x] 完成本次修复的 Release 构建和签名验证（`:app:assembleRelease` 与 `apksigner verify` 通过）
