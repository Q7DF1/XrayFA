# KMP 迁移交接文档 — Step 43 / 阶段 E.4（2026-08-07）

本文档记录阶段 E 第 4 项：验证 `AndroidLibXrayLite` iOS xcframework 构建；
添加可复现脚本，**不接入 `:core:native-bridge` iosMain 运行时**（留 E.5）。

**前置**：Step 42 / E.3b 已 commit（`690c869`）。

---

## 前置条件检查（E.4 入口）

| 前置项 | 状态 |
|--------|------|
| E.3b VpnController 迁移完成 | ✅ committed |
| macOS + Xcode 可用 | ✅ Xcode 16.3 |
| Go toolchain | ✅ go1.26.5 |
| gomobile | ✅ 本机安装并 `gomobile init` |

---

## 验证结果

```bash
./scripts/build_libxray_ios.sh
# 等价于 AndroidLibXrayLite 内：
# gomobile bind -target ios,iossimulator -o LibXrayLite.xcframework ./
```

| 项 | 结果 |
|----|------|
| 构建 | ✅ **BUILD SUCCESSFUL** |
| 产物 | `AndroidLibXrayLite/LibXrayLite.xcframework` (~117MB) |
| 架构 | `ios-arm64` + `ios-arm64_x86_64-simulator` |
| ObjC API | `Libv2rayCoreController`, `Libv2rayInitCoreEnv`, `Libv2rayNewCoreController`, `Libv2rayMeasureOutboundDelay` 等（见 `Libv2ray.objc.h`） |

**注意**：
- xcframework **体积大**（~117MB），但 Network Extension **运行时内存**仍须单独 profiling（NE ~15MB 限制）。
- `libv2ray_android.go`（`RegisterProcessFinder`）在 iOS bind 中仍可编译；iOS 不使用 per-app UID 路由。
- 产物已加入 `AndroidLibXrayLite/.gitignore`，**不提交二进制**。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `scripts/build_libxray_ios.sh` | 可复现 iOS xcframework 构建脚本 |
| `AndroidLibXrayLite/.gitignore` | 忽略 `LibXrayLite.xcframework` |
| `AndroidLibXrayLite/README.md` | 补充 iOS 构建说明 |

### 未改动

- `:core:native-bridge` iOS stub（仍为 no-op）
- `:app` / Gradle Android 构建
- Koin / VPN 运行时

---

## 设计决策与原因

1. **脚本放主仓库 `scripts/`** —— 与 `app` 的 `bindXrayLib`（Android）对称；submodule 内仅文档 + gitignore。
2. **验证与接线分离** —— E.4 证明 gomobile iOS 路径可行；E.5 再链 Xcode + KMP Framework + `IosXrayBridge` actual。
3. **不提交 xcframework** —— 体积大、平台特定；CI/开发者本地构建，或后续 Artifact 托管。

---

## 验证状态

```bash
./scripts/build_libxray_ios.sh                                    # BUILD SUCCESSFUL (local)
./gradlew :core:native-bridge:compileKotlinIosSimulatorArm64       # BUILD SUCCESSFUL
./gradlew :platform:vpn:compileKotlinIosSimulatorArm64             # BUILD SUCCESSFUL
./gradlew :app:assembleDebug                                      # BUILD SUCCESSFUL
```

**待用户确认后再 commit。**

---

## 阶段 E 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| E.1–E.3b | native-bridge + vpn | ✅ committed |
| E.4 | iOS xcframework 构建验证 + 脚本 | ✅（本步，待 commit） |
| E.5 | iOS 应用壳 + link xcframework + `IosXrayBridge` | ⬜ 下一步 |
| E.6 | Compose Multiplatform 共享 UI | ⬜ |

---

## 下一步（E.5）待办清单

1. **创建 `app-ios` 骨架** —— Xcode project + Network Extension target
2. **KMP shared Framework** —— 汇聚 `:domain` / `:platform:vpn` / `:core:*`
3. **`IosXrayBridge` actual** —— cinterop / Swift 胶水调用 `LibXrayLite.xcframework`
4. **替换 `IosVpnController` stub** —— App Group + Extension 通信
5. **NE 内存 profiling** —— Go runtime `GOGC` / `SetMemoryLimit`

**验证命令（E.5 起）**：
```bash
./scripts/build_libxray_ios.sh
./gradlew :shared:compileKotlinIosSimulatorArm64   # 待建 shared 模块
```

---

## Commit 建议

```
feat(kmp): add iOS xcframework build script and verify gomobile bind (E.4)
```

若 submodule 文档一并提交，需在 `AndroidLibXrayLite` 子模块内单独 commit 后更新主仓库 pointer。

---
