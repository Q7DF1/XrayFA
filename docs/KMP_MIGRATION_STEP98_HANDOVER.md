# KMP 迁移交接文档 — Step 98 / Phase 7 B1+B2（2026-08-27）

本文档记录 Phase 7 **B1 + B2**：落地 Phase B 写操作（VPN prepare / 订阅刷新 / 延迟 / 开屏），并把设置总开关同步到 `AppFunctionManager.setAppFunctionEnabled`。同时修掉 A5 发现的 `getTrafficSpeeds` 空采样挂起。C1 Gemini EAP **不做**。

**前置**：Step 97 / A5。交接：`docs/KMP_MIGRATION_STEP97_HANDOVER.md`。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `domain/.../XrayAgentCatalog` + `XrayAgentCatalogTest` | `refreshSubscription`：缺失 → `SUBSCRIPTION_NOT_FOUND`；`fetchAndSaveNodes`；异常 → `NETWORK_ERROR` |
| `DefaultXrayAgentFacade` + 单测 | `connectVpn` / `disconnectVpn` / `refreshSubscription` / `measureNodeDelay` / `openScreen`；`getTrafficSpeeds` 用 `firstOrNull` + `withTimeoutOrNull`，空流/超时回 `0.0` |
| `AndroidAgentDelayProbe` | `ParserFactory` + `XrayCore.measureOutboundDelay`；`<=0` 或异常 → `NETWORK_ERROR` |
| `XrayFAAppFunctions` | 7 个 Phase B `@AppFunction`，仍走 `withAgentEnabled`；返回 `AppFnActionResult` / `AppFnDelayResult`（非 sealed） |
| `AgentFunctionGate.parseScreen` | Home/Config/Settings/Subscriptions/Apps/RouteSettings |
| `AndroidRootAction.OpenScreen` + `AndroidAppShell` | `selectTab`：Home→Home；Config/Subscriptions→Config；Settings/Apps/RouteSettings→Settings |
| `AppAgentDiModule` | 接 coordinator / `VpnService.prepare` / delay probe / 开屏 Intent |
| `AgentAppFunctionEnableSync` + IDs | B2：null writer（API&lt;36）no-op；逐 ID `setAppFunctionEnabled`；单 ID 失败不中断 |
| `XrayFAApplication` | `settingsFlow.agentFunctionsEnabled.distinctUntilChanged()` → `sync` |

### 未改动

- Phase C（删节点、改路由 JSON、LAN / 分应用、剪贴板、logcat）
- C1 Gemini EAP
- iOS 无 Agent 入口
- AppFunctions 仍钉 **alpha08**（compileSdk 36 / AGP 8.10）

---

## 设计决策

1. **`connectVpn` 必须 `VpnService.prepare`** — 未授权时 `startActivity(prepare + NEW_TASK)` 并返回 `NeedsUserConsent("VPN permission")`，不静默连。已连接则 Success、不重连。
2. **写结果用扁平 DTO** — `@AppFunctionSerializable` 不能表达 sealed class。`status` = `success` / `failure` / `needs_consent`。
3. **`measureNodeDelay` 的 url 用空字符串表示默认** — 避免 alpha08 对可空入参的不确定性；空则用 `settings.delayTestUrl`。每节点 5s 限流 → `RATE_LIMITED`。
4. **`openScreen` 只切顶栏 Tab** — Apps / RouteSettings 是 Settings 内本地 `remember` 状态，Agent 落到 Settings tab。
5. **B2 不依赖 Robolectric** — `AppFunctionManager` 抽成 `AgentAppFunctionEnabledWriter`；低版本 `getInstance` 为 null 则 writer=null。
6. **`getTrafficSpeeds` 不再 `first()`** — 空 `SharedFlow` 会挂到系统超时；`emptyFlow().first()` 会抛 `NoSuchElementException`。改为 `firstOrNull` + 1.5s timeout，回退 `0.0 to 0.0`。

---

## Function ID（与 KSP `XrayFAAppFunctionsIds` 对齐）

前缀：`com.android.xrayfa.agent.appfunctions.XrayFAAppFunctions#`

| Phase | 方法 |
|-------|------|
| A | `getVpnStatus` `getSelectedNode` `listNodes` `getNode` `listSubscriptions` `getSettingsSummary` `getTrafficSpeeds` `getAppInfo` |
| B | `selectNode` `setFavorite` `connectVpn` `disconnectVpn` `refreshSubscription` `measureNodeDelay` `openScreen` |

