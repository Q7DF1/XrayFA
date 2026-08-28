# KMP 迁移交接文档 — Step 104 / iOS CommonCrypto digest（2026-08-27）

本文档记录：`IosDigestCalculatorStub`（`finalize()` 空数组）换成 CommonCrypto。`calculateBytesHash` 在 iOS 上与 JVM 同一套 SHA-256 / MD5 向量。Android 仍走 `MessageDigest`。未做 FileDownloader SOCKS，未做 Phase C Agent。

**前置**：Step 103（`24c684a`）。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `PlatformCrypto.ios.kt` | `CC_SHA256` / `CC_MD5`；未知算法 `error()`；流式先缓冲再一次性 hash |
| `DigestCalculatorTest` | 空串 / `abc` 官方向量 + 分块与一次性一致 |
| `loadGeoIp2CountryTestMmdb` expect/actual | commonTest 去掉 `::class.java`，`:common:iosX64Test` 才能编过 |

### 未改动

- Android `JvmDigestCalculator` / `FileUtils.calculateFileHash`
- iOS FileDownloader 仍直连（不走 SOCKS）
- Agent Phase C / C1

---

## 设计决策

1. **只实现 SHA-256 与 MD5** — 与现有 `CryptoUtilsTest` / `calculateBytesHash` 默认一致；其它算法名抛错（对齐 JVM `NoSuchAlgorithmException` 的「失败」语义）。
2. **缓冲后一次性 CC_*** — iOS 当前没有大文件流式 hash；避免 `CC_SHA256_CTX` cinterop。
3. **MMDB fixture 仍 JVM-only** — Native 无 Java resources；iOS 上 lookup 测试直接 return。

---

## 验证状态

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home"
./gradlew :common:iosX64Test --tests com.android.xrayfa.common.utils.DigestCalculatorTest
# 4 tests, 0 failures（本机 x86_64；`iosSimulatorArm64Test` 会被 skip）
./gradlew :common:testDebugUnitTest --tests com.android.xrayfa.common.utils.DigestCalculatorTest
# 4 tests, 0 failures
./gradlew :common:compileKotlinIosSimulatorArm64
# BUILD SUCCESSFUL
```

- [x] SHA-256 / MD5 空串与 `abc` 向量
- [x] 分块 update 与一次性一致
- [x] 未知算法抛错（stub 不会抛）
- [ ] 真机业务路径调用 `calculateBytesHash`（当前 iOS 产品代码几乎不调 digest）

---

## 下一步

1. 可选：iOS FileDownloader 走 SOCKS（NE 内 SOCKS 对宿主 App 的 `127.0.0.1` 不一定可达，需先查 PacketTunnel） — ❌ 不可达，Step 105 关闭 iOS 下载
2. 可选：geoip.dat / geosite.dat 迁入共享设置
3. **不要**做 Agent Phase C / C1，除非产品明确要求
