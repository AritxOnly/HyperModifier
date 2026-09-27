# 锁屏密码页背景模糊与压暗：排查记录

## 目标

在 HyperOS 的 PIN／密码解锁页中单独调整壁纸背景的模糊和压暗，使其不影响锁屏快捷方式、通知中心或控制中心。

## 当前结论

密码数字键的柔光玻璃与密码页背景模糊是两条不同的渲染链路：

| 项目 | 所属机制 | 当前结论 |
| --- | --- | --- |
| PIN 数字键柔光玻璃 | `MiGlassCompat` 材质层，附着在每个数字键的前景图层 | 可用，和背景模糊无直接关系 |
| 密码页壁纸比例／浅色混色 | SystemUI 的 `MiuiKeyguardBlurInteractor.wallpaperBlurRatio` | 传给壁纸服务的比例，不是 bouncer 容器自身的模糊半径 |
| 密码页实际背景模糊 | SystemUI 的 `MiuiKeyguardBlurInteractor.bouncerMiBlurRadius` | 返回整数半径，最终直接作用于 `bouncerContainer` |
| 根视图 `MiBackgroundBlur` | 对 PIN／密码根视图新增一层实时背景模糊 | 会叠加在系统已有模糊上，造成双重模糊；不是最终方案 |

因此，不能根据数字键的柔光玻璃是否生效来推断密码页背景模糊的注入是否正确。

## 已确认的设备源码事实

以下结论来自当前设备的 `MiuiSystemUI.apk`（`17.03.260226.r`）及正在运行的 `MiWallpaper.apk`（`7.0.7-ALPHA-08241616`）反编译结果；窗口状态也确认锁屏壁纸 Surface 由 `com.miui.miwallpaper.wallpaperservice.ImageWallpaper` 提供。

1. `com.android.keyguard.injector.MiuiKeyguardSecurityContainerControllerInjector` 持有 `mBgImageView`，对应资源 ID 为 `keyguard_bouncer_bg`。
2. `updateWallpaperPreview(boolean)` 在默认锁屏主题下会跳过 `keyguard_bouncer_bg` 的预览生成分支。
3. 非默认主题分支会调用 `MiuiKeyguardWallPaperManager.requestKeyguardWallpaperBlurPreview(...)`。
4. `MiuiKeyguardWallPaperManager$7.run()` 会：
   - 取得锁屏壁纸预览；
   - 缩放为原尺寸三分之一；
   - 读取 `window_background_blur_radius`；
   - 调用 `miuix.graphics.BitmapFactory.fastBlur(...)`；
   - 使用 `blur_background_mask` 绘制压暗蒙层；
   - 生成 `mKeyguardWallpaperBlurPreview`。
5. 锁屏快捷方式使用的是另一套资源／控制器，不能与上面的 bouncer 预览混为一谈。
6. PIN 页存在两条并行实时链路：
   - 壁纸比例：`_keyguardBlurRatio` → `wallpaperBlurRatio$2$1` → `MiuiKeyguardWallPaperManager.updateKeyguardWallpaperRatio(...)` → Binder → MiWallpaper 原生壁纸渲染器；
   - bouncer 背景半径：`_keyguardBlurRatio` → `bouncerMiBlurRadius$2$1` → `startUpdatingWallpaperRatio$1$1.emit(...)`（class-id 1）→ `MiBlurCompat.setMiBackgroundBlurRadiusCompat(...)`，接收者为 `bouncerContainer`。
7. `bouncerMiBlurRadius$2$1` 仅在锁屏／`SHOWINGBOUNCER` 等进入态中将比例转换为 `BlurUtils.blurRadiusOfRatio(ratio) * 0.7` 的整数半径；其余状态返回 0。这与壁纸比例和此前 `blur_background_mask` 的 bouncer 预览 Canvas 支路不同。

## 已尝试方案

