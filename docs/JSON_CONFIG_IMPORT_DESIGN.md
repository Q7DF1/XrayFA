# JSON 配置文件导入与启动：功能范围和修改方案

日期：2026-10-07

状态：已获用户确认并进入实施。具体实现及验证结果见 `JSON_CONFIG_IMPLEMENTATION.md`。

## 1. 目标和范围

用户可直接选择一个本地 Xray JSON 配置文件，将其保存为“JSON 配置”，随后在现有配置列表中选中并从首页启动 VPN，不需要转换成分享链接或手动填写协议。

本文按“完整 Xray 配置对象”设计：保留多个 outbound、用户路由、DNS、传输参数及应用模型尚不认识的字段。首版不把文件拆成普通节点，也不把单个 outbound 对象、Clash/sing-box JSON、订阅 JSON 当作完整 Xray 配置导入。

Android 和 iOS 均为本次必须实现的平台，不分成“Android 首版、iOS 后续”。共享 UI、数据与配置处理采用 KMP 实现，两端分别接入系统文件选择器和平台 VPN 启动流程。Android 为参考实现，iOS 提供相同的导入、编辑保存、选择、启动、停止和错误反馈能力。

当前没有 iOS 调试条件只影响验证，不缩减实现范围。不能以占位页面、禁用入口或未实现的 actual 代替 iOS 功能。两端实现完成后，iOS 编译和实际运行验证若无法执行，必须单独标记为未验证；不能用 Android 测试代替，也不能宣称两端验收全部通过。

## 2. 代码现状和必须修改的原因

| 当前实现 | 发现 | 对新功能的影响 |
| --- | --- | --- |
| `shared/.../config/ConfigLinkImporter.kt` | 按空白、逗号拆分剪贴板文本，仅接受协议链接 | 不能把完整 JSON 接到这个入口，必须独立导入 |
| `domain/.../model/Node.kt`、`core/database/.../entity/NodeEntity.kt` | 已有可空的 `jsonData` 字段 | 可以存原始 JSON，初步不需要数据库结构迁移 |
| `domain/.../parser/ParserFactory.kt` | 根据 `url` 的协议前缀选择解析器 | JSON 记录不能继续调用链接解析器 |
| `domain/.../parser/AbstractConfigParser.kt` | 从链接重新生成 inbounds、outbounds、DNS、routing 等完整配置 | 用它处理用户 JSON 会改变用户路由或丢失自定义字段 |
| `shared/.../vpn/VpnStartOptionsResolver.kt` | 从所选节点及订阅链得到 URL | 需能区分链接配置和 JSON 配置 |
| `androidApp/.../core/XrayBaseServiceManager.kt`、`StartOptions.kt` | Android 另有一条从所选节点构造启动参数的路径 | 只改共享 Resolver 不够；首页、快捷开关、重启都要覆盖 |
| `androidApp/.../core/XrayCoreManager.kt` | 启动时固定执行链接解析 | 必须添加显式 JSON 分支 |
| `shared/.../vpn/IosVpnConnectCoordinator.kt` | iOS 也先执行链接解析，然后传给隧道 | 必须采用同样的配置类型分支 |
| `platform/vpn/.../IosVpnController.kt` | `restartIfNeeded()` 仅断开后连接，没有重新准备当前选择的配置 | JSON 与普通节点之间切换、编辑后重启都需重新准备，不能复用旧 pending JSON |
| `shared/.../vpn/createDelayProbe.kt` 与 Android 测速入口 | 未连接测速依赖节点 URL | JSON 配置需要能力判断，避免显示假“超时”或抛解析异常 |
| `shared/.../ui/config/SharedConfigNodeRow.kt`、`ui/home/HomeSelectedNodeCard.kt` | 默认按单协议、单地址展示 | JSON 配置需要单独类型标签，不伪造服务器地址 |

上表路径中的 `...` 为包目录省略；第 6 节给出模块和具体文件名。

## 3. 用户流程

