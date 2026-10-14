# 1.5.1 工作区静态性能审查

日期：2026-10-14。基线为 `f829eb3`，审查包括修改文件和非忽略未跟踪文件，
见 [范围清单](RELEASE_1.5.1_WORKSPACE_INVENTORY.md)。文档/测试/工具按运行时影响归类，
源码按启动、回调、设置刷新、缓存生命周期和后台任务路径审查；这不是逐行形式验证。
本次没有连接测试设备、采集 Perfetto/FrameTimeline 或进行功耗/微基准对照。

## 范围和结论

| 变更组 | 静态审查结果与保留边界 |
| --- | --- |
| MyHyperModifier、作用域、Manifest、原生入口 | 新增 system_server/XiaoAi 和小组件提供方入口；专用提供方不再安装通用设置加载 Hook/桌面桥接，Glass-only 的早期入口仍只安装半径 Hook。system_server Hook 对原动作保留退出路径。版本调整为 1.5.1/39。 |
| 全局 Glass、超级岛、清除按钮、通用卡片 | 复用原生采样/shader；独立配置和弱目标；相关值去重、合并任务、状态身份取消旧回放。已有记录详见 GLOBAL_GLASS_PERFORMANCE.md。 |
| 背景压暗、采样缩放、窗口压暗、MiLink | 精确背景归属，保持原动画比例和恢复；缩放重复 setter 可跳过。压暗启用仍涉及每次颜色数组及最终写入补偿，是保留的渲染风险，需要 FrameTimeline 验证。 |
| 桌面状态栏、手势条转场 | 状态栏基于已有 owner 决策回调、去重设置与合并刷新。转场更新 DisplayPolicy 缓存，只在配置/相关设置事件执行，不 Hook getter 或做每帧 IPC。 |
| 原生桌面时钟、小组件、ABI/ZIP 指纹 | 时钟格式路径复用原有缓冲；原生安装 worker 最多 120×0.5s，成功/拒绝后结束；文件指纹校验仅安装阶段，Dart 镜像约 29MB 一次读取属于启动成本，不能称零开销。能力 Hook 无关键值直接退出；配置存盘移出共享能力锁。 |
| Java RemoteViews、小组件规则/清单 | 缓存稳定字段及解析规则；默认全选无 OFF 例外时不逐实例 getAppWidgetInfo。精细规则仍需元数据 IPC，背景适配仍会 clone/解析布局 XML；这些是 provider 更新事件开销，不是新增绘制回调。清单仅用户请求时后台 dump，BUSY 防重入和 1MiB 读取上限。 |
| 桌面设置桥接 | 原实现启动后永久每分钟广播，现移除。保留首次约 22s 的有限重试，其后仅相关设置/用户解锁；桌面可读取持久化缓存。新增解锁事件是已有系统广播，不使用 alarm/轮询。解锁时载荷编码仍有一次事件成本。 |
| 快捷键、小爱 Service 生命周期 | 输入路径读本地配置快照，沿用系统检测/去抖，不新建检测线程或 key 轮询。原生服务初始化保留，每次私有启动最多一个 20s 清理，后续原生启动撤销 stop 权限；没有常驻 keep-alive。动作执行中的反射/服务 IPC 是按次成本。 |
| 设置主页入口及新图标缓存 | 见下一节。头部列表构造仍需校验启用模块的可用 Activity/权限，属于列表创建成本，未改成异步以保持原列表契约。 |
| 配置 Store/SyncQueue/BackupCodec | 普通保存异步串行同步并丢弃过时排队写入；安全门/预设保留同步持久化及 barrier，可能阻塞当前 UI，这是正确性边界，未删除。备份/恢复/包组件操作在 IO dispatcher，限制备份大小，不增加后台定时工作。 |
| UI、滑块、模块/小组件页面 | 使用已有宿主与控件，不增加进程外模糊层；拖动值局部更新，松手/150ms 无变化/生命周期结束后去重提交。150ms 是用户交互触发的取消重排任务，不是常驻轮询。包/小组件清单 IO 在后台且超时。 |
| 测试、文档、分析工具与参考校验 | 不进入 APK 的后台运行路径。历史 Bilibili 测试断言与 HEAD 默认值 true 冲突；修正测试为核对权威默认值，保留系统预设关闭和作用域断言，没有修改产品默认行为。 |

## 设置主页冷滑动

原 setIcon Hook 在第一次模块行绑定时同步执行 createPackageContext/getApplicationIcon，
Resources.getIdentifier/getDrawable，以及矢量首次准备；旧缓存只解决第二次绑定。
原 boundedIcon.draw 每次创建 Path/RectF，进入滚动绘制路径。

修复后列表创建完成最多预载 16 个入口，单 worker 加载及软件栅格化为实际 28dp 图标，
最多缓存 32 份 Bitmap ConstantState。worker 空闲 5s 后退出，无定时唤醒或保活。
主线程绑定只读缓存、新建轻量 BitmapDrawable；无每次 draw 分配或跨包资源加载。
未准备好时保留原生 fallback，并在后台完成后更新仍绑定同一入口的弱 ImageView。
普通行没有待完成图标时直接退出，不追加 ImageView 字段读取。

