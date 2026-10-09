# 关于手机双卡片与浮光颜色

依据 `reference/Settings.apk` 的资源与 smali 实现。两个功能默认关闭，入口位于模块主页的系统功能 → 系统设置。

## 已确认的系统实现

- `com.android.settings.device.MiuiMyDeviceSettings.onViewCreated(View, Bundle)`：页面布局准备完成的入口。
- `device_basic_layout`：竖向 `LinearLayout`，原背景为 `new_device_card_back_ground`。
- `device_name_card_view` / `device_memory_card_view`：`MiuiDeviceNameCard` / `MiuiMemoryCard`，均继承 `FrameLayout`。
- 卡片内部布局 `my_device_info_item`：`title`、`summary` 和右箭头。
- 名称刷新写入 `mDeviceNameText`；存储回调写入 `totalText`。双卡片复用原 TextView 和原卡片实例，因此保留数据刷新、名称编辑、用户限制和存储页面跳转。
- `MiuiMemoryCard$MemoryInfoCallback.handleTaskResult(long)` 返回可用空间，结合 `MiuiAboutPhoneUtils.getTotalMemoryBytes()` 计算占用环；数值文案保留系统的已用/总容量格式，移除末尾的扩容说明。
- `BgEffectPainter` 的 `uColors` 是 16 个 float 的 RGBA 光点数组，`mBgRuntimeShader` 使用 `bg_frag.glsl`。控制器每帧先调用 `updateMaterials(float)`，再调用 `getRenderEffect()`。

## 修改范围

双卡片从原信息组抽出，等宽并排显示标签、加粗数值和底部图示，相对原位置上移 24dp。标题和数值均为单行，数值超宽时循环滚动。卡片基础高度 148dp，大字体时等高增加空间。两侧图示统一为 64dp，固定距卡片起始边 14dp、底边 12dp。存储占用环使用原回调的字节数，不解析本地化文案。背景复用系统卡片 drawable，其余行保留在原信息组内。HyperOS 标志、版本、保修、硬件参数和后续列表使用原布局。

浮光使用 `#RRGGBB` 指定颜色，替换着色器彩色光点的 RGB，保留每点明暗和 alpha、原位置、运动和动画时间，以及中性的页面底色。不修改系统保存的原调色板，关闭功能后下一帧可恢复原浮光。系统禁用浮光的第三方主题/低性能路径不额外创建浮光。

配置键：`about_phone_cards_enabled`、`about_phone_glow_enabled`、`about_phone_glow_color`。已接入本地存储、Provider Bundle、LSPosed 远程配置、作用域状态和重启选择。LSPosed 需启用 `com.android.settings`；更改布局后重新创建关于手机页面，或重启系统设置。

## 验证

- Kotlin / Java 编译通过。
- `testDebugUnitTest` 通过，新增用例覆盖颜色格式与回退、调色板不变性、alpha/底色保持、占用比例边界及功能默认值和独立开关。
- `assembleDebug` 通过。
- 尚未安装此构建验证实机布局、名称编辑及存储详情交互；后续需要检查深浅色、长名称、大字体及横屏。
