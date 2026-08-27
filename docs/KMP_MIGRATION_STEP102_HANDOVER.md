# KMP 迁移交接文档 — Step 102 / 共享设置 GeoLite 下载 + `geoLiteInstall`（2026-08-27）

本文档记录：共享设置网络区提供 GeoLite2-Country.mmdb 下载；成功后才写 `geoLiteInstall=true`，parser / iOS GeoIP 才会查库出旗。Android extras 去掉重复的 GeoLite 行（geoip.dat / geosite.dat 仍在 extras）。未接测速按钮，未做 Phase C Agent。

**前置**：Step 101（`a2af23d`）。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `GeoLiteInstaller` + `GeoLiteInstallerTest` | 官方 URL 落到 dest；成功才 `setInstalled(true)`；下载钮需 VPN 且空闲 |
| `FileDownloader.downloadToFile` + `FileByteSink` expect/actual | 双端写文件；去掉 Android 专用 extension |
| `DefaultSettingsComponent.onDownloadGeoLite` | 用 Koin `FileDownloader` + `XrayAssetPaths` |
| `SharedSettingsDownloadRow` | 进度条 + VPN 提示，对齐 Android `SettingsWithBtnBox` |
| `AndroidSettingsNetworkViewModelExtras` | 删除 GeoLite 行，避免与共享 UI 重复 |
| iOS `iosNetworkDiModule` | 绑定 Darwin `FileDownloader`（直连，不走 SOCKS） |

### 未改动

- geoip.dat / geosite.dat 下载与导入仍在 Android extras
- iOS 下载不经本地 SOCKS（Android 仍走代理 FileDownloader）；须先连 VPN 的提示文案与 Android 相同
- 共享 Home/Config **仍不传** `onTest`
- Agent Phase C / C1

---

## 设计决策

1. **失败不置 install** — 旧 Android `downloadGeoLite` 无论成败都 `setGeoLiteInstall(true)`；共享路径与 ViewModel 现都只在成功时置位。
2. **下载钮跟 VPN** — 与 Android `downloadEnable = isVpnConnected` 一致。
3. **R-1** — GeoLite 进共享设置，Android extras 不再画第二份。

---

## 验证状态

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home"
./gradlew :common:testDebugUnitTest --tests com.android.xrayfa.common.core.GeoLiteInstallerTest
# GeoLiteInstallerTest 3 tests, 0 failures
./gradlew :androidApp:compileDebugKotlin :shared:compileKotlinIosSimulatorArm64
# BUILD SUCCESSFUL
```

- [x] 成功：官方 URL + dest + install true
- [x] 失败：不改 install
- [x] 下载钮：需 VPN 且非 downloading
- [ ] 真机/模拟器：连 VPN 后点下载，节点行出国旗（本步未手测）

---

## 下一步

1. 共享 Home/Config 接 `onTest` + iOS `XrayCore` 委托 Step 100 shim
2. 可选：geoip.dat / geosite.dat 也迁入共享设置；iOS FileDownloader 走 SOCKS
3. **不要**做 Agent Phase C / C1，除非产品明确要求
