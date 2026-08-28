# KMP 迁移活清单（Phase 8）

> 每步交接只更新本文件 + [`IOS_STUBS.md`](./IOS_STUBS.md) + 该步 `KMP_MIGRATION_STEPNNN_HANDOVER.md`。  
> 详细历史仍在 [`KMP_MIGRATION_PLAN.md`](./KMP_MIGRATION_PLAN.md)。  
> 日期：2026-08-28。当前步：Step 110 / 111。

---

## 硬约束（Phase 8）

- Android 主路径冻结：`MainActivity` → `AndroidAppShell` → 共享 `RootContent` + `AndroidPlatformRootHooks`。底栏只有 Config | Home；Settings 等是 overlay。
- iOS 只填 `IosPlatformRootHooks`，不改 `RootContent` / `DefaultRootComponent` overlay 语义，不把 iOS 实现写进 `commonMain` 挤掉 Android hook。
- **不再换壳**（禁止再切回 `XrayFAContainer` 作为主路径）。
- **不做** Agent Phase C / C1、iOS AppFunctions。
- 不要把 `IosStubTunBridge` / 宿主 `startXrayCore`「做真」—— TUN/Xray 在 PacketTunnel。
- 编译闸：`:androidApp:compileDebugKotlin` + `AgentScreenRootTabTest` + `:shared:compileKotlinIosX64`（或 simulatorArm64）。

---

## 已完成（不必再做）

| 步骤 | 内容 |
|------|------|
| 骨架 | KMP 模块、Room KMP + migration、DataStore、Ktor、Koin、Decompose、gomobile xcframework、PacketTunnel（Xray + hev-socks5）、App Group（含 Room） |
| 73–92 | parser `commonTest`、CI 子集、去重、断 `:domain → datastore`、`:core:data`、四屏薄封装、compose-resources、`AGENT.md`、iOS 底栏/流量/NE 错误/Go 内存、Room 索引、Android 进 `RootContent` |
| 93–98 | Phase 7 Agent A1–A5、B1–B2（Android only）。C1 未做 |
| 99–107 | iOS `darkMode`、outbound delay shim、GeoIP MMDB、GeoLite 下载（后关 iOS 钮）、Home/Config 测速、CommonCrypto digest、宿主链 LibXrayLite、共享 delay URL |
| 108–109 | Android 功能回到共享壳 + hooks；导航对齐原生底栏 |
| **110** | 本清单 + `IOS_STUBS.md` + `IosPlatformRootHooks` 骨架（行为与 Step 109 默认 hooks 一致） |
| **111** | iOS `ShareNode` 二维码 + 剪贴板导出 |

核心已打通、不要当桩修：`IosXrayConfigEncoder`（kotlinx）、GeoIP、digest、`measureOutboundDelay`。

---

## 待完成

### P0 验证（非写功能）

- [ ] Step 109 Android 真机：底栏 Config\|Home、齿轮进设置、Config 搜索、相册扫码、节点二维码、bug report、快捷方式扫码、Agent `openScreen` overlay
- [ ] iOS 真机 VPN：连接 → Home 流量 → 断开 → `IosAppGroupStorage.readTunnelLastError()`

### P1 hooks 增量

- [x] `docs/IOS_STUBS.md`
- [x] 节点分享二维码（Step 111）
- [ ] geoip.dat / geosite.dat 文件导入（Step 112）
- [ ] 分应用代理（Step 115；先拍板系统能做多少）

### P2 体验

- [ ] iOS 扫码相册 + 闪光灯（Step 113）
- [ ] Bug report（Step 114）
- [ ] overlay 系统返回 / NE 日志桥（Step 116）
- [ ] GeoLite 手动放入 App Group（下载钮已关）

### P3 工程债（不挡功能）

- [ ] P0 Android 真机过后再冻结/删除 `XrayFAContainer`（Step 117）
- [ ] `build-logic`、拆 `feature/*`、Widget / On-Demand VPN / Share Extension — 未排期

---

## Step 111+ 顺序

| 步骤 | 内容 | 状态 |
|------|------|------|
| 110 | 活清单 + 桩清单 + `IosPlatformRootHooks` | ✅ |
| 111 | iOS `ShareNode` | ✅ |
| 112 | iOS geoip/geosite 文件导入 | ⬜ |
| 113 | iOS 扫码相册 + 闪光灯 | ⬜ |
| 114 | iOS Bug report | ⬜ |
| 115 | 分应用（产品拍板后） | ⬜ |
| 116 | overlay 返回、NE 日志桥 | ⬜ |
| 117 | 冻结/删除 `XrayFAContainer`（真机过后再动） | ⬜ |
