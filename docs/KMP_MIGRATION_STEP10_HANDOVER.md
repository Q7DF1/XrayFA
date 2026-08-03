# KMP 迁移交接文档 — Step 10（2026-08-03）

本文档记录阶段 B 第 6 项：parser 包消除 `javax.inject`，
DI 装配下沉至 `ParserModule`，parser 类变为纯 Kotlin 可共享实现。

验证状态：
- `./gradlew :common:compileDebugKotlin` **BUILD SUCCESSFUL**
- `./gradlew :common:testDebugUnitTest` **全部通过**
- `./gradlew :app:compileDebugKotlin` **BUILD SUCCESSFUL**

---

## 改动概要

**目标**：7 个协议 parser + `ParserFactory` 不再依赖 JSR-330 注解，
便于后续迁入 `:domain` KMP 模块或切换 Koin；运行时解析行为与依赖关系不变。

### 改动清单

**新增**
- `app/.../di/ParserModule.kt`：`@Provides @Singleton` 组装 7 个 parser 与 `ParserFactory`

**修改**
- `GlobalModule.kt`：`includes` 增加 `ParserModule::class`
- `ParserFactory.kt`：去掉 `@Singleton` / `@Inject`，保留显式构造函数参数
- 7 个 `*ConfigParser.kt`：去掉 `@Singleton` / `@Inject` 及 `javax.inject` import

**grep 复查（Step 10 验收）**
- `parser` 包 **零 `javax.inject` import** ✅
- `ParserFactory` / 各 parser **零 Dagger 注解** ✅
- `ParserModule` **统一提供单例** ✅
- parser 构造函数依赖 **仍为 `SettingsRepository` + `GeoIpProvider` + `Gson`** ✅

**仍属 Android / 框架绑定（后续阶段）**
- parser 依赖 `Gson` —— 阶段 C kotlinx.serialization 迁移
- parser 依赖 `SettingsRepository`（含 Android 注解）—— 随 `:common` KMP 拆分
- ViewModel / Repository 等 **仍使用 `javax.inject`** —— 随 Dagger→Koin 整体迁移

---

## 设计决策

1. **新建 `ParserModule` 而非扩写 `GlobalModule`** —— parser 装配逻辑集中，
   后续整体替换为 Koin `module { factory { ... } }` 时边界清晰。
2. **保留 `@Provides @Singleton` 在 Module 层** —— Dagger 图行为与原先 parser 类上
   `@Singleton` 一致；消费方（`XrayCoreManager`、`SubscriptionRepository` 等）无需改动。
3. **parser 类构造函数签名不变** —— 仅移除注解；`ParserModule` 显式传参，逻辑零变化。
4. **本步不迁 parser 到 `:common`** —— 仍依赖 Gson 与 Android 侧 model；先解耦 DI 注解，
   物理迁移留阶段 C。

---

## 阶段 B 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| Step 5 | `XrayCore` 接口 | ✅ |
| Step 6 | `GeoIpProvider` + `XrayAssetPaths` | ✅ |
| Step 7 | SettingsRepository DataStore 平台化 | ✅ |
| Step 8 | `XrayAssetPaths` geo 扩展 + Application | ✅ |
| Step 9 | SettingsViewmodel geo 路径接口化 | ✅ |
| Step 10 | parser 消除 `javax.inject` | ✅ |
| Step 11 | `SettingsRepository` 去除 `android.util.Log` | ⬜ |

---

## 下一步（Step 11）待办清单

### 11.1 SettingsRepository 日志平台化

**目标**：`common/.../SettingsRepository.kt` 中 `android.util.Log` 阻碍 `:common` 转 KMP。

**建议步骤**：
1. 在 `common` 定义轻量 `Logger` 接口或引入 Kermit expect/actual
2. Android actual 委托 `Log`；iOS actual 委托 `NSLog` / os_log
3. `SettingsRepository` 改注入或顶层 `Logger`，删除 `android.util.Log` import

**涉及文件（预估）**：
- `common/.../repository/SettingsRepository.kt`
- 新建 `common/.../utils/Logger.kt`（或 Kermit 依赖）

### 11.2 后续

| 步骤 | 内容 | 关键文件 |
|------|------|----------|
| Step 12 | `XrayCoreManager` filesDir 路径抽象 | `XrayCoreManager.kt` |
| 阶段 C | Gson → kotlinx.serialization | `model/`、`parser/` |
| 阶段 C | parser 物理迁入 `:domain` | `app/.../parser/` |

**Step 11 验证命令**：
```bash
./gradlew :common:compileDebugKotlin :common:testDebugUnitTest
./gradlew :app:compileDebugKotlin
# 手动：设置读写、日志相关功能仍正常
```

---

## 阶段 C/D 预览（更远，Step 11 不做）

- Dagger → Koin（ViewModel Factory、Repository 等仍带 `@Inject`）
- `:common` 转 multiplatform plugin
- parser + model 迁入 KMP `domain` 模块
