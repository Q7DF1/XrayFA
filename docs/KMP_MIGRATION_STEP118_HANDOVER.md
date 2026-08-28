# KMP 迁移交接文档 — Step 118 / Phase 9 Android 发版收尾（2026-08-28）

本文档记录 Phase 9：以发布 Android 1.7.0 为 KMP 移植收尾门禁。iOS 只保基本功能。未做 Agent Phase C / C1。未改三星 Live Update（OEM 开发者选项）。

**前置**：Step 111。活清单：`docs/KMP_MIGRATION_STATUS.md`。backlog：`docs/KMP_POST_MIGRATION.md`。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `docs/KMP_MIGRATION_STATUS.md` | Phase 9 活清单；112–116 标移植后；P0 真机表 |
| `docs/KMP_POST_MIGRATION.md` | 发版后 backlog |
| `docs/KMP_MIGRATION_PLAN.md` | Phase 9 小节 |
| `AGENT.md` | 移植已收尾；版本 1.7.0 / 34 |
| `README.md` / `README_zh-CN.md` | 此版本起 UI 跑共享 `RootContent` |
| `gradle.properties` | `1.7.0` / `34` |
| `androidApp/proguard-rules.pro` | Koin / Decompose / kotlinx.serialization keep |
| `androidApp/build.gradle.kts` | lint 关闭 `Instantiatable`（`XrayAppCompatFactory` 构造 Activity/Service） |
| `HomeScreen.kt` Compact/Expanded | Home 测速改 `HomeComponent.onTestDelay` + `DelayProbe` |
| 删除 | 无主路径 `XrayFAContainer`、孤立 Config/Subscription/Edit/Route 屏、`HomeScreenV2`、未用 Navigation3 辅助 |

### 未改动

- `NotificationHelper` Live Update 路径
- Agent Phase C / C1
- iOS hooks 112–116

---

## 设计决策

1. **先发版，页面不打磨** — 搜索条 / 转场 / 滚动藏栏停放。
2. **Home 测速一条路径** — Android hook 的 `NodeCard` 不再走 `XrayViewmodel.measureDelay`。
3. **死壳删除** — 主路径已是 `RootContent`；`LogcatActionButton` 挪到 `LogcatScreen.kt`。设置控件留在 `SettingsScreen.kt`。

---

## 验证状态

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home"
./gradlew :androidApp:compileDebugKotlin :androidApp:testDebugUnitTest \
  --tests com.android.xrayfa.ui.AgentScreenRootTabTest
./gradlew :common:testDebugUnitTest :domain:testDebugUnitTest
./gradlew :androidApp:assembleRelease
```

- [x] `:androidApp:compileDebugKotlin` + `AgentScreenRootTabTest` + `:common` / `:domain` `testDebugUnitTest` BUILD SUCCESSFUL
- [x] `:androidApp:assembleRelease` BUILD SUCCESSFUL（本机无 jks，unsigned；lint `Instantiatable` 已关）
- [ ] 真机 P0：见 `KMP_MIGRATION_STATUS.md` Phase 9 表（需人工；本步不打 tag / 不 push）

---

## 下一步

1. 真机勾完 STATUS P0 后打 `v1.7.0`（本步不 push / 不打 tag）
2. iOS 按 `KMP_POST_MIGRATION.md` 慢慢做
3. **不要**做 Agent Phase C / C1
