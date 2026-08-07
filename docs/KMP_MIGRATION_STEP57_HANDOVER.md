# KMP 迁移交接文档 — Step 57 / 阶段 E.6i（2026-08-07）

本文档记录阶段 E 第 6i 项：iOS `IosTrafficStatsSource`（PacketTunnel → App Group → HomeComponent）；
Android `ExpandedHomeContent` 嵌入 `SharedHomeSection(HomeComponent)`，删除冗余 `V2rayStarterLarge`。

**前置**：Step 56 / E.6h 已 commit（`125ef80`）。

---

## 前置条件检查（E.6i 入口）

| 前置项 | 状态 |
|--------|------|
| E.6h `AndroidVpnConnectCoordinator` + Compact 嵌入 SharedHomeSection | ✅ committed |
| E.6g `DefaultHomeComponent` + `TrafficStatsSource` 订阅 | ✅ committed |
| Android `AndroidTrafficStatsSource` 桥接 `XrayCore.trafficFlow` | ✅ |
| iOS `IosVpnConnectCoordinator` + App Group IPC | ✅ E.6c/E.5d |
| Libv2ray `queryStats` / xcframework 可用 | ✅ E.4/E.5e |

**结论**：E.6i 前置已全部满足。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `common/.../IosPlatformConstants.kt` | 新增 `vpn_upload_speed_kbps` / `vpn_download_speed_kbps` App Group 键 |
| `platform/vpn/.../IosAppGroupStorage.kt` | 读写/清零流量 KB/s；公开 `readVpnTrafficSpeedsKbps()` |
| `platform/vpn/.../IosVpnController.kt` | `disconnect()` 时清零流量 |
| `iosApp/PacketTunnel/PacketTunnelProvider.swift` | 3s 轮询 `queryStats(proxy,uplink/downlink)` 写入 App Group |
| `shared/iosMain/.../IosTrafficStatsSource.kt` | 3s 轮询 App Group，接入 `DefaultHomeComponent` |
| `shared/iosMain/.../IosPlatformDiModule.kt` | Koin 注册 `TrafficStatsSource` |
| `shared/.../HomeComponentFactory.kt` | iOS 工厂注入 `trafficStatsSource` |
| `shared/.../SharedHomeSection.kt` | 新增 `largeStatusLabel`（平板大号状态文案） |
| `androidApp/.../HomeScreen.kt` | Expanded 布局嵌入 SharedHomeSection；移除 `V2rayStarterLarge` |

### 未改动

- Config Tab 节点列表（留 E.6j）
- Android Compact 路径 NodeCard 仍走 `XrayViewmodel`（测速/收藏/编辑）
- `AndroidLibXrayLite` 子模块（无 commit；`queryStats` API 已存在）
- `measureOutboundDelay` 共享（留 E.5f 可选）

---

## 设计决策与原因

1. **iOS 流量经 App Group IPC** — Xray 运行在 PacketTunnel 进程，与 Android `XrayCoreManager.startTrafficDetection` 对齐：NE 内 `queryStats("proxy", uplink/downlink)` → KB/s → UserDefaults；主 App `IosTrafficStatsSource` 3s 轮询。不引入新 XPC 协议，复用 E.5d IPC 模式。
2. **Expanded 与 Compact 统一 HomeComponent** — 平板左侧连接区改由 `SharedHomeSection` + `DefaultHomeComponent` 驱动，Connect/状态/流量与 iOS 共享层一致；右侧完整 `NodeCard` 仍留 `:androidApp`（E.6h 策略延续）。
3. **删除 `V2rayStarterLarge`** — 已由共享 `HomeConnectButton` 替代，避免 Android 双轨 Connect UI；逻辑等价于 E.6h Compact 路径（VPN 权限门控 + `HomeComponent.onConnectToggle`）。
4. **`largeStatusLabel`** — Expanded 布局需要大号连接状态 typography，参数化而非复制 Composable。

---

## 验证状态

```bash
./gradlew :androidApp:assembleDebug
./gradlew :shared:compileKotlinIosSimulatorArm64
./gradlew :platform:vpn:compileKotlinIosSimulatorArm64
# BUILD SUCCESSFUL
```

---

## 阶段 E 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| E.6i | iOS TrafficStatsSource + Expanded Android 嵌入 | ✅（本步，待 commit） |
| E.6j | Config Tab 节点列表迁移 | ⬜ 下一步 |
| E.5f（可选） | measureOutboundDelay 共享 | ⬜ |

---

## 下一步（E.6j）待办

1. **Config Tab 节点列表** — Decompose 子组件 + 共享列表 UI（替换 `:androidApp` 内重复列表）
2. **Android NodeCard 测速** — 可选接入共享 `HomeComponent` 或独立 `NodesComponent`
3. **Compact/Expanded 移除 ViewModel 冗余订阅** — NodeCard 所需字段逐步从 `XrayViewmodel` 迁至 Component
4. **iOS 实机 NE 流量验证** — 需 VPN Entitlement + 真机 PacketTunnel

---

## 子仓库备忘

| 仓库 | 事项 | 动作 |
|------|------|------|
| `AndroidLibXrayLite` | 无改动；`queryAllOutboundTrafficStats` 未使用 | 无需 issue/PR |

---

## Commit 建议

```
feat(kmp): add iOS traffic stats IPC and unify expanded home layout (E.6i)
```

---
