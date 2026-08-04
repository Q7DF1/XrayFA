# KMP 迁移交接文档 — Step 14 / 阶段 C.1（2026-08-04）

本文档记录阶段 C 第 1 项：`:common` 模块转为 Kotlin Multiplatform，配置
`androidTarget` + `iosArm64` + `iosSimulatorArm64`，并完成源码集拆分。

验证状态：
- `./gradlew :common:compileDebugKotlinAndroid` **BUILD SUCCESSFUL**
- `./gradlew :common:testDebugUnitTest` **全部通过**
- `./gradlew :common:compileKotlinIosSimulatorArm64` **BUILD SUCCESSFUL**
- `./gradlew :common:compileKotlinIosArm64` **BUILD SUCCESSFUL**
- `./gradlew :app:compileDebugKotlin` **BUILD SUCCESSFUL**

---

## 前置条件检查（阶段 B → C 入口）

| 检查项 | 状态 |
|--------|------|
| 阶段 B Step 5–13 全部完成 | ✅ |
| `:common` 无 `filesDir` / `@IntDef` / parser `javax.inject` | ✅ |
| `XrayCore` / `GeoIpProvider` / `XrayAssetPaths` 接口就绪 | ✅ |
| `UrlCodec` / `Base64Compat` 已 KMP 化（Step 3–4） | ✅ |
| `Logger` 接口抽象（Step 11） | ✅ |
| 无 build-logic 约定插件（Phase 0 未做，不阻塞 C.1） | ℹ️ 后续可选 |

**结论**：阶段 B 前置已全部满足，C.1 可以安全启动。

---

## 改动概要

**目标**：`:common` 从 Android Library 升级为 KMP 模块；纯 Kotlin 代码进入
`commonMain`，Android 绑定代码进入 `androidMain`，iOS 首次可编译（空壳 actual）。

**跨平台意义**：这是首个双平台编译的模块，后续 C.2–C.4 的序列化 / DataStore /
domain 迁入都依赖此结构。

### 改动清单

**Gradle / 构建**
- `gradle/libs.versions.toml`：新增 `kotlin-multiplatform` 插件
- `build.gradle.kts`（根）：apply KMP plugin
- `common/build.gradle.kts`：KMP + androidTarget + iosArm64/iosSimulatorArm64
- `gradle.properties`：`kotlin.mpp.androidGradlePluginCompatibility.nowarn=true`
  （AGP 8.10 > KGP 官方测试上限 8.5，仅抑制警告，不影响编译）

**源码集拆分**

| 源集 | 内容 | 原因 |
|------|------|------|
| `commonMain` | 接口、UrlCodec、Base64Compat、SocksConfigGenerator、Constant | 纯 Kotlin，双平台共享 |
| `androidMain` | SettingsRepository、FileUtils、Jvm*、Dagger qualifiers | DataStore/Gson/Dagger/JVM IO |
| `iosMain` | PlatformCrypto actual（SecRandom + digest 空壳） | iOS 平台实现 |
| `androidUnitTest` | UrlCodecTest、CryptoUtilsTest | 依赖 JDK 参考实现 |

**expect/actual（新增）**
- `PlatformCrypto.kt`（commonMain）：`defaultCryptoRandom` / `defaultDigestCalculator`
- `PlatformCrypto.android.kt`：委托 `JvmCryptoRandom` / `JvmDigestCalculator`（行为不变）
- `PlatformCrypto.ios.kt`：`SecRandomCopyBytes` 真随机；digest 为编译空壳（返回空数组）

**Dagger qualifier 迁移**
- 5 个 `.java` → `Qualifiers.kt`（Kotlin `annotation class`）
- 原因：KMP `androidMain/java` 不被 Java 编译任务拾取，导致 tun2socks KSP 找不到
  `@Application`；Kotlin 注解等效，RUNTIME retention 不变

**KMP 兼容修复**
- `toHexLowercase()`：`"%02x".format` → 手动 hex 表（JVM `String.format` 非 KMP）

**未改动（刻意保留在 androidMain，C.2/C.3 处理）**
- `SettingsRepository` + Gson + DataStore + `@Inject`
- `FileUtils`（`java.io.File` / `InputStream`）

---

## 设计决策

1. **不新建 `:core:common` 模块** —— 在现有 `:common` 上直接 KMP 化，减少
   模块引用变更面；`:app` / `:tun2socks` 仍 `implementation(project(":common"))`。
2. **Android 逻辑零变更** —— crypto/digest 仍走 `java.security`；Repository
   读写路径、默认值、JSON 序列化均未修改。
3. **iOS digest 暂为空壳** —— C.1 目标是验证工具链；CommonCrypto cinterop
   留 C.2 或独立子步，避免 C.1 diff 过大。
4. **qualifier 改 Kotlin 而非保留 Java** —— KMP android 源集对 Java 支持有限；
   注解语义与 Dagger 完全兼容。

---

## 阶段 C 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| C.1 | `:common` 转 KMP multiplatform | ✅ |
| C.2 | Gson → kotlinx.serialization（model 子集试点） | ⬜ |
| C.3 | DataStore KMP | ⬜ |
| C.4 | parser + model 物理迁入 `:domain` | ⬜ |

---

## 下一步（C.2）待办清单

### C.2 Gson → kotlinx.serialization（model 子集试点）

**目标**：先迁 1–2 个无 Android 依赖的 model（如 `Rule` 或 settings 相关 DTO），
验证 JSON 往返与 Gson 输出兼容。

**关键文件**：
- `app/src/main/java/com/android/xrayfa/model/`（选试点类）
- `common/.../SettingsRepository.kt` 内 Gson 用法（`Rule`、`allow_packages`）

**注意**：
- `@SerializedName` → `@SerialName`；添加 `@Serializable`
- 编写 commonTest 往返测试，对比 Gson 与新 Json 输出
- `SettingsRepository` 仍在 androidMain，可先在 androidUnitTest 做兼容测试

**阶段 C 验证命令（每子步）**：
```bash
./gradlew :common:compileDebugKotlinAndroid :common:testDebugUnitTest
./gradlew :common:compileKotlinIosSimulatorArm64
./gradlew :app:compileDebugKotlin
```

---

## 阶段 D 预览（阶段 C 不做）

- Dagger → Koin
- Compose Multiplatform UI
- iOS VPN Network Extension

---

## 目录结构（C.1 后）

```
common/
├── build.gradle.kts          # KMP + android library
└── src/
    ├── commonMain/kotlin/com/android/xrayfa/common/
    │   ├── Constant.kt
    │   ├── core/             # XrayCore, GeoIpProvider, XrayAssetPaths, ...
    │   └── utils/            # UrlCodec, Base64Compat, PlatformCrypto (expect)
    ├── androidMain/kotlin/com/android/xrayfa/common/
    │   ├── repository/       # SettingsRepository (+ Gson/DataStore)
    │   ├── utils/            # FileUtils, Jvm*, PlatformCrypto (actual)
    │   └── di/qualifier/     # Qualifiers.kt
    ├── iosMain/kotlin/.../utils/
    │   └── PlatformCrypto.ios.kt
    └── androidUnitTest/kotlin/...
```
