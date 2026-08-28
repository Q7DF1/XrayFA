# KMP 迁移交接文档 — Step 111 / iOS ShareNode 二维码（2026-08-28）

本文档记录：iOS Config 分享节点不再弹「开发中」，改为二维码 Dialog + 剪贴板导出，对齐 Android `AndroidPlatformRootHooks.ShareNode`。不碰 `XrayViewmodel`。未做 Agent Phase C / C1。

**前置**：Step 110。活清单：`docs/KMP_MIGRATION_STATUS.md`。桩：`docs/IOS_STUBS.md`（`ShareNode` 已移出未实现表）。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `common/.../ShareLinkCleaner.kt` + `ShareLinkCleanerTest` | 分享前去掉 `allowInsecure`（vless/trojan/hysteria2 query；vmess JSON） |
| `shared/.../IosQrEncoder.kt` | `qrcode-kotlin` 出 PNG → Skia `ImageBitmap` |
| `IosPlatformRootHooks.ShareNode` | Dialog：二维码 + `clipboard_export`；空 URL 只出按钮 |
| `gradle/libs.versions.toml` + `shared/build.gradle.kts` | iosMain `libs.qrcode.kotlin`（不改 Android） |

### 未改动

- `AndroidPlatformRootHooks.ShareNode` / `XrayViewmodel.generateQRCode`
- 分应用 / geo 导入 / HexTun / Bug report 仍为「开发中」
- Agent Phase C / C1

---

## 设计决策

1. **只填 iOS hook** — 不把 QR 生成放进 `commonMain`，Android 继续 ZXing。
2. **清洗逻辑进 `:common`** — iOS 不能引用 `androidApp` 的 `LinkUtils`；单测锁 vless/vmess/ss。
3. **编码失败仍给复制** — 没有 bitmap 时 Dialog 只留剪贴板按钮，不假装分享失败成「开发中」。

---

## 验证状态

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home"
./gradlew :common:testDebugUnitTest --tests com.android.xrayfa.common.utils.ShareLinkCleanerTest
./gradlew :androidApp:compileDebugKotlin :androidApp:testDebugUnitTest \
  --tests com.android.xrayfa.ui.AgentScreenRootTabTest
./gradlew :shared:compileKotlinIosX64
```

- [x] `ShareLinkCleanerTest` 4 tests, 0 failures；`:androidApp:compileDebugKotlin` + `AgentScreenRootTabTest` 3 tests, 0 failures；`:shared:compileKotlinIosX64` BUILD SUCCESSFUL
- [ ] 模拟器/真机：Config 节点分享出二维码；复制后粘贴为清洗过的 URL

---

## 下一步

1. Step 112：iOS geoip.dat / geosite.dat 文件导入（HexTun 继续开发中或注明 NE 固定 hev）
2. **不要**做 Agent Phase C / C1
3. **不要**删除 `XrayFAContainer`，直到 Step 109 Android 真机清单勾完