列表重建/Configuration 改变递增代次并清空排队任务；老 worker 的完成结果不会回写。
行复用会撤销等待绑定；完成/失败后清除等待记录；失败保持 fallback 直至下次列表重建。
ConstantState 只缓存 bitmap 元数据，等待 View 是弱键，worker 使用 Application Context。
没有缓存 Activity 或某一行的可变 Drawable。

此修改明确移除了滚动帧的 IPC/资源解码/裁剪对象分配，但增加了有上限的后台预载工作和
小 bitmap 缓存/纹理首次上传。不能据此保证总 CPU/GPU 或首次卡顿幅度；需真实冷启动轨迹确认。

## 已保留的风险

- GPU：更大模糊半径、系统 Glass pipeline 及压暗开启后的实际 GPU 时间/混合/耗电需实测。
- 通用卡片非精确路径仍有调用栈及祖先检查，不能通过直接跳过分类破坏边界和原值恢复。
- RemoteViews 精细实例选择/布局适配、Settings 头部创建仍有事件级 IPC/反射/解析；未静态证明没有长尾。
- 本次移除周期桥接后，桌面原生持久化缓存和解锁刷新需要真机验证：桌面重启、冷开机、设置改变、模块关闭。
- Native ABI 与文件指纹仅覆盖已验证 Runtime 桌面镜像；拒绝未知版本优先于强行覆盖。
- SettingsIconCache 的线程切换、配置/包更新、行复用正确性已做结构审查，尚无真机 UI/性能验证。

因此本轮交付为可安装的 Release 候选包，不是“功耗不增加”的测量认证。

## 最终验证结果

- 工作区范围清单：106 个变更文件（包括本轮文档）。
- 全量 JVM 单元测试：280 项，0 失败/错误；Release 构建成功。
- ASan/UBSan 原生 widget policy、真实 Launcher APK/存储 ZIP/损坏和压缩拒绝测试通过；picker ABI 96 项通过；时钟格式测试通过。
- APK v2 签名验证通过；实际清单版本 1.5.1 / versionCode 39。
- 反编译核对实际 R8 Java 入口存在、直接继承 XposedModule 且保留 public 无参构造；native_init 指向的 arm64 so 已打包。
- 旧 tools/verify-hmc-release.py 强制 com.os4.musiccover.Main 为入口，与当前项目 MyHyperModifier 入口及 R8 配置不相符，未将其失败计为通过项；以上实际入口校验替代该过时检查。本轮没有修改其脚本。
- git diff --check 通过。未安装本轮候选包，未进行冷滑动、功耗或渲染真机回归。

候选 APK SHA256：`739a298db13cd8fab225b89a28754c97d4869c68c9b8a9124b8903ed96ad98c0`。

## 1.5.1 设置展示收尾

移除 Deadliner 的推荐 scope、声明常量和状态卡片，以及冗余 package query；
不迁移旧小组件规则键，保留配置兼容。既有 LSPosed 用户勾选列表由框架管理，
修改推荐声明不会自动改写已安装设备的手动选择。
系统界面标签下所有入口使用本地 Material 矢量；系统桌面使用 home，不再读取应用图标，
快捷键改为 mobile_arrow_up_right。来源为 Google Material Symbols Outlined（Apache-2.0）；
Android VectorDrawable 保留 SVG 的 viewBox 原点变换，两份图标已用 rsvg-convert 渲染检查。

精简新增设置描述，保留作用域/重启/实例优先级/移出堆叠等操作说明。
合并超级岛背景、权限/交互分组及桌面单项卡片，删除快捷键“使用说明”板块。
操作的 setting key、更新回调和两类背景的独立性保留；补齐作用域状态的背景开关判定。
没有新增渲染层或后台工作，减少一处应用图标读取及多余 Compose Text/Card 节点。
图标渲染与编译验证属于静态/本地检查，整页布局未真机截图验证。

设置展示收尾后重新验证：280 项全量测试通过，Release 构建与 APK v2 签名通过，打包作用域不包含 Deadliner。

按用户指定将快捷键更换为 Mobile Arrow Up Right、系统桌面更换为 Home。两份图标渲染检查及 Release 构建/签名验证通过。

### HyperMusicCover 二级页与包名检测

按用户最新要求，只在进入/恢复页面时于 IO 线程查包名，并提供持久化“强制启用”手动解锁设置。已移除 UI 注入检测带来的广播、接收器、延迟任务和最多 8 次调用栈采样，关闭功能时恢复原有廉价退出路径；保留功能自身的运行类绑定和扩展启停。禁用文字使用颜色透明度，无整组离屏合成层。静态检查没有新增系统渲染或常驻任务，无真机功耗对照数据。

包名检测及强制启用最终验证：280 项 JVM 测试通过（0 失败/错误），Release 构建成功，APK v2 签名验证通过。包名检测与灰色 UI 未做真机测量。
