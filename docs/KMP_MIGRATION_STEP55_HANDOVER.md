# KMP 迁移交接文档 — Step 55 / 阶段 E.6g（2026-08-07）

本文档记录阶段 E 第 6g 项：将 Home Connect 状态与交互逻辑从 `SharedHomeSection`
抽离至 Decompose `DefaultHomeComponent`；UI 层改为订阅 `HomeComponent.state`。

**前置**：Step 54 / E.6f 待 commit（Android HomeScreen 共享 UI 切片）。

---

## 前置条件检查（E.6g 入口）

| 前置项 | 状态 |
|--------|------|
| E.6f Android HomeScreen 共享 UI 切片 | ✅ 已实现（**待 commit**） |
| E.6e Decompose 根导航 + iosX64 | ✅ committed |
| E.6d 共享 composable 已存在于 `:shared` | ✅ |
| iOS `IosVpnConnectCoordinator` 真实 Connect | ✅ E.6c |
| `:androidApp` 仍用 `XrayViewmodel` Connect | ✅ 本步不改动 |

**结论**：E.6g 前置已全部满足（E.6f 代码就绪，commit 可与 E.6g 分开或顺序提交）。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `gradle/libs.versions.toml` | 新增 `essenty-lifecycle-coroutines` |
| `shared/build.gradle.kts` | 引入 lifecycle-coroutines |
| `shared/.../navigation/HomeState.kt` | Home 展示状态数据类 |
| `shared/.../navigation/HomeTabComponent.kt` | 升级为 `HomeComponent` 接口 |
| `shared/.../navigation/DefaultHomeComponent.kt` | VPN/节点/Connect 逻辑（自 SharedHomeSection 迁入） |
| `shared/.../navigation/HomeComponentFactory.kt` | Koin 工厂，`DefaultRootComponent` 使用 |
| `shared/.../navigation/DefaultRootComponent.kt` | Home Tab 创建 `DefaultHomeComponent` |
| `shared/.../navigation/RootComponent.kt` | `Child.Home` 类型改为 `HomeComponent` |
| `shared/.../ui/SharedHomeSection.kt` | 纯 UI：接收 `HomeComponent`，移除 Koin 注入 |
| `shared/.../ui/RootContent.kt` | 向 `SharedHomeSection` 传递 `child.component` |

### 未改动

- `:androidApp` `HomeScreen.kt` / `XrayViewmodel` / `V2rayStarterLarge`
- `AndroidVpnConnectCoordinator` stub（Android 仍走 ViewModel）
- iOS `TrafficStatsSource`（upload/download 仍为 0 stub）
- Config Tab 节点列表迁移

---

## 设计决策与原因

1. **Decompose `Value<HomeState>` 而非 Composable 内 Koin** —— Connect/busy/error 状态由 Component 生命周期管理，为后续 Android 嵌入 `SharedHomeSection` 统一入口铺路。
2. **`defaultHomeComponentFactory()` + KoinPlatform** —— iOS `MainViewController` 已 `IosKoinInit.ensureStarted()`，工厂在 Root 创建 Home 子组件时解析依赖；不在 commonMain 使用 `GlobalContext`（iOS klib 不可用）。
3. **Connect 逻辑与 E.6d SharedHomeSection 完全一致** —— 无节点 → 2s 错误提示；已连接 → disconnect；未连接 → prepareConfig → connect；busy 防重复点击。
4. **流量仍为 0 stub** —— `TrafficStatsSource` expect/actual 留 E.5f/E.6h，避免本步扩大范围。
5. **Android 暂不嵌入 HomeComponent** —— 避免 stub coordinator 与 ViewModel 双轨；Android 仍用 E.6f 共享 UI composables + ViewModel。

---

## 验证状态

```bash
./gradlew :androidApp:assembleDebug
./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosX64
# BUILD SUCCESSFUL
```

---

## 阶段 E 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| E.6f | Android HomeScreen 共享 UI 切片 | ✅（待 commit） |
| E.6g | DefaultHomeComponent 抽离 Connect 状态 | ✅（本步，待 commit） |
| E.6h | Android 嵌入 SharedHomeSection + 真实 AndroidVpnConnectCoordinator | ⬜ 下一步 |
| E.5f（可选） | TrafficStatsSource + measureOutboundDelay | ⬜ |

---

## 下一步（E.6h）待办

1. **`AndroidVpnConnectCoordinator` 真实实现** —— 对接 `AppVpnController` / VPN 权限 / `XrayViewmodel` 等效流程
2. **Android `:androidApp` 嵌入 `SharedHomeSection(component)`** —— 替换 `CompactHomeContent` 中 Connect + 状态区（NodeCard 完整版仍留 androidApp）
3. **`TrafficStatsSource` expect/actual** —— iOS/Android 真实 upload/download
4. **Config Tab 迁移节点列表**

---

## Commit 建议

**先 commit E.6f（若尚未提交）：**
```
feat(kmp): wire Android HomeScreen to shared home UI composables (E.6f)
```

**再 commit E.6g：**
```
feat(kmp): extract Home connect state into DefaultHomeComponent (E.6g)
```

---
