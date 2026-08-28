# KMP 迁移交接文档 — Step 18 / 阶段 C.4b（2026-08-04）

本文档记录阶段 C 第 4 项（第二批）：12 个 leaf / 低耦合 model 迁入 `:domain`；
Gson 绑定的 `RoutingObject` 与 graph 核心 model 仍留 `app`。

验证状态：
- `./gradlew :domain:compileDebugKotlinAndroid` **BUILD SUCCESSFUL**
- `./gradlew :domain:testDebugUnitTest` **全部通过**
- `./gradlew :domain:compileKotlinIosSimulatorArm64` **BUILD SUCCESSFUL**
- `./gradlew :app:compileDebugKotlin` **BUILD SUCCESSFUL**

**待用户确认后再 commit。**

---

## 改动概要

**目标**：在 `:domain` 继续堆积可双平台编译的 Xray 配置 leaf model；package
名不变，app import 零改动。

### 迁入 `:domain/commonMain`（`com.android.xrayfa.model`）

| 文件 | 说明 |
|------|------|
| `Version.kt` | 纯 data class |
| `LogObject.kt` | 无 Gson |
| `ApiObject.kt` | 无 Gson |
| `DnsObject.kt` | + `DnsServerObject` |
| `PolicyObject.kt` | 已有 `@Serializable` |
| `FakeDNSObject.kt` | 空占位 class |
| `MetricsObject.kt` | 空占位 class |
| `ReverseObject.kt` | 空占位 class |
| `ObservatoryObject.kt` | 空占位 class |
| `BurstObservatoryObject.kt` | 空占位 class |
| `BugReportData.kt` | UI 上报 DTO |
| `StatsObject.kt` | 未引用，一并迁入 |

**修改**
- `domain/build.gradle.kts`：`kotlin-serialization` + `kotlinx-serialization-json`（`PolicyObject`）

**仍留 `app`**
- `XrayConfiguration.kt`（聚合根，引用 stream / outbound / routing）
- `InboundObject.kt` / `OutboundObject.kt`
- `RoutingObject.kt`（Gson `@SerializedName`）
- `model/stream/*`

---

## 设计决策

1. **只迁无 Gson、无 stream 依赖的 leaf** —— iOS target 可编译；聚合根下一轮再动。
2. **`PolicyObject` 随模块启用 serialization** —— 已在 app 中使用 `@Serializable`，迁入后 domain 承担插件依赖。
3. **package 不变** —— `AbstractConfigParser` / `XrayConfiguration` 等仍 `import com.android.xrayfa.model.*`。

---

## 阶段 C 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| C.4a | `:domain` + SubscriptionParser + Protocol | ✅ |
| C.4b | leaf model 迁入 | ✅（待 commit） |
| C.4c | RoutingObject / stream / outbound 迁入 | ⬜ |

---

## 下一步（C.4c）待办清单

1. **`RoutingObject`**：`@SerializedName` → `@Serializable`（对齐 C.2 `Rule` 做法）
2. **`model/stream/*`**：逐文件迁入，检查 Gson 字段名
3. **`InboundObject` / `OutboundObject` / `XrayConfiguration`**：最后迁聚合根
4. **config parser 解耦**：抽接口替代直接依赖 `SettingsRepository`（或 parser 暂留 app）

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
