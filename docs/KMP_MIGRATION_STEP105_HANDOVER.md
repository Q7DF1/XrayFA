# KMP 迁移交接文档 — Step 105 / iOS 关闭 GeoLite 设置下载（2026-08-27）

本文档记录：Android 经本机 SOCKS 下 GeoLite **不能**原样搬到 iOS。Xray SOCKS 在 Network Extension 的 `127.0.0.1`，宿主 App 连不到。按产品决定：iOS 设置页下载钮禁用；Android 行为不变。未做 Phase C Agent。

**前置**：Step 104（未提交）/ Step 103（`24c684a`）。可与 Step 104 同一批提交。

---

## 为什么不能走 SOCKS

| | Android | iOS |
|--|---------|-----|
| Xray 进程 | 与 App 同进程（VpnService） | PacketTunnel Network Extension |
| SOCKS | App 可连 `127.0.0.1:socksPort` | SOCKS 只在 **扩展进程** loopback；宿主 `127.0.0.1` 不是那个端口 |
| TUN | 可选 | 默认路由进 utun；hev 在扩展内连 SOCKS |

Darwin 直连在 VPN 已连时 theoretically 会进隧道，但产品要求「不能 SOCKS 就不让下」，故关闭 iOS 下载，避免假装可用。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `geoLiteDownloadSupported` expect/actual | Android `true`；iOS `false` |
| `geoLiteDownloadEnabled(..., downloadSupported)` | 不支持则钮永不启用 |
| `GeoLiteDownloadState.downloadSupported` | UI 换 iOS 提示文案 |
| `geo_lite_download_unavailable` | 四语文案 |

### 未改动

- Android SOCKS `FileDownloader`
- iOS 仍绑定 Darwin `FileDownloader`（本步不再调用下载）
- Agent Phase C / C1

---

## 验证状态

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home"
./gradlew :common:testDebugUnitTest --tests com.android.xrayfa.common.core.GeoLiteInstallerTest
./gradlew :androidApp:compileDebugKotlin :shared:compileKotlinIosSimulatorArm64
```

- [x] 不支持平台：即使 VPN 已连，下载钮仍关
- [x] Android：仍须 VPN + 空闲
- [x] `:common:testDebugUnitTest --tests GeoLiteInstallerTest` 通过
- [x] `:androidApp:compileDebugKotlin` + `:shared:compileKotlinIosSimulatorArm64` 通过
- [ ] 真机：iOS 设置 GeoLite 行提示不可下载（本步未手测）

---

## 下一步

1. Step 104–106 与本步同一批提交
2. 可选：geoip.dat / geosite.dat 仍仅 Android extras
3. **不要**做 Agent Phase C / C1，除非产品明确要求
