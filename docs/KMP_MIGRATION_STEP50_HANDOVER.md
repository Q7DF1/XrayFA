# KMP 迁移交接文档 — Step 50 / 阶段 E.6b（2026-08-07）

本文档记录阶段 E 第 6b 项：iOS Koin 平台层补全（Settings/DataStore/parser/network）；
首个共享 UI 切片 `HomeConnectionPanel` + `SharedHomeSection`；**Android 全量 Home 仍留 `:androidApp`**。

**前置**：Step 49 / E.6 已 commit（`2ce33bc`）。

---

## 前置条件检查（E.6b 入口）

| 前置项 | 状态 |
|--------|------|
| E.6 CMP AppShell + MainViewController | ✅ committed |
| `:core:datastore` iOS App Group 路径 | ✅ E.5d |
| `:core:network` iOS Ktor Darwin | ✅ |
| `:domain` parserDiModule | ✅ |

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `shared/.../di/IosPlatformDiModule.kt` | Logger / DataStore / SettingsRepository / asset paths / VPN |
| `shared/.../di/IosNetworkDiModule.kt` | `SubscriptionFetcher` |
| `shared/.../di/SharedCoroutineDiModule.kt` | Main/Background scope |
| `shared/.../platform/Ios*.kt` | Logger / XrayAssetPaths / GeoIp stub |
| `shared/.../vpn/VpnConnectCoordinator.kt` | 跨平台 connect 协调接口 |
| `shared/.../ui/home/HomeConnectionPanel.kt` | 首个共享 Home UI 组件 |
| `shared/.../ui/SharedHomeSection.kt` | Koin + VPN + settings 状态 |
| `core/network/.../IosNetworkFactory.kt` | 补 `createSubscriptionFetcher` |
| `IosKoinInit.kt` | 加载 coroutine + platform + network + parser |

### 未改动

- `:androidApp` `HomeScreen.kt` / ViewModel / Navigation3
- Room / SubscriptionRepository iOS Koin（留 E.6c）
- Decompose 导航

---

## 设计决策与原因

1. **iOS Koin 对齐 Android `appPlatformDiModule` 子集** —— Settings + parser 依赖可解析配置；不一次引入 Room 降低 E.6b 风险。
2. **`VpnConnectCoordinator` expect/actual 模式** —— iOS 写 App Group config；Android stub 返回 false（主 App 仍走 ViewModel）。
3. **`HomeConnectionPanel` 纯 UI + `SharedHomeSection` 接 Koin** —— 首个可复用 Compose 组件；Android 暂不嵌入 AppShell，零行为变化。
4. **移除 `PlatformVpnControls`** —— trial 逻辑迁入 `IosVpnConnectCoordinator` + 共享 UI，避免 Swift/Kotlin 双份按钮。
5. **`parserDiModule()` 进 iOS Koin** —— 为 E.6c 节点/config 生成铺路；GeoIP 暂 stub 空字符串。

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
| E.6 | CMP AppShell + MainViewController | ✅ committed |
| E.6b | iOS Koin + HomeConnectionPanel | ✅（本步，待 commit） |
| E.6c | Room/Subscription iOS + parser 真实 connect | ⬜ 下一步 |
| E.5f（可选） | measureOutboundDelay shim | ⬜ |

---

## 下一步（E.6c）待办清单

1. **iOS Koin 补 Room** —— `NodeRepository` / `SubscriptionRepository` actual
2. **`IosVpnConnectCoordinator` 接 ParserFactory** —— 替换 trial JSON
3. **共享 Home 扩展** —— 节点卡片 / 流量（从 HomeScreen 抽取）
4. **（可选）Android 嵌入 `SharedHomeSection` 对照验证**

---

## Commit 建议

```
feat(kmp): add iOS Koin platform bindings and shared HomeConnectionPanel (E.6b)
```

---
