# KMP 迁移交接文档 — Step 48 / 阶段 E.5e（2026-08-07）

本文档记录阶段 E 第 5e 项：PacketTunnel 完整 VPN 管线（Xray `startLoop` + hev-socks5-tunnel）；
主 App 试验性接 `IosVpnController`；**measureOutboundDelay ObjC shim 留 E.5f**。

**前置**：Step 47 / E.5d 已 commit（`a3a4738`）。

---

## 前置条件检查（E.5e 入口）

| 前置项 | 状态 |
|--------|------|
| E.5d App Group + IosVpnController + PacketTunnel bootstrap | ✅ committed |
| `LibXrayLite.xcframework` 本地构建 | ✅ |
| `HevSocks5Tunnel.xcframework` 本地构建 | ✅ `./scripts/build_hev_tun_ios.sh` |
| Android hexTun / `AppVpnController` 路径 | ✅ 未改动 |

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `scripts/build_hev_tun_ios.sh` | 复现 hev-socks5-tunnel iOS xcframework 构建 |
| `iosApp/PacketTunnel/PacketTunnelProvider.swift` | hexTun 管线：`startLoop(tunFd:0)` + hev on utun |
| `iosApp/PacketTunnel/HevSocks5TunnelHelper.swift` | utun fd + hev 包装 |
| `iosApp/PacketTunnel/UtunFdHelper.{h,m}` | C 链 hev `hev_socks5_tunnel_main` |
| `iosApp/PacketTunnel/Tun2SocksConfigBuilder.swift` | YAML 对齐 Android `Tun2SocksConfigUtil` |
| `iosApp/PacketTunnel/TunnelCoreCallbackHandler.swift` | Libv2ray 回调 |
| `iosApp/PacketTunnel/PacketTunnel-Bridging-Header.h` | hev C API |
| `iosApp/PacketTunnel.xcconfig` | 静态链 `-lhev-socks5-tunnel` + LibXrayLite |
| `shared/.../IosSharedInit.kt` | 暴露 `setPendingVpnConfig` / `connectVpn` |
| `iosApp/iosApp/ContentView.swift` | Connect/Disconnect 试验按钮 |
| `core/native-bridge/.../NativeBridgeFactory.ios.kt` | 注释：iOS tun 在 NE Swift，KMP TunBridge 仍 stub |

### 未改动

- `:androidApp` VPN / ViewModel / `BridgedTun2SocksService`
- KMP `IosStubTunBridge`（NE 不链 KMP tun 层，与 E.5d 决策一致）
- `measureOutboundDelay` K/N 出参 shim（可选，下一步）

---

## 设计决策与原因

1. **hexTun 路径对齐 Android** —— `startLoop(config, tunFd: 0)` 启动 Xray SOCKS inbound；hev 在 utun fd 上跑 tun2socks。与 `XrayBaseService` 在 `hexTunEnable` 时行为一致，不改 Android 逻辑。
2. **hev 用现有 submodule + `build-apple.sh`** —— 与 Android 共用 `hev-socks5-tunnel` 源码；文档中的 Tun2socksKit 为 NEPacketFlow 包装库，本步直接用 hev C API + utun fd，减少第三方依赖。
3. **utun fd 经 `packetFlow` socket fd** —— NE 内 `sys/kern_control.h` 在扩展 target 编译不可用；采用业界常见的 `NEPacketTunnelFlow` socket fd 方式（非公开 API，真机 NE 需验证）。
4. **tun2socks YAML 在 Swift 生成** —— 默认 port 10808，并从 Xray JSON inbounds 解析 socks port；ipv4/mtu 与 NE TUN 设置（198.18.0.1 / 1500）一致。
5. **IosSharedInit 试验接线** —— E.6 Compose UI 前用 ContentView 验证 `VpnController` 跨平台接口；trial config 仅 freedom outbound，真机需替换为 parser 产出 JSON。
6. **KMP TunBridge 保持 stub** —— iOS tun 在 NE 进程 Swift/C 层；强行接 KMP 会引入 Extension 链 KMP + fd 生命周期问题。

---

## 验证状态

```bash
./scripts/build_libxray_ios.sh    # 若需要
./scripts/build_hev_tun_ios.sh    # 若需要（约 4 分钟）

./gradlew :shared:compileKotlinIosSimulatorArm64
./gradlew :platform:vpn:compileKotlinIosSimulatorArm64
./gradlew :androidApp:assembleDebug
# BUILD SUCCESSFUL

./scripts/generate_ios_xcodeproj.sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -configuration Debug -arch arm64 \
  ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=NO build
# BUILD SUCCEEDED
```

---

## iOS 运行状态说明

| 能力 | 状态 |
|------|------|
| 模拟器 / 无签名编译 | ✅ |
| NE 内 Xray + hev 代码路径 | ✅ 已接线 |
| 模拟器上实际 VPN 流量 | ❌ NE VPN 需真机 + entitlement |
| 主 App Connect 试验按钮 | ✅（需 VPN entitlement 才成功） |
| 与 Android 功能对等 | ⬜ 待真机 NE 联调 + parser 配置 |

**结论**：iOS **尚不能**视为「与 Android 一样可正常使用的 VPN 客户端」；代码与编译链已就绪，**真机 + Apple VPN entitlement + 有效节点配置** 后方可验证端到端流量。

---

## 阶段 E 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| E.5a–E.5d | shared + Xcode + cinterop + App Group | ✅ committed |
| E.5e | startLoop + hev tun2socks + IosVpnController trial | ✅（本步，待 commit） |
| E.6 | Compose Multiplatform 共享 UI | ⬜ 下一步 |
| E.5f（可选） | measureOutboundDelay shim + NE 内存调优 | ⬜ |

---

## 下一步（E.6 / E.5f）待办清单

1. **E.6 — Compose Multiplatform `MainViewController`** —— 共享 UI + Koin iOS actual
2. **真机 NE 联调** —— VPN entitlement、parser 配置写入 App Group、流量验证
3. **（可选）E.5f — `Libv2rayMeasureOutboundDelay` ObjC shim** —— 供 K/N 调用
4. **（可选）Go `SetMemoryLimit` / NE 15MB  profiling**

---

## Commit 建议

```
feat(kmp): wire iOS PacketTunnel Xray startLoop and hev tun2socks (E.5e)
```

**注意**：不要提交 `HevSocks5Tunnel.xcframework` / `LibXrayLite.xcframework`（与 E.4 一致）。

---
