# KMP 迁移交接文档 — Step 119 / iOS BugReport（2026-09-04）

本文档记录：iOS 问题反馈不再弹「开发中」，改为与 Android 同一套共享表单，提交后打开 GitHub New Issue。未做 Agent Phase C / C1。

**前置**：Step 118。活清单：`docs/KMP_MIGRATION_STATUS.md`。桩：`docs/IOS_STUBS.md`（`BugReport` 已移出未实现表）。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `common/.../BugReportIssueComposer.kt` + `BugReportIssueComposerTest` | 组装 GitHub issue URL（title/body/labels=bug + 环境行） |
| `shared/.../SharedBugReportDialog.kt` | 原 Android `BugReportDialog` 迁入共享；`SharedBugReport` 走 `AppMetadataProvider.openUrl` |
| `AppMetadataProvider` | 增加 `getOsName` / `getOsVersion` / `getDeviceModel` |
| `IosPlatformRootHooks` / `AndroidPlatformRootHooks` | 都接 `SharedBugReport` |
| 删除 | `androidApp/.../BugReportDialog.kt` |

### 未改动

- 分应用 / geo 导入 / HexTun / 相册扫码 / NE 日志仍为「开发中」
- Agent Phase C / C1
- `XrayViewmodel.submitBugReport` 仍在（主路径已不再调用）

---

## 设计决策

1. **共享表单，双端同一 hook** — R-1/R-2：不再保留 Android 平行 Dialog。
2. **组 URL 进 `:common`** — 不依赖 `:domain` 的 `BugReportData`；单测锁 title 编码和环境字段。
3. **打开浏览器走已有 `AppMetadataProvider`** — iOS `UIApplication.openURL`，Android `ACTION_VIEW`。

---

## 验证状态

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home"
./gradlew :common:testDebugUnitTest --tests com.android.xrayfa.common.utils.BugReportIssueComposerTest
./gradlew :androidApp:compileDebugKotlin :androidApp:testDebugUnitTest \
  --tests com.android.xrayfa.ui.AgentScreenRootTabTest
./gradlew :shared:compileKotlinIosX64
```

- [x] `BugReportIssueComposerTest` 1 test, 0 failures；`:androidApp:compileDebugKotlin` + `AgentScreenRootTabTest` 3 tests, 0 failures；`:shared:compileKotlinIosX64` BUILD SUCCESSFUL
- [ ] 模拟器/真机：Config 菜单问题反馈出表单；提交打开 GitHub；取消关闭

---

## 下一步

1. Step 112：iOS geoip.dat / geosite.dat 文件导入（HexTun 继续开发中或注明 NE 固定 hev）
2. **不要**做 Agent Phase C / C1
