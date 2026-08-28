# KMP 迁移交接文档 — Step 78 / 期中 R-7 `:core:data`（2026-08-21）

本文档记录期中评审 **R-7** 的第二刀：Repository 实现不得住在 UI 伞模块 `:shared`。新建 `:core:data`，迁入 `RoomNodeRepository` / `KmpSubscriptionRepository` / `EntityMappers`。

**前置**：Step 77 已 commit（`c4b4d9f`）。本步不改运行时行为（包名与 Koin 装配保持不变）。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `core/data/build.gradle.kts` | 新 KMP 模块；依赖 `:common` / `:domain` / `:core:database` / `:core:datastore` / `:core:network` |
| `core/data/.../RoomNodeRepository.kt` | 从 `:shared` **git mv** |
| `core/data/.../KmpSubscriptionRepository.kt` | 从 `:shared` **git mv** |
| `core/data/.../EntityMappers.kt` | 从 `:shared` **git mv** |
| `settings.gradle.kts` | `include(":core:data")` |
| `shared/build.gradle.kts` | `api(project(":core:data"))` |
| `androidApp/build.gradle.kts` | `implementation(project(":core:data"))` |
| `.github/workflows/kmp-unit-tests.yml` | 加 `:core:data:compileDebugKotlinAndroid` |
| `AGENT.md` | 分层纪律 + 目录 + CI 表 |
| `docs/KMP_MIGRATION_PLAN.md` | Step 78 从表里拆出 |

### 未改动

- 包名仍是 `com.android.xrayfa.repository` / `com.android.xrayfa.dto`（Android / iOS DI import 不变）
- `appDataDiModule` / `iosDataDiModule` 装配代码未改
- 未给 `RoomNodeRepository` 加 `distinctUntilChanged`（期中 MAJOR，不塞进本步）
- 未做 Android 收敛 `RootContent`（R-1，下一步）

---

## 设计决策

1. **只搬家，不改包名** — 目标是拆模块边界；DI 与调用方零 diff。
2. **`:shared` 用 `api(:core:data)`** — iOS Koin 在 `shared/iosMain`，framework 消费者仍能解析实现类。
3. **`:androidApp` 显式依赖** — Data DI 在 app 模块，不靠传递依赖碰运气。

---

## 验证状态

```bash
./gradlew :core:data:compileDebugKotlinAndroid :shared:compileDebugKotlin :androidApp:compileDebugKotlin :domain:testDebugUnitTest
# BUILD SUCCESSFUL in 5m 40s（exit 0）
```

- [x] 上述命令通过
- [x] `:shared` commonMain 不再含 repository 实现 / EntityMappers
- [ ] 真机：节点列表 / 订阅更新（人工）

---

## 期中后进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| 73–77 | commonTest / CI / submodule / R-2 去重 / 断 domain→datastore | ✅ |
| 78 | 拆 `:core:data` | ✅ 本步 |
| 79 | Android 收敛 `RootContent`（R-1：Subscription → BottomNav → Logcat → Apps） | ⬜ |

---

## 手动验证清单

### Android
- [ ] 启动后节点列表加载
- [ ] 订阅 URL 更新能写入节点
- [ ] 选中 / 收藏 / 删除节点

### iOS
- [ ] 启动后节点列表加载（Koin 仍解析 `NodeRepository`）

---

## Commit 建议（确认后执行）

```
refactor(kmp): extract shared repositories into :core:data
```
