# KMP 迁移交接文档 — Step 21 / 阶段 D.1a（2026-08-04）

本文档记录阶段 D 第 1 项（首批）：引入 Koin 基础设施；
在 `:domain` 建立 parser Koin 图；`:common` 的 `SettingsRepository` 去除 `javax.inject`；
Android 侧 Dagger 与 Koin **并行共存**（运行时仍以 Dagger 为主）。

---

## 前置条件检查（D.1 入口）

| 前置项 | 状态 |
|--------|------|
| C.5 config parser 迁入 `:domain` | ✅（Step 20，工作区未 commit） |
| `:domain` parser 图零 `javax.inject` | ✅ |
| `ConfigParserSettingsProvider` 接口 | ✅ |
| `XrayConfigEncoder` 平台抽象 | ✅ |
| iOS target 编译 parser + model | ✅ |

**说明**：阶段 C 与 D.1a 目前均在同一工作区批次中。建议 commit 顺序：
1. 先 commit Step 19–20（阶段 C 收尾）
2. 再 commit 本步（阶段 D.1a）

---

## 改动概要

**目标**：为 Dagger → Koin 渐进迁移打地基；使 `:domain` parser 依赖图可在 **commonMain + Koin** 中声明，
iOS 与未来 Android 共享同一套 module 定义；Android 运行时行为不变。

### 1. 版本目录 + 依赖

| 文件 | 说明 |
|------|------|
| `gradle/libs.versions.toml` | 新增 `koin = "4.0.1"`、`koin-core`、`koin-android` |
| `domain/build.gradle.kts` | `commonMain` 引入 `koin-core` |
| `app/build.gradle.kts` | 引入 `koin-android` |

**原因**：Koin 是 KMP 原生 DI，无注解处理；parser 已在 `:domain/commonMain`，DI 定义也应在此层，
以便 iOS 壳 `initKoin { modules(...) }` 时直接复用。

**Koin 版本：暂用 4.0.1（见下方「版本决策」）**

### 2. `:domain` Koin 模块

| 文件 | 平台 | 说明 |
|------|------|------|
| `di/ParserDiModule.kt` | commonMain | 7 个 parser + `ParserFactory`（镜像 `ParserModule`） |
| `di/AndroidDomainDiModule.kt` | androidMain | `Gson` + `GsonXrayConfigEncoder` |
| `di/IosDomainDiModule.kt` | iosMain | `IosXrayConfigEncoder` 占位 |

**原因**：parser 依赖 `XrayConfigEncoder` 等平台实现，按 KMP 惯例拆 common / android / ios module；
commonMain 只声明 parser 拓扑，平台绑定由各自 `*DomainDiModule` 提供。

### 3. `:common` — 去除 Dagger 绑定

| 文件 | 改动 |
|------|------|
| `SettingsRepository.kt` | 移除 `@Inject` / `@Singleton` / `javax.inject` import |

**原因**：`SettingsRepository` 是 KMP 模块中最后一个带 `javax.inject` 的业务类（qualifier 注解另计）；
去掉后 `:common` 业务代码不再编译依赖 Dagger，iOS/common 测试路径更干净。

### 4. `app` — Dagger 桥接 + Koin 启动

| 文件 | 改动 |
|------|------|
| `GlobalModule.kt` | 新增 `@Provides provideSettingsRepository(...)`（替代原 `@Inject` 构造） |
| `di/KoinModules.kt` | `appPlatformDiModule` + `androidKoinModules()` 聚合 |
| `XrayFAApplication.kt` | `onCreate` 中 `startKoin { androidContext; modules(...) }` |

**原因**：
- Dagger 桥接保证 **所有现有 `@Inject SettingsRepository` 注入点零改动**，逻辑与单例生命周期不变。
- Koin 并行启动，验证 module 图可解析；**尚未**把 ViewModel / Service 切到 Koin 获取依赖。

**仍保留 Dagger**
- `ParserModule` —— 下一步 D.1b 再切 consumer 或删模块
- 全部 ViewModel / Repository / Service `@Inject`

---

## 设计决策

1. **双 DI 共存** —— 迁移计划 2.4 策略：先加 Koin、后逐组件替换、最后删 Dagger；
   本步只落地 module 定义 + 启动，降低一次性切换风险。
2. **`parserDiModule()` 用函数返回 Module** —— 便于平台测试按需 `loadKoinModules`；
   与 `androidDomainDiModule` / `iosDomainDiModule` 组合。
