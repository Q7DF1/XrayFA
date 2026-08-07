# KMP 迁移交接文档 — Step 45 / 阶段 E.5b（2026-08-07）

本文档记录阶段 E 第 5b 项：创建 iOS Xcode 工程骨架（App + PacketTunnel NE），
通过 Gradle `embedAndSignAppleFrameworkForXcode` 链接 `XrayFAShared.framework`；
**不接入 LibXrayLite / IosXrayBridge 运行时**（留 E.5c）。

**前置**：Step 44 / E.5a 已 commit（`337910c`）。

**目录命名**：应用壳统一为 KMP 惯例 `androidApp/` + `iosApp/`（`:androidApp` Gradle 模块）。

---

## 前置条件检查（E.5b 入口）

| 前置项 | 状态 |
|--------|------|
| E.5a `:shared` framework 导出 | ✅ committed |
| xcodegen 可用 | ✅ `brew install xcodegen` |
| Xcode 16+ | ✅ 16.3 |
| KMP embed 任务 | ✅ Kotlin 插件内置 `embedAndSignAppleFrameworkForXcode` |

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `iosApp/project.yml` | xcodegen 规格：iosApp + PacketTunnel targets |
| `iosApp/iosApp/*.swift` | SwiftUI 壳，`ContentView` 读取 `XrayFAShared.VERSION` |
| `iosApp/PacketTunnel/*` | NE stub（返回 not-implemented） |
| `iosApp/Config.xcconfig` / `iosApp.xcconfig` | 基础设置 + iosApp 专用 framework 搜索路径 |
| `iosApp/*/Entitlements/*.entitlements` | VPN / App Group 模板（需 Apple Developer 配置 Team） |
| `iosApp/iosApp.xcodeproj/` | xcodegen 生成（可复现） |
| `scripts/generate_ios_xcodeproj.sh` | 生成 Xcode 工程脚本 |
| `iosApp/.gitignore` | 忽略 xcuserdata |

### 未改动

- `:core:native-bridge` iOS stub
- `:androidApp` Android 运行时逻辑
- 未 link `LibXrayLite.xcframework`

---

## 设计决策与原因

1. **xcodegen + `project.yml`** —— pbxproj 可复现；改 spec 后 `./scripts/generate_ios_xcodeproj.sh` 再生。
2. **Gradle embed 用 Kotlin 插件内置任务** —— 自定义同名 task 会冲突；Build Phase 直接调 `:shared:embedAndSignAppleFrameworkForXcode`。
3. **PacketTunnel 暂不 link KMP** —— NE 在 iosApp 之前构建；extension stub 仅依赖 NetworkExtension，避免 link 顺序问题。
4. **Simulator 建议 `-arch arm64`** —— KMP `iosSimulatorArm64` 切片；x86_64 simulator 需单独 framework 或排除架构。
5. **Entitlements 为模板** —— 含 VPN / NE / App Group 占位；真机运行需配置 `DEVELOPMENT_TEAM` 与 Apple 能力审批。

---

## 验证状态

```bash
./scripts/generate_ios_xcodeproj.sh

# iOS simulator (arm64)
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -configuration Debug -arch arm64 \
  ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=NO build
# BUILD SUCCEEDED

./gradlew :androidApp:assembleDebug
# BUILD SUCCESSFUL
```

Smoke test：`ContentView` 显示 `XrayFAShared.shared.VERSION` == `0.1.0-kmp`。

---

## 阶段 E 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| E.5a | `:shared` KMP 聚合 | ✅ committed |
| E.5b | Xcode 骨架 + KMP framework link | ✅（本步，待 commit） |
| E.5c | `IosXrayBridge` cinterop + LibXrayLite | ⬜ 下一步 |
| E.6 | Compose Multiplatform 共享 UI | ⬜ |

---

## 下一步（E.5c）待办清单

1. **cinterop / Swift 胶水** —— `:core:native-bridge` iosMain 调用 `LibXrayLite.xcframework`
2. **PacketTunnel 接入 Xray** —— 替换 stub `startTunnel`
3. **`IosVpnController` actual** —— App Group IPC（主 App ↔ Extension）
4. **NE 内存 profiling** —— Go `GOGC` / `SetMemoryLimit`
5. **SettingsDataStore App Group 路径** —— `:core:datastore` iosMain

**验证命令（E.5c 起）**：
```bash
./scripts/build_libxray_ios.sh
./gradlew :core:native-bridge:compileKotlinIosSimulatorArm64
```

---

## Commit 建议

```
feat(kmp): add iOS Xcode shell with KMP framework integration (E.5b)
```

**注意**：提交 `iosApp.xcodeproj`（xcodegen 生成物）；本地 `DEVELOPMENT_TEAM` 勿写入 repo。

---
