# KMP 迁移交接文档 — Step 51 / 阶段 E.6c（2026-08-07）

本文档记录阶段 E 第 6c 项：iOS Koin 补 Room/Subscription 数据层；
`VpnStartOptionsResolver` + `ParserFactory` 真实 connect；共享 Home 显示选中节点。

**前置**：Step 50 / E.6b 已 commit（`7d816cb`）。

---

## 前置条件检查（E.6c 入口）

| 前置项 | 状态 |
|--------|------|
| E.6b iOS Koin + HomeConnectionPanel | ✅ committed |
| `:core:database` iOS Room factory | ✅ 已有 `IosXrayDatabaseFactory` |
| `:domain` NodeRepository / SubscriptionRepository 接口 | ✅ |
| `:domain` ParserFactory + parserDiModule | ✅ E.6b 已进 iOS Koin |

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `shared/.../dto/EntityMappers.kt` | Node/Subscription entity ↔ domain 映射（KMP 副本） |
| `shared/.../repository/RoomNodeRepository.kt` | Room 实现 `NodeRepository` |
| `shared/.../repository/KmpSubscriptionRepository.kt` | 订阅拉取 + 节点入库（无 Android API） |
| `shared/.../vpn/VpnStartOptionsResolver.kt` | 从选中节点 + 订阅链构建 `CoreStartOptions` |
| `shared/.../di/IosDataDiModule.kt` | DAO / Repository / Resolver Koin 绑定 |
| `shared/.../vpn/IosVpnConnectCoordinator.kt` | 用 ParserFactory 替换 trial JSON |
| `shared/.../ui/SharedHomeSection.kt` | 显示选中节点；无节点时禁用 Connect |
| `shared/.../ui/home/HomeConnectionPanel.kt` | 增加 `selectedNodeLabel` / `hasSelectedNode` |
| `IosKoinInit.kt` | 加载 `iosDataDiModule` |
| `IosPlatformDiModule.kt` | 注入 `VpnStartOptionsResolver` 到 coordinator |

### 未改动

- `:androidApp` HomeScreen / ViewModel / 本地 EntityMappers
- Decompose 导航
- Android `SharedHomeSection` 嵌入（留 E.6d 可选）

---

## 设计决策与原因

1. **Repository 实现放在 `:shared` commonMain** —— iOS Koin 可直接注入；Android 仍用 app 内副本，零行为变化。
2. **`EntityMappers` 短期 duplicate** —— 避免 E.6c 牵动 `:androidApp` 模块依赖；后续可抽到 `:core:database` 或 `:domain`。
3. **`VpnStartOptionsResolver` 对齐 `XrayBaseServiceManager.getConfigInformation`** —— pre/next 节点 URL 与 Android 一致。
4. **Connect 前置校验** —— 无选中节点时 `prepareConfigForConnect()` 返回 false，UI 禁用 Connect 按钮。
5. **Room DB 路径** —— 暂沿用 `IosXrayDatabaseFactory` 默认路径；App Group 对齐可后续单独步。

---

## 验证状态

```bash
./gradlew :shared:compileKotlinIosSimulatorArm64
./gradlew :shared:compileDebugKotlinAndroid
./gradlew :androidApp:assembleDebug
# BUILD SUCCESSFUL

xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -configuration Debug -arch arm64 \
  ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=NO build
# BUILD SUCCEEDED
```

---

## 阶段 E 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| E.6c | Room/Subscription iOS + parser 真实 connect | ✅（本步，待 commit） |
| E.6d | 共享 Home 扩展（节点卡片 / 流量） | ⬜ 下一步 |
| E.5f（可选） | measureOutboundDelay shim | ⬜ |

---

## 下一步（E.6d）待办清单

1. **从 `HomeScreen` 抽取节点卡片 composable** —— 迁入 `:shared` commonMain
2. **流量统计 UI 切片** —— 接 VPN 状态 / stats flow（iOS stub 可占位）
3. **（可选）Android 嵌入 `SharedHomeSection` 对照验证**
4. **（可选）Decompose 导航骨架**

---

## iOS 可运行性

| 能力 | 状态 |
|------|------|
| Simulator 编译 | ✅ |
| Compose Home + 节点名显示 | ✅ |
| Connect 需 DB 中有选中节点 | ⚠️ 需手动/import 订阅数据 |
| VPN 端到端 | ❌ 需真机 + entitlement + 有效节点 |

---

## Commit 建议

```
feat(kmp): add iOS Room data layer and ParserFactory VPN connect (E.6c)
```

---
