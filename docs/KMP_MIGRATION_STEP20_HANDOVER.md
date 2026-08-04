# KMP 迁移交接文档 — Step 20 / 阶段 C.5（2026-08-04）

本文档记录阶段 C 第 5 项（收尾）：config parser 全部迁入 `:domain`；
抽离 `ConfigParserSettingsProvider` / `XrayConfigEncoder` 解耦 Android 绑定；
`app/.../parser/` 目录清空。

**前置条件检查（C.5 入口）**

| 前置项 | 状态 |
|--------|------|
| C.4c model 迁入 `:domain` | ✅（Step 19，与本步同批未 commit） |
| `:common` KMP + DataStore | ✅ |
| `Rule` kotlinx.serialization | ✅ |
| `CoreStartOptions` | ✅ |

验证状态：
- `./gradlew :common:compileDebugKotlinAndroid :common:testDebugUnitTest` **BUILD SUCCESSFUL**
- `./gradlew :domain:compileDebugKotlinAndroid :domain:testDebugUnitTest` **全部通过**
- `./gradlew :domain:compileKotlinIosSimulatorArm64` **BUILD SUCCESSFUL**
- `./gradlew :app:compileDebugKotlin` **BUILD SUCCESSFUL**

**待用户确认后再 commit（建议先 commit Step 19 C.4c，再 commit 本步；或合并为一次 commit）。**

---

## 改动概要

**目标**：7 个 config parser + `ParserFactory` + `ProtocolConfigs` 物理迁入 `:domain/commonMain`，
iOS target 可编译完整 parser 图；Android 运行时 JSON 输出与路由逻辑不变。

### `:common` 新增 / 调整

| 文件 | 说明 |
|------|------|
| `SettingsEnums.kt` | `Theme` / `RoutingMode` / `DomainStrategy` 提升到 commonMain |
| `ConfigParserSettings.kt` | parser 所需 settings 快照 + `ConfigParserSettingsProvider` 接口 |
| `SettingsRepository.kt` | 实现 `ConfigParserSettingsProvider`；删除内联 enum 定义 |

**原因**：parser 不能依赖 `androidMain` 的 `SettingsRepository` / DataStore，否则 iOS 无法编译。

### `:domain` 迁入（`commonMain`）

| 类别 | 文件 |
|------|------|
| Parser | `AbstractConfigParser`、`ParserFactory`、7 × `*ConfigParser` |
| DTO | `ProtocolConfigs.kt`、`ParseModels.kt`（`ParseLinkInput` / `ParsedNode`） |
| 辅助 | `RuleMapping.kt`、`VmessJson.kt` |
| 编码 | `config/XrayConfigEncoder.kt`（interface） |

| 平台 | 文件 |
|------|------|
| `androidMain` | `GsonXrayConfigEncoder`（Gson 输出，与原先 `Gson().toJson` 一致） |
| `iosMain` | `IosXrayConfigEncoder`（编译占位，VPN 未实现前 throw） |

**关键逻辑替换（行为等价）**

1. **路由规则**：`gson.fromJson<List<RuleObject>>` → `decodeRules()` + `Rule.toRuleObject()`
2. **启动参数**：`StartOptions` → `CoreStartOptions`（app 边界仍保留 Parcelable `StartOptions`）
3. **VMess JSON**：Gson `JsonObject` → kotlinx `JsonObject`（字段读写语义不变）
4. **KMP 字符串**：`String(bytes)` / `toByteArray()` → `decodeToString()` / `encodeToByteArray()`
5. **配置 JSON 输出**：经 `XrayConfigEncoder` 注入；Android 仍走 Gson reflection

### `app` 调整

| 改动 | 说明 |
|------|------|
| 删除 | `app/.../parser/*`（9 文件）、`dto/ProtocolConfigs.kt` |
| 新增 | `dto/ParseAdapters.kt`（`Link` ↔ `ParseLinkInput`，`ParsedNode` ↔ `Node`） |
| DI | `ParserModule` 注入 `ConfigParserSettingsProvider` + `XrayConfigEncoder` |
| 调用方 | `XrayCoreManager` / `XrayViewmodel` / `SubscriptionRepository` 适配 |
| UI | `EditScreen` / `DetailViewmodel` VMess 字段读取改用 kotlinx JSON |

**仍留 `app`**
- `dto/Link.kt`、`dto/Node.kt`（Room `@Entity`，待 Room KMP 后再拆 domain model）
- Dagger `ParserModule`（阶段 D 再迁 Koin）

---

## 设计决策

1. **`ConfigParserSettings` 而非整体迁移 `SettingsState`** —— 只暴露 parser 需要的 13 个字段，
   避免 DataStore / UI 设置泄漏进 domain。
2. **`ParseLinkInput` / `ParsedNode` 边界类型** —— Room Entity 暂留 app；parser 返回平台中立 DTO，
   app 层一行 adapter 转 `Node`，订阅导入逻辑不变。
3. **`XrayConfigEncoder` 分平台实现** —— 完整 Xray config 图尚未全部 `@Serializable`；
   Android 继续 Gson 保证输出字节级兼容；iOS 先编译通过。
4. **`Rule` 与 `RuleObject` 暂不合并** —— 结构已一致，通过 `RuleMapping` 转换；
   合并留待阶段 C 后 cleanup（可选）。
5. **package 名不变** —— `com.android.xrayfa.parser` / `.dto` 保持，app import 最小改动。

---

## 阶段 C 进度（完成）

| 步骤 | 内容 | 状态 |
|------|------|------|
| C.1 | `:common` 转 KMP | ✅ |
| C.2 | kotlinx.serialization 试点 | ✅ |
| C.3 | DataStore KMP | ✅ |
| C.4a | SubscriptionParser + Protocol | ✅ |
| C.4b | leaf model | ✅ |
| C.4c | RoutingObject + stream + 聚合根 | ✅ |
| C.5 | config parser 迁入 `:domain` | ✅（待 commit） |

---

## 下一步（阶段 D 入口）待办清单

阶段 C 领域层提取已完成。建议阶段 D 顺序：

### D.1 Dagger → Koin（`:domain` / `:common` DI）

- `ParserModule`、`GlobalModule` 等迁 Koin module
- `SettingsRepository` 去掉 `javax.inject`

### D.2 Room Entity 与 Domain Model 分离

- `Node` / `Link` domain 纯 model → `:domain`
- Room Entity 留 `core:database` 或 app（Room KMP 步骤）

### D.3 XrayConfiguration 全图 `@Serializable`

- 替换 `GsonXrayConfigEncoder`，iOS 可真正 `parse()` 出 JSON
- ProGuard keep 规则复查

### D.4 Compose Multiplatform / iOS 壳

见 `KMP_MIGRATION_PLAN.md` Phase 4–5。

**验证命令（阶段 D 每子步）**：
```bash
./gradlew :common:compileDebugKotlinAndroid :common:testDebugUnitTest
./gradlew :domain:compileDebugKotlinAndroid :domain:testDebugUnitTest
./gradlew :domain:compileKotlinIosSimulatorArm64
./gradlew :app:compileDebugKotlin
```

---

## Commit 建议

若分两次 commit：
1. `feat(kmp): migrate Xray config models to domain (C.4c)`
2. `feat(kmp): migrate config parsers to domain with platform abstractions (C.5)`

若合并一次：
`feat(kmp): complete phase C domain layer (models + parsers)`
