# 关于手机产品素材与在线预设

12 款手机的透明 PNG 保存在 `phone-presets/images/`，位于 app/src 之外，不打包进 APK。GitHub 独立分支 `codex/phone-image-presets` 托管 `phone-presets/manifest.json` 和图片，默认 raw URL 见 PhonePresetRepository.DEFAULT_URL。`sources.json` 记录原始来源和 SHA-256，`edit-prompts.json` 记录内置 image_gen 抠图提示词。

三级页路径：系统设置 → 手机型号图片。支持自动匹配、选择在线预设、上传图片和配置 GitHub raw 清单地址。打开模块时自动准备当前机型图片；三级页可手动刷新清单。网络失败保留缓存，未缓存机型显示通用图。系统设置进程只读取共享配置中的缩略图，不联网。

清单 schemaVersion 为 1，每项包含 id、name、相对图片路径 file 和 sha256。下载限制为 8 MB，校验 SHA-256 后缓存原图。导入/传输图片解码不超过 512 像素，保留 alpha，缩略图限制为 96 KiB。共享缩略图缓存与用户设置独立存储，其他设置保存不会清除它。

PhoneImageView 为三级页与系统设置共用的绘制组件：按透明轮廓去除外围留白、保持图片比例、应用 X/Y（64dp 坐标空间，单位 dp）与 scale，最后裁切在圆形容器内。默认参数来自用户导出的 about-phone-image-layout.json，X/Y 四舍五入至整数 dp，scale 保留两位小数；记录见 default-layout.json。用户单张图片的调整优先于这些默认值。

调参后保存，再使用右上角重启系统设置。JSON 可保存或复制，包含默认参数、用户调整与当前草稿，并记录 contain 适配与 circle 裁切，不包含上传的图片字节。

按用户要求，此次在线预设/默认值/圆形裁切修改仅编译 release，不进行模拟器测试。此前三级页与预设预览已通过模拟器检查，旧版单元测试通过；不将其作为此次修改的实机验证。

## 离线预设包

`phone-presets.zip` 根目录为 `manifest.json` 与 `images/`，清单与在线预设使用相同格式。三级页的“一键导入预设包”通过系统文件选择器读取 ZIP，整包验证后将原图写入持久缓存，最后原子更新当前源的清单；不需要访问 GitHub。列表完全来自缓存清单，新增或删除机型无需更新 APK。包可通过任意文件渠道分发，也可使用本页“下载全部预设到缓存”提前离线准备。当前选中图片的缩略图仍通过共享配置传给系统设置。

ZIP 限制 128 MiB，清单限制 256 KiB，单张图片限制 8 MiB，最多 100 个预设。导入检查重复文件、无效路径、SHA-256 和图片解码；不直接解压 ZIP 路径。失败不会发布新清单，原有缓存和列表继续可用。包覆盖当前预设源的本地清单；“刷新在线预设”可恢复该源最新在线清单。

小米 17 已替换为用户提供的视频截图中的完整浅蓝色背面，使用 image_gen 去除背景、播放控件与底部暗影，透明裁切图保存在 `phone-presets/images/xiaomi-17.png`。