| 方案 | 实施位置 | 结果 | 结论 |
| --- | --- | --- | --- |
| `ViewRootImpl.setWallpaperBlur` | 窗口级壁纸模糊 | 密码页无效果，容易波及非目标区域 | 排除 |
| 在 MiWallpaper 服务端缩放 `WallpaperServiceController.U(float, long)` | 锁屏壁纸比例更新 | 无可见变化 | 移除；SystemUI 的上游协程是更直接的控制点 |
| 通用 `calculateDimBlurEffect`／全局模糊控制 | 锁屏／通知中心共用计算 | 曾让快捷方式跟随滑杆变化 | 范围过宽，排除 |
| 在密码页上套 `RenderEffect` | PIN／密码根视图 | 只能处理自身内容，不能正确处理背后的壁纸 | 排除 |
| 修改 `window_background_blur_radius` | `MiuiKeyguardWallPaperManager$7` 的资源读取 | 单独没有可见变化 | 该预览可能不是当前默认主题下的最终可见背景 |
| 修改 `blur_background_mask` | `$7.run()` 的 Canvas 压暗颜色 | 单独没有可见变化 | 同上，未触及实际显示图层 |
| 清空预览缓存后反射调用 `requestKeyguardWallpaperBlurPreview` | `MiuiKeyguardSecurityContainerControllerInjector` | 无可见变化 | 外部补调可能被原控制器状态／显示时序覆盖 |
| 在 `updateWallpaperPreview` 内临时绕过默认主题判断 | `CommonUtil.isDefaultLockScreenTheme()` | 无可见变化 | 虽能尝试进入预览支路，但该支路仍不是最终可见图层，或目标调用未发生 |
| 对 PIN／密码根视图调用 `MiBackgroundBlur` 与混色 | `KeyguardPINView`／`KeyguardPasswordView` | 出现系统原模糊再叠加一层模糊 | 证明这是一条附加实时模糊链路，不是替换系统背景的正确入口；应移除或禁用 |

## 已避免的回归

1. 锁屏左下角／右下角快捷方式不再随着“密码页背景模糊”滑杆变化。
2. 控制中心背景模糊不应由密码页选项改写。
3. 数字键柔光玻璃只保留在 `key0` 到 `key9`，不承担全屏背景模糊职责。

## 暂停状态（2026-09-23）

该功能已从运行时停用：模块不再注册任何密码页背景相关的协程、`MiBlurCompat` 或 bouncer 容器 hook，也不会在 PIN／密码 View 生命周期中写入模糊、压暗或混色属性。设置页保留为禁用入口，避免已保存的偏好继续产生效果。

暂停前已完成的可验证检查如下：

1. 已从当前设备 `MiuiSystemUI.apk`（`17.03.260226.r`）确认用户提供的 `MiuiKeyguardBlurInteractor$wallpaperBlurRatio$2$1.invokeSuspend(Object)` 存在，方法签名与 smali 片段一致。
2. 连续验证过该协程本体、Kotlin `Function3.invoke` 桥接入口、`bouncerMiBlurRadius$2$1`、其最终 `emit` 消费器，以及 `MiBlurCompat.setMiBackgroundBlurRadiusCompat(int, View)`；对这些位置强制传入零值均没有改变实际密码页。
3. 已从当前设备资源确认 `keyguard_bouncer_container` 的 ID 为 `0x7f0b05df`，并尝试在已经能驱动数字键材质的 `KeyguardPINView.onFinishInflate` 生命周期中直接关闭该容器的窗口模糊、背景模糊和混色；仍无可见变化。
4. 已核验设备安装的模块 APK SHA-256 与本地构建一致；SystemUI 进程的运行时长也短于模块更新时间，排除“旧 APK”与“旧进程未重新注入”。
5. 最终 release dex 中存在候选类名、Hook ID、注册调用以及 `com.android.systemui` scope，排除 R8 删除这些实验代码的可能。

因此，目前不能把已定位的这些内部调用等同于实际密码页的最终显示层。后续恢复时，应以可获得的系统补丁／精确运行时堆栈为前提，重新确认真正的壁纸 Surface 或渲染器入口；不要恢复对 `keyguard_bouncer_bg`、根 View `MiBackgroundBlur`、通知中心全局模糊或快捷方式模糊的改动。

## 日志现状

多次通过 `adb logcat` 按模块 tag 过滤均未获得有效输出。因此，现阶段不能再以“日志没有报错”作为 hook 已命中的证据；后续应优先采用可验证的、只作用于候选视图的临时视觉标记，确认真实层级和方法调用，再替换为模糊／压暗实现。

## 后续排查边界

若这个 bouncer 消费者入口仍未产生变化，应先验证 `MiBlurCompat.setMiBackgroundBlurRadiusCompat` 的接收 View 与实际 PIN 页面是否一致；不要回退到 `keyguard_bouncer_bg`、全局 `ViewRootImpl`、壁纸服务端比例或根视图 `MiBackgroundBlur`。PIN 数字键的柔光玻璃和其布局间距调整可以保留，二者不依赖该背景方案。