1. 配置页“+”菜单新增“导入 JSON 配置文件”。已有剪贴板导入、扫码、订阅、手动添加的入口和行为保持。
2. 进入共享 JSON 编辑／确认页，通过“选择 JSON 文件”打开系统文件选择器，不要求全盘存储权限。取消选择不更改列表、当前选择或连接。
3. 文件读取后显示原始 JSON、配置名称和入口映射选项；保存时完成检查。名称默认使用文件名，可修改。
4. 检查通过后保存为一个独立配置项，归入“手动”分类，类型显示“JSON 配置”。导入本身不自动切换节点、不自动断开或连接。
5. 用户选中该配置后，沿用首页启动按钮。JSON 配置走独立准备分支，普通节点仍走原链接解析路径。
6. JSON 配置编辑采用名称和原始 JSON 文本编辑／重新选择文件，不打开 VLESS、VMess 等协议表单。校验成功才覆盖保存。

连接中选中 JSON 配置仍遵循现有“切换配置后重启”的行为，但必须先完成兼容检查；检查失败保持旧选择和旧连接。编辑当前运行的 JSON 配置时，先检查，再原子保存并重新准备／重启；编辑未运行配置不触发重启。停止按钮必须不依赖 JSON 校验，即使文件失效或当前记录被删除，仍能停止正在运行的 VPN。

首版只要求文件导入；从剪贴板粘贴 JSON、系统“用 XrayFA 打开”、远程 JSON 地址和自动更新不在本次范围内。原剪贴板导入继续只处理链接。

## 4. 最重要的行为边界：移动 VPN 适配

“直接提供 JSON”不能等同于任意桌面配置逐字传给手机 VPN。现有 Android 同时支持原生 TUN 和 hev-socks5-tunnel；iOS 通过本地 SOCKS 接入 hev-socks5-tunnel。没有相应入口，即使核心启动也不代表 VPN 流量可用。

建议采用“保存原文，运行时做最小移动端适配”的方式：

| 配置内容 | JSON 配置的处理原则 |
| --- | --- |
| 原始文件 | 原文保存在 `jsonData` 中；运行时适配生成另一份配置，不回写原文 |
| outbounds、balancers、用户 routing、DNS、streamSettings、自定义字段 | 按 JSON 树保留，不经过现有强类型 Xray 模型反序列化再编码；不抽取一个 outbound 替代整个文件 |
| VPN 必需入口 | 根据 Android 当前 TUN 模式或 iOS SOCKS 通道补齐应用入口；相应监听地址、端口、UDP 和认证参数必须与平台通道一致。原生 TUN 模式不要求复用 SOCKS；hev/iOS 模式必须显式确定 SOCKS 入口 |
| 已有 inbounds | 保留与应用通道无关且不冲突的入口，不能因它们不是 SOCKS/TUN 就拒绝整个文件。应用入口可兼容则明确复用；占用所需监听资源而不兼容时报错，不静默覆盖 |
| routing 对 inboundTag 的限制 | 规则含入口限制时，导入确认页允许用户显式指定 VPN 流量对应的入口 tag；运行时只给已选入口适配传输所需字段，规则原文保持。不能无歧义确定入口且用户未指定时，阻止激活并说明原因，不能仅提示后继续接入默认出口 |
| tag、端口、API 冲突 | 按核心实际命名空间和监听地址／协议检查冲突，不简单禁止 inbound 与 outbound 使用相同 tag。新增 tag 需避开已有引用。首版统计不可用时不为 JSON 自动注入 API 或占用 10085；不把用户的同名 outbound 改成应用默认 outbound |
| 系统 DNS 与 JSON DNS 的衔接 | 系统 VPN 的 DNS 地址和 JSON 内 `dns` 是两个层次。iOS 当前固定使用 `198.18.0.1`，必须设计此虚拟 DNS 流量到用户 DNS 的衔接；Android 也需检查系统下发 DNS 与 JSON 路由的关系。不得覆盖用户 `dns` 或静默套用应用 DNS。无法保证衔接的配置明确阻止激活，不宣称“DNS 原样可用” |
| native bridge 必需字段 | 当前 Go `doStartLoop` 直接断言取得 stats manager。实施时必须验证不含 `stats` 的 JSON 能否安全启动；若需补 `stats: {}` 才兼容，则仅在运行副本中补齐并记录，避免原生 panic。不据此打开速率统计，也不覆盖用户已有 stats/policy |
| 应用路由模式、应用 DNS 配置、前置／后置节点 | 不注入用户 JSON。普通链接配置仍按当前设置生成 |
| 系统 VPN 授权、按应用分流、VPN 地址、MTU、平台连接生命周期 | 继续由现有平台 VPN 流程管理 |
| LAN 代理开关 | 首版不向 JSON 配置自动添加 HTTP/LAN 入口；用户文件显式定义的监听需在导入页提示。普通节点保持当前行为 |
| Geo 文件及证书等外部资源 | 只支持应用能访问的资源。依赖桌面绝对路径、未导入的相对文件、额外配置片段时，给出明确错误；首版不做资源包导入 |

