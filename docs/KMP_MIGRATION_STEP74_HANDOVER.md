# KMP 迁移交接文档 — Step 74 / 期中查漏补缺 P1-CI（2026-08-20）

本文档记录期中评审 **R-5**：让 CI 在迁移分支上真正跑测试，并让 iOS shared 编译在干净检出上有意义。

**前置**：Step 73 已 commit（`94a88b6`）。

**Commit**：`29ca665` `ci(kmp): run shared-module tests on feature branches`

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `.github/workflows/kmp-unit-tests.yml` | **新增** — `main` / `feat/**` / PR：`:common` / `:domain` / `:core:datastore` `testDebugUnitTest`（无 NDK / gomobile） |
| `.github/workflows/ios-shared.yml` | 触发扩到 `feat/**`；`domain-ios-tests` job；编译前 cache/构建 `LibXrayLite.xcframework` |
| `.github/workflows/android.yml` | AAR 之后、`assembleRelease` 之前加 `./gradlew test`。触发仍是 `main` / tag / PR to `main` |
| `core/native-bridge/build.gradle.kts` | xcframework 检查改到 `taskGraph.whenReady`，且仅当**本模块**真正 compile/cinterop/link iOS 时检查 |
| `.gitignore` | 忽略 `.kotlin/` |
| `AGENT.md` | §4 测试、§8 CI、§9 产物、§10.4 CI 勾选 按本步更新 |

### 刻意没做

- **没有**把 `feat/**` 接到 `assembleRelease`。那条 job 含 NDK + gomobile + 签名，功能分支每次全量打 release 不划算。共享测试走 `kmp-unit-tests.yml`。
- **没有**上 `xcodebuild -scheme iosApp -sdk iphonesimulator`（签名 / framework 链路，单独一步）。

---

## 设计决策

1. **轻量测试 workflow vs 重 release workflow** — 满足 R-5「feat 分支要跑测试」，同时不把 F-Droid 复现构建套到每条 `feat/**`。
2. **xcframework 按 `go.mod` / `go.sum` / 构建脚本 hash 缓存** — 首次慢，之后应命中 cache。
3. **收窄 native-bridge 的 Ios 任务名检查** — 原先 `taskNames` 含 `"Ios"` 就失败，导致 `:domain:iosSimulatorArm64Test` 也被误伤。

---

## 验证状态

本地无法代替 GitHub Actions。推送后实际结果：

- 第一次失败：submodule SHA 不在上游（见 Step 75），与本步 workflow 逻辑无关。
- JVM / iOS domain 测试 job 在 SHA 修好后应能跑到 Gradle。

```bash
# 本地等价
./gradlew :common:testDebugUnitTest :domain:testDebugUnitTest :core:datastore:testDebugUnitTest
```

- [x] workflow YAML 已写入仓库并 commit
- [x] native-bridge `:domain:iosX64Test --dry-run` 不再因缺 xcframework 在 configuration 失败
- [ ] GitHub Actions `KMP Unit Tests` 绿灯（依赖 Step 75 的 submodule 修复）
- [ ] GitHub Actions `iOS Shared Compile CI` / `domain-ios-tests` 绿灯
- [ ] `compile-ios-shared` 首次 gomobile 后 cache 命中

---

## 期中后进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| 73 | domain commonTest parser 黄金用例 | ✅ |
| 74 | CI 跑共享模块测试 | ✅ committed `29ca665` |
| 75 | 修正 unpublished submodule SHA | ✅ Step 75 |

---

## 手动验证清单

- [ ] 推 `feat/migrateToKMP` 后 `KMP Unit Tests` 跑过 parser 黄金用例
- [ ] `domain-ios-tests` 在 `macos-latest`（Apple Silicon）上执行 `iosSimulatorArm64Test` 而非 skip
- [ ] `compile-ios-shared` 能找到 / 构建 `LibXrayLite.xcframework` 再编译 `:shared`
- [ ] `android.yml` 仍不在 feat 分支触发
