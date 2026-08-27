# KMP 迁移交接文档 — Step 103 / 共享 Home·Config 测速 + iOS `XrayCore`（2026-08-27）

本文档记录：共享 Home 节点卡与 Config「全部测速」接到 `DelayProbe`；iOS 绑定 `IosXrayCore`，`measureOutboundDelay` 走 Step 100 ObjC shim。VPN 启停仍在 PacketTunnel。未做 Phase C Agent。

**前置**：Step 102（`d994fbb`）。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `DelayProbe` + `DelayProbeTest` | live>0 用运行中 core；否则 outbound；`<=0` → `-2` timeout |
| `IosXrayCore` | `initCoreEnv` + shim outbound；`startXrayCore` 恒 `false`（TUN 在 NE） |
| `DefaultHomeComponent.onTestDelay` | 须 VPN 已连；5s 超时；对齐 Android `measureDelay` |
| `DefaultConfigComponent.onTestAllDelays` | 并发 32，不要求 VPN；对齐 Android `measureAllNodesDelay` |
| `iosPlatformDiModule` | `XrayBridge` + `XrayCore` → `IosXrayCore` |

### 未改动

- Config **单节点**测速按钮仍不画（Android `ConfigScreen` 也只做全部测速）
- Home / Config 延迟 map 不共享（Android 旧 ViewModel 曾共用；Decompose 两个 component）
- `IosDigestCalculatorStub`、iOS FileDownloader SOCKS
- Agent Phase C / C1

---

## 设计决策

1. **iOS Home 回落到 outbound** — 进程内 core 不在跑（NE 才跑），`measureDelaySync` 恒 `-1`；连上 VPN 后对当前节点 `measureOutboundDelay`。Android 仍走 live core。
2. **R-1** — 测速进共享 Home/Config，不另做 iOS 屏。
3. **device id** — `initCoreEnv` 用 `"ios-device"`，与 PacketTunnel 一致。

---

## 验证状态

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home"
./gradlew :common:testDebugUnitTest --tests com.android.xrayfa.common.core.DelayProbeTest
# DelayProbeTest 7 tests, 0 failures
./gradlew :androidApp:compileDebugKotlin :shared:compileKotlinIosSimulatorArm64
# BUILD SUCCESSFUL
```

- [x] live 正延迟不走 outbound；live 失败回落 outbound；无节点 → `-2`
- [x] Home 测速钮需 VPN；Config 全部测速需空闲
- [x] iOS simulator Kotlin 编译（`IosXrayCore` + Koin）
- [ ] 真机/模拟器点测速（本步未手测）

---

## 下一步

1. 可选：`IosDigestCalculatorStub` 换真 digest；iOS FileDownloader 走 SOCKS — ✅ digest Step 104；SOCKS ❌ Step 105
2. 可选：geoip.dat / geosite.dat 迁入共享设置
3. **不要**做 Agent Phase C / C1，除非产品明确要求
