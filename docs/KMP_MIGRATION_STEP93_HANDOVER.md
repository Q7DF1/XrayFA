# KMP 迁移交接文档 — Step 93 / Phase 7 A1 Agent 契约（2026-08-26）

本文档记录 Phase 7 **A1**：在 `:domain` 落地 `XrayAgentFacade` 契约、Agent DTO / 错误码，以及可单测的 `XrayAgentCatalog`（节点/订阅查询与本地写）。AppFunctions 运行时、VPN prepare、设置总开关都不在本步。

**前置**：Step 92 Android `MainActivity` → 共享 `RootContent` 已完成（`d063d57`）。KMP 期中查漏补缺表 73–92 已勾完。本步是 `docs/ANDROID_AGENT_APPFUNCTIONS_PLAN.md` 的第一刀编码。

**范围**：仅 **Android Agent 契约**；iOS **不**实现 Facade。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `domain/.../agent/AgentModels.kt` | DTO、`AgentActionResult`、`AgentErrorCode`、`AgentScreen`、`AgentNodeFilter` |
| `domain/.../agent/XrayAgentFacade.kt` | Phase A 只读 + Phase B 写 的完整接口 |
| `domain/.../agent/XrayAgentCatalog.kt` | 委托 `NodeRepository` / `SubscriptionRepository`；摘要化映射 |
| `domain/.../agent/XrayAgentCatalogTest.kt` | 12 条 `commonTest`，内存 Fake repo（不是 mockk） |
| `docs/KMP_MIGRATION_PLAN.md` | Step 93 / A1 状态 |
| `docs/ANDROID_AGENT_APPFUNCTIONS_PLAN.md` | 状态改为 A1 已落地 |
| `AGENT.md` | §4 测试、§5 domain agent 包 |

### 未改动

- 未写 `DefaultXrayAgentFacade` / Koin（A2）
- 未加 DataStore `agent_functions_enabled` / 设置页开关（A3）
- 未引入 `androidx.appfunctions`（A4）
- `selectNode` **不**调用 `VpnController.restartIfNeeded()`（留给 A2）
- iOS 无 Agent 入口

---

## 设计决策

1. **Catalog 与 Facade 拆开** — 计划要求 A1「接口 + 单元测试（mock repo）」。VPN / Settings / Traffic 依赖不在 `:domain`（R-7）。能在 domain 测的节点/订阅逻辑放进 `XrayAgentCatalog`；完整 Facade 仍是接口，A2 在 `:androidApp` 组装。
2. **内存 Fake 而不是 mock 框架** — domain `commonTest` 没有 mockk；Fake 在 Native（`iosX64Test`）也能跑。
3. **`AgentNodeFilter` 用 data class** — 计划草稿是 enum，但 `SubscriptionId` 必须带 id。`kind` + `subscriptionId`；`Manual` 对齐 `ConfigFilterIds.SUB_MANUAL = -1`。
4. **摘要化是编译期约束** — `AgentNodeSummary` / `AgentSubscriptionSummary` 没有 `url` / `jsonData`；测试再断言 `toString()` 不含 token。
5. **写操作部分进 Catalog** — `selectNode` / `setFavorite` 只碰 repository，缺失 id 返回 `NODE_NOT_FOUND`。VPN 重启、总开关、consent 仍是 A2/A3/B1。

### 相对计划的接口补全

计划 §4.1 未写出、本步补上的类型：`AgentSubscriptionSummary`、`AgentTrafficSpeeds`、`AgentAppInfo`、`AgentDelayResult`。

---

## 验证状态

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home"
./gradlew :domain:testDebugUnitTest    # 29 tests, 0 failures（含 Catalog 12）
./gradlew :domain:iosX64Test           # 25 tests, 0 failures（含 Catalog 12；Gson 对比仍仅 JVM）
```

- [x] `:domain:testDebugUnitTest` 通过（Catalog 12 / 全模块 29）
- [x] `:domain:iosX64Test` 通过（Catalog 12 / commonTest 25）
- [x] `list_nodes` / `list_subscriptions` 摘要不含 url / json / token
- [x] 不存在 nodeId → `NODE_NOT_FOUND`
- [ ] 真机 `adb shell cmd app_function`（A5，本步无 runtime）
- [ ] Agent 关闭时 execute 返回 disabled（A3）

---

## 期中后 / Phase 7 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| 73–92 | P0/P1/R-1…R-9 查漏 + Android `RootContent` | ✅ |
| **93 / A1** | `:domain` Facade + DTO + Catalog `commonTest` | ✅ 本步 |
| A2 | `DefaultXrayAgentFacade` + Koin | ⬜ 下一步 |
| A3 | DataStore 总开关 + 设置页 | ⬜ |
| A4 | AppFunctions 依赖 + Phase A `@AppFunction` | ⬜ |
| A5 | `adb cmd app_function` 手测 | ⬜ |

---

## 下一步（A2）待办清单

1. `:androidApp` 实现 `DefaultXrayAgentFacade : XrayAgentFacade`：
   - 节点/订阅方法委托 `XrayAgentCatalog`
   - `getVpnStatus` ← `VpnController.state` / `connectError`
   - `getSettingsSummary` ← `SettingsRepository.settingsFlow`（**不含** `socksPassword`；`agentFunctionsEnabled` 在 A3 加 key 前可先写死 `false` 或与 A3 同 PR）
   - `getTrafficSpeeds` ← `TrafficStatsSource`
   - `getAppInfo` ← `BuildConfig`
   - `selectNode` 在 Catalog 成功后 `vpnController.restartIfNeeded()`
2. Koin `single<XrayAgentFacade> { DefaultXrayAgentFacade(...) }`；`XrayAgentCatalog` 也注册为 single。
3. 建议把 A3（`agent_functions_enabled` 默认 **false**）一起做，否则 Facade 没有总开关可查。若 A2 单独合入，`getSettingsSummary.agentFunctionsEnabled` 先返回 `false` 并在 handover 标明。
4. **不要**在 A2 引入 AppFunctions 依赖（那是 A4）。
5. 单测：androidApp 可用 Fake Catalog 依赖测 VPN/settings 映射；不要把 Android 测试塞进 `commonTest`。

---

## 手动验证清单

### Android
- [ ] 无（本步无 UI / 无 Service；回归：App 启动与节点列表，确认 domain 接口未破坏 DI）

### iOS
- [ ] 无需新行为（未改 iosMain；commonTest 已在 `iosX64Test` 跑过 Catalog）

---

## Commit 建议（确认后执行）

```
feat(agent): add domain XrayAgentFacade and catalog queries
```
