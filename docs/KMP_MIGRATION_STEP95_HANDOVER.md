# KMP 迁移交接文档 — Step 95 / Phase 7 A3 Agent 总开关（2026-08-26）

本文档记录 Phase 7 **A3**：DataStore 磁盘键 `agent_functions_enabled`（默认 **false**）、设置页总开关、Facade 设置摘要读取该字段。未引入 AppFunctions，未调用 `setAppFunctionEnabled`。

**前置**：Step 94 / A2（`DefaultXrayAgentFacade` + Koin）。交接：`docs/KMP_MIGRATION_STEP94_HANDOVER.md`。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `core/datastore/.../SettingsRepository.kt` | `SettingsState.agentFunctionsEnabled`；键名锁定 `agent_functions_enabled`；`setAgentFunctionsEnabled` |
| `core/datastore/.../SettingsStateDefaultsTest.kt` | 默认 false + 磁盘键名 |
| `DefaultXrayAgentFacade` | `getSettingsSummary` 读 `settings.agentFunctionsEnabled` |
| `DefaultXrayAgentFacadeTest` | 打开开关时摘要为 true |
| `SettingsComponent` / `DefaultSettingsComponent` | `onSetAgentFunctionsEnabled` |
| `SharedSettingsGeneralSection` | 总开关（开机自启下方） |
| `SettingsUiLabels` + `rememberSettingsUiLabels` | 新字段 |
| `composeResources/values{,-zh-rCN,-ko,-ru-rRU}/strings.xml` | `agent_functions_title` / `agent_functions_desc` |

### 未改动

- 未引入 `androidx.appfunctions`（A4）
- 未调用 `AppFunctionManager.setAppFunctionEnabled`（B2）
- Facade 在开关关闭时**仍会执行**查询（门控在 A4 Service / B2）。本步只提供用户可写的设置位。
- 未改 `androidApp/res/values*/strings.xml`（Compose 走 compose-resources）

---

## 设计决策

1. **缺 key ≡ false** — 与 `boot_auto_start` 相同：`prefs[KEY] == true`。老用户升级不会突然打开 Agent。
2. **开关放共享 General** — Android `RootContent` 与遗留 `SettingsScreen` 都走 `SharedSettingsGeneralSection`。iOS 也能看到开关并写入 DataStore，但本阶段没有 Agent 入口。
3. **A4 才执行门控** — 现在没有 `@AppFunction`，Service 层的 `requireAgentEnabled` 下一步再做。

---

## 验证状态

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home"
./gradlew :core:datastore:testDebugUnitTest --tests com.android.xrayfa.datastore.SettingsStateDefaultsTest
# 2 tests, 0 failures
./gradlew :androidApp:testDebugUnitTest --tests com.android.xrayfa.agent.DefaultXrayAgentFacadeTest
# 11 tests, 0 failures
./gradlew :shared:compileDebugKotlinAndroid :androidApp:compileDebugKotlin
# BUILD SUCCESSFUL
```

- [x] 默认 `agentFunctionsEnabled == false`
- [x] 磁盘键名 `agent_functions_enabled`
- [x] Facade 映射 settings → summary
- [x] 四套 compose-resources 文案
- [ ] 真机：设置页开关翻转后重启 App 仍保持（人工）
- [ ] A4 `requireAgentEnabled` / A5 adb

---

## 期中后 / Phase 7 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| 93 / A1 | domain 契约 + Catalog | ✅ |
| 94 / A2 | Android Facade + Koin | ✅ |
| **95 / A3** | DataStore 总开关 + 设置页 | ✅ 本步 |
| A4 | AppFunctions + Phase A 只读 `@AppFunction` | ⬜ 下一步 |
| A5 | `adb cmd app_function` | ⬜ |
| B1 | connect / refresh + VPN prepare | ⬜ |
| B2 | `setAppFunctionEnabled` 随设置 | ⬜ |

---

## 下一步（A4）待办清单

1. catalog：`appfunctions = "1.0.0-alpha10"`；`androidApp` `implementation` + `ksp` compiler；`ksp { arg("appfunctions:aggregateAppFunctions", "true") }`。
2. `XrayFAAppFunctionService`：Phase A **只读**函数；首参 `AppFunctionContext`；`Dispatchers.IO`；返回 domain DTO 的 Android 可序列化镜像（若 compiler 要求 `@AppFunctionSerializable`）。
3. **`requireAgentEnabled`**：`settings.agentFunctionsEnabled == false` → `AGENT_DISABLED` / 失败结果。不要在未授权时返回节点摘要。
4. compileSdk 已是 36。低版本 `AppFunctionManager.getInstance()` 为 null 时不 crash。
5. **不要**做 Phase B 写操作（B1）或 `setAppFunctionEnabled`（B2）。
6. 验证：`:androidApp:compileDebugKotlin`；生成的 metadata / manifest。A5 再 `adb shell cmd app_function`。

---

## 手动验证清单

### Android
- [ ] 设置 → General：看到 Agent 开关，默认关
- [ ] 打开后杀进程再进，仍为开
- [ ] 中文/英文界面文案正确

### iOS
- [ ] 设置页同样出现开关（写入 App Group DataStore；无 Agent 运行时）

---

## Commit 建议（确认后执行）

A1–A3 若尚未提交，可一次：

```
feat(agent): add Agent facade, catalog, and settings master switch
```

仅本步：

```
feat(agent): add agent_functions_enabled setting (default off)
```
