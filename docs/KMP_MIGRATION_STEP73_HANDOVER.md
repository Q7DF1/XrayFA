# KMP 迁移交接文档 — Step 73 / 期中查漏补缺 P0（2026-08-20）

本文档记录期中评审 **B-2 / R-4**：为 `:domain` 建立 `commonTest`，补协议 parser 与配置生成黄金用例。

**前置**：`IosXrayConfigEncoder` 已实现、`println(config)` 已删除（`97c8a51`）。本步不改运行时行为。

**Commit**：`94a88b6` `test(kmp): add domain commonTest parser golden cases`

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `domain/src/commonTest/.../ProtocolParserGoldenTest.kt` | 7 协议 + `socks5://` 别名 + 未知协议：分享链接 → kotlinx outbound JSON |
| `domain/src/commonTest/.../AbstractConfigParserGoldenTest.kt` | VLESS 完整 `parse()` 快照；pre/next `dialerProxy` 链式；HTTP TCP-only 路由规则 |
| `domain/src/commonTest/.../ParserOutboundGoldens.kt` | outbound 黄金 JSON |
| `domain/src/commonTest/.../VlessFullConfigGolden.kt` | 完整配置黄金 JSON（GLOBAL 路由 + 固定 fake settings） |
| `domain/src/commonTest/.../ParserTestFixtures.kt` | Fake settings / GeoIP / `ParserFactory` |
| `domain/src/commonTest/.../ShareLinks.kt` | 稳定分享链接（含 Base64 VMess / SS） |
| `domain/src/commonTest/.../SubscriptionParserTest.kt` | 从 `androidUnitTest` 迁入（Gson 对比测试仍留 Android） |
| `domain/build.gradle.kts` | `kotlinx-serialization-json` 改走 catalog |

### 未改动

- Gson ↔ kotlinx 对比仍在 `androidUnitTest`（Gson 仅 JVM）
- 未新建 `feature/*` / `build-logic`
- 未改 parser 运行时逻辑；黄金 JSON 锁定**当前** kotlinx 输出（含 Hysteria masquerade 默认值）

---

## 设计决策

1. **`commonTest` 而不是再堆 `androidUnitTest`** — 期中 R-4：业务逻辑必须在双平台跑。
2. **结构比较 JSON** — `Json.parseToJsonElement` 比较，避免空白/字段顺序误伤。
3. **完整 `parse()` 只锁一条 VLESS** — 信封（inbounds / routing / dns）与协议 outbound 分开测，避免 7 份巨型快照。
4. **本机 x86_64 JDK 上 `iosSimulatorArm64Test` 会被 skip** — 用 `:domain:iosX64Test` 跑同一套 commonTest。

---

## 验证状态

```bash
./gradlew :domain:testDebugUnitTest    # 17 tests, 0 failures
./gradlew :domain:iosX64Test           # 13 tests, 0 failures（commonTest on Kotlin/Native）
```

- [x] JVM unit tests 全绿
- [x] iOS x64 Native tests 全绿
- [ ] Apple Silicon 上 `:domain:iosSimulatorArm64Test`（本机 Gradle 为 x86_64 JBR，任务 skip）

---

## 期中后进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| 73 | domain commonTest parser 黄金用例 | ✅ committed `94a88b6` |
| 74 | CI 跑共享模块测试 | ✅ Step 74 |
| 75 | 修正 unpublished submodule SHA | ✅ Step 75 |
| 76 | R-2 去重 androidApp 副本 | ⬜ |

---

## 手动验证清单

- [x] 各协议分享链接解析后的 kotlinx JSON 与黄金文件一致
- [x] VLESS `parse()` 含 socks/api/tun inbound 与 GLOBAL 规则
- [x] pre/next 节点 tag 与 `dialerProxy` 正确
- [x] HTTP outbound 附加 TCP-only 路由 `ruleTag`
- [ ] 人工：改 encoder 后黄金用例应失败（安全网是否生效）
