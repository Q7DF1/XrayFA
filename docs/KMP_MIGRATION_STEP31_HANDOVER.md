# KMP 迁移交接文档 — Step 31 / 阶段 D.3a（2026-08-05）

本文档记录阶段 D 第 3 项（第一批）：创建 `:core:network` KMP 模块，
订阅拉取改走 Ktor；OkHttp 保留给延迟测试 / SOCKS 代理等 Android 路径。

**前置**：Step 30 / D.2c 已 commit（`d7b8c11`）。

---

## 改动概要

**目标**：网络层开始向 KMP 共享代码迁移；订阅 HTTP 逻辑跨平台可复用。

### 新增模块 `:core:network`

| 文件 | 说明 |
|------|------|
| `SubscriptionHeaderParser.kt` | 订阅响应头解析（自 `HttpResponseUtils` 提升，KMP 安全） |
| `SubscriptionFetcher.kt` | Ktor `HttpClient` 拉取订阅 body + meta |
| `HttpClientFactory.*` | expect/actual：`OkHttp`（Android）/ `Darwin`（iOS） |
| `AndroidNetworkFactory.kt` | Android 侧 `createSubscriptionFetcher(userAgent)` |

### `:app` 变更

| 变更 | 说明 |
|------|------|
| `AndroidSubscriptionRepository` | `OkHttp` → `SubscriptionFetcher` |
| `AppNetworkDiModule` | 注册 `SubscriptionFetcher`；保留 `SHORT_TIME`/`LONG_TIME` OkHttp |
| 删除 `HttpResponseUtils.kt` | 逻辑已迁入 `:core:network` |

**未改动**：`XrayViewmodel` / `SettingsViewmodel` 仍用 OkHttp（延迟测试、SOCKS 代理）。

---

## 设计决策与原因

1. **Strangler Fig** —— 仅订阅拉取先迁 Ktor；复杂 OkHttp 配置留 app，降低风险。
2. **app 不依赖 Ktor 类型** —— 通过 `AndroidNetworkFactory` 封装，`AppNetworkDiModule` 只见 `SubscriptionFetcher`。
3. **iOS 已编译验证** —— `:core:network:compileKotlinIosSimulatorArm64` 通过（Darwin engine）。
4. **Header 解析 KMP 化** —— 使用 `decodeToString()` 替代 `Charsets.UTF_8`（iOS 兼容）。

---

## 验证状态

```bash
./gradlew :core:network:compileDebugKotlinAndroid                        # BUILD SUCCESSFUL
./gradlew :core:network:compileKotlinIosSimulatorArm64                   # BUILD SUCCESSFUL
./gradlew :domain:testDebugUnitTest                                      # BUILD SUCCESSFUL
./gradlew :app:assembleDebug                                             # BUILD SUCCESSFUL
```

**待用户确认后再 commit。**

---

## 阶段 D 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| D.2c | `:core:database` | ✅ committed |
| D.3a | `:core:network` + 订阅 Ktor | ✅（待 commit） |
| D.3b | Kotlin 2.1 升级 + Room iOS | ⬜ |
| D.3c | ViewModel OkHttp → Ktor（延迟测试等） | ⬜ |

---

## 下一步（D.3b）待办清单

1. **Kotlin 2.0.21 → 2.1.x + KSP 对齐**
2. **`:core:database` 启用 iOS target + BundledSQLite**
3. **Room iOS KSP 编译验证**
4. **全量回归** `:app:assembleDebug` + iOS simulator targets

**验证命令**：
```bash
./gradlew :core:database:compileKotlinIosSimulatorArm64
./gradlew :core:network:compileKotlinIosSimulatorArm64
./gradlew :domain:testDebugUnitTest
./gradlew :app:assembleDebug
```

---

## Commit 建议

```
feat(kmp): add core:network module and migrate subscription fetch to Ktor (D.3a)
```
