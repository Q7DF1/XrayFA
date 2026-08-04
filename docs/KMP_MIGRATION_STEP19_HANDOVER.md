# KMP 迁移交接文档 — Step 19 / 阶段 C.4c（2026-08-04）

本文档记录阶段 C 第 4 项（第三批）：`RoutingObject` Gson→`@Serializable`、
`model/stream/*`、聚合根 `InboundObject` / `OutboundObject` / `XrayConfiguration`
全部迁入 `:domain`；`app/.../model/` 目录清空。

验证状态：
- `./gradlew :domain:compileDebugKotlinAndroid` **BUILD SUCCESSFUL**
- `./gradlew :domain:testDebugUnitTest` **全部通过**（含 `RoutingObjectSerializationTest`）
- `./gradlew :domain:compileKotlinIosSimulatorArm64` **BUILD SUCCESSFUL**
- `./gradlew :app:compileDebugKotlin` **BUILD SUCCESSFUL**

**待用户确认后再 commit。**

---

## 改动概要

**目标**：完成 Xray config model 层向 `:domain` 的物理迁移；消除 `RoutingObject`
对 Gson 的编译依赖；iOS target 可编译完整 model 图。

### 迁入 `:domain/commonMain`

| 类别 | 文件 |
|------|------|
| Routing | `RoutingObject.kt`（`@Serializable` / `@SerialName`，含 `RuleObject` 等） |
| Stream | `stream/*`（7 文件） |
| 聚合根 | `InboundObject.kt`、`OutboundObject.kt`、`XrayConfiguration.kt` |
| 提取 | `Sockopt.kt`（自 `InboundObject.kt` 拆出，供 stream 引用） |

**修改**
- `InboundObject.kt`：删除内联 `Sockopt` / `HappyEyeballs`（改 import 同 package）
- `RuleObject.type`：`String? = "field"` → `String = "field"`（与 common `Rule` 对齐）

**测试**
- `RoutingObjectSerializationTest`：Gson ↔ kotlinx 往返

**仍留 `app`**
- `app/.../parser/*`（绑 `SettingsRepository`、Dagger、Gson config 构建）
- `ParserFactory`

**`app/.../model/`** —— **已空（全部迁完）** ✅

---

## 设计决策

1. **`RoutingObject` 用 `@SerialName` 替 `@SerializedName`** —— iOS 无需 Gson；
   Gson 仍可通过字段名解析（与 C.2 `Rule` 同策略）。
2. **`Sockopt` 独立文件** —— `StreamSettingsObject` 依赖；避免 stream 反向依赖 `InboundObject`。
3. **parser 暂不迁** —— 下一步 C.5 或阶段 D 前抽 `ConfigParserDependencies` 接口。
4. **抽象类保留** —— `AbsInboundConfigurationObject` 等暂不 `@Serializable`；
   Gson 多态序列化仍由 app parser 承担。

---

## 阶段 C 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| C.4a | SubscriptionParser + Protocol | ✅ |
| C.4b | leaf model | ✅ |
| C.4c | RoutingObject + stream + 聚合根 | ✅（待 commit） |
| C.5 | parser 迁入 `:domain` | ⬜ |

---

## 下一步（C.5 / 阶段 C 收尾）待办清单

1. **抽 parser 依赖接口** —— 替代 `SettingsRepository` / `GeoIpProvider` 直注入
2. **物理迁入 `app/.../parser/`** → `:domain/commonMain`
3. **评估 `Rule` vs `RuleObject` 合并** —— 结构已一致
4. **ProGuard**：确认 `domain` model keep 规则（若需要）

**验证命令**：
```bash
./gradlew :domain:compileDebugKotlinAndroid :domain:testDebugUnitTest
./gradlew :domain:compileKotlinIosSimulatorArm64
./gradlew :app:compileDebugKotlin
```

---

## 阶段 D 预览

- Dagger → Koin
- Compose Multiplatform UI
- iOS VPN Network Extension
