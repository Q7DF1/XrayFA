# KMP 迁移交接文档 — Step 44 / 阶段 E.5a（2026-08-07）

本文档记录阶段 E 第 5a 项：新增 `:shared` KMP 聚合模块，导出 `XrayFAShared.framework` 供 iOS 壳接入；
**不创建 Xcode 工程、不改动 Android 运行时**（留 E.5b）。

**前置**：Step 43 / E.4 已 commit（`0a5e53b`）。

---

## 前置条件检查（E.5a 入口）

| 前置项 | 状态 |
|--------|------|
| E.4 xcframework 构建脚本可用 | ✅ |
| `:domain` / `:core:*` / `:platform:vpn` iOS 编译通过 | ✅ |
| `:app` Android 构建不受影响 | ✅ |

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `shared/build.gradle.kts` | KMP 模块；`api()` 聚合 domain + core + platform:vpn + common；iOS 导出 static `XrayFAShared.framework` |
| `shared/src/commonMain/.../XrayFAShared.kt` | 版本标记 + `VpnState` 暴露 + `VpnControllerProvider` typealias |
| `shared/src/iosMain/.../IosSharedInit.kt` | iOS 占位（E.5b+ 接 Compose / Koin） |
| `settings.gradle.kts` | `include(":shared")` |
| `app-ios/README.md` | iOS 壳集成清单与目录规划 |

### 未改动

- `:app` 未依赖 `:shared`（Android 仍走现有模块图）
- `:core:native-bridge` iOS stub（no-op）
- `IosVpnController` stub
- 无 Xcode project

---

## 设计决策与原因

1. **独立 `:shared` 而非让 `:app` 双端** —— Android `:app` 已是完整 Compose 应用；iOS 需要 static framework 导出，单独模块更清晰。
2. **`api()` 而非 `implementation()`** —— Swift/ObjC 通过 framework 头文件需可见 domain/vpn 类型；`api` 保证 transitive export。
3. **`isStatic = true`** —— 与 `LibXrayLite.xcframework` 及 NE 内存约束更兼容；后续可按 profiling 调整。
4. **`app-ios/` 仅 README** —— E.5a 验证 Gradle 导出链；Xcode 工程在 E.5b 创建。

---

## 验证状态

```bash
./gradlew :shared:compileKotlinIosSimulatorArm64 \
          :shared:linkDebugFrameworkIosSimulatorArm64 \
          :shared:compileDebugKotlinAndroid \
          :app:assembleDebug
# BUILD SUCCESSFUL
```

产物路径：
```
shared/build/bin/iosSimulatorArm64/debugFramework/XrayFAShared.framework
```

---

## 阶段 E 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| E.1–E.4 | native-bridge + vpn + xcframework | ✅ committed |
| E.5a | `:shared` KMP 聚合 + iOS framework 导出 | ✅（本步，待 commit） |
| E.5b | Xcode 工程骨架 + link frameworks | ⬜ 下一步 |
| E.5c | `IosXrayBridge` cinterop + LibXrayLite | ⬜ |
| E.6 | Compose Multiplatform 共享 UI | ⬜ |

---

## 下一步（E.5b）待办清单

1. **创建 `app-ios/iosApp.xcodeproj`** —— App + Network Extension targets
2. **Build Phase 脚本** —— 调用 `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`（或 embedAndSignAppleFrameworkForXcode）
3. **Link** `XrayFAShared.framework` + `LibXrayLite.xcframework`
4. **Entitlements 占位** —— App Group、Network Extension（不提交个人 signing）
5. **SwiftUI 最小壳** —— 加载 framework、`XrayFAShared.VERSION` smoke test

**验证命令（E.5b 起）**：
```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
xcodebuild -project app-ios/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator build
```

---

## Commit 建议

```
feat(kmp): add shared module exporting XrayFAShared iOS framework (E.5a)
```

---
