# KMP 迁移交接文档 — Step 56 / 阶段 E.6h（2026-08-07）

本文档记录阶段 E 第 6h 项：实现真实 `AndroidVpnConnectCoordinator`；
Android `CompactHomeContent` 嵌入 `SharedHomeSection(HomeComponent)`；
Android 流量经 `TrafficStatsSource` 接入 `DefaultHomeComponent`。

**前置**：Step 55 / E.6g 已 commit（`e2ba552`）。

---

## 前置条件检查（E.6h 入口）

| 前置项 | 状态 |
|--------|------|
| E.6g `DefaultHomeComponent` + `SharedHomeSection(component)` | ✅ committed |
| E.6f Android 共享 UI composables | ✅ committed |
| `:androidApp` Koin 含 `VpnController` / repositories | ✅ |
| iOS `IosVpnConnectCoordinator` | ✅ 不受影响 |

**结论**：E.6h 前置已全部满足。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `shared/.../vpn/TrafficStatsSource.kt` | 流量 Flow 抽象 + `EmptyTrafficStatsSource` |
| `shared/.../vpn/AndroidVpnConnectCoordinator.kt` | 真实实现：`VpnStartOptionsResolver` + `VpnController` |
| `shared/.../di/AndroidSharedDiModule.kt` | 注册 resolver + coordinator |
| `shared/.../navigation/DefaultHomeComponent.kt` | 订阅 `TrafficStatsSource` |
| `shared/.../ui/home/HomeUiLabels.kt` | 可注入字符串（Android stringResource） |
| `shared/.../ui/SharedHomeSection.kt` | `showNodeCard` / `onConnectToggle` / `labels` |
| `androidApp/.../di/KoinModules.kt` | 加入 `androidSharedDiModule` |
| `androidApp/.../vpn/AndroidTrafficStatsSource.kt` | 桥接 `XrayCore.trafficFlow` |
| `androidApp/.../ui/home/RememberAndroidHomeComponent.kt` | Compose 生命周期内创建 `DefaultHomeComponent` |
| `androidApp/.../ui/component/HomeScreen.kt` | `CompactHomeContent` 嵌入共享区 + VPN 权限门控 |

### 未改动

- `ExpandedHomeContent` / `V2rayStarterLarge`（平板布局仍走 ViewModel）
- 完整 `NodeCard`（测速/收藏/编辑）仍留 `:androidApp`
- iOS 流量仍为 `EmptyTrafficStatsSource` stub
- Config Tab 节点列表

---

## 设计决策与原因

1. **`AndroidVpnConnectCoordinator`** — `prepareConfigForConnect` 校验 `VpnStartOptionsResolver`；`connect`/`disconnect` 走已有 `AppVpnController`，与 `XrayViewmodel.startXrayService` 等价。
2. **VPN 权限留在 Android Composable** — `onConnectToggle` 回调在 `CompactHomeContent` 中拦截 `VpnService.prepare()`，授权后再调 `HomeComponent.onConnectToggle()`；共享层不依赖 Activity API。
3. **`showNodeCard = false`** — Compact 布局节点区仍用 Android 完整 `NodeCard`，避免简化卡片替换现有交互。
4. **`HomeUiLabels`** — Compact 路径继续用 `stringResource(R.string.*)`，不 regress E.6f 本地化。
5. **`TrafficStatsSource`** — Android 经 `XrayCore.trafficFlow` 实时流量；iOS 暂留 empty，下一步 E.5f/E.6i 补 actual。

---

## 验证状态

```bash
./gradlew :androidApp:assembleDebug
./gradlew :shared:compileKotlinIosSimulatorArm64
# BUILD SUCCESSFUL
```

---

## 阶段 E 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| E.6h | Android SharedHomeSection + 真实 coordinator | ✅（本步，待 commit） |
| E.6i | iOS TrafficStatsSource + Expanded Android 嵌入 | ⬜ 下一步 |
| E.6j | Config Tab 节点列表迁移 | ⬜ |
| E.5f（可选） | measureOutboundDelay 共享 | ⬜ |

---

## 下一步（E.6i）待办

1. **`IosTrafficStatsSource`** — PacketTunnel / XrayBridge 流量回调
2. **`ExpandedHomeContent` 嵌入 `SharedHomeSection`** — 平板布局统一
3. **Config Tab 节点列表** — Decompose 子组件 + 共享列表 UI
4. **Android 逐步移除 Compact 中重复 ViewModel 状态订阅**（仅保留 NodeCard 所需）

---

## Commit 建议

```
feat(kmp): embed SharedHomeSection on Android with real VPN coordinator (E.6h)
```

---
