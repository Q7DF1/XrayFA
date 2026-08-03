# KMP 迁移交接文档 — Step 11（2026-08-03）

本文档记录阶段 B 第 7 项：`SettingsRepository` 去除 `android.util.Log`，
引入 `Logger` 平台抽象，使 `:common` 在日志维度 KMP-ready。

验证状态：
- `./gradlew :common:compileDebugKotlin` **BUILD SUCCESSFUL**
- `./gradlew :common:testDebugUnitTest` **全部通过**
- `./gradlew :app:compileDebugKotlin` **BUILD SUCCESSFUL**

---

## 改动概要

**目标**：`:common` 模块零 `android.util.Log`；共享层通过 `Logger` 接口输出日志，
Android actual 委托 Logcat，iOS 后续 actual 委托 NSLog / os_log。

### 改动清单

**新增（`common`）**
- `common/.../utils/Logger.kt`：`Logger` 接口 + 内部 `NoOpLogger`（单测备用）

**新增（Android 实现）**
- `app/.../core/AndroidLogger.kt`：委托 `android.util.Log`

**修改**
- `SettingsRepository.kt`：注入 `Logger`；`Log.i("test", ...)` → `logger.i(TAG, ...)`
- `GlobalModule.kt`：`bindLogger(AndroidLogger)`

**grep 复查（Step 11 验收）**
- `:common` 模块 **零 `android.util.Log` import** ✅
- `SettingsRepository` **通过 `Logger` 接口打日志** ✅
- `:app` / `:tun2socks` 中 Log **本步未动**（不阻塞 KMP，见下方策略）✅

**仍属 Android 绑定（`:common`，后续阶段）**
- `@IntDef` / `Theme` / `RoutingMode` / `DomainStrategy` —— Step 12 改 enum
- `androidx.datastore` 类型 —— 阶段 C DataStore KMP
- `Gson` 内联 —— 阶段 C kotlinx.serialization
- `javax.inject` on `SettingsRepository` —— 阶段 D Dagger→Koin

---

## 设计决策

1. **只清 `:common` 的 Log，不改 `:app`** —— 迁移按模块边界推进；app 壳 Log 不挡
   `:common` 转 `commonMain`；等代码迁入共享层时再改用 `Logger`。
2. **轻量 `Logger` 接口，非全量 Kermit** —— 当前仅 1 处调用；避免引入新依赖；
   阶段 D 可整体换 Kermit 或扩展接口。
3. **修正 debug tag** —— 原 `Log.i("test", ...)` 改为 `logger.i("SettingsRepository", ...)`，
   行为等价（仍输出 addAllowedPackages 列表大小）。
4. **`NoOpLogger` 内部可见** —— 供未来 common 单测使用；生产路径走 Dagger 注入的
   `AndroidLogger`。

---

## 日志迁移策略（全项目）

| 层级 | 范围 | 时机 |
|------|------|------|
| **层 1（本步）** | `:common` 去 `android.util.Log` | Step 11 ✅ |
| **层 2** | 代码迁入 `:domain` / `:common` 时改用 `Logger` | 阶段 C parser/model 迁移 |
| **层 3（可选）** | `:app` / `:tun2socks` 统一 Kermit | 阶段 D，不阻塞 KMP |

`:app` 当前约 15 个文件、40+ 处 `Log` —— **故意保留**，因属 Android 应用壳。

---

## 阶段 B 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| Step 5–10 | （见前序交接文档） | ✅ |
| Step 11 | `SettingsRepository` Logger 抽象 | ✅ |
| Step 12 | `@IntDef` → enum（SettingsRepository） | ⬜ |
| Step 13 | `XrayCoreManager` filesDir 路径抽象 | ⬜ |

---

## 下一步（Step 12）待办清单

### 12.1 SettingsRepository `@IntDef` 改纯 Kotlin enum

**目标**：消除 `androidx.annotation.IntDef`，使 Theme / RoutingMode / DomainStrategy
可在 KMP `commonMain` 编译。

**建议步骤**：
1. 将 `@IntDef` annotation + companion const 改为 `enum class` 或 sealed class
2. DataStore 仍存 `Int` ordinal / code（保持用户数据兼容）
3. 更新 UI / ViewModel 中 `@Theme` 等注解引用

**涉及文件（预估）**：
- `common/.../repository/SettingsRepository.kt`
- `SettingsViewmodel.kt`、Compose 设置页

**注意点**：枚举值必须与现有 Int 常量数值一致，避免破坏已存 Preferences

### 12.2 后续

| 步骤 | 内容 | 关键文件 |
|------|------|----------|
| Step 13 | `XrayCoreManager` filesDir 路径抽象 | `XrayCoreManager.kt` |
| 阶段 C | Gson → kotlinx.serialization | `model/`、`parser/` |

**Step 12 验证命令**：
```bash
./gradlew :common:compileDebugKotlin :common:testDebugUnitTest
./gradlew :app:compileDebugKotlin
# 手动：主题切换、路由模式、域名策略设置仍正常
```

---

## 阶段 C/D 预览（更远，Step 12 不做）

- `:common` 转 multiplatform plugin
- parser/model 迁入 `:domain`；迁入代码改用 `Logger`
- 可选全项目 Kermit 替换 `:app` 层 Log
