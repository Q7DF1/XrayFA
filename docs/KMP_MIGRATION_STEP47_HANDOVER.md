# KMP 迁移交接文档 — Step 47 / 阶段 E.5d（2026-08-07）

本文档记录阶段 E 第 5d 项：App Group IPC、`IosVpnController`（NETunnelProviderManager）、
DataStore App Group 路径、PacketTunnel TUN 引导 + LibXrayLite 环境初始化；
**Tun2socksKit / startLoop / 完整流量转发留 E.5e**。

**前置**：Step 46 / E.5c 已 commit（`114f1d2`）。

---

## 前置条件检查（E.5d 入口）

| 前置项 | 状态 |
|--------|------|
| E.5c Libv2ray cinterop | ✅ committed |
| App Group entitlements（iosApp + PacketTunnel） | ✅ E.5b |
| `LibXrayLite.xcframework` 本地构建 | ✅ |
| Android `AppVpnController` / VPN 运行时 | ✅ 未改动 |

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `common/.../IosPlatformConstants.kt` | App Group ID、IPC keys、NE bundle id |
| `core/datastore/.../SettingsDataStoreFactory.ios.kt` | 优先 App Group 容器路径 |
| `platform/vpn/.../IosAppGroupStorage.kt` | UserDefaults IPC + `setPendingConfig` |
| `platform/vpn/.../IosVpnController.kt` | NETunnelProviderManager connect/disconnect |
| `iosApp/PacketTunnel/PacketTunnelProvider.swift` | 读配置、TUN 设置、Libv2rayInitCoreEnv |
| `iosApp/PacketTunnel.xcconfig` | LibXrayLite xcframework + `-lresolv` |
| `iosApp/project.yml` | PacketTunnel 使用 xcconfig |

### 未改动

- `:androidApp` VPN / ViewModel / `AppVpnController`
- `:core:native-bridge` Android / iOS XrayBridge（E.5c）
- `IosStubTunBridge`（iOS packet tun 待 Tun2socksKit）
- PacketTunnel 内 **未调用** `startLoop`（无 TUN fd）

---

## 设计决策与原因

1. **App Group 作为 IPC 总线** —— 主 App（KMP）写 `pending_vpn_config_json`，NE（Swift）读取；NE 写 `vpn_tunnel_connected`。与 Android DataStore/Room 分离，符合 iOS 进程模型。
2. **DataStore 迁入 App Group** —— 主 App 与 NE 未来可共享设置；无 entitlement 时回退 Documents（编译/无签名模拟器）。
3. **`IosVpnController` 在 KMP** —— 与 Android 共用 `VpnController` 接口；内部用 NetworkExtension API，不改 `:androidApp`。
4. **NE 内 Swift 调 LibXrayLite** —— Extension 不链 KMP（E.5b 构建顺序决策）；仅 `InitCoreEnv` + `CheckVersionX` 验证链接，完整代理留 Tun2socksKit。
5. **`-lresolv`** —— gomobile Go runtime 在 iOS 需要 resolver 符号；否则 PacketTunnel link 失败。

---

## 验证状态

```bash
./scripts/build_libxray_ios.sh   # 若需要

./gradlew :platform:vpn:compileKotlinIosSimulatorArm64
./gradlew :core:datastore:compileKotlinIosSimulatorArm64
./gradlew :shared:compileKotlinIosSimulatorArm64
./gradlew :androidApp:assembleDebug
# BUILD SUCCESSFUL

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
| E.5a–E.5c | shared + Xcode + Libv2ray cinterop | ✅ committed |
| E.5d | App Group + IosVpnController + PacketTunnel bootstrap | ✅（本步，待 commit） |
| E.5e | Tun2socksKit + startLoop + measureOutboundDelay shim | ⬜ 下一步 |
| E.6 | Compose Multiplatform 共享 UI | ⬜ |

---

## 下一步（E.5e）待办清单

1. **Tun2socksKit** —— NEPacketFlow → SOCKS5，替换 `IosStubTunBridge`
2. **PacketTunnel `startLoop`** —— TUN fd / packet 模式接 Xray
3. **`measureOutboundDelay` ObjC shim** —— 可选，补 K/N 无法直接调用的 API
4. **NE 内存 profiling** —— Go `SetMemoryLimit` / `GOGC`
5. **主 App 接 `IosVpnController`** —— Swift/Koin 或 `ContentView` 试验 connect

---

## Commit 建议

```
feat(kmp): add iOS App Group VPN IPC and PacketTunnel bootstrap (E.5d)
```

---
