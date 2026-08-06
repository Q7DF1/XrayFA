# KMP 迁移交接文档 — Step 36 / 阶段 D.4c（2026-08-06）

本文档记录阶段 D 第 4 项（第三批）：`Rule` / `AppJson` / `SettingsEnums` 迁入 `:core:datastore`；
清理 `:common` 遗留 Dagger KSP 与 serialization 依赖。

**前置**：Step 35 / D.4b 已 commit（`9aa0f1c`）。

---

## 前置条件检查（D.4c 入口）

| 前置项 | 状态 |
|--------|------|
| D.4b SettingsRepository 在 `:core:datastore` | ✅ committed |
| `:core:datastore` 已依赖 `:common`（ConfigParserSettings + Logger） | ✅ |

---

## 改动概要

**目标**：设置相关模型与 JSON 工具与 DataStore 同模块；`:common` 仅保留 parser 桥接类型与工具类。

### 迁入 — `:core:datastore`

| 文件 | 说明 |
|------|------|
| `Rule.kt` | 路由规则模型 + `encodeRules` / `decodeRules` / `defaultRoutes` |
| `AppJson.kt` | kotlinx.serialization JSON 实例 |
| `SettingsEnums.kt` | `Theme` / `DomainStrategy` / `RoutingMode` |
| `RuleSerializationTest.kt` | androidUnitTest（自 `:common` 迁入） |

**包名**：`com.android.xrayfa.datastore`

### 变更

| 文件 | 变更 |
|------|------|
| `core/datastore/build.gradle.kts` | `kotlin.serialization` + `kotlinx-serialization-json` + androidUnitTest |
| `common/build.gradle.kts` | 移除 Dagger/KSP 插件与 deps；移除 `kotlin.serialization` |
| `domain/build.gradle.kts` | 新增 `implementation(project(":core:datastore"))` |
| `:app` 5 处 / `:domain` 3 处 | import 改 `com.android.xrayfa.datastore.*` |
| `common/proguard-rules.pro` / `consumer-rules.pro` | 移除已迁出的 Rule/SettingsState keep 规则 |

### 删除 — `:common`

- `commonMain/.../Rule.kt`、`AppJson.kt`、`SettingsEnums.kt`
- `androidUnitTest/.../RuleSerializationTest.kt`
- `androidMain/.../di/qualifier/Qualifiers.kt`（遗留 Dagger `@Qualifier`，已无引用）

**仍留 `:common`**：
- `ConfigParserSettings` / `ConfigParserSettingsProvider`
- 工具类（Logger、Crypto、UrlCodec 等）
- Xray core 抽象（`XrayCore`、`TrafficDetector` 等）

**未改动**：
- Preference key 名称与默认值
- 路由规则 JSON 格式
- Koin 绑定结构

---

## 设计决策与原因

1. **模型与 Repository 同模块** —— Rule/AppJson 仅服务设置与 parser，与 DataStore 内聚。
2. **`:domain` 直依赖 `:core:datastore`** —— parser 使用 Rule/AppJson；避免 `:common` 再 export 设置模型。
3. **单向依赖保持** —— `:core:datastore` → `:common`（ConfigParserSettings）；`:domain` → `:core:datastore` + `:common`。
4. **删除 Qualifiers.kt** —— app 已全面 Koin（`KoinQualifiers`）；Dagger qualifier 无引用。

---

## 验证状态

```bash
./gradlew :core:datastore:compileKotlinIosSimulatorArm64   # BUILD SUCCESSFUL
./gradlew :core:datastore:testDebugUnitTest               # BUILD SUCCESSFUL
./gradlew :domain:testDebugUnitTest                       # BUILD SUCCESSFUL
./gradlew :app:assembleDebug                              # BUILD SUCCESSFUL
```

**建议手动回归**：路由规则编辑/保存、主题切换、域名策略、VPN 连接后路由生效。

---

## 阶段 D 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| D.4a | `:core:datastore` 工厂层 | ✅ committed |
| D.4b | SettingsRepository 迁入 | ✅ committed |
| D.4c | Rule/AppJson 等设置模型 + 清理 common Dagger | ✅（本步） |
| D.3d | Koin 4.1+ bump（可选） | ⬜ |
| D.5 | Entity mapper 等（可选） | ⬜ |

---

## 下一步待办清单

1. **阶段 E 入口**：平台抽象（VPN / native-bridge）或 Compose Multiplatform 壳
2. **（可选）D.3d**：Koin 4.1+ bump
3. **（可选）Entity mapper 迁入 `:core:database`**
4. **（可选）进一步瘦身 `:common`** —— 评估 parser 桥接类型是否可下沉到 `:domain`

**验证命令**：
```bash
./gradlew :core:datastore:compileKotlinIosSimulatorArm64
./gradlew :domain:testDebugUnitTest
./gradlew :app:assembleDebug
```

---

## Commit 建议

```
feat(kmp): move settings models into core:datastore and remove common Dagger KSP (D.4c)
```
