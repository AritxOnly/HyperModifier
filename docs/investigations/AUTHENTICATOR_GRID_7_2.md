# Google Authenticator 7.2 多列布局

## APK 基线与结论

- 包名：`com.google.android.apps.authenticator2`
- 版本：7.2，versionCode 7002015
- SHA-256：`30a4ccc92f3859fdce1e77759a6b9a5aeef3b4c73e4744df9e7b05ed2d23b076`
- 本地输入：用户提供的 `身份验证器_7.2.APK`；解包结果留在临时目录，不提交 APK 或反汇编。

主页为 XML + `android.support.v7.widget.RecyclerView`，不是 Compose。
`main_fragment.xml` 中主页 `otp_recycler_view`（0x7f0a026d）和搜索结果
`filtered_otp_recycler_view`（0x7f0a0113）都在 `but.ad(View, Bundle)` 中
构造竖向 LinearLayoutManager，再调用 `RecyclerView.Y(kz)` 安装。

因此 DPI 只会改变可用宽度，不会让原版管理器自动变成多列。

## 专用实现

设置入口：折叠屏设置 → Google Authenticator → 验证码多列布局。默认关闭。
勾选 LSPosed 的 Google Authenticator 作用域并重启该应用后，开关和模块总开关实时生效。
版本检查不通过时不绑定列表，不修改应用界面。仅支持上述 versionCode。

- 可用列表宽度 <600dp：原版单列；600–839dp：双列；≥840dp：三列。
- 使用实际列表宽度扣除 padding，适应窄窗口/分屏；不硬编码物理分辨率或屏幕方向。
  这是宽度适配规则，不读取折叠铰链状态；外屏若人为改到 ≥600dp 也会进入多列。
- 保留应用原 Adapter、账户存储、验证码生成、倒计时、复制和侧滑逻辑。
- 目标类已混淆，不能直接调用示例中的标准 AndroidX 构造函数。
  本包 GridLayoutManager 仅保留 `(Context, AttributeSet, int, int)` 构造函数；
  在全新的、尚未附着且 span 缓存为空的管理器上设置混淆字段 `b` 后安装。
- 缓存原管理器；切换列数时保留首个可见条目的位置，回到单列时装回原对象。
  不承诺精确保留滚动像素偏移。
- `OtpView` 是 RelativeLayout：验证码默认 38dp，左右边距默认 24dp。
  网格模式把验证码字号上限设为 28dp、起始边距缩到 12dp，末端保留 52dp 给倒计时/HOTP 控件；
  不读取、拼接或缓存验证码及账户文本。单列恢复原字号和布局参数。
- 原 `jk` 分隔线会对每个条目横跨整个列表绘制，网格时移除这一对象，单列恢复原顺序。
  不移除 ItemTouchHelper 或其他模块添加的 decoration。
- `bvq.f()` 的原拖动方向为 START/END（0x30），网格补充 UP/DOWN（0x03）。
  原逻辑禁止拖动（返回 0）时仍然禁止；侧滑方向保持原逻辑。
- 检测到其他模块替换管理器后让出控制权；仅恢复仍属于本功能的卡片参数及字号。
  管理器安装异常时尝试回滚原对象，当前列表停止继续适配，避免反复异常。

## 性能与生命周期审查

Authenticator 主进程在 PackageReady 独立安装三个精确方法 Hook：列表初始化、
OtpView 附着和原拖动方向回调。子进程和共享库回调直接退出，
不走 ModuleSettings 全量解析、通用 View/玻璃 Hook、桥接服务或广播刷新。
只监听 `authenticator_grid_enabled` 和 `module_hooks_enabled`，相关更新合并到主线程执行。

稳定布局回调只计算当前宽度对应列数，不查找反射成员、不遍历列表、不分配状态对象。
卡片初始化发生在附着时；反射成员在安装时缓存。仅跨越列数阈值时重建管理器。
未启用时卡片附着 Hook 在原方法执行后直接退出。
拖动回调仅使用已缓存的 Field/Method，不采集堆栈或读取设置。

静态列表索引同时使用弱键和弱状态引用，View 的 layout listener 自身持有状态；
避免静态 Map → LayoutManager → RecyclerView 的泄漏路径。
不增加模糊、截图、轮询、常驻定时任务、GPU 图层或验证码刷新任务。
版本验证在首次目标页面绑定时读取一次 PackageInfo，有一次初始包管理器查询，
不会在绘制/触摸/动画回调中重复 IPC。

以上是代码审查与结构性开销约束，没有真机前后对照数据，不代表帧率、卡顿或耗电改善。

## 验证与真机待验

本地执行：

```sh
apktool d 身份验证器_7.2.APK -o /private/tmp/mhm-authenticator-7.2
python3 tools/analysis/verify-authenticator-grid.py /private/tmp/mhm-authenticator-7.2
./gradlew :app:testDebugUnitTest --tests '*AuthenticatorGrid*' \
  --tests '*QQRemoteBootstrapTest' --tests '*NotificationWidthSettingsTest' :app:assembleDebug --offline
```

验证脚本已通过 22 项资源/Hook 合约检查。上述筛选的 9 项单元测试全部通过（含相邻 QQ 和通知设置回归）。
验证脚本核对资源 ID、继承的 Fragment 根视图访问器、方法和字段签名。
Robolectric 测试覆盖宽度边界、异常密度、禁用恢复、首个可见位置、稳定列数不重建、
分隔线与其他 decoration 的恢复顺序、其他模块替换管理器、安装异常回滚、
卡片原值恢复、设置持久化/远程 Bundle/备份恢复及默认关闭。
这类测试不执行实际 APK 的 RecyclerView 实现，不能代替真机 LSPosed 验证。

当前没有 ADB 设备连接，尚未验证目标应用截图、折叠/旋转、搜索、滚动、拖动、
侧滑、复制、TalkBack、长账户名称及八位/HOTP 验证码的真机表现。
尤其需确认应用的 resizeableActivity=false 在设备上的实际窗口宽度：
如果系统仍为其提供窄兼容窗口，模块按宽度保持单列；需让应用获得宽窗口后再检验网格。
