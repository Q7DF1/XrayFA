# KMP 迁移交接文档 — Step 94 / Phase 7 A2 Android Facade（2026-08-26）

本文档记录 Phase 7 **A2**：在 `:androidApp` 实现 `DefaultXrayAgentFacade`，用 Koin 装配，节点/订阅委托 A1 的 `XrayAgentCatalog`。未引入 AppFunctions，未做设置页总开关。

**前置**：Step 93 / A1 已落地（未提交亦可基于同一工作区继续）。交接：`docs/KMP_MIGRATION_STEP93_HANDOVER.md`。

**范围**：仅 Android。iOS **不**注册 Facade。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `domain/.../XrayAgentCatalog.kt` | 抽出 `XrayAgentNodeQueries`；Catalog 实现该接口（方便 Android 单测 Fake） |
| `androidApp/.../agent/DefaultXrayAgentFacade.kt` | Phase A 只读 + `selectNode`/`setFavorite`；其余 Phase B → `UNSUPPORTED` |
| `androidApp/.../di/AppAgentDiModule.kt` | `XrayAgentNodeQueries` / `TrafficStatsSource` / `XrayAgentFacade` |
| `androidApp/.../di/KoinModules.kt` | 加入 `appAgentDiModule`（在 data + core 之后） |
| `RememberAndroidHomeComponent.kt` | 流量源改为 `koin.get<TrafficStatsSource>()`，与 Facade 共用 |
| `androidApp/.../DefaultXrayAgentFacadeTest.kt` | 10 条 JVM 单测（Fake catalog / VPN / settings） |
| 计划 / `AGENT.md` / 本交接 | 状态 |

### 未改动

- 未加 DataStore `agent_functions_enabled` / 设置页（A3）
- `getSettingsSummary.agentFunctionsEnabled` **恒为 `false`**
- 未引入 `androidx.appfunctions`（A4）
- `connectVpn` / `disconnectVpn` / `refreshSubscription` / `measureNodeDelay` / `openScreen` 返回 `UNSUPPORTED`（B1）
- `selectNode` 成功后调用 `vpnController.restartIfNeeded()`；失败不重启

---

## 设计决策

1. **Settings 用 `suspend () -> SettingsState`** — `SettingsRepository` 不是接口，单测不绑 DataStore。Koin 里闭包读 `settingsFlow.first()`。
2. **`TrafficStatsSource` 升为 Koin single** — Home 与 Facade 同源，避免两套 `AndroidTrafficStatsSource`。
3. **Phase B 显式 `UNSUPPORTED`** — 不是静默成功桩（期中 R-3）。connect 需要 `VpnService.prepare`，留给 B1。
4. **A3 未合并** — 总开关默认关的语义已由 `agentFunctionsEnabled = false` 表达；真正的 key + UI 下一步做。

---

## 验证状态

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home"
./gradlew :androidApp:testDebugUnitTest --tests com.android.xrayfa.agent.DefaultXrayAgentFacadeTest
# 10 tests, 0 failures
./gradlew :domain:testDebugUnitTest --tests com.android.xrayfa.agent.XrayAgentCatalogTest
# 12 tests, 0 failures
./gradlew :androidApp:compileDebugKotlin
# 已随 test 任务通过
```

- [x] Facade 单测 10 条全绿
- [x] Catalog 回归 12 条全绿
- [x] settings 摘要 `toString()` 不含 SOCKS 密码
- [x] `selectNode` 失败不 `restartIfNeeded`
- [ ] 真机启动后 Koin 能解析 `XrayAgentFacade`（人工）
- [ ] A3 总开关 / A4 AppFunction / A5 adb

---

## 期中后 / Phase 7 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| 93 / A1 | `:domain` Facade 契约 + Catalog `commonTest` | ✅ |
| **94 / A2** | `DefaultXrayAgentFacade` + Koin | ✅ 本步 |
| A3 | DataStore 总开关 + 设置页 | ⬜ 下一步 |
| A4 | AppFunctions + Phase A `@AppFunction` | ⬜ |
| A5 | `adb cmd app_function` | ⬜ |
| B1 | connect / refresh / delay + VPN prepare | ⬜ |

---

## 下一步（A3）待办清单

1. `:core:datastore`：`SettingsKeys.AGENT_FUNCTIONS_ENABLED`（磁盘键名 **`agent_functions_enabled`**，默认 **false**）。`SettingsState.agentFunctionsEnabled`。setter `setAgentFunctionsEnabled`.
2. `DefaultXrayAgentFacade.getSettingsSummary` 改为读 `settings.agentFunctionsEnabled`，去掉硬编码 `false`。
3. 设置页总开关 + 说明文案：`compose-resources` **四套** `strings.xml`（R-9）。不要只改英文默认值。
4. **不要**在 A3 引入 AppFunctions（A4）或 `setAppFunctionEnabled`（B2）。
5. 单测：DataStore 默认 false；映射进 `AgentSettingsSummary`。

---

## 手动验证清单

### Android
- [ ] 冷启动无崩溃（Koin 图含 `XrayAgentFacade` / `TrafficStatsSource`）
- [ ] Home 流量数字仍更新（同源 `TrafficStatsSource`）

### iOS
- [ ] 无需新行为（未改 iosMain）

---

## Commit 建议（确认后执行；若 A1 未单独提交可与 93 合一次）

```
feat(agent): implement Android DefaultXrayAgentFacade
```

A1 若仍未提交，可用：

```
feat(agent): add domain Agent catalog and Android facade
```
