# KMP 迁移交接文档 — Step 106 / iOS 宿主链 LibXrayLite + ObjC gomobile 回调（2026-08-27）

本文档记录：宿主 App 链接静态 `XrayFAShared` 时补上 gomobile 符号；PacketTunnel 回调改为 ObjC 子类，避免 `go_seq_go_to_refnum`。未做 Phase C Agent。

**前置**：Step 104–105（本批一并提交）/ Step 103（`24c684a`）。

---

## 现象与根因

| 问题 | 根因 |
|------|------|
| Undefined `_Libv2ray*` / `_XrayFAMeasureOutboundDelay` | `XrayFAShared` 是静态库，cinterop 不把 `LibXrayLite.a` 打进去；`iosApp.xcconfig` 原先只链 `XrayFAShared` |
| `go_seq_go_to_refnum on objective-c objects is not permitted` | Swift / KN 继承 `Libv2rayCoreCallbackHandler` 没有 Go ref；`NewCoreController` 不能收普通 ObjC 对象 |

gomobile 回调必须用 **ObjC 子类** 的 `init`（生成 Go ref）。Swift `override` 选择子须为 `onEmitStatus:p1:`。`startLoop`/`stopLoop` 的 `NSError**` 在 Swift 里是 `throws`。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `iosApp/iosApp.xcconfig` | `-framework LibXrayLite -lresolv` + slice `FRAMEWORK_SEARCH_PATHS` |
| `iosApp/project.yml` | 宿主编 `XrayFAMeasureOutboundDelay.m` |
| `TunnelCoreCallbackHandler.{h,m}` | ObjC 子类；删 Swift 版 |
| `PacketTunnelProvider.swift` | `startLoop`/`stopLoop` 走 `throws` |
| `IosXrayCore.kt` | 宿主不再 `newCoreController`（Xray 只在 NE）；测速仍走 delay shim |

### 未改动

- PacketTunnel 仍自己链 LibXrayLite（与宿主是两个进程）
- Agent Phase C / C1

---

## 验证状态

```bash
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -configuration Debug ARCHS=x86_64 \
  ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=NO build
# BUILD SUCCEEDED
```

- [x] 宿主链接不再缺 Libv2ray / delay shim 符号
- [x] PacketTunnel ObjC 回调可编译
- [ ] 模拟器点连接：本步未在对话里复测 VPN 握手（需用户侧确认不再抛 go_seq）

---

## 下一步

1. 可选：geoip.dat / geosite.dat 迁入共享设置（iOS 同样不能走 NE SOCKS，下载需禁用或另方案）
2. 共享延迟测试 URL — ✅ Step 107
3. **不要**做 Agent Phase C / C1，除非产品明确要求
