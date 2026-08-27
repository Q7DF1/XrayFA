# KMP 迁移交接文档 — Step 101 / iOS GeoIP（common MMDB + 国旗 emoji）（2026-08-27）

本文档记录：`IosGeoIpProvider` 不再恒回 `""`。MaxMind `geoip2` JAR 是 JVM-only，无法上 iOS；在 `:common` 实现 GeoLite2/GeoIP2 Country MMDB 读取 + ISO→国旗 emoji，双端共用。Android 去掉 `com.maxmind.geoip2`。未做 iOS GeoLite 下载 UI，未接测速按钮，未做 Phase C Agent。

**前置**：Step 100（`57920ab`）。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `CountryFlagEmoji` + `GeoIpCountryDisplay` | 2 字母 ISO → 区域指示符国旗；非法长度 → `❓`；lookup `null` → `""` |
| `MmdbCountryLookup` | 读 MMDB 字节 + IPv4/IPv6 字面量，返回 `country.iso_code` |
| `CountryFlagEmojiTest` / `GeoIpCountryDisplayTest` / `MmdbCountryLookupTest` | 10 个 commonTest；fixture 为 MaxMind-DB Apache-2.0 的 `GeoIP2-Country-Test.mmdb` |
| `IosGeoIpProvider` | App Group `geoLiteDatabasePath` 读文件；域名走 IPv4 `getaddrinfo` |
| `AndroidGeoIpProvider` | 同一 lookup；域名走 `InetAddress.getByName` |
| `androidApp` / catalog | 删除 `libs.maxmind.geoip2` |

### 未改动

- 共享设置仍无 GeoLite 下载 / `geoLiteInstall` 开关（Android extras 才有）。parser 只在 `geoLiteInstall==true` 时调 provider；iOS 默认关，文件也不在时国旗仍为空
- iOS 域名解析只取 A 记录（IPv6 **字面量**仍可查）
- 共享 Home/Config **仍不传** `onTest`
- Agent Phase C / C1

---

## 设计决策

1. **不编 libmaxminddb** — Country DB 只需 map/string/pointer 子集；纯 Kotlin 可在 JVM 用官方测试库对拍。
2. **显示语义对齐 Android** — 查不到 / 坏文件 / 非法 IP → `""`；查到但无 2 字母码 → `❓`。
3. **测试 fixture 放 `androidUnitTest/resources`** — KMP `commonTest` 的 `::class.java` 只在 Android 单元测试 classpath 上能读到资源；CI 跑的是 `:common:testDebugUnitTest`。

---

## 验证状态

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home"
./gradlew :common:testDebugUnitTest \
  --tests com.android.xrayfa.common.core.CountryFlagEmojiTest \
  --tests com.android.xrayfa.common.core.GeoIpCountryDisplayTest \
  --tests com.android.xrayfa.common.core.MmdbCountryLookupTest
# 10 tests, 0 failures
./gradlew :androidApp:compileDebugKotlin :shared:compileKotlinIosSimulatorArm64
# BUILD SUCCESSFUL
```

- [x] US/GB/JP 国旗；非法长度 `❓`；lookup `null` → `""`
- [x] 测试库 `81.2.69.160` → GB / 🇬🇧；`74.209.24.0` → US；`2001:218::` → JP；未知/非法 IP → null
- [x] iOS simulator Kotlin 编译（`IosGeoIpProvider` 读文件 + getaddrinfo）
- [ ] 真机/模拟器：放入 `GeoLite2-Country.mmdb` 且打开 `geoLiteInstall` 后节点行出旗（本步无下载 UI，未手测）

---

## 下一步

1. iOS / 共享设置：GeoLite 下载 + `geoLiteInstall`（否则 lookup 写好了也看不到旗） — ✅ Step 102
2. 共享 Home/Config 接 `onTest` + iOS `XrayCore` 委托 Step 100 shim
3. **不要**做 Agent Phase C / C1，除非产品明确要求