确认页应简短说明：“保留文件中的代理、路由和 DNS；启动时适配手机 VPN 通道。”需要入口映射时明确显示用户选择，兼容冲突显示具体原因。所谓最小适配包括入口、必要的 DNS 通道衔接及 native bridge 所需运行字段，不能承诺只添加一个 inbound 就能运行任意完整配置。

DNS 衔接与入口 tag 映射属于实现前必须完成的适配规则，不允许留给运行时临时猜测。无法兼容的配置应明确列入首版限制；不能以“支持完整 Xray JSON”承诺支持所有桌面运行环境。

JSON 格式检查不等于 Xray 核心语义检查。首版需分开报告：文件/结构有效、移动通道兼容、核心启动结果。已检查现有 `XrayBridge` 和 Go 导出接口，没有独立的完整配置校验接口；不能承诺仅凭 JSON 解析就知道所有协议参数有效，也不能为校验而停止当前核心。本次不改 Go 子模块增加校验接口，剩余核心语义错误由启动结果明确反馈，并对错误信息脱敏。

## 5. 对现有能力的处理

| 功能 | 普通链接节点 | JSON 配置首版建议 |
| --- | --- | --- |
| 首页启动、停止、重启；快捷开关 | 保持原路径和行为 | 选中后使用独立 JSON 配置准备分支，复用 VPN 生命周期 |
| 列表选择、收藏、搜索、删除 | 保持 | 支持，以名称和 JSON 类型展示，地址／国家信息不伪造 |
| 手动协议添加和编辑 | 保持 | 使用专门 JSON 编辑界面，不能进入协议表单 |
| 链接剪贴板导入、扫码、订阅更新 | 保持 | 不参与；订阅刷新不能删除或重写手动 JSON 记录 |
| 前置／后置链式代理 | 保持 | 不允许把完整 JSON 配置当作单个链节点；用户在 JSON 内自行配置链 |
| 未连接的单节点测速、批量测速 | 保持 | 首版跳过 JSON 记录并标示“不支持”，不记为超时，不启动第二个完整配置争用监听端口 |
| 首页连接后的延迟测试 | 保持 | JSON 首版两端均禁用该按钮；完整配置的实时探测留待后续独立验证，不能回退到链接解析或显示虚假超时 |
| 速率统计 | 保持 | Android 和 iOS 当前统计都固定依赖 `proxy` outbound tag，JSON 可含任意 tag。首版明确显示不可用，不能把始终为 0 当正常结果；完整多出口统计作为后续功能 |
| 链接分享／二维码 | 保持 | 禁用链接二维码；如需要导出原 JSON 文件，作为单独出口设计，不生成假协议链接 |
| Agent/AppFunctions 列表、选择和启动 | 保持 | 摘要能识别 JSON 类型；不暴露原始 JSON。测速接口返回明确的不支持状态 |

这部分限制只针对新增 JSON 配置，不能在全局关闭测速、统计或现有节点动作。

## 6. 需要修改的模块和文件

以下为实施范围，新增类名可随最终实现调整。

