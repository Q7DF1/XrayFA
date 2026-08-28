# KMP 迁移交接文档 — Step 96 / Phase 7 A4 AppFunctions Phase A（2026-08-26）

本文档记录 Phase 7 **A4**：在 `:androidApp` 接入 Jetpack AppFunctions，暴露 8 个 Phase A **只读** `@AppFunction`，并用设置总开关 `agentFunctionsEnabled` 在调用时拒绝。未做 Phase B 写操作，未调用 `AppFunctionManager.setAppFunctionEnabled`。

**前置**：Step 95 / A3（DataStore `agent_functions_enabled` + 设置页开关）。交接：`docs/KMP_MIGRATION_STEP95_HANDOVER.md`。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `gradle/libs.versions.toml` | `appfunctions = "1.0.0-alpha08"`；`appfunctions` + `appfunctions-service` + compiler |
| `androidApp/build.gradle.kts` | implementation + KSP；`ksp { arg("appfunctions:aggregateAppFunctions", "true") }`；Guava 编译 workaround |
| `XrayFAAppFunctions` | 8 个 Phase A 只读函数；首参 `AppFunctionContext`；`requireEnabled` → `AppFunctionDeniedException("AGENT_DISABLED")` |
| `AgentFunctionGate` + `AgentFunctionGateTest` | 开关检查 + `filterKind` 解析（4 tests） |
| `AppFunctionModels.kt` | domain DTO 的 `@AppFunctionSerializable` 镜像 |
| `XrayFAApplication` | `AppFunctionConfiguration.Provider`，Koin 工厂构造 `XrayFAAppFunctions` |
| `AndroidManifest.xml` | `android.app.appfunctions.app_metadata` → `@xml/app_metadata`；**不**自注册 Service（走库的 `PlatformAppFunctionService`） |
| `res/xml/app_metadata.xml` + 四套 `appfunctions_display_description` | 应用级描述 |
| `proguard-rules.pro` | keep `com.android.xrayfa.agent.appfunctions.**` 与 `androidx.appfunctions.**` |

### 未改动

- Phase B 写操作（`connectVpn` / `refreshSubscription` / `measureNodeDelay` / `navigateTo`）仍走 Facade `UNSUPPORTED`（B1）
- 未调用 `AppFunctionManager.setAppFunctionEnabled`（B2）
- iOS 无 Agent 入口
- 未升 compileSdk 37 / AGP 9.1

---

## 设计决策

1. **钉死 alpha08** — `1.0.0-alpha09` / `alpha10` 的 AAR metadata 要求 **compileSdk 37 + AGP 9.1**。当前工程是 compileSdk 36 / AGP 8.10，不能为 A4 升整条工具链。alpha08 要求 compileSdk 36 + AGP ≥ 8.9.1，匹配现状。
2. **alpha08 架构 ≠ 计划草稿里的 alpha10** — 没有 `@AppFunctionServiceEntryPoint`。`@AppFunction` 在 `androidx.appfunctions.service`；首参必须是 `AppFunctionContext`；系统绑定库合并进来的 `PlatformAppFunctionService`；KSP 生成 `assets/app_functions_v2.xml`。
3. **门控在函数入口** — 开关关闭时不返回节点/订阅摘要，抛 `AppFunctionDeniedException(AGENT_DISABLED)`。OS 级 disable 留给 B2。
4. **Guava 显式 implementation** — `appfunctions-service` 运行时带 Guava，AGP consistent-resolution 把 CameraX 的 `listenablefuture:1.0` 升成空 stub `9999`，编译期看不到 `ListenableFuture`。补 `com.google.guava:guava:32.0.1-android` 与 runtime 对齐。

---

## Function ID（A5 用这个，不要用计划里的 snake_case 草稿）

KSP 生成 `XrayFAAppFunctionsIds`：

