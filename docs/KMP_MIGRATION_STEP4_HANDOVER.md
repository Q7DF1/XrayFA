# KMP 迁移交接文档 — Step 4（2026-08-03）

本文档记录阶段 A 第 4 项（收尾）：消除 `common` 模块共享逻辑中的 `java.security.*` 依赖，
并为 `java.io.File` 文件哈希保留平台边界。

验证状态：
- `./gradlew :common:compileDebugKotlin` **BUILD SUCCESSFUL**
- `./gradlew :common:testDebugUnitTest` **全部通过**（含 UrlCodec fuzz + 新增 CryptoUtilsTest）
- `./gradlew :app:compileDebugKotlin` 在本环境因缺少 `libv2ray` 原生库失败，与本次改动无关

---

## 改动概要

阶段 A 收尾：将 `SocksConfigGenerator` 与 `FileUtils` 中的 JVM 加密 API 收敛到
**平台抽象 + 隔离实现** 模式，与 Step 2/3 的 Base64Compat / UrlCodec 策略一致——
共享逻辑文件零 `java.security.*` import，JVM 实现集中在 `Jvm*` 文件中，
后续 `:common` 转 KMP 时可直接变为 `expect/actual`。

### 改动清单

**新增（平台抽象层，无 java.* import）**
- `common/.../utils/CryptoRandom.kt`：`nextBytes` / `nextInt(n)` 接口
- `common/.../utils/DigestCalculator.kt`：`StreamingDigest` + `createDigest` 接口，
  以及可跨平台复用的 `calculateBytesHash`
- `common/.../utils/JvmCryptoRandom.kt`：`SecureRandom` 实现（**唯一** SecureRandom 引用点）
- `common/.../utils/JvmDigestCalculator.kt`：`MessageDigest` 实现（**唯一** MessageDigest 引用点）
- `common/src/test/.../utils/CryptoUtilsTest.kt`：JDK 对照单测（SHA-256/MD5、流式分块、端口范围）

**修改**
- `SocksConfigGenerator.kt`：移除 `import java.security.SecureRandom` 与 `import java.util.Base64`，
  改注入 `CryptoRandom`，Base64 编码改走 `Base64Compat.encodeUrlSafeNoPadding`（与 Step 2 语义等价）
- `FileUtils.kt`：移除 `import java.security.MessageDigest`；新增 `calculateStreamHash(InputStream)`；
  `calculateFileHash(File)` 委托给流式哈希（逻辑与分块读取 8192 字节完全一致）

**未动（属后续阶段）**
- `FileUtils.kt` 中的 `java.io.File` / `FileInputStream` / `InputStream` —— 调用方
  `SettingsViewmodel` 仍传 Android `File`，整体文件将迁入 `androidMain` source set
- `common/.../di/qualifier/*.java` —— Dagger 注解，属 Dagger→Koin 阶段
- `di/NetworkModule.kt`、`SubscriptionScreen.kt`、`Device.kt` 等 app 层 JVM API —— 见 Step 3 未动清单

**grep 复查（Step 4 第 3 项）**
- `common` 模块无 `java.util.UUID` / `java.time` / `java.text` 残留 ✅

---

## 设计决策

1. **不用 `kotlin.random.Random` 替代 SecureRandom** —— 安全语义降级，必须保留 CSPRNG。
2. **Digest 采用 StreamingDigest 接口** —— 保留原 8192 字节分块读取，避免大文件全量读入内存。
3. **hex 格式保持 `%02x` 小写** —— 与 `MessageDigest` + `joinToString` 原实现一致。
4. **KMP 迁移路径**：`CryptoRandom` / `DigestCalculator` → `expect` 声明；
   `JvmCryptoRandom` / `JvmDigestCalculator` → `androidMain` actual；
   iOS 侧分别用 `SecRandomCopyBytes` / `CommonCrypto`。

---

## 阶段 A 完成状态

| 步骤 | 内容 | 状态 |
|------|------|------|
| Step 1 | 清除纯逻辑代码中的 Android import | ✅ |
| Step 2 | Base64 → Base64Compat | ✅ |
| Step 3 | java.net URI/URLDecoder → UrlCodec | ✅ |
| Step 4 | SecureRandom/MessageDigest → 平台抽象 | ✅ |

---

## 下一步（Step 5 / 阶段 B 第 1 项）待办清单

进入阶段 B —— **抽平台接口**，按依赖阻塞关系，建议先做 **XrayCore 接口化**：

### 5.1 `XrayCoreManager` → `XrayCore` 接口

**目标**：全项目仅 2 个文件直接触碰 `libv2ray`（`XrayCoreManager.kt`、`XrayViewmodel.kt:537`），
将其收敛到单一 Android 实现类，common/domain 层只依赖接口。

**建议步骤**：
1. 在 `common` 或新建 `core/native-bridge` 模块定义 `XrayCore` 接口（`commonMain` 可编译的子集）：
   - `suspend fun start(startOptions: StartOptions, tunFd: Int?): Boolean`
   - `fun stop()`
   - `fun measureDelay(...): Long`（按需）
   - `fun queryStats(...): Long`（按需）
   - `val trafficFlow: SharedFlow<Pair<Double, Double>>`
2. `XrayCoreManager` 实现该接口，保留全部 `libv2ray` 调用
3. `XrayViewmodel`、`XrayBaseService`、`GlobalModule` 等注入点改为 `XrayCore` 接口类型
4. `XrayViewmodel.kt:537` 的直接 `Libv2ray` 调用移入 `XrayCoreManager`

**注意点**：
- 接口方法签名须与现有调用一一对应，不改启动/停止/测延迟时序
- `TrafficDetector` 已是独立接口，可让 `XrayCore` extend 或组合
- iOS actual 需等 gomobile xcframework 验证通过后再做（本步只做接口 + Android impl）

**涉及文件（预估）**：
- `app/.../core/XrayCoreManager.kt`（实现接口）
- `app/.../viewmodel/XrayViewmodel.kt`（移除 libv2ray import）
- `app/.../core/XrayBaseService.kt`（注入类型改接口）
- `app/.../di/GlobalModule.kt`（bind 声明）
- 新增 `common/.../core/XrayCore.kt` 或独立模块

### 5.2 后续阶段 B 项（Step 6–7，本步不做）

| 步骤 | 内容 | 关键文件 |
|------|------|----------|
| Step 6 | `Device.getCountryISOFromIp` → `GeoIpProvider` 接口；parser 消除对 `XrayAppCompatFactory.xrayPATH` 全局状态的依赖 | `Device.kt`、6 个 parser、`XrayAppCompatFactory.kt` |
| Step 7 | `SettingsRepository` DataStore 创建下沉平台层 | `common/.../SettingsRepository.kt`、`XrayFAApplication.kt` |

### 5.3 阶段 C/D 预览（更远）

- Gson → kotlinx.serialization（`model/` ~24 文件，parser 迁入 domain 的主要障碍）
- Dagger → Koin；Room/DataStore/OkHttp → KMP 版本
- 建 `core:model` / `core:domain` KMP 模块，`:common` 转 multiplatform plugin

**Step 5 验证命令**：
```bash
./gradlew :common:compileDebugKotlin :common:testDebugUnitTest
./gradlew :app:compileDebugKotlin   # 需 libv2ray 原生库就绪
```