| 范围 | 现有文件／新增内容 | 修改目的 |
| --- | --- | --- |
| 共享导入 UI | `shared/.../ui/config/SharedConfigImportMenu.kt`、`ConfigTabScreen.kt`、`RootContent.kt`；新增 JSON 导入／编辑页 | 新入口、文件结果、确认、错误与编辑 |
| 页面状态和导航 | `shared/.../navigation/ConfigTabComponent.kt`、`ConfigState.kt`、`DefaultConfigComponent.kt`；需要独立页面时扩展 `RootStackConfig.kt`、`RootComponent.kt`、`DefaultRootComponent.kt` | 导入状态、保存、取消、编辑路由 |
| 文件读取 | 新增共享文件读取契约；通过平台实现或复用项目适合的文件选择设施；必要时扩展 `ui/platform/PlatformRootHooks.kt` 和 Android/iOS hooks | Android 系统文档选择器、iOS 文档选择器；取消与读失败处理 |
| JSON 导入服务 | `shared/.../config/` 新增 JSON importer/editor；保持 `ConfigLinkImporter.kt` 原行为 | 有界读取、UTF-8、可处理 BOM、严格 JSON 对象、保存原文 |
| 数据类型 | `domain/.../model/Node.kt` 增加统一 JSON 类型判定和能力判断；复用 `protocolPrefix` 类型标记及 `jsonData` | 类型必须显式，不能仅判断 `jsonData != null`，也不能把原文塞进 `url` |
| 持久化 | `domain/.../repository/NodeRepository.kt`、`core/data/.../repository/RoomNodeRepository.kt`、数据库 DAO；必要时 `EntityMappers.kt` | 原子更新 JSON 与名称，保留 id、选择和收藏。当前 `updateNode` 接口不能更新 `jsonData`，需要增量扩展 |
| 运行时 JSON 准备 | `domain/.../config/` 新增 JSON 树校验和移动通道适配 | 保留未知字段；检查端口/tag/入口兼容性，输出运行配置 |
| 通用启动参数 | `common/.../core/CoreStartOptions.kt`、`shared/.../vpn/VpnStartOptionsResolver.kt` | 显式区分链接／JSON 来源，保持原调用默认兼容 |
| Android 启动 | `androidApp/.../core/StartOptions.kt`、`XrayBaseServiceManager.kt`、`XrayCoreManager.kt`；必要时 `XrayBaseService.kt` | 让首页、快捷开关、连接中切换和重启都能读取 JSON；按 TUN 模式适配 |
| Android 参数传输 | 同上 | Intent/Parcelable 传记录 ID 等小参数，服务端读取 JSON，不把大文件塞入 Binder。启动参数日志不得记录 JSON 原文 |
| iOS 启动 | `shared/.../vpn/IosVpnConnectCoordinator.kt`；审核 `iosApp/PacketTunnel/Tun2SocksConfigBuilder.swift`、`PacketTunnelProvider.swift` 与 App Group IPC | 选择正确的应用 SOCKS 入口，避免取到用户的第一个 SOCKS；同步监听／认证参数，不改变普通节点的隧道行为 |
| iOS 重启与配置传输 | `platform/vpn/.../IosVpnController.kt`、`IosAppGroupStorage.kt` 及连接协调层；Swift 对应 App Group 读取 | 重新解析当前配置后再重启；运行 JSON 与入口端口／认证／类型等元数据以同一版本快照传递，不能拼接不同版本；普通节点保持原数据格式的兼容读取 |
| TUN/SOCKS 参数与 DNS | `tun2socks/.../utils/Tun2SocksConfigUtil.kt`、`androidApp/.../core/XrayBaseService.kt`，iOS 同上 | 审核并按 JSON 类型接入端口、认证、UDP、MTU 与系统 DNS 衔接，普通节点通道参数不变 |
| 列表和首页展示 | `SharedConfigNodeRow.kt`、`HomeSelectedNodeCard.kt`、配置动作绑定和分享 hooks | JSON 标签、独立编辑、能力按钮可用性 |
| 测速和 Agent | `DefaultConfigComponent.kt`、`DefaultHomeComponent.kt`、`createDelayProbe.kt`；`AndroidAgentDelayProbe.kt`、Agent 摘要与相关接口；审核旧 `XrayViewmodel.kt` 路径 | 所有入口一致跳过不支持操作，不能误调用链接解析器 |
| 流量 UI 状态 | `TrafficStatsSource` 消费处、首页状态及对应 UI | JSON 记录没有可用统计时展示明确状态；普通节点统计保持 |
| 依赖注入和文案 | `shared`／`domain` 对应 DI；`shared/src/commonMain/composeResources/values*` 四种语言 | 注入新增服务，补充入口、类型、校验与能力提示 |
| 测试和文档 | `domain/src/commonTest`、`shared` 对应测试、Android 集成验证；`AGENT.md`，必要时 `docs/IOS_STUBS.md` | 新 JSON 路径与旧链接路径回归，记录平台验证范围 |

