# LSPosed 直接启动方案

目标：设置主页的 LSPosed 入口直接打开管理器，不要求常驻通知，也不要求桌面快捷方式。

官方入口：LSPosedService.registerSecretCodeReceiver 注册
android.telephony.action.SECRET_CODE，URI android_secret_code://5776733。
该接收器需要发送方持有 android.permission.CONTROL_INCALL_EXPERIENCE，
收到后调用 LSPManagerService.openManager(null)。通知开关与此接收器无关。

源码：https://github.com/LSPosed/LSPosed/blob/master/daemon/src/main/java/org/lsposed/lspd/service/LSPosedService.java

实现：

1. 独立安装的管理器仍通过常规 Intent 启动。
2. 寄生管理器由系统设置进入 HyperModifier 自身的专用启动 Activity。
3. 启动 Activity 仅允许持有 WRITE_SECURE_SETTINGS 的调用方进入。
4. 在 HyperModifier 自身进程后台通过通用 su 请求 Root（Magisk、KernelSU、APatch），发送以下固定命令：

```sh
/system/bin/am broadcast --user 0 -a android.telephony.action.SECRET_CODE -d android_secret_code://5776733
```

5. 命令不接受调用方输入，不能替换命令、目标、URI 或用户编号。
6. Root 请求超时、拒绝或 su 不可用时显示失败提示，不启动 Shell Activity，不提示开启常驻通知。
7. 移除上一版读取常驻通知的逻辑。

Root 授权会赋予 HyperModifier 高权限，本入口的实现只发送上述固定请求。
发送成功仅表示请求提交成功，不等于管理器已经显示；需真机确认。

状态：用户已明确允许试用 Root 启动方案，并要求兼容非 KSU 用户；实现采用通用 su 接口，不依赖特定 Root 管理器。未连接手机，尚未实机验证。