3. **不改动 parser / settings 业务逻辑** —— 仅 DI 装配层变化；Dagger `@Provides` 与 Koin `single` 参数顺序一致。
4. **package 仍为 `com.android.xrayfa.di`** —— domain 与 app 同包名，import 最小化（与阶段 C 一致）。
5. **Koin 暂用 4.0.1，后续随 Kotlin 升级** —— 见下节；不阻塞 D.1 DI 迁移。

### Koin 版本决策（2026-08-04 确认）

| 项 | 说明 |
|----|------|
| **当前锁定** | `koin = "4.0.1"`（`libs.versions.toml`） |
| **原因** | 项目 Kotlin **2.0.21**；Koin 4.1+ 的 iOS klib 由 Kotlin 2.1+ 编译，在 2.0.21 上 `:domain:compileKotlinIosSimulatorArm64` 会 ABI 不兼容 |
| **已验证** | 4.0.1 在 Android + iOS Simulator 均可编译 |
| **曾尝试** | 4.1.1 → iOS 失败（`Incompatible ABI version … produced by '2.1.21' compiler`） |

**后续升级路径**（单独子步，不与本步 D.1 混做）：

| 阶段 | Kotlin | Koin | 备注 |
|------|--------|------|------|
| 当前 | 2.0.21 | **4.0.1** | D.1 DI 迁移继续 |
| 中期 | 2.1.x / 2.2.x | 4.1.1（建议 BOM） | 对齐 `KMP_MIGRATION_PLAN` 原 4.1.0 目标 |
| 长期 | 2.3+ | 4.2.2 | 官方最新稳定线 |

升级 Koin 前须先升 Kotlin 并跑通 iOS target；升级时建议改用 `koin-bom` 统一管理版本。

---

## 验证状态

```bash
./gradlew :common:compileDebugKotlinAndroid :common:testDebugUnitTest   # BUILD SUCCESSFUL
./gradlew :domain:compileDebugKotlinAndroid :domain:testDebugUnitTest     # BUILD SUCCESSFUL
./gradlew :domain:compileKotlinIosSimulatorArm64                        # BUILD SUCCESSFUL
./gradlew :app:compileDebugKotlin                                       # BUILD SUCCESSFUL
```

**待用户确认后再 commit。**

---

## 阶段 D 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| D.1a | Koin 基础设施 + domain parser module + SettingsRepository 去 inject | ✅（待 commit） |
| D.1b | 切换 `ParserFactory` 消费端到 Koin；删除 `ParserModule` | ⬜ |
| D.1c | Repository / ViewModel 等 app 层迁 Koin | ⬜ |
| D.1d | 移除 Dagger（GlobalModule、Component、KSP） | ⬜ |
| D.2 | Room Entity 与 Domain Model 分离 | ⬜ |
| D.3 | XrayConfiguration 全图 `@Serializable` | ⬜ |
| D.4 | Compose Multiplatform / iOS 壳 | ⬜ |

---

## 下一步（D.1b）待办清单

1. **切换 parser 注入源**
   - `XrayCoreManager` / `XrayViewmodel` / `SubscriptionRepository` / `DetailViewmodel`：
     可选 Koin `get()` 或构造注入桥接
   - 删除 `app/.../di/ParserModule.kt`
   - 从 `GlobalModule.includes` 移除 `ParserModule`

2. **Koin 图校验（可选）**
   - `checkModules { androidKoinModules() }` 单元测试

3. **iOS 入口占位**
   - `shared` 或 `iosApp` 中 `initKoin { modules(iosPlatformModule, iosDomainDiModule, parserDiModule()) }`
   - 需 ios 侧 `ConfigParserSettingsProvider` / `GeoIpProvider` stub

**验证命令（阶段 D 每子步）**：
```bash
./gradlew :common:compileDebugKotlinAndroid :common:testDebugUnitTest
./gradlew :domain:compileDebugKotlinAndroid :domain:testDebugUnitTest
./gradlew :domain:compileKotlinIosSimulatorArm64
./gradlew :app:compileDebugKotlin
```

---

## Commit 建议

若阶段 C 尚未 commit：
1. `feat(kmp): complete phase C domain layer (models + parsers)`
2. `feat(kmp): add Koin parser DI graph and de-inject SettingsRepository (D.1a)`

若阶段 C 已 commit：
- `feat(kmp): add Koin parser DI graph and de-inject SettingsRepository (D.1a)`