数据库方案优先复用已有列，不增加 schema version，不做破坏性迁移。JSON 记录的 `url` 只保留稳定、无敏感内容的内部标识，不能作为分享链接。已核对 `NodeEntity` 和 `NodeDao`：当前只有 id 主键，没有 URL 唯一约束；仍为每条 JSON 配置分配独立标识，避免后续链接判断或去重混淆。若用户指定入口映射，其元数据需持久化在内部标识或现有列的明确编码中，不能混入原始 `jsonData`，不得临时依赖 UI 内存；具体编码在实现前定稿并测试。

## 7. 不影响存量功能的具体约束

- 不修改 `protocolsPrefix` 来把 JSON 混入既有协议解析；不改写 VLESS/VMess/Trojan 等编码／解码行为。
- 启动层只有显式 JSON 类型走新增分支，其余记录继续使用 `ParserFactory` 和 `AbstractConfigParser` 的现有生成方式。
- 不批量转换已有节点，不改已有订阅内容，不改变全局设置值，不改变普通节点的路由／DNS／链式代理。
- 原始 JSON 及准备后的运行 JSON 分离，重启时从保存的原文重新准备，避免多次启动重复添加入口或规则。
- 导入失败、取消、编辑失败均不写入数据库，不改变当前选择，不操作正在运行的 VPN。
- 连接中切换到 JSON 前先做静态兼容检查。静态检查失败不得先断开旧连接；核心启动失败必须清理新 VPN/TUN 资源并反馈错误。旧连接恢复只能在实际恢复成功后显示，不能承诺不中断。
- 收藏、搜索、删除等通用功能继续使用现有记录 ID；JSON 编辑不通过“先删除再新增”丢失关联状态。
- 所有启动入口都覆盖，包括首页、选中节点后重启、系统快捷开关、Agent；不只修一个按钮。
- 选择、编辑、启动等操作串行化；启动时读取一致的记录快照。待启动配置与实际运行配置要区分，不能因为用户点选了 B，就把仍运行 A 的状态或能力展示成 B。
- 删除当前运行 JSON 配置沿用现有停止／重启规则，但不能从 iOS App Group 中重新启动已删除记录的旧 JSON；无可用选择时清理待启动配置并停止连接。
- 不在日志、异常提示、Agent 摘要或崩溃报告中输出含账号信息的 JSON；测试使用明确的虚拟配置。

## 8. 输入校验和错误反馈

建议首版将输入上限设为 1 MiB，以有界流读取执行限制；后续可按真实需求调整。限制在读取阶段生效，不是完整读入内存后才检查。

取消是正常退出，不显示错误。需要区分并提示：文件不可读、超限、编码错误、JSON 语法错误（行列或位置）、根节点不是对象、缺少有效 outbounds、不是支持的配置格式、移动入口冲突、外部资源不可用、核心启动失败。外部资源检查仅覆盖可识别的资源字段；未知字段仍交给核心处理，不承诺静态检查能识别全部文件依赖。

使用严格 JSON；首版不支持注释或 JSONC。未知字段保留交给核心处理，不因应用模型不认识就丢弃。校验报错指出字段路径或原因，不回显密码、密钥或整份原文。

原文保存为 UTF-8 解码后的文本；可去掉开头 BOM 作为解析输入，但不重新格式化已存原文。拒绝重复键，避免 JSON 树转换前后取值不一致。文件读取和解析在后台执行，设置合理嵌套深度限制。Android 使用文档 URI 流；iOS 必须处理 security-scoped URL 的访问生命周期／文件协调，不能假定选择结果是可长期访问的普通路径。保存后运行依赖数据库副本，不依赖源文件权限持续有效。

