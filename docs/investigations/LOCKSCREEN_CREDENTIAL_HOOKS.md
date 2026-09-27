# 反编译 SystemUI 实现密码／指纹双向切换

整理日期：2026-09-27。本文说明如何直接修改反编译后的 SystemUI，实现“密码键盘退场 → 显示指纹 → 可返回密码输入”。参考包为 `reference/MiuiSystemUINew.apk`。

这是实施方案，目前尚未生成或验证修改后的 SystemUI APK。数字键柔光玻璃属于独立材质功能，不是双向切换的必需改动。

## 1. 反编译与定位

使用 JADX 阅读调用关系，用 apktool／baksmali 修改资源和 smali。以设备实际使用的 APK 为基础，确认框架资源、目标类、方法签名和布局资源与参考包一致。

| 目标类 | smali 方法签名 | 修改用途 |
| --- | --- | --- |
| `com.android.keyguard.KeyguardPINView`、`KeyguardPasswordView` | `updatePositionForFod()V` | 系统计算位置后移除底部 FOD 占位 |
| 上述两个 View | `startAppearAnimation()V` | 新一轮进入密码页前恢复密码模式 |
| `com.android.keyguard.KeyguardPinViewController`、`KeyguardPasswordViewController` | `onViewAttached()V` | 原始监听器安装后接管切换按钮 |
| `com.android.keyguard.KeyguardSecurityContainer` | `dispatchTouchEvent(Landroid/view/MotionEvent;)Z` | 识别上下滑，并取消原始控件触摸 |
| `com.miui.keyguard.biometrics.fod.MiuiGxzwManager` | `showGxzwView(Z)V` | 密码模式阻止指纹触摸窗口创建 |
| 同上 | `updateGxzwState()V` | 移除已有窗口、离开密码模式后恢复显示请求 |
| 同上 | `onKeyguardHide()V` | 清除指纹模式和抑制记录 |

还应定位 `MiuiFingerPrintFactory.getFingerPrintManager()`、`MiuiGxzwManager.dismissGxzwView()V`、`onKeyguardShow()V`，以及 View 脱离窗口的生命周期方法。

## 2. 新增统一切换控制器

建议新增辅助类，例如 `CredentialModeController`，集中保存当前密码 View、键盘控件、按钮、指纹 manager、模式、动画状态和手势状态。

按钮、手势、FOD 窗口控制都读取同一份模式状态，避免键盘和指纹窗口分别维护状态后失去同步。可以参考模块 [LockscreenHooks.java](app/src/main/java/com/aritxonly/myhypermodifier/runtime/LockscreenHooks.java) 中的 `CredentialSwitchState`，为每个密码 View 保存状态，并在 View 脱离和锁屏退出时清理。

若需要保留功能开关，应先明确 APK 内部的配置来源，再统一接入所有判断。当前模块的 `ModuleSettings` 和 LSPosed 远程配置链路不会自动存在于修改后的 SystemUI 中。

## 3. 修改下沉位置

在两个 `updatePositionForFod()V` 的原始位置计算完成后，将 `pin_fod_bottom_distance` 或 `password_fod_bottom_distance` 设为 `GONE`。只修改 XML 初始可见性可能被系统后续的位置计算覆盖，因此方法执行路径也需要处理。

应按实际 APK 的资源表定位 ID，避免直接复用其他版本的整数资源 ID。

## 4. 修改指纹窗口创建与恢复

在 SystemUI 内部加入统一的 FOD 抑制判断，条件为：

```text
功能开启
&& manager.mBouncer == true
&& manager.mDozing == false
&& manager.mSecurityMode 是 PIN 或 Password
&& 当前没有显式进入指纹模式
```

核心行为如下：

```text
密码模式且处于 PIN／Password bouncer：
    showGxzwView() 入口直接返回
    已有窗口则调用 dismissGxzwView()

显式进入指纹模式：
    放行 showGxzwView()
    调用 onKeyguardShow() 请求恢复窗口

返回密码模式：
    清除显式指纹标记
    先调用 dismissGxzwView()
    再恢复密码控件
```

重要插入位置：原始 `updateGxzwState()` 在 `mShowed == false` 时会提前返回。窗口移除后，“退出抑制状态并恢复显示”的判断必须能在这个提前返回之前执行，或由独立状态切换入口执行；不能简单把恢复代码追加在原方法末尾。运行时 Hook 的后置回调能在原方法提前返回后执行，而直接修改 smali 需要显式覆盖这条执行路径。

仅修改 `dismissFingerpirntIcon()` 或图标透明度无法解决触摸拦截。需要管理的是 `dismissGxzwView()` 所移除的独立窗口，以及 `showGxzwView()` 的重新创建路径。

恢复时调用检测状态感知的 `onKeyguardShow()`，保留系统对指纹检测、认证锁定和强认证的判断。不要为了显示图标而无条件强制创建可触摸的指纹窗口。

## 5. 修改按钮与动画

在两个 Controller 的 `onViewAttached()V` 完成原始监听器安装后，为 `cancel_button` 安装自己的点击监听器。它原本是右下角“返回”按钮；此处复用按钮替换为双向切换。

- 密码模式显示“使用指纹解锁”。
- 指纹模式显示“使用密码解锁”。
- PIN 动画目标为 `row0`～`row4`。
- 文字密码动画目标为 `passwordEntry`、`mixed_password_keyboard_view`。
- 保留底部按钮、紧急呼叫区域与键盘布局占位。