共 **15** 个。`AgentAppFunctionIds.ALL` 必须与生成常量一致（单测反射核对）。

---

## 验证状态

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home"
./gradlew :domain:testDebugUnitTest --tests com.android.xrayfa.agent.XrayAgentCatalogTest
# XrayAgentCatalogTest 15 tests, 0 failures
./gradlew :androidApp:compileDebugKotlin :androidApp:testDebugUnitTest \
  --tests com.android.xrayfa.agent.DefaultXrayAgentFacadeTest \
  --tests com.android.xrayfa.agent.appfunctions.AgentFunctionGateTest \
  --tests com.android.xrayfa.agent.appfunctions.AgentAppFunctionEnableSyncTest \
  --tests com.android.xrayfa.ui.AgentScreenRootTabTest
# BUILD SUCCESSFUL
# DefaultXrayAgentFacadeTest 23 / AgentFunctionGateTest 6 /
# AgentAppFunctionEnableSyncTest 4 / AgentScreenRootTabTest 1；0 failures
```

- [x] KSP 生成 15 个 ID + `assets/app_functions_v2.xml`
- [x] 无选中节点 / 未授权 VPN / 已连接 / prepare 失败 / connect 失败 单测
- [x] 空 traffic flow 回零（不再抛、不再挂）
- [x] B2 sync：null writer no-op；逐 ID 写入；单 ID 失败继续
- [x] API 36 真机（SM-S9420，sdk 36）：`list-app-functions` **15** 个 ID；`getTrafficSpeeds` **0.0/0.0**（`--timeout-duration 5` 未挂）；无 VPN 授权时 `connectVpn` → `status: needs_consent` / `VPN_NOT_PREPARED`

adb 复用 STEP97 的 Samsung 子命令。有参示例：

```bash
# measureNodeDelay：url 空字符串 = 用设置里的 delay-test URL
adb shell "cmd app_function execute-app-function --package com.android.xrayfa \
  --function 'com.android.xrayfa.agent.appfunctions.XrayFAAppFunctions#measureNodeDelay' \
  --parameters '{\"nodeId\":[1],\"url\":[\"\"]}' --user 0 --brief-yaml"

# openScreen
adb shell "cmd app_function execute-app-function --package com.android.xrayfa \
  --function 'com.android.xrayfa.agent.appfunctions.XrayFAAppFunctions#openScreen' \
  --parameters '{\"target\":[\"Settings\"]}' --user 0 --brief-yaml"
```

开关关掉后再 execute：B2 先把 OS enable 位置为 DISABLED，系统直接拒绝，实测为 **`AppFunctionException` code 1002**（function disabled），到不了应用内 `AGENT_DISABLED (1000)`。这是预期：OS 门在 Facade 门前面。测完已把设置页开关恢复为用户原值（开）。

**不要**把真实节点 IP / 订阅 URL 写进文档。

---

## 期中后 / Phase 7 进度

Phase 7 必做 **7** 项全部完成。C1 为可选，不计入。

| 步骤 | 内容 | 状态 |
|------|------|------|
| 93 / A1 | domain 契约 + Catalog | ✅ |
| 94 / A2 | Android Facade + Koin | ✅ |
| 95 / A3 | DataStore 总开关 + 设置页 | ✅ |
| 96 / A4 | AppFunctions + Phase A 只读 | ✅ |
| 97 / A5 | `adb cmd app_function` 手测 | ✅ |
| **98 / B1** | Phase B 写 + VPN prepare + traffic 超时修复 | ✅ 本步 |
| **98 / B2** | `setAppFunctionEnabled` 随设置 | ✅ 本步 |
| C1 | Gemini EAP / 私有预览 | ⬜ 可选，未做 |

---

## 下一步

1. **不要**做 Phase C 或 C1，除非产品明确要求。
2. 新加 `@AppFunction` 时同步 `AgentAppFunctionIds.ALL`（反射单测会红）。
3. iOS 产品缺口（非 Agent）：设置 `darkMode` 接到共享 `XrayTheme`；`measureOutboundDelay`；GeoIP。

---

真机手测已记入上文验证清单（SM-S9420）。
