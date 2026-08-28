# KMP 迁移交接文档 — Step 53 / 阶段 E.6e（2026-08-07）

本文档记录阶段 E 第 6e 项：在 `:shared` 引入 Decompose 根导航骨架
（Config / Home / Settings 三 Tab；Home 接现有 `SharedHomeSection`，Config/Settings 占位）；
并补充 **Intel Mac 模拟器 `iosX64` 目标**。

**前置**：Step 52 / E.6d 已 commit（`6662478`）。

---

## 前置条件检查（E.6e 入口）

| 前置项 | 状态 |
|--------|------|
| E.6d 共享 Home UI 切片 | ✅ committed |
| `:shared` Compose + iOS Koin + `SharedHomeSection` | ✅ |
| Decompose 依赖 / iOS framework export | ✅（本步新增） |
| Android `:androidApp` Navigation3 替换 | ⬜ 留 E.6f+ |

**结论**：E.6e 前置已全部满足。

---

## 改动概要

### E.6e — Decompose 导航

| 文件 | 说明 |
|------|------|
| `gradle/libs.versions.toml` | `decompose` 3.2.2、`essenty` 2.3.0、serialization |
| `shared/build.gradle.kts` | Decompose + serialization；iOS framework export |
| `shared/.../navigation/*` | `RootComponent` / `ChildPages` / `RootTab` |
| `shared/.../ui/RootContent.kt` | Scaffold + 底栏 + 页面切换 |
| `shared/.../ui/AppShell.kt` | 接收 `RootComponent` |
| `shared/.../MainViewController.kt` | 生命周期 `resume`/`destroy` |

### E.6e-b — Intel Mac 模拟器（iosX64）

| 文件 | 说明 |
|------|------|
| 全部 KMP 模块 `build.gradle.kts` | 添加 `iosX64()` |
| `core/native-bridge/build.gradle.kts` | `iosX64` → `ios-arm64_x86_64-simulator` slice |
| `iosApp/README.md` | 文档化双 simulator 目标 |

### 未改动

- `:androidApp` `HomeScreen.kt` / Navigation3
- `SharedHomeSection` VPN/节点逻辑

---

## 设计决策

1. **Decompose `ChildPages`** —— 对齐 Android 底栏 Tab 模式。
2. **`iosX64` 复用 universal xcframework slice** —— 无需重编 LibXrayLite / HevSocks5Tunnel。
3. **不强制 `-arch arm64`** —— Xcode 按宿主 CPU 自动选架构。

---

## 验证状态

```bash
./gradlew :shared:linkDebugFrameworkIosX64
./gradlew :androidApp:assembleDebug
# BUILD SUCCESSFUL

xcodebuild ... ARCHS=x86_64 CODE_SIGNING_ALLOWED=NO build  # Intel: BUILD SUCCEEDED
xcrun simctl install booted .../XrayFA.app && xcrun simctl launch booted com.android.xrayfa.ios  # OK
```

---

## 阶段 E 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| E.6e | Decompose 根导航 + iosX64 | ✅（本步，待 commit） |
| E.6f | Android 嵌入共享 Home UI 切片 | ⬜ 下一步 |

---

## Commit 建议

```
feat(kmp): add Decompose root nav skeleton and iosX64 simulator target (E.6e)
```

---
