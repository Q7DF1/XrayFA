# KMP 迁移交接文档 — Step 107 / 共享设置延迟测试 URL（2026-08-27）

本文档记录：把 Android extras 里的 `delayTestUrl` 迁入共享网络设置。Home/Config 测速早已读 DataStore；iOS 现在也能改。未迁 geoip.dat / geosite.dat（iOS 同样不能走 NE SOCKS；Android 还要 SAF 导入）。未做 Phase C Agent。

**前置**：Step 106（`f5f7888`）。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `SettingsComponent.onSetDelayTestUrl` | 写入 `SettingsRepository.setDelayTestUrl`（与 Android 一样不重启 VPN） |
| `SharedSettingsNetworkDetailsSection` | GeoLite 下行：可编辑测速 URL |
| `AndroidSettingsNetworkViewModelExtras` | 去掉重复的 test url 行（hexTun / geoip 导入仍在 extras） |

### 未改动

- `hexTunEnable` 仍仅 Android extras
- geoip.dat / geosite.dat 下载+导入仍仅 Android extras
- Agent Phase C / C1

---

## 验证状态

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home"
./gradlew :androidApp:compileDebugKotlin :shared:compileKotlinIosX64
```

- [x] `:androidApp:compileDebugKotlin` + `:shared:compileKotlinIosX64` 通过
- [ ] 真机：iOS 设置改 URL 后 Home 测速用新地址

---

## 下一步

1. 可选：geoip.dat / geosite.dat 迁入共享设置（iOS 下载需禁用或另方案；Android 导入需 SAF）
2. **不要**做 Agent Phase C / C1，除非产品明确要求
