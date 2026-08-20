# KMP 迁移交接文档 — Step 75 / CI submodule 检出（2026-08-20）

本文档记录推送 Step 74 后流水线在 **checkout submodules** 失败的修复。

**前置**：Step 74 已 commit（`29ca665`）。

**Commits**：
- `6f547fc` `fix(ci): pin AndroidLibXrayLite to a published upstream commit`
- `048c36d` `revert(ci): keep submodule URLs on SSH`（HTTPS 回退，见下）

---

## 根因

父仓库 gitlink 指向 `48b0bb55`（E.4 在 submodule **本地**加的 iOS README / gitignore）。该 SHA **从未推到** `2dust/AndroidLibXrayLite`。

GitHub Actions `git submodule update` 克隆上游默认分支后，再取 `48b0bb55`，GitHub 返回：

```
fatal: remote error: upload-pack: not our ref 48b0bb55...
```

这不是 SSH 鉴权失败：日志里 submodule 已用 `git@github.com:2dust/AndroidLibXrayLite.git` 注册并开始 clone，死在「提交不在远端」。

`48b0bb55` 相对 `origin/master`（`b213898`）只多了 README 片段和 `.gitignore` 的 `*.xcframework`。iOS 构建说明已在父仓库 `scripts/build_libxray_ios.sh` / `iosApp/README.md`。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `AndroidLibXrayLite` gitlink | `48b0bb55` → `b213898`（上游 `origin/master`） |
| `.gitmodules` | 曾改为 HTTPS，**已恢复 SSH**（与父仓库 origin 一致；CI 失败与协议无关） |
| `.gitignore` | 父仓库忽略 `AndroidLibXrayLite/LibXrayLite.xcframework`、`**/HevSocks5Tunnel.xcframework` |
| `kmp-unit-tests.yml` / `ios-shared.yml` 的 `domain-ios-tests` | 去掉 `submodules: recursive`（这两项不需要 Go 源码） |

`compile-ios-shared` 与 `android.yml` 仍 recursive checkout submodule。

---

## 设计决策

1. **不把本地 submodule 提交硬推到 2dust** — 无上游写权限；文档应留在父仓库。
2. **SSH 保留** — Actions 对公开 GitHub 仓库的 `git@` URL，`actions/checkout` 常会 insteadOf 到 HTTPS；本地开发与 `origin` 一致更重要。
3. **不需要 submodule 的 job 不要 recursive** — 避免再被 gitlink 事故挡住 JVM 测试。

---

## 验证状态

- [x] 本地 `git -C AndroidLibXrayLite rev-parse HEAD` = `b213898`
- [x] `git ls-tree HEAD AndroidLibXrayLite` 已是 `b213898`
- [x] `.gitmodules` 为 `git@github.com:...`
- [ ] 再推后 checkout 不再报 `not our ref`
- [ ] `KMP Unit Tests` 进入 Gradle（不再卡在 submodule）

---

## 期中后进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| 73 | domain commonTest parser 黄金用例 | ✅ |
| 74 | CI 跑共享模块测试 | ✅ |
| 75 | 修正 unpublished submodule SHA + 恢复 SSH URL | ✅ `6f547fc` + `048c36d` |
| 76 | R-2 去重 | ⬜ |

---

## 手动验证清单

- [ ] `git push` 后 Actions checkout 成功
- [ ] 本地 `./scripts/build_libxray_ios.sh` 仍可用（不依赖已丢弃的 submodule README）
- [ ] 不要再把仅存在于本机的 submodule commit 写进父仓库 gitlink
