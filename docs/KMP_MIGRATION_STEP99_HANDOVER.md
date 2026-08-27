# KMP 迁移交接文档 — Step 99 / iOS 主题跟随设置 `darkMode`（2026-08-27）

本文档记录：共享 `AppShell`（iOS 入口）的 `XrayTheme` 不再只跟系统深色走，改为读取 DataStore `darkMode`，语义与 Android `XrayBaseActivity` 一致。未做延迟测试 / GeoIP 桩，未做 Phase C Agent。

**前置**：Step 98 真机手测已记入 `KMP_MIGRATION_STEP98_HANDOVER.md`（`d347e6b`）。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `Theme.resolvesToDark` + `ThemeResolvesToDarkTest` | Light=浅、Dark=深、Auto=跟随系统；未知 code → Auto |
| `AppShell` | `applyTheme==true`（iOS）时 `koinInject<SettingsRepository>()` + `XrayTheme(darkTheme=…)` |
| `XrayBaseActivity` | 同一 helper，去掉手写 `when (code)` |

### 未改动

- iOS 系统 chrome（status bar / `overrideUserInterfaceStyle`）仍可能跟系统走；本步只改 Compose Material 配色
- `measureOutboundDelay` 仍恒 `-1L`
- `IosGeoIpProvider` 仍恒 `""`
- Android `AndroidAppShell` 仍不走 `AppShell`（自己用 `V2rayForAndroidUITheme`）

---

## 验证状态

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home"
./gradlew :core:datastore:testDebugUnitTest --tests com.android.xrayfa.datastore.ThemeResolvesToDarkTest
# ThemeResolvesToDarkTest 4 tests, 0 failures
./gradlew :shared:compileDebugKotlin :androidApp:compileDebugKotlin :shared:compileKotlinIosSimulatorArm64
# BUILD SUCCESSFUL
```

- [x] Light / Dark / Auto / unknown code 单测
- [ ] iOS 模拟器：设置切 Dark / Light / Auto，共享 UI 配色跟着变（本步未开模拟器）

---

## 下一步

1. iOS `measureOutboundDelay`（目前 K/N cinterop 出参不可用，需 NE `measureDelay` 或 shim）
2. iOS GeoIP（节点国家标记）
3. **不要**做 Agent Phase C / C1，除非产品明确要求
