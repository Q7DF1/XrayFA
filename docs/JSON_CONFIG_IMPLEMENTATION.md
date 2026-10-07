# JSON 配置导入实现与验证

日期：2026-10-07。范围依据 `JSON_CONFIG_IMPORT_DESIGN.md`，Android 与 iOS 功能代码均接入；iOS 构建及设备运行仍需 macOS 验证。

关联需求：#342。

## Android 设备验证补充（2026-10-07）

- 本地 hev 子模块曾停留在 `cf312ec`，与主仓库锁定的 `197f642` 不一致，导致 JNI 注册时找不到 Boolean 返回类型对应的方法。保留原 Kotlin 接口，对齐子模块及嵌套依赖并清理原生构建产物后重新构建。
- 新 Release 构建及 ARM64 APK 签名校验通过；SM-S9110 上 `TProxyJniTest` 原生库加载测试通过。
- 用户确认新包中 direct 配置可以联网、block 配置无法联网，基本 JSON 导入和出口执行链路通过。
- 以下原始验证范围中的未完成项仍应逐项验收；本次结果不代表所有 TUN 模式、UDP、DNS、复杂路由或 iOS 已通过。

## 使用方式

配置页右上角“+” → “导入 JSON 配置文件” → “选择 JSON 文件”。编辑名称和原文，必要时选择 VPN 对应的原始 inbound tag，保存。随后在配置列表中选中该配置，回到首页启动。保存新配置不会自动切换当前节点或操作 VPN。

支持完整 Xray JSON 对象，至少包含一个具有字符串 `protocol` 的 outbound。文件上限 1 MiB，严格 UTF-8 / JSON，支持开头 BOM；拒绝重复键及超过 64 层嵌套。JSONC、单独 outbound、Clash/sing-box 配置和外部资源包不在范围内。

## 实现决定

- 共享 Compose 页面和 Decompose 导航，两端分别使用系统文档选择器。Android 有界流读取，iOS Import 模式协调本地副本并管理 security-scoped URL；读取与解析在后台执行。取消不保存。
- `protocolPrefix=json-config`，原文存入已有 `jsonData` 列，无 schema 迁移。内部标识格式为 `json-config://<随机标识>?inbound=<编码tag>`，不包含原文或代理账号。编辑执行单条 SQL 更新，保留 id、收藏、选择和订阅归属。
- `JsonVpnConfig` 通过 JSON 树产生运行副本。保留用户 outbounds、DNS、balancers、路由规则和未知字段。原始文件不会被重新格式化或运行适配结果覆盖。
- 路由规则使用 inboundTag 时，必须显式指定原始入口。适配后保持该 tag 和 sniffing，替换 listen/port/protocol/settings，并移除不适用于本地通道的 streamSettings/allocate。未选入口继续保留；额外 TUN 或应用 SOCKS 端口冲突会被拒绝。
- Android 原生 TUN 使用 `tun` 入口；hev 模式使用设置中的 SOCKS 端口及认证。iOS 使用固定的私有 `127.0.0.1:10808`、无认证 SOCKS 入口；传递明确的端口，不扫描用户入口猜测通道。
- 两端 JSON 会话的系统 DNS 使用 `198.18.0.1`。在用户规则前增加一条仅匹配应用入口、虚拟 DNS 地址和 53 端口的专用规则，转交新增 DNS outbound。用户规则内容及顺序不变；新增 tag 避开现有名称。缺少 `dns` 时仅在运行副本补 `1.1.1.1` / `8.8.8.8` 作为引导服务器。
- 运行副本在缺少 `stats` 时补 `{}`，满足当前 native bridge 的 stats manager 要求；不会增加 API 监听或改写用户 stats/policy。JSON 会话不使用固定 proxy tag 的速率统计。
- Android Intent 仅传记录 id，服务端读取 JSON；服务启停互斥，重启先准备后停止，失败清理 TUN/core。iOS 单个版本化 App Group envelope 包含运行配置及通道元数据；重启重新准备当前记录，并等待原隧道停止。删除所选 JSON 时清理待启动快照并断开。
- JSON 记录禁用链接分享、各类延迟测试、应用订阅链节点和固定出口速率统计；不显示旧节点的延迟结果。普通节点继续使用原 parser、DNS、路由、订阅链和能力按钮。Agent 选择 JSON 前做兼容检查，测速返回 UNSUPPORTED。
- 应用层启动参数、核心回调及错误提示不输出 JSON 原文。可识别的证书/密钥文件和日志文件路径会被静态拒绝；其他核心语义和未知外部依赖仍由启动结果判断。

## 验证范围

新增测试覆盖运行字段保留、DNS 通道、入口映射、tag/监听端口及范围冲突、重复键/深度/大小限制、TUN 准备、远程传输字段移除；共享保存测试覆盖原文及旧节点不变、编辑保留身份/收藏/选择、无效编辑不写入、普通订阅链与 JSON 独立启动参数；Agent 测试覆盖兼容检查失败不切换记录。

最终验证命令：

```powershell
./gradlew :domain:testDebugUnitTest :shared:testDebugUnitTest :androidApp:testDebugUnitTest :androidApp:assembleDebug :androidApp:assembleRelease
```

结果：BUILD SUCCESSFUL，112 项测试通过、0 失败。Debug 和 Release APK 均已生成，Release 完成 R8 / lint，ARM64 Release APK 通过 apksigner 签名校验。`git diff --check` 通过。

构建仍有项目现有 API 弃用提示及 Compose 动画类的 R8 type-check 警告；构建成功不替代设备启动和界面验证。

以下尚未完成，不能视为通过：

- Android 真机导入界面、升级安装、原生 TUN / hev 的 TCP、UDP、DNS 实际流量和连接切换。ADB 曾短暂识别设备，后续检查已无在线设备，未安装或替换用户应用。
- iOS 共享 framework、应用、Network Extension 的 Xcode 构建，以及文件选择、权限、实际 VPN 流量和重启验收。当前为 Windows 环境，没有 macOS/Xcode。
- 任意用户配置的全部核心语义预检。现有 native API 没有独立完整配置校验接口，静态检查不能替代核心启动及网络实测。
