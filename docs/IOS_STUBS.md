# iOS stubs / 平台缺口（R-3）

> `AGENT.md` 要求：actual 不得以抛异常 / 恒 false / 空串结案而不登记。  
> 更新：2026-09-04（Step 119）。实现后把条目移到「已不再是桩」或删掉。

---

## 故意桩（设计如此，不要在宿主「做真」）

| 位置 | 行为 | 用户可见？ | 说明 |
|------|------|------------|------|
| `core/native-bridge/.../NativeBridgeFactory.ios.kt` `IosStubTunBridge` | `startTun2Socks` / `stop` / `isRunning` → false | 否 | TUN 在 PacketTunnel 的 HevSocks5Tunnel，不走 KMP `TunBridge` |
| `shared/.../IosXrayCore.kt` `startXrayCore` | 恒 `false` | 否 | Xray 只在 Network Extension 进程跑 |
| `IosXrayCore.measureDelaySync` | 恒 `-1L` | 部分 | 宿主没有 live core；Home 测速回落到 `measureOutboundDelay`（需已连 VPN） |
| `common/.../GeoLiteDownloadSupport.ios.kt` `geoLiteDownloadSupported` | `false` | 是（下载钮禁用） | 宿主连不到 NE 内 `127.0.0.1` SOCKS。Step 105 |

---

## 用户可见未实现（`IosPlatformRootHooks`）

| Hook / 功能 | UI | 阻塞？ | 计划步骤 |
|-------------|-----|--------|----------|
| `AppsScreen` 分应用代理 | `SharedInDevelopmentScreen` | 是 | 115（系统 API 受限，先拍板） |
| Settings geoip.dat / geosite.dat 导入 | 行显示「开发中」 | 是（路由用户） | 112 |
| Settings HexTun | 行显示「开发中」 | 部分 | NE 固定 hev-socks5；保持诚实或注明 |
| 扫码相册 / 闪光灯 | 仅 AVFoundation 实时相机 | 否 | 113 |
| `SystemBackHandler` | 空实现 | 否 | 116；靠系统手势 |
| 日志 | `SharedInProcessAppLogScreen`（进程内 `AppLogStore`） | 部分 | 116 NE 日志桥 |

`ShareNode`：**Step 111 已实现**（二维码 Dialog + 剪贴板），不再算桩。  
`BugReport`：**Step 119 已实现**（共享表单 + GitHub issue URL），不再算桩。

---

## 已不再是桩（文档若仍写 stub 以本表为准）

| 项 | 现状 |
|----|------|
| `IosXrayConfigEncoder` | 委托 `KotlinxXrayConfigEncoder` |
| `IosGeoIpProvider` | App Group 读 GeoLite2 MMDB + 国旗 emoji |
| Digest / `calculateBytesHash` | CommonCrypto SHA-256 / MD5 |
| `XrayBridge.measureOutboundDelay` | ObjC shim `XrayFAMeasureOutboundDelay` |
| 主题 `darkMode` | `AppShell` + `XrayTheme` 读 DataStore |
| `BugReport` | 共享 `SharedBugReport` + `BugReportIssueComposer` 打开 GitHub |

---

## 登记规则

新加 iOS `actual` 若返回常量、抛 `UnsupportedOperationException`、或 UI 走「开发中」，同一 PR 必须改本文件，且对应 Stage **不得**标 ✅。
