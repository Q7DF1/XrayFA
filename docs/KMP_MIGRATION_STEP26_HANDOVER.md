# KMP 迁移交接文档 — Step 26 / 阶段 D.1d-b（2026-08-05）

本文档记录阶段 D 第 1 项（第六批）：5 个 ViewModel Factory、`NotificationHelper`、
`XrayBaseServiceManager` 迁入 Koin；Dagger 经 `KoinBridgeModule` 桥接。

**前置**：Step 25 / D.1d-a 已 commit（`601b213`）。

---

## 改动概要

**目标**：UI 层 Factory 与 Service 辅助类由 Koin 装配；`MainActivity` 等仍 `@Inject` Factory，零 UI 改动。

### 新增

| 文件 | 说明 |
|------|------|
| `AppViewModelDiModule.kt` | 5 个 `*ViewmodelFactory` |

### 扩展

| 模块 | 新增 |
|------|------|
| `AppCoreDiModule` | `NotificationHelper`、`XrayBaseServiceManager` |
| `KoinBridgeModule` | 上述 7 个类型的 `@Provides` 桥接 |

### 去 `javax.inject`

| 类 |
|----|
| `XrayViewmodelFactory` / `SettingsViewmodelFactory` / `DetailViewmodelFactory` |
| `SubscriptionViewmodelFactory` / `AppsViewmodelFactory` |
| `NotificationHelper` / `XrayBaseServiceManager` |

**仍保留 Dagger `@Inject`**（D.1d-c 再处理）：
- `MainActivity`、5 个 Factory 注入点（经桥接）
- `XrayBaseService`、`QuickStartTileService`、`BootBroadcastReceiver`
- `ComponentResolver`、`TProxyService`

---

## 设计决策

1. **Factory 用 Koin `single` 而非 `factory`** —— 与原先 Dagger `@Singleton` Factory 行为一致。
2. **不引入 `koin-androidx-viewmodel`** —— 本步仅迁装配，不改 `ViewModelProvider` 用法；Decompose 阶段再评估。
3. **`MainActivity` 构造注入不变** —— 降低 UI 层 diff，Strangler Fig 过渡。

---

## 验证状态

```bash
./gradlew :app:compileDebugKotlin   # BUILD SUCCESSFUL
```

**待用户确认后再 commit。**

---

## 阶段 D 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| D.1d-a | Platform + Core 迁 Koin | ✅ committed |
| D.1d-b | ViewModel Factory + Service helpers | ✅（待 commit） |
| D.1d-c | 移除 Dagger（Component、KSP） | ⬜ |
| D.2 | Room Entity 与 Domain Model 分离 | ⬜ |

---

## 下一步（D.1d-c）待办清单

1. **Koin 注册剩余 `@Inject` 组件** —— `ComponentResolver`、Services、Receivers
2. **删除 `KoinBridgeModule` + `XrayFAComponent` + KSP Dagger 依赖**
3. **改用 `XrayAppCompatFactory` 直接 `getKoin().get()` 或简化入口**

**验证命令**：
```bash
./gradlew :common:compileDebugKotlinAndroid :common:testDebugUnitTest
./gradlew :domain:compileDebugKotlinAndroid :domain:testDebugUnitTest
./gradlew :domain:compileKotlinIosSimulatorArm64
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
```

---

## Commit 建议

```
feat(kmp): migrate ViewModel factories and service helpers to Koin (D.1d-b)
```
