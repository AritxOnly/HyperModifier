# 快捷键自定义与小爱记忆

## 参考模块分析

输入：`/Users/aritxonly/Downloads/hook.apk`，Xposed 入口 `com.example.myno.hook.MainHook`。
SHA-256：`4c16e8acdf2567114b1c0ad1765c6acb2e6385673137d361320e65f7b7c295db`。

使用 apktool 读取 smali，APK 中的文字仅作为分析资料。

- `XiaoAiCaptureScreenHook` 注入 `com.miui.voiceassist`，捕获
  `com.xiaomi.voiceassistant.VoiceService`，接收模块自定义截图广播。
- 优先调用 `VoiceService.triggerMemoryWithScreenshot(String, String)`，来源为
  `com.miui.voiceassist.query&&ai_physical_key`。这条原生流程负责截图与记忆。
- 备用流程通过混淆的 `FavoriteActionManager`、Hilt 与 NLP 请求执行截图收藏。
  本实现不复制这条版本依赖较重的备用路径。
- `XiaoAiScreenRecognitionHook` 在识屏 Activity 创建后补充截图。
  首选 `com.xiaomi.voiceassistant.db.a.getScreenCaptureInner()`，写入静态
  `db.A`（Bitmap）、`db.B`（RectF）；另有截图工具类/DexKit 查找备用路径。

## 本项目实现

设置页：系统界面 → 快捷键。两个快捷键独立保存，值为
`stock / wallet / memory / screen / none`。默认 `stock`，不改写系统设置数据库。
“无”消费快捷操作；“系统默认”放行原方法。模块总开关关闭时恢复放行。

作用域：`system` 与 `com.miui.voiceassist`。首次安装需重启；运行时读取远程设置。

音量+ 与电源键：观察 `KeyCombinationManager.addRule`，只识别 24、26 两个键码的规则，
只 Hook 已确认组合键实例的 execute，不 Hook 共用 preCondition。兼容 Android 17 将类从 policy 移至 input 的变化。
失败时放行原 execute；不会拦截整个关机界面，也不会改动音量减截图组合。

双击电源键：检查 HyperOS `ShortCutActionsUtils.triggerFunction` 四/五参数 boolean 重载，
只接管来源 `double_click_power_key`。钱包沿用原生 `mi_pay` 动作。
只 Hook `MiuiKeyShortcutManager.getFunction`，移除旧版共用 MiuiSingleKeyRule.getFunction 拦截与显式去优化。
自定义时向检测器提供 `mi_pay`，保持双击检测和系统的延迟处理；实际动作仍在分发边界替换。

小爱：用显式 Intent 启动 VoiceService，再在 onStartCommand 中执行请求。
原生服务启动回调完成后，排到主线程下一次消息执行请求；冷启动链路尚待目标版本真机验证。没有新增导出的广播接收器。
记忆调用参考 APK 的原生完整链路。识屏在服务内先获取截图并设置缓存，再打开识屏 Activity，
避免首次截到识屏自身。对字段和方法检查签名；版本不匹配时日志与 Toast 提示。

## 证据与验证边界

公开 HyperOS 框架参考（较旧版本，不能替代目标 ROM 的反编译和真机验证）：

- [ShortCutActionsUtils](https://github.com/chrmhoffmann/miui-services/blob/main/sources/com/miui/server/input/util/ShortCutActionsUtils.java)
- [MiuiKeyShortcutManager](https://github.com/chrmhoffmann/miui-services/blob/main/sources/com/android/server/policy/MiuiKeyShortcutManager.java)
- [MiuiSingleKeyRule](https://github.com/chrmhoffmann/miui-services/blob/main/sources/com/android/server/policy/MiuiSingleKeyRule.java)

本地 `/private/tmp/mhm-device-services.jar` 中验证了 Android 17 的 input.KeyCombinationManager
类名、addRule、mKeyCode1/2、execute/preCondition 签名。没有对应 ROM 的 miui-services.jar
或超级小爱 APK，因此 OEM 双击检测与小爱混淆字段仍需真机核对。

待真机检查：两种快捷键 × 系统默认/钱包/记忆/识屏/无；冷启动小爱；屏幕亮灭和锁屏；
切回默认、关闭模块总开关；单击电源、长按电源、音量减截图、SOS 连按的回归。
未验证的 ROM 若改变检测器/缓存签名，日志会指出不可用项。

验证结果：`:app:assembleDebug` 成功；快捷键策略、默认值和作用域共 5 项测试通过。
全量 243 项测试有 1 项已有失败：BilibiliNavigationPolicyTest 期望模块预设关闭替换底栏，
而当前 ModuleDefaultValues 默认开启（不属于本次修改）。
普通 Android 模拟器中已检查页面布局、全部五个选项，验证组合键设为小爱记忆、双击电源键设为无后，两项独立保存且进程停止后仍保留。
模拟器不能验证 HyperOS 按键和小爱运行链路。


## 2026-10-13 静态修复：双击记忆不触发、整机卡顿耗电

用户配置：音量+ 与电源键为钱包，双击电源键为小爱记忆。此轮没有连接真机，
以下是代码中发现的问题与修正，不能据此确认实际设备根因或宣称记忆链路已经成功。

- 输入回调改为只读 volatile 配置快照；只在启动与相关设置变更通知中读取远程偏好。
- 移除组合键 preCondition Hook 和共用 MiuiSingleKeyRule.getFunction Hook；组合键注册时
  确认目标实例，执行时仅匹配已注册实例，不在输入判断中反射键码。移除快捷键显式去优化。
- 小爱自定义 onStartCommand 先放行原生启动初始化，再在下一次主线程消息调用记忆。
  反射支持祖先类/非 public 方法，日志仅确认请求已派发，不声称云端记忆完成。
- 之前直接启动 VoiceService 后没有桥接清理。现在私有请求返回 START_NOT_STICKY，
  失败时清理私有启动，成功派发后做一次 20 秒限时清理；后续原生启动撤销停止权限；已有绑定由 Android 保持，清理仅释放私有启动状态，
  stopSelfResult(startId) 也避免停止后来请求。无周期任务、轮询或重试。
- 启用系统框架作用域也会进入 GestureHandleAppTransitionHooks。原实现功能关闭时仍
  安装 getter Hook 并去优化转场消费者；现在关闭时不安装，开启时只在构造/配置变更/
  偏好通知同步真实缓存，移除 getter 热路径同步和消费者去优化。首次开启仍需重启手机。

需真机确认：当前超级小爱版本是否仍支持上述原生接口，VoiceService 对私有 action 的
原生启动处理，记忆调用是否异步完成，以及 CPU/耗电是否恢复。静态测试不覆盖这些结果。

系统侧还会在本地配置快照中检查已同步的 LSPosed 作用域是否包含 com.miui.voiceassist。
缺失时放行原动作，不向未注入的小爱进程发送私有服务启动请求，只输出一次提示。
修改作用域后需要打开模块设置重新同步，再重启手机。这也防止桥接 Hook 未安装时，
私有启动请求进入原生服务但无人处理/清理。

本轮最终验证：快捷键配置/策略/默认值、服务启动所有权、手势转场策略与作用域共 12 项
测试通过；Release 构建与 lintVitalRelease 通过，沿用现有 release 密钥的 APK v2 签名验证
通过。交付 app-release-shortcut-fix.apk。没有进行真机小爱功能或功耗验证。