| 方法 | ID |
|------|----|
| `getVpnStatus` | `com.android.xrayfa.agent.appfunctions.XrayFAAppFunctions#getVpnStatus` |
| `getSelectedNode` | `…XrayFAAppFunctions#getSelectedNode` |
| `listNodes` | `…XrayFAAppFunctions#listNodes` |
| `getNode` | `…XrayFAAppFunctions#getNode` |
| `listSubscriptions` | `…XrayFAAppFunctions#listSubscriptions` |
| `getSettingsSummary` | `…XrayFAAppFunctions#getSettingsSummary` |
| `getTrafficSpeeds` | `…XrayFAAppFunctions#getTrafficSpeeds` |
| `getAppInfo` | `…XrayFAAppFunctions#getAppInfo` |

---

## 验证状态

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home"
./gradlew :androidApp:compileDebugKotlin
# BUILD SUCCESSFUL；KSP: assets/app_functions_v2.xml + XrayFAAppFunctionsIds
./gradlew :androidApp:testDebugUnitTest \
  --tests com.android.xrayfa.agent.appfunctions.AgentFunctionGateTest \
  --tests com.android.xrayfa.agent.DefaultXrayAgentFacadeTest
# AgentFunctionGateTest 4 tests, 0 failures
# DefaultXrayAgentFacadeTest 11 tests, 0 failures
```

- [x] `:androidApp:checkDebugAarMetadata` 通过（alpha08 / compileSdk 36）
- [x] 合并后的 debug manifest 含 `PlatformAppFunctionService` 与 `app_functions_v2.xml`
- [x] 开关关闭 → `AGENT_DISABLED`（门控单测）
- [ ] A5：API 36+ 设备 `adb shell cmd app_function list/execute`

---

## 期中后 / Phase 7 进度

Phase 7 必做 **7** 项（A1–A5、B1–B2）。C1 Gemini EAP 为可选，不计入。

| 步骤 | 内容 | 状态 |
|------|------|------|
| 93 / A1 | domain 契约 + Catalog | ✅ |
| 94 / A2 | Android Facade + Koin | ✅ |
| 95 / A3 | DataStore 总开关 + 设置页 | ✅ |
| **96 / A4** | AppFunctions + Phase A 只读 `@AppFunction` | ✅ 本步 |
| A5 | `adb cmd app_function` 手测 | ⬜ 下一步 |
| B1 | connect / refresh + VPN prepare | ⬜ |
| B2 | `setAppFunctionEnabled` 随设置 | ⬜ |

**还剩 3 个任务。**

---

## 下一步（A5）待办清单

1. 安装 debug APK 到 **API 36+** 设备/模拟器（系统需支持 AppFunctions extension）。
2. 设置 → General：**打开** Agent 功能。
3. ```bash
   adb shell cmd app_function list --package com.android.xrayfa
   adb shell cmd app_function execute --package com.android.xrayfa \
     --id com.android.xrayfa.agent.appfunctions.XrayFAAppFunctions#getVpnStatus
   ```
   实际 subcommand 以设备 `cmd app_function help` 为准（有的构建用 `--function` 而非 `--id`）。
4. 关闭开关后再 execute：应失败 / `AGENT_DISABLED`，且不得返回节点摘要。
5. **不要**在 A5 实现 Phase B 写操作或 `setAppFunctionEnabled`。

---

## 手动验证清单

### Android（A5）
- [ ] `list` 能看到 8 个 Phase A 函数
- [ ] 开关开：`getVpnStatus` / `getAppInfo` 成功
- [ ] 开关关：execute 被拒绝
- [ ] API < 36：App 启动不 crash（`AppFunctionManager.getInstance()` 可为 null）

### iOS
- [ ] 无 AppFunctions；设置页开关仍只写 DataStore

---

## Commit 建议（确认后执行）

A1–A4 若尚未提交，可一次：

```
feat(agent): expose Phase A AppFunctions behind the settings master switch
```

仅本步：

```
feat(agent): add alpha08 AppFunctions for Phase A read-only queries
```
