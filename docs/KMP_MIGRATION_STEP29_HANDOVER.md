# KMP 迁移交接文档 — Step 29 / 阶段 D.2b（2026-08-05）

本文档记录阶段 D 第 2 项（第二批）：Repository **接口**提取至 `:domain`；
Android 实现重命名；`SubscriptionMeta` 迁入 domain model。

**前置**：Step 28 / D.2a 已 commit（`16599b0`）。

---

## 改动概要

**目标**：ViewModel / Service 依赖 Repository 接口而非 Android 实现，便于 iOS 侧复用 domain 契约与测试。

### 新增 — `:domain`

| 文件 | 说明 |
|------|------|
| `repository/NodeRepository.kt` | 节点 CRUD 接口 |
| `repository/SubscriptionRepository.kt` | 订阅 CRUD + `fetchAndSaveNodes` 接口 |
| `model/SubscriptionMeta.kt` | 订阅 HTTP 元数据（自 app utils 提升） |

### 重命名 — `:app` 实现

| 原类 | 新类 | 说明 |
|------|------|------|
| `NodeRepository` | `RoomNodeRepository` | 实现 `NodeRepository` 接口 |
| `SubscriptionRepository` | `AndroidSubscriptionRepository` | 实现 `SubscriptionRepository` 接口 |

### 调整

| 文件 | 变更 |
|------|------|
| `AppDataDiModule.kt` | `single<NodeRepository> { RoomNodeRepository(...) }` |
| `HttpResponseUtils.kt` | 返回 `model.SubscriptionMeta` / `SubscriptionUserInfo` |
| `SubscriptionViewmodel.kt` | import 改 domain model |

**ViewModel / Factory / Service 签名不变** —— 仍注入 `NodeRepository` / `SubscriptionRepository`，现为接口类型。

---

## 设计决策与原因

1. **接口与实现分包同名** —— `com.android.xrayfa.repository.NodeRepository` 接口在 domain；`RoomNodeRepository` 在 app，Koin 绑定接口 → 实现。
2. **`SubscriptionMeta` 提升到 domain** —— 接口 `fetchAndSaveNodes` 返回类型须 KMP 可编译；解析逻辑仍留 app（OkHttp）。
3. **本步不建 `:core:database`** —— Room DAO/Entity 仍在 app，D.2c 再提取。

---

## 验证状态

```bash
./gradlew :domain:compileDebugKotlinAndroid :domain:testDebugUnitTest   # BUILD SUCCESSFUL
./gradlew :domain:compileKotlinIosSimulatorArm64                        # BUILD SUCCESSFUL
./gradlew :app:compileDebugKotlin                                       # BUILD SUCCESSFUL
./gradlew :app:assembleDebug                                            # BUILD SUCCESSFUL
```

**待用户确认后再 commit。**

---

## 阶段 D 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| D.2a | Domain Model + Entity 分离 | ✅ committed |
| D.2b | Repository 接口提取至 `:domain` | ✅（待 commit） |
| D.2c | 创建 `:core:database` Room KMP 模块 | ⬜ 下一步 |

---

## 下一步（D.2c）待办清单

1. **创建 `:core:database` KMP 模块** —— 沿用 build-logic convention（若已有）或复制 `:domain` 结构
2. **迁移 `NodeEntity` / `SubscriptionEntity` / DAO / Database / Migrations** 至 `commonMain` + `androidMain` 驱动
3. **`:app` 删除本地 Room 代码**，依赖 `:core:database`
4. **启用 Room schema export** —— golden schema 用于 migration 测试
5. **iOS**：BundledSQLite 驱动 stub 或 compile-only 验证

**验证命令**：
```bash
./gradlew :core:database:compileDebugKotlinAndroid
./gradlew :core:database:compileKotlinIosSimulatorArm64
./gradlew :domain:testDebugUnitTest
./gradlew :app:assembleDebug
```

---

## Commit 建议

```
feat(kmp): extract Node and Subscription repository interfaces to domain (D.2b)
```
