# 小横条应用退场跟随

入口：小横条设置 → 应用退场时跟随。默认关闭，受「启用手势提示线修改」和模块总开关控制。需在 LSPosed 勾选「系统框架」（system），首次注入后重启设备。现有「滑动时跟随」属于 SystemUI 内的触摸位移；新功能控制导航栏 Surface 在应用退场过渡中的附着行为，两者为独立设置。

用户截图描述将 GestureLineOverlay 的 config_attachNavBarToAppDuringTransition 改为 true。AOSP 的 DisplayPolicy 将该资源缓存为 mShouldAttachNavBarToAppDuringTransition，Transition 与 RecentsAnimationController 经 shouldAttachNavBarToAppDuringTransition 判断是否执行附着。参考：

- https://android.googlesource.com/platform/frameworks/base/+/1850ba18fa20/services/core/java/com/android/server/wm/DisplayPolicy.java
- https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/wm/Transition.java

通过 libxposed onSystemServerStarting 接入 DisplayPolicy，在构造完成、onConfigurationChanged 完成、附着判断前和设置监听回调中同步 mShouldAttachNavBarToAppDuringTransition。使用 getCurrentUserResources 与系统策略相同的当前用户 Overlay 资源，手势模式（2）且三个开关开启时设为 true，其他情况从原始资源恢复。原判断方法仍自行执行导航栏存在与 OEM 条件；不持久改写 ROM Overlay。WeakHashMap 跟踪策略实例，监听器保持强引用，不持有失效 DisplayPolicy。

旧版仅临时修改 getter 内字段并立即还原，而且通过普通 mContext 判断导航模式。前者可能被已编译内联调用绕过，后者可能读不到当前用户手势 Overlay。新版同步真实缓存，并针对 Transition / RecentsAnimationController / AppTransition 的 NavigationBar、NavBar 和 handleLegacyRecents 方法执行 deoptimize。日志标签 GestureHandleTransition 输出安装状态、首次应用的 navigationMode / overlay / cached、首个同步错误以及设置刷新实例数量。没有真机框架或日志时，不能认定这些就是该 ROM 的实际故障原因。

系统框架使用 LSPosed 远程首选项读取三个开关；不依赖 SystemUI 的 Application 或 ContentProvider，也不安装其他 SystemUI Hook。未改动小横条高度。单元测试验证手势模式覆盖、按钮模式保持以及关闭时恢复原始资源。设备只连接通用模拟器，HyperOS 实际动画仍需真机核对。

## 2026-10-11 真机日志定位

用户提供 LSPosed_20261011_225453.zip。当前启动的 modules 日志显示其他模块加载在 `(system)`，但 HyperModifier 没有系统框架入口或 Hook 安装日志。旧声明错误地使用 `android`；Modern Xposed 区分 `system`（system_server）与 `android`（系统对话框等进程）。已将静态 scope.list 与 ModuleScopePackage.SYSTEM_FRAMEWORK 统一修正为 `system`，在回调第一行增加入口日志，并增加作用域资源一致性测试。参考 https://github.com/LSPosed/LSPosed/releases/tag/v1.9.1 。

ADB 确认三个设置都为 true、手势模式为 2、原始附着资源为 false；真机 services.jar 中存在全部目标方法与字段。日志还有 system_server 的 ART native 崩溃记录，但其匿名混淆堆栈不足以归因到本模块，不能据此推断模块冲突。本修复尚需更新后再次重启验证注入和动画。
