# KMP 迁移交接文档 — Step 27 / 阶段 D.1d-c（2026-08-05）

本文档记录阶段 D 第 1 项（第七批）：**完全移除 Dagger**，Android 组件改由 Koin
直接装配；`app` 与 `tun2socks` 模块删除 Dagger/KSP 依赖。

**前置**：Step 26 / D.1d-b（ViewModel Factory + Service helpers 迁 Koin）——**请先 commit D.1d-b，再 commit 本步**。

---

## 前置条件检查

| 条件 | 状态 |
|------|------|
| D.1d-a Platform + Core 已 commit | ✅（Step 25） |
| D.1d-b Factory + NotificationHelper 已 Koin 注册 | ✅ 代码完成，**待 commit** |
| `KoinBridgeModule` 桥接全部 Koin 类型 | ✅ D.1d-b 已完成 |
| 剩余 `@Inject` 组件清单明确 | ✅ 见下表 |

本步处理的剩余 `@Inject` 组件：

| 组件 | 依赖来源 |
|------|----------|
| `MainActivity` | 5 个 ViewModel Factory |
| `XrayBaseService` | Tun2Socks、XrayCore、Settings、Notification |
| `QuickStartTileService` | XrayBaseServiceManager |
| `BootBroadcastReceiver` | Manager、Background Scope、Settings |
| `TProxyService` / `Tun2SocksConfigUtil` | Context、Settings（tun2socks 模块） |
| `ComponentResolver` | 上述组件的 factory map |

---

## 改动概要

**目标**：Koin 成为 `:app` 唯一 DI 框架；删除 Dagger 双轨与 KSP 代码生成，为后续 KMP 共享 DI 模块铺路。

### 新增

| 文件 | 说明 |
|------|------|
| `AppComponentDiModule.kt` | Activity/Service/Receiver/Tun2Socks/ComponentResolver 的 Koin 注册 |

### 重构（逻辑不变）

| 文件 | 变更 |
|------|------|
| `ComponentResolver` | Dagger `Provider` map → Koin `() -> T` factory map |
| `XrayAppCompatFactory` | 移除 Dagger `rootComponent`，直接 `GlobalContext.get()` |
| `XrayFAApplication` | `XrayAssetPaths` 改 Koin `inject()`；删除 `ContextAvailableCallback` |
| `MainActivity` 等 4 个组件 | 去掉 `@Inject`，保留构造函数参数 |
| `TProxyService` / `Tun2SocksConfigUtil` | 去掉 `@Inject`/`@Singleton`，由 app 模块 Koin 装配 |

### 删除

| 文件 | 说明 |
|------|------|
| `KoinBridgeModule.kt` | Dagger↔Koin 桥接，不再需要 |
| `XrayFAComponent.kt` | Dagger 根组件 |
| `GlobalModule.kt` | Dagger 模块 |
| `ServiceModule.kt` / `ActivityModule.kt` | Dagger multibinding |

### 构建

| 模块 | 变更 |
|------|------|
| `app/build.gradle.kts` | 移除 `dagger` / `dagger.android` / KSP processor |
| `tun2socks/build.gradle.kts` | 移除 Dagger 依赖与 KSP 插件 |

**仍保留**（后续 D.2 处理）：
- `common/.../Qualifiers.kt` 中 `javax.inject.Qualifier` 注解（仅文档对齐，无 Dagger 运行时）
- Room KSP（`app` 模块数据库仍用 KSP）

---

## 设计决策与原因

1. **Activity/Service/Receiver 用 Koin `factory`** —— 与原先 Dagger `Provider.get()` 每次新建实例的行为一致，不改变 Android 组件生命周期语义。
2. **`Tun2SocksService` 用 Koin `single`** —— 对应旧 `@Singleton TProxyService`，VPN 进程内 tun2socks 仍为单例。
3. **保留 `AppComponentFactory` + `ComponentResolver` 模式** —— 系统侧实例化入口不变，仅 DI 后端从 Dagger 换为 Koin；`MainActivity` 构造注入签名不变，UI 层零 diff。
4. **tun2socks 去掉 Dagger 但不在模块内引入 Koin** —— tun2socks 保持纯 Android library；装配集中在 `:app` 的 `AppComponentDiModule`，便于日后 iOS 侧独立桥接。
5. **不引入 `koin-androidx-viewmodel`** —— 本步只完成 DI 框架统一；ViewModel 获取方式不变，Decompose 阶段再评估。

---

## 验证状态

```bash
./gradlew :common:compileDebugKotlinAndroid :common:testDebugUnitTest          # BUILD SUCCESSFUL
./gradlew :domain:compileDebugKotlinAndroid :domain:testDebugUnitTest          # BUILD SUCCESSFUL
./gradlew :domain:compileKotlinIosSimulatorArm64                              # BUILD SUCCESSFUL
./gradlew :tun2socks:compileDebugKotlin                                       # BUILD SUCCESSFUL
./gradlew :app:compileDebugKotlin                                             # BUILD SUCCESSFUL
./gradlew :app:assembleDebug                                                  # BUILD SUCCESSFUL
```

**待用户确认后再 commit（建议 D.1d-b 与 D.1d-c 分两次 commit）。**

---

## 阶段 D 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| D.1d-a | Platform + Core 迁 Koin | ✅ committed |
| D.1d-b | ViewModel Factory + Service helpers | ✅（待 commit） |
| D.1d-c | 移除 Dagger（Component、KSP） | ✅（待 commit） |
| D.2 | Room Entity 与 Domain Model 分离 | ⬜ 下一步 |

---

## 下一步（D.2）待办清单

1. **梳理 `app/dto/` 与 `app/dao/` Entity** —— 对照 `domain` / `core:model` 已有模型
2. **Room Entity 保留在 `app` 或迁至 `core:database`** —— 先做 typealias/Mapper 层，运行时行为不变
3. **Repository 返回 Domain Model** —— Entity ↔ Model 转换在 Repository 内部完成
4. **删除 deprecated `Link` typealias 链** —— 统一为 `Node`
5. **iOS 侧验证** —— `:core:database` commonMain schema 与 Android migration 对齐

**验证命令**：
```bash
./gradlew :common:compileDebugKotlinAndroid :common:testDebugUnitTest
./gradlew :domain:compileDebugKotlinAndroid :domain:testDebugUnitTest
./gradlew :domain:compileKotlinIosSimulatorArm64
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
# Room migration 回归（Android 专用）
./gradlew :app:connectedDebugAndroidTest
```

---

## Commit 建议

**先 commit D.1d-b（若尚未 commit）：**
```
feat(kmp): migrate ViewModel factories and service helpers to Koin (D.1d-b)
```

**再 commit D.1d-c：**
```
feat(kmp): remove Dagger and wire Android components through Koin (D.1d-c)
```