可沿用 `240 ms`、向下移动 `24 dp` 和透明度渐变的参数。进入指纹模式时，在键盘动画完成后将目标控件设为 `INVISIBLE`，再请求指纹窗口显示；返回密码模式时先移除指纹窗口，再执行键盘入场动画。

保持 bouncer 打开，不调用 `hideBouncer()`，才能形成同一密码界面内的二级指纹状态。直接调用系统 `startBackAnimation()` 会涉及整个密码根 View 的透明度和系统退场行为，不能直接等同于只隐藏键盘。

辅助控制器、点击监听器和动画结束回调可以先用 Java 编译，再转换成 smali 合入目标 APK，减少手写寄存器和回调类的复杂度。辅助代码应匹配目标 Android 版本；依赖 SystemUI 内部类时可使用准确的编译桩或反射，避免把整份 SystemUI 类重复打入辅助 dex。

## 6. 接入手势并保留原事件分发

在 `KeyguardSecurityContainer.dispatchTouchEvent()` 的原始分发前，通过 `mSecurityViewFlipper.getSecurityView()` 读取当前安全 View，只处理已安装切换控制器的 PIN／文字密码页。

手势判定可沿用当前模块参数：

- `ACTION_DOWN` 记录起点，起点 Y 不小于容器高度的 `45%`。
- 只识别单指；第二根手指加入时取消本次跟踪。
- 纵向位移达到 `max(64 dp, 4 × scaledTouchSlop)`。
- 横向位移不超过纵向位移绝对值的 `75%`。
- 密码模式上滑进入指纹模式，指纹模式下滑返回密码模式。

识别为切换后先向密码 View 分发 `ACTION_CANCEL`，再消费剩余触摸事件，防止同一次滑动同时触发数字键点击。未达到阈值的事件继续执行原分发，并保留原方法的布尔返回值。

单指、起点区域、纵向阈值和横向限制应统一由控制器维护。FOD 是独立窗口，从其触摸区域开始的手势是否能到达安全容器仍需设备验证，不能只根据容器代码认定所有区域都支持下滑返回。

## 7. 补齐清理路径

至少需要覆盖：

1. 两个 View 的 `startAppearAnimation()`：执行系统入场动画前恢复密码模式。
2. View 脱离窗口：取消动画、恢复控件属性、清理状态和监听器。
3. `MiuiGxzwManager.onKeyguardHide()`：清除 manager 的模式与抑制标记。
4. 快速来回切换：取消旧动画，防止旧结束回调再次开启指纹窗口。
5. 布局重建、旋转或折叠状态变化：确保旧 View 的状态不会影响新 View。

## 8. 回编译、签名与验证

修改完成后回编译 APK，检查 smali 寄存器数量、分支标签、方法返回类型、类重复、资源引用，以及新增 dex 是否包含所有辅助回调类。JADX 输出用于阅读，不能把其反编译 Java 直接视作可完整回编译的原始 SystemUI 源码。

系统部署还需要与 ROM 匹配的签名和安装方式。普通应用签名不能直接替代原系统签名；单纯替换 APK 文件也不能保证通过系统签名与共享 UID 等检查。部署前应明确目标 ROM 的可用修改方式、原 APK 恢复路径，并关闭同功能的 LSPosed Hook，避免 APK 内部修改与运行时 Hook 重复执行。

建议按以下顺序验证，逐步定位故障：

1. 仅完成密码页下沉，确认布局与数字点击正常。
2. 接入 FOD 窗口抑制，确认密码区域不再被截获。
3. 接入按钮双向切换，确认原生认证状态仍被遵守。
4. 加入动画，再验证快速反复切换。
5. 最后加入手势，检查数字键取消事件与系统原手势是否冲突。
6. 完整检查息屏唤醒、认证锁定、布局重建、PIN／文字密码，以及返回按钮与 FOD 的触摸区域。

这些修改只调整显示与触摸入口；密码校验、认证结果和系统强认证策略继续由原生流程处理。

## 9. 最小实现顺序与源码参考

最小可验证版本先实现“密码页下沉 + FOD 窗口抑制 + 按钮双向切换”。确认按钮切换与触摸正常后，再加入动画和手势。

模块对应实现集中在 [LockscreenHooks.java](app/src/main/java/com/aritxonly/myhypermodifier/runtime/LockscreenHooks.java)，按以下方法名定位：

| 模块方法 | 直接修改 APK 时需要移植的内容 |
| --- | --- |
| `lowerLockscreenCredential()` | 隐藏 FOD 底部占位 |
| `shouldSuppressCredentialFod()` | 密码模式的指纹窗口抑制条件 |
| `installLockscreenFingerprintHooks()` | `showGxzwView`、`updateGxzwState` 和锁屏退出处的内部判断 |
| `installCredentialFingerprintSwitch()` | 查找按钮、键盘控件和 FOD manager，创建控制器 |
| `CredentialSwitchState.enterFingerprintMode()` | 键盘退场完成后请求显示指纹窗口 |
| `CredentialSwitchState.returnToPassword()` | 先移除指纹窗口，再恢复键盘 |
| `CredentialSwitchState.handleSwipe()` | 手势阈值、触摸取消和事件消费 |
| `CredentialSwitchState.close()` | View 生命周期清理 |

LSPosed 的 `module.hook()`、`chain.proceed()` 和 `ExceptionMode.PROTECTIVE` 不属于 APK 内部代码。移植时要把这些行为改成原方法中的条件分支和辅助控制器调用，尤其注意提前返回、多个返回出口和异常分支。
