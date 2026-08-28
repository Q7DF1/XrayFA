# KMP 迁移交接文档 — Step 110 / Phase 8 活清单 + IosPlatformRootHooks（2026-08-28）

本文档记录 Phase 8 开场：把完成/待办收成活清单，登记 iOS 桩，并给 iOS 独立的 `IosPlatformRootHooks`（行为与 Step 109 默认 hooks 一致）。**Android 运行时零行为变化。** 未做 Agent Phase C / C1。

**前置**：Step 109。活清单：`docs/KMP_MIGRATION_STATUS.md`。桩：`docs/IOS_STUBS.md`。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `docs/KMP_MIGRATION_STATUS.md` | Phase 8 完成/待办 + 硬约束 |
| `docs/IOS_STUBS.md` | R-3：故意桩 / 用户可见缺口 / 已不再是桩 |
| `docs/KMP_MIGRATION_PLAN.md` | Step 110–117 表 + Phase 8 小节 |
| `AGENT.md` | §1/§6/`§12` 链到 STATUS、STUBS、STEP110 |
| `PlatformRootHooks.kt` | `InDevelopmentDialog` / 网络 extras 抽成 `internal`；默认仍是 CompositionLocal fallback |
| `IosPlatformRootHooks.kt` | iosMain 显式 hooks（开发中槽位与 109 相同） |
| `MainViewController.kt` | `CompositionLocalProvider(… provides IosPlatformRootHooks)` |

### 未改动

- `AndroidPlatformRootHooks`、`AndroidAppShell`、`RootContent` overlay 栈
- Agent Phase C / C1
- iOS VPN / 相册扫码 / 分应用真实实现（见 STUBS）

---

## 设计决策

1. **不再换壳** — Android 继续 `RootContent` + `AndroidPlatformRootHooks`。
2. **iOS 只填 hooks** — 后续每步改 `IosPlatformRootHooks` 的一个槽，不把实现塞进 `commonMain` 挤掉 Android。
3. **默认 hooks 留下** — 未注入 CompositionLocal 时（预览/测试）仍走 `DefaultPlatformRootHooks`。

---

## 验证状态

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home"
./gradlew :androidApp:compileDebugKotlin :androidApp:testDebugUnitTest \
  --tests com.android.xrayfa.ui.AgentScreenRootTabTest
./gradlew :shared:compileKotlinIosX64
```

- [x] 上列命令（与 Step 111 同一批）：`:androidApp:compileDebugKotlin` + `AgentScreenRootTabTest` + `:shared:compileKotlinIosX64` 通过
- [ ] 真机：iOS 设置 geo / HexTun / 分应用仍为「开发中」

---

## 下一步

1. Step 111：iOS `ShareNode` 二维码 + 剪贴板（不碰 `XrayViewmodel`）
2. **不要**做 Agent Phase C / C1
3. **不要**删除 `XrayFAContainer`，直到 Step 109 Android 真机清单勾完
