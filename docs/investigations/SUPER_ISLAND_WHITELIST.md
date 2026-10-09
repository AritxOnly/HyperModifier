# 超级岛白名单开关

默认关闭。设置位于媒体设置的超级岛权限部分，读写/远程 Bundle/全部关闭均包含 super_island_whitelist_disabled。LSPosed 作用域新增 com.xiaomi.xmsf；首次安装需勾选系统界面、小米服务框架并重启目标进程（界面提示重启手机）。

本地 reference/MiuiSystemUINew.apk 核验：NotificationSettingsManager.isInSupportBlockFocusXmsList(String):boolean 决定焦点名单准入；canShowFocusState(Context,String):int / canShowFocusStateApp(Context,String):int 随后读取用户保存值。仅放行前一个名单函数，保留明确关闭的焦点权限，不强制覆盖全部 canShowFocusState 结果。

XMSF AuthSession 的最终实例方法 Bundle(AuthError) 与 Bundle() 使用严格签名发现；调用成功结果构造方法完成焦点认证错误分支。签名缺失/歧义时不安装，使用现有 HookDiagnostics 报告兼容性失败。没有连接真机，当前 XMSF 具体 APK 的运行验证尚未完成。关闭开关/模块总开关时调用原方法；不会修改网络、防火墙或持久系统白名单。

参考 HyperCeiler：
- https://github.com/ReChronoRain/HyperCeiler/blob/main/library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/rules/xmsf/UnlockFoucsAuth.kt
- https://github.com/ReChronoRain/HyperCeiler/blob/main/library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/rules/systemui/statusbar/island/UnlockFocus.kt
依据公开钩子目标和本地系统反编译独立实现，未复制其框架代码。

## 验证
- 独立源码快照 Debug / Release 构建成功，112 项单测全部通过，包含严格认证签名识别、歧义和无关方法拒绝。
- 最终修改源文件与快照一致；Release 签名通过 apksigner 校验，APK CRC 通过；产物 build/super-island-whitelist/app-release.apk。
- 本地 SystemUI smali 核实名单函数签名与后续焦点开关读取；没有连接真机，XMSF 实际版本的注入和超级岛展示尚未验证。
- Deadliner 配套 SYSTEM_HOOK 模式直接提交岛通知，绕过 Root/Shizuku 断网窗口；不要将手动选择这个模式当作模块实际生效的检测结果。