## 9. 验证清单与完成标准

### 新功能

- 合法单出口、多出口、路由／DNS／balancer、自定义字段配置导入后原文一致，运行配置保留用户语义。
- 取消、坏 JSON、非对象、超限、BOM、不可读文件、错误配置格式与端口/tag 冲突的处理。
- 重复启动不累积适配字段；配置更新保留 id、收藏、选择；关闭重开应用仍能读取。
- 入口 tag 映射持久化；含 inboundTag 限制的多出口规则按用户所选入口生效；无法确定映射时不改变旧连接。
- 系统 DNS 到 JSON DNS 的衔接、无 stats 配置的核心启动、用户 API／同名跨命名空间 tag、不同 SOCKS 认证与 UDP 配置的覆盖。
- Android 原生 TUN 与 hev 模式分别测试实际 TCP、UDP、DNS 流量，而非仅看连接状态灯。
- JSON 配置从首页、快捷开关、重启、连接中切换与 Agent 入口启动行为一致。
- iOS 文件选择、取消、读取失败、编辑保存、选择、启动、停止、重启与错误反馈均有完整实现；Android 专有的快捷开关和 Agent 不作为 iOS 必须新增的功能。
- iOS 校验 App Group 配置传输以及 Network Extension 的 SOCKS 入口、认证和 UDP 通道，验证普通节点和 JSON 配置之间切换。
- iOS 在连接中切换／编辑／删除 JSON 配置时不会复用旧 pending 配置；JSON 与元数据版本不一致时拒绝启动。
- 导入／编辑失败不影响已连接的普通节点；启动失败不残留 VPN、监听端口或错误连接状态。
- JSON 的测速、分享、链节点、统计入口明确呈现能力限制，不崩溃、不显示假数据。

### 存量回归

- 所有现有协议 parser golden 测试继续通过；普通节点生成的关键配置保持一致。
- 链接剪贴板导入、扫码、手动协议添加／编辑、订阅添加／更新和分类筛选。
- 普通节点选择、收藏、删除、分享、单节点／批量／首页测速和流量统计。
- 普通节点前置／后置链、路由模式、DNS、IPv6、按应用分流、LAN 代理。
- 从 JSON 切回普通节点后，应用设置和连接流程恢复普通节点原行为。
- 带已有数据库升级安装，原节点、选择、订阅及收藏不丢失。

实现完成标准：Android 与 iOS 的导入到启动全流程代码均完成，共享逻辑有测试覆盖，不留 iOS 功能占位。

验证分别记录：相关 commonTest、Android 受影响模块编译与 Debug APK；iOS 在 macOS 条件下执行共享 framework、应用及 Network Extension 构建，模拟器用于 UI／文件导入测试，实际 VPN 流量需带正确 Network Extension 权限的真机验收。若 iOS 验证条件仍不可用，交付明确区分“已实现”和“未验证”，保持 iOS 验收项待完成。

## 10. 建议实施顺序及需要确认的产品选择

1. 确认本文的配置语义和首版能力边界。
2. 先定稿入口映射、DNS 衔接、native 必需字段和两端运行快照传输，再做 JSON 数据类型、导入校验、保留用户字段的运行适配及测试，不动旧解析器。
3. 在本次实现中接入两端系统文件选择、共享导入／编辑 UI，以及 Android 和 iOS 的完整启动路径。
4. 完成共享测试和 Android 旧节点回归、两种 TUN 模式实测；执行条件允许的 iOS 构建和运行验证，明确未验证项并整理交付文档。iOS 实现不能推迟到后续版本。

实施前建议确认：

- 导入的是完整 Xray JSON，而非仅一段 outbound 配置。
- 接受“保留原文、启动时做明确的手机 VPN 通道适配”，涉及入口、DNS 衔接及 native 必需字段；有歧义时需用户指定入口，仍无法兼容时明确拒绝激活。
- 首版 JSON 配置暂不提供未连接测速、链接二维码、应用前后置链和固定节点速率统计；原有链接节点继续正常提供这些功能。

如果需要 JSON 导出文件、粘贴 JSON、完整多出口速率统计或严格逐字运行，应先调整本文范围，再进入代码修改。
