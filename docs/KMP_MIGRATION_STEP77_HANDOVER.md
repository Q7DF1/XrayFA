# KMP 迁移交接文档 — Step 77 / 期中 R-7 分层（2026-08-21）

本文档记录期中评审 **R-7** 的第一刀：`:domain` 不得依赖 `:core:*`。把路由枚举、规则 JSON 编解码和 `AppJson` 下沉到 `:common`，DataStore 实现留在 `:core:datastore`。

**前置**：Step 76 已 commit（`9a95c43`）。本步不改运行时行为（磁盘键名、JSON 形状、parser 输出不变）。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `common/.../routing/RoutingEnums.kt` | 从 datastore 迁入 `RoutingMode` / `DomainStrategy` |
| `common/.../routing/Rule.kt` | 从 datastore 迁入 `Rule`、`defaultRouteList`、`defaultRoutes`、`encodeRules` / `decodeRules` |
| `common/.../json/AppJson.kt` | 从 datastore 迁入 `AppJson`、`encodeStringList` / `decodeStringList` |
| `common/src/androidUnitTest/.../RuleSerializationTest.kt` | Gson↔kotlinx 磁盘格式测试随代码迁到 `:common` |
| `common/build.gradle.kts` | 加 serialization 插件 + catalog `kotlinx-serialization-json` |
| `domain/build.gradle.kts` | **删除** `implementation(project(":core:datastore"))` |
| `core/datastore/.../SettingsEnums.kt` | 只留 `Theme` |
| `core/datastore/.../SettingsRepository.kt` | 改 import；注释说明为何不直接用 `RuleObject` |
| `core/datastore/build.gradle.kts` | 去掉 serialization 插件与硬编码 json 依赖 |
| `AbstractConfigParser` / `VmessJson` / `RuleMapping` / UI | import 改为 `common.routing` / `common.json` |
| `AGENT.md` | 分层纪律；`:common:testDebugUnitTest` |
| `docs/KMP_MIGRATION_PLAN.md` | Step 77 从表里拆出 |

### 未改动

- 未新建 `:core:data`（repository 仍住 `:shared`，下一步）
- `Theme` / `SettingsRepository` / DataStore 工厂仍在 `:core:datastore`
- `Rule` 与 `RuleObject` 仍是两份结构相同的模型（避免 `:core:datastore → :domain`）
- 未改磁盘 key 或规则 JSON

---

## 设计决策

1. **类型进 `:common` 而不是 `:domain`** — datastore 已依赖 common；若放 domain，datastore 会反向依赖整包 parser/model。
2. **先断倒置，再拆 `:core:data`** — 模块搬家依赖干净的 domain 边界。
3. **Gson 对比测试跟着 `Rule` 走** — 锁的是 DataStore 磁盘格式，但编解码已不在 datastore 模块。

---

## 验证状态

```bash
./gradlew :common:testDebugUnitTest :domain:testDebugUnitTest :core:datastore:testDebugUnitTest :androidApp:compileDebugKotlin :shared:compileDebugKotlin
# BUILD SUCCESSFUL in 13m 39s（exit 0）
# :common:testDebugUnitTest  20 tests, 0 failures（含迁入的 7 条 Rule JSON）
# :domain:testDebugUnitTest  17 tests, 0 failures
# :core:datastore:testDebugUnitTest  无用例（编解码测试已随代码迁走）
```

- [x] 上述命令通过
- [x] `:domain` 源码不再 `import com.android.xrayfa.datastore`
- [x] `domain/build.gradle.kts` 不再依赖 `:core:datastore`
- [ ] 真机：改路由模式 / 域名策略 / 自定义规则后连接（人工）

---

## 期中后进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| 73 | domain commonTest parser 黄金用例 | ✅ |
| 74 | CI 跑共享模块测试 | ✅ |
| 75 | unpublished submodule SHA + SSH URL | ✅ |
| 76 | R-2 去重 androidApp 副本 | ✅ |
| 77 | 断 `:domain → :core:datastore` | ✅ 本步 |
| 78 | 拆 `:core:data`（repository 迁出 `:shared`） | ⬜ |

---

## 手动验证清单

### Android
- [ ] 设置 → 路由模式 GLOBAL / ROUTE 切换后连接
- [ ] 域名策略三项切换后生成配置（log 中 routing.domainStrategy）
- [ ] 自定义规则保存后再进路由页内容仍在

### iOS
- [ ] 同上（共享 `DefaultSettingsComponent` + parser 路径）

---

## Commit 建议（确认后执行）

```
refactor(kmp): move routing types out of datastore into common
```
