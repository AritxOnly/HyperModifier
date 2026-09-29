# 媒体超级岛：播放器高度与插件背景高度不同步

## 实机证据（2026-09-28）

设备 25113PN0EC。通过 `cmd window dump-visible-window-views` 获取 SystemUI
实时 ViewHierarchyEncoder 数据，并与 `dumpsys window windows`、展开动画日志对照：

- 主 `PlayerIslandConstraintLayout` 和 `MusicBgView` 高度均为 442px。
- dummy 播放器也为 442px，且 `DynamicIslandContentFakeView` 为 INVISIBLE。
- `DynamicIslandExpandedView` 为 442px。
- `DynamicIslandWindow` 触控区域为 `(44,162)-(1175,684)`，高度仍为 522px。
- `DynamicIslandAnimationDelegateHelper` 记录 `height:522`。
- 同时截图仍可见播放器底部下方的浅色半透明圆角区域。

因此，“只改播放器根布局”及“只刷新 dummy”不足以解决此设备上的问题。

## 插件 APK 路径

对设备实际 `/product/app/MIUISystemUIPlugin/MIUISystemUIPlugin.apk` 的代码检查发现：

1. `DynamicIslandWindowView.updateExpandedView` 的媒体分支不使用播放器实际高度，
   而直接将 `getExpandedViewMaxHeight()` 传给 `updateExpandedSize`。
2. `DynamicIslandBaseContentView.updateExpandedSize(int,int,DynamicIslandData)`
   把请求高度按 stock 最小/最大高度裁剪，写入 `expandedViewHeight`。
3. 插件展开动画和背景边界读取 `getExpandedViewHeight()`，因此可与播放器内容高度不同。

当前设备默认最大值为 168dp，折合 522px。没有证据支持将背景问题归因于
`calHeight` 的字段可见性；设备上的该字段是 public。

## 修复范围

`MediaIslandHostHooks` 在插件实际 ClassLoader 中拦截 `updateExpandedSize`。
仅当当前 `DynamicIslandData.getView()` 的子树包含
`PlayerIslandConstraintLayout` 时，使用媒体高度滑块或媒体自定义 XML 的明确高度。
关闭媒体高度且未配置明确 XML 高度时，保留原始流程。

高于默认上限的媒体高度在本次调用内临时放宽 `expandedViewMaxHeight`，并在 finally
恢复；不修改全局 `expanded_island_height_dp` 资源，不改变非媒体事件的请求参数。
stock 84dp 最小高度仍保留（滑块范围为 96–200dp）。

## 尚需实机验收

安装新签名包并重启 SystemUI 后重新展开媒体岛：

- 日志应出现 `Installed media island plugin geometry hook`。
- 尺寸日志应出现 `Media island host height: ... requested=442`。
- 展开动画高度、触控边界高度、播放器背景高度应一致。
- 截图确认无残留底部半透明区域，再检查 12306 仍使用原始高度。

编译/单元测试成功不能替代上述实机验收。
