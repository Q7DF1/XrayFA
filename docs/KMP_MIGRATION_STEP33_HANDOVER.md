# KMP 迁移交接文档 — Step 33 / 阶段 D.3c（2026-08-06）

本文档记录阶段 D 第 3 项（第三批）：ViewModel 剩余 OkHttp 调用迁移至 Ktor；
`:app` 移除 OkHttp 直接依赖。

**前置**：Step 32 / D.3b 已 commit（`ad26d2e`）。

---

## 前置条件检查（D.3c 入口）

| 前置项 | 状态 |
|--------|------|
| D.3b Kotlin 2.1.10 + Room iOS | ✅ committed |
| D.3a 订阅拉取已走 Ktor | ✅ committed |
| `:core:network` iOS/Android 编译 | ✅ |

---

## 改动概要

**目标**：`:app` 不再直接依赖 OkHttp；Geo 文件下载与 SOCKS 代理逻辑迁入 `:core:network`，Android 行为不变。

### 新增 — `:core:network`

| 文件 | 说明 |
|------|------|
| `FileDownloader.kt` | Ktor 流式下载（commonMain，iOS 可复用） |
| `SocksProxyConfig.kt` | SOCKS 端口/认证配置数据类 |
| `SocksProxyHttpClientFactory.android.kt` | 镜像原 `LONG_TIME` OkHttp：SOCKS + Authenticator + 120s 超时 |
| `FileDownloader.android.kt` | `downloadToFile()` 扩展（写本地文件 + 进度） |
| `IosNetworkFactory.kt` | `createStandardFileDownloader`（Darwin，无 SOCKS，供 iOS 后续使用） |
| `AndroidNetworkFactory.kt` | 新增 `createProxyFileDownloader()` |

### 变更 — `:app`

| 文件 | 变更 |
|------|------|
| `SettingsViewmodel` | `OkHttpClient` → `FileDownloader.downloadToFile()` |
| `XrayViewmodel` | 移除未使用的 `okHttp` 注入（延迟测试走 `XrayCore`） |
| `AppNetworkDiModule` | 删除 OkHttp `SHORT_TIME`/`LONG_TIME`；注册 `FileDownloader` |
| `AppViewModelDiModule` | Factory 注入 `FileDownloader` |
| `app/build.gradle.kts` | 移除 `okhttp` / `logging-interceptor` 依赖 |
| `HttpTest.kt` | 删除（无实际测试，仅 OkHttp setup） |

**未改动**：
- 延迟测试仍通过 `XrayCore.measureDelaySync` / `measureOutboundDelay`
- Geo 下载仍走本地 SOCKS 代理（`127.0.0.1:socksPort`）+ 认证逻辑
- 下载进度、异常处理 UI 行为不变

---

## 设计决策与原因

1. **SOCKS 配置动态读取** —— `configProvider: () -> SocksProxyConfig` 保留原 `ProxySelector` / `Authenticator` 每次请求读 Settings 的语义。
2. **移除 XrayViewmodel 的 dead dependency** —— `okHttp` 注入后从未使用；延迟测试不经过 HTTP 客户端。
3. **OkHttp 仅留 `:core:network` androidMain** —— 作为 Ktor OkHttp engine 传递依赖，`:app` classpath 不再暴露 OkHttp 类型。
4. **iOS `createStandardFileDownloader`** —— 占位工厂，后续 iOS 设置页下载 Geo 文件可直接复用。

---

## 验证状态

```bash
./gradlew :core:network:compileKotlinIosSimulatorArm64   # BUILD SUCCESSFUL
./gradlew :core:network:compileDebugKotlinAndroid        # BUILD SUCCESSFUL
./gradlew :domain:testDebugUnitTest                       # BUILD SUCCESSFUL
./gradlew :app:assembleDebug                              # BUILD SUCCESSFUL
```

**待用户确认后再 commit。**

**建议手动回归**：
- 设置页下载 GeoIP / GeoSite / GeoLite（需 SOCKS 代理可用）
- 节点延迟测试（单节点 / 全部）
- 订阅刷新

---

## 阶段 D 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| D.3a | `:core:network` + 订阅 Ktor | ✅ committed |
| D.3b | Kotlin 2.1 + Room iOS | ✅ committed |
| D.3c | ViewModel OkHttp → Ktor | ✅（待 commit） |
| D.3d | Koin 4.1+ bump（可选） | ⬜ |
| D.4 | `:core:datastore` KMP / Entity mapper 等 | ⬜ 下一步 |

---

## 下一步（D.4 / Phase 2 续）待办清单

1. **DataStore KMP 模块** —— `:common` 中 DataStore 逻辑提取至 `:core:datastore`
2. **（可选）Entity mapper 迁入 `:core:database` 或 `:domain`**
3. **（可选）Koin 4.1.1+ bump**
4. **清理 `KoinQualifiers.SHORT_TIME` / `LONG_TIME` 常量**（已无引用，可下步删除）

**验证命令**：
```bash
./gradlew :core:datastore:compileKotlinIosSimulatorArm64
./gradlew :domain:testDebugUnitTest
./gradlew :app:assembleDebug
```

---

## Commit 建议

```
feat(kmp): migrate ViewModel HTTP downloads to Ktor FileDownloader (D.3c)
```
