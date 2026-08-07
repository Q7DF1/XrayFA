# KMP 迁移交接文档 — Step 46 / 阶段 E.5c（2026-08-07）

本文档记录阶段 E 第 5c 项：`:core:native-bridge` iosMain 通过 cinterop 链接
`LibXrayLite.xcframework`，实现与 Android `Libv2rayXrayBridge` 对称的 iOS actual；
**不接入 PacketTunnel 运行时 / IosVpnController / Tun2socks**（留 E.5d）。

**前置**：Step 45 / E.5b 已 commit（Xcode 骨架 + KMP framework link）。

---

## 前置条件检查（E.5c 入口）

| 前置项 | 状态 |
|--------|------|
| E.5a `:shared` framework 导出 | ✅ committed |
| E.5b Xcode 骨架 + embed | ✅（待 commit 或已 commit） |
| E.4 `build_libxray_ios.sh` + xcframework 验证 | ✅ committed |
| 本地 `LibXrayLite.xcframework` | ✅ 需 `./scripts/build_libxray_ios.sh` |
| Android `Libv2rayXrayBridge` actual | ✅ committed（行为基准） |

**结论**：E.5c 前置已全部满足；本步仅替换 iOS XrayBridge stub，Android 与 `:androidApp` 运行时零改动。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `core/native-bridge/build.gradle.kts` | iOS cinterop + xcframework linkerOpts；缺 xcframework 时友好报错 |
| `core/native-bridge/src/nativeInterop/cinterop/libv2ray.def` | cinterop 定义 |
| `core/native-bridge/src/nativeInterop/cinterop/headers/*.h` | gomobile 头 shim（`@import` → `#import`，cinterop 兼容） |
| `core/native-bridge/src/iosMain/.../Libv2rayXrayBridge.ios.kt` | iOS XrayBridge actual |
| `core/native-bridge/src/iosMain/.../Libv2rayAdapters.ios.kt` | Callback / CoreController 适配器 |
| `core/native-bridge/src/iosMain/.../NativeBridgeFactory.ios.kt` | Xray 用 actual；Tun 仍 stub |
| `iosApp/README.md` | 进度 E.5c ✅ / E.5d 待办 |

### 未改动

- `:androidApp` Android VPN / libv2ray JNI 路径
- `PacketTunnelProvider.swift`（仍为 not-implemented stub）
- `IosVpnController` stub
- `HevTunBridge` / Android tun2socks
- `:core:datastore` App Group 路径

---

## 设计决策与原因

1. **cinterop + 净化头文件** —— gomobile 生成头使用 `@import Foundation`，Kotlin cinterop 无法直接解析；复制头文件并将 `@import` 改为 `#import`（仅构建用，不修改 submodule）。跨平台目标：iOS 与 Android 共用同一套 `XrayBridge` 接口。
2. **镜像 Android 适配器结构** —— `Libv2rayXrayBridge` + `Libv2rayCoreCallbackAdapter` + `Libv2rayCoreControllerAdapter`，降低后续 NE 接线认知成本；Android 逻辑不变。
3. **TunBridge 保持 iOS stub** —— Android 使用 TUN fd + hev JNI；iOS 需 NEPacketFlow + Tun2socksKit，API 不同，强行统一会改 Android 语义。留 E.5d。
4. **`measureOutboundDelay` 暂返 -1L** —— `Libv2rayMeasureOutboundDelay` 的 `int64_t*` 出参在 K/N cinterop 中标记为不可导入；与迁移前 iOS stub 行为一致。运行中延迟可经 `CoreController.measureDelay`（已接 cinterop）在 E.5d NE 内使用。
5. **xcframework 不提交** —— 与 E.4 一致；Gradle 编译 iOS 任务前检查产物是否存在。

---

## 验证状态

```bash
./scripts/build_libxray_ios.sh   # 若 xcframework 不存在

./gradlew :core:native-bridge:compileKotlinIosSimulatorArm64
./gradlew :core:native-bridge:compileKotlinIosArm64
./gradlew :shared:compileKotlinIosSimulatorArm64
./gradlew :androidApp:assembleDebug
# 全部 BUILD SUCCESSFUL

./scripts/generate_ios_xcodeproj.sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -configuration Debug -arch arm64 \
  ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=NO build
# BUILD SUCCEEDED
```

---

## 阶段 E 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| E.5a | `:shared` KMP 聚合 | ✅ committed |
| E.5b | Xcode 骨架 + KMP framework link | ✅（待 commit） |
| E.5c | `IosXrayBridge` cinterop + LibXrayLite | ✅（本步，待 commit） |
| E.5d | PacketTunnel + IosVpnController + Tun2socks + App Group | ⬜ 下一步 |
| E.6 | Compose Multiplatform 共享 UI | ⬜ |

---

## 下一步（E.5d）待办清单

1. **PacketTunnel 接入 Xray** —— NE 内调用 KMP `XrayBridge` / `CoreController.startLoop`（或 Swift 薄封装）
2. **`IosVpnController` actual** —— `NETunnelProviderManager` + App Group IPC
3. **Tun2socksKit / packet-based tun** —— 替换 `IosStubTunBridge`
4. **`measureOutboundDelay` ObjC shim**（可选）—— 小 `.m` 包装 `Libv2rayMeasureOutboundDelay` 供 K/N 调用
5. **SettingsDataStore App Group 路径** —— `:core:datastore` iosMain
6. **NE 内存 profiling** —— Go `GOGC` / `SetMemoryLimit`

**验证命令（E.5d 起）**：
```bash
./gradlew :platform:vpn:compileKotlinIosSimulatorArm64
# 真机 + Network Extension 需 Apple VPN entitlement
```

---

## Commit 建议

若 E.5b 尚未提交，请先单独 commit E.5b，再 commit 本步：

```
feat(kmp): wire iOS Libv2ray cinterop for native-bridge XrayBridge (E.5c)
```

**注意**：不要提交 `LibXrayLite.xcframework`；cinterop headers 为构建 shim，随 repo 提交。

---
