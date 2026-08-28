# KMP 迁移交接文档 — Step 17 / 阶段 C.4（首批）（2026-08-04）

本文档记录阶段 C 第 4 项（首批）：新建 KMP `:domain` 模块，迁入无 Android 依赖的
`SubscriptionParser` 与 `Protocol`；其余 parser / Gson model 仍留 `app`。

验证状态：
- `./gradlew :domain:compileDebugKotlinAndroid` **BUILD SUCCESSFUL**
- `./gradlew :domain:testDebugUnitTest` **全部通过**
- `./gradlew :domain:compileKotlinIosSimulatorArm64` **BUILD SUCCESSFUL**
- `./gradlew :common:testDebugUnitTest` **全部通过**
- `./gradlew :app:compileDebugKotlin` **BUILD SUCCESSFUL**

**待用户确认后再 commit。**

---

## 改动概要

**目标**：建立 `:domain` KMP 模块作为业务逻辑层容器；首批迁入已 KMP 化的订阅解析
与协议枚举，package 名不变，app import 零改动。

**跨平台意义**：`SubscriptionParser` + `Protocol` 首次在 iOS target 编译；为后续
config parser / model 批量迁入提供模块骨架。

### 改动清单

**新增模块 `:domain`**
- `domain/build.gradle.kts`：KMP + androidTarget + iosArm64/iosSimulatorArm64
- 依赖 `:common`（`Base64Compat`）

**物理迁入（`domain/src/commonMain`，package 不变）**
- `com.android.xrayfa.parser.SubscriptionParser`
- `com.android.xrayfa.model.protocol.Protocol`（含 `protocolsPrefix` / `protocolPrefixMap`）

**从 `app` 删除**（同上路径，改由 `:domain` 提供）

**修改**
- `settings.gradle.kts`：`include(":domain")`
- `app/build.gradle.kts`：`implementation(project(":domain"))`

**KMP 兼容修复**
- `SubscriptionParser.parseUrl`：`String(bytes)` → `bytes.decodeToString()`（iOS 可编译，UTF-8 语义不变）

**测试**
- `domain/.../SubscriptionParserTest.kt`：Base64 订阅拆行

**仍留 `app`（后续 C.4 批次）**
- `AbstractConfigParser` 及 7 个 `*ConfigParser`（依赖 `SettingsRepository`、Gson、model）
- `ParserFactory`
- `app/.../model/*`（Gson `@SerializedName`）
- `dto/`

---

## 设计决策

1. **保留原 package 名** —— `com.android.xrayfa.parser` / `.model.protocol` 不变，
   app / DI / UI 无需改 import，Strangler Fig 最小 diff。
2. **首批只迁零 Android 依赖文件** —— config parser 绑 `SettingsRepository` + Gson，
   等 model 序列化迁移后再迁。
3. **`decodeToString()` 替代 JVM `String(ByteArray)`** —— 订阅内容为 UTF-8 文本，
   与 Android 行为一致。

---

## 阶段 C 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| C.1 | `:common` 转 KMP | ✅ |
| C.2 | kotlinx.serialization 试点 | ✅ |
| C.3 | DataStore KMP | ✅ |
| C.4 | parser + model → `:domain` | 🔄 C.4a ✅，C.4b 待 commit，C.4c 待续 |

---

## 下一步（C.4 续）待办清单

### C.4b 迁入纯 model 子集

**候选**（无 Android API、可先保留 Gson 或改 `@Serializable`）：
- `Version.kt`
- 逐步：`LogObject`、`DnsObject` 等 leaf model

### C.4c 迁入 config parser

**前置**：model 子集进 `:domain`；或抽 `ConfigParserDependencies` 接口解耦 `SettingsRepository`

**验证命令**：
```bash
./gradlew :domain:compileDebugKotlinAndroid :domain:testDebugUnitTest
./gradlew :domain:compileKotlinIosSimulatorArm64
./gradlew :app:compileDebugKotlin
```

---

## 阶段 D 预览（阶段 C 不做）

- Dagger → Koin
- Compose Multiplatform UI
- iOS VPN Network Extension
