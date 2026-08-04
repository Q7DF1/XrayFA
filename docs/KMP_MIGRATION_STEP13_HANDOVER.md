# KMP 迁移交接文档 — Step 13（2026-08-04）

本文档记录阶段 B 第 9 项（收尾）：`XrayCoreManager` 去除 `context.filesDir` 直读，
Xray 运行时工作目录经 `XrayAssetPaths.basePath` 提供。

验证状态：
- `./gradlew :common:compileDebugKotlin` **BUILD SUCCESSFUL**
- `./gradlew :common:testDebugUnitTest` **全部通过**
- `./gradlew :app:compileDebugKotlin` **BUILD SUCCESSFUL**

---

## 改动概要

**目标**：`Libv2ray.initCoreEnv` 的工作目录与 geo 资产路径同源，均由 `XrayAssetPaths`
定义；Android 仍为 `filesDir`，iOS 后续 actual 为 App Group。

### 改动清单

**修改（`common`）**
- `XrayAssetPaths.kt`：新增 `basePath`（Xray 运行时根目录）

**修改（Android）**
- `AndroidXrayAssetPaths.kt`：实现 `basePath`；geo 三路径改 `"$basePath/<file>"`（格式不变）
- `XrayCoreManager.kt`：注入 `XrayAssetPaths`；`initCoreEnv` 改 `assetPaths.basePath`

**grep 复查（Step 13 验收）**
- `XrayCoreManager` **零 `filesDir` 引用** ✅
- `Libv2ray.initCoreEnv` 路径 **与 Step 8 前 `filesDir.absolutePath` 等价** ✅
- geo 路径 **仍与 basePath 下文件名一致** ✅

**仍属 Android 绑定（app 层，不阻塞阶段 B 收尾）**
- `XrayCoreManager` 中 `Context`（Toast）、`android.util.Log`、`@StringDef`
- `Device.getDeviceIdForXUDPBaseKey()` —— Android ID，iOS 需单独 actual

---

## 设计决策

1. **扩展 `XrayAssetPaths` 而非新建接口** —— geo 文件与 core env 同根目录；
   一个接口覆盖全部运行时路径，iOS actual 只维护一处 basePath。
2. **`AndroidXrayAssetPaths` 内 geo 路径复用 `basePath`** —— DRY；消除四处重复
   `"${context.filesDir.absolutePath}/..."` 拼接。
3. **`Context` 保留在 `XrayCoreManager`** —— 仅 Toast 等 UI 边界仍需；路径职责已分离。
4. **阶段 B 至此完成** —— `:common` 侧平台化清单（XrayCore、GeoIp、DataStore、Logger、enum、basePath）
   均已就绪，可进入阶段 C。

---

## 阶段 B 进度（完成）

| 步骤 | 内容 | 状态 |
|------|------|------|
| Step 5 | `XrayCore` 接口 | ✅ |
| Step 6 | `GeoIpProvider` + `XrayAssetPaths` | ✅ |
| Step 7 | SettingsRepository DataStore 平台化 | ✅ |
| Step 8 | geo 路径扩展 + Application 初始化 | ✅ |
| Step 9 | SettingsViewmodel geo 路径 | ✅ |
| Step 10 | parser 去 `javax.inject` | ✅ |
| Step 11 | Logger 抽象 | ✅ |
| Step 12 | `@IntDef` → enum | ✅ |
| Step 13 | `XrayCoreManager` basePath | ✅ |

---

## 下一步（阶段 C 入口）待办清单

阶段 C 改动面较大，建议拆多个小 PR，顺序如下：

### C.1 `:common` 转 KMP multiplatform plugin

**目标**：`commonMain` 可编译 iOS target（空壳或现有接口子集）。

**关键文件**：
- `common/build.gradle.kts`
- 根 `settings.gradle.kts` / `libs.versions.toml`

**注意**：`androidx.datastore`、`Gson`、`javax.inject` 仍在 common，需 C.2/C.3 并行或先后处理

### C.2 Gson → kotlinx.serialization（model 子集试点）

**目标**：先迁 1–2 个无 Android 依赖的 model，验证往返 JSON 兼容。

**关键文件**：`app/.../model/`、`SettingsRepository` 内 Gson 用法

### C.3 DataStore KMP

**目标**：`SettingsRepository` 使用 KMP DataStore API。

### C.4 parser + model 物理迁入 `:domain`

**依赖**：C.2 序列化、common 已 KMP

**阶段 C 验证命令（每子步）**：
```bash
./gradlew :common:compileDebugKotlin :common:testDebugUnitTest
./gradlew :app:compileDebugKotlin
# iOS 子步追加：./gradlew :common:compileKotlinIosSimulatorArm64（配置后）
```

---

## 阶段 D 预览（阶段 C 不做）

- Dagger → Koin
- Compose Multiplatform UI
- iOS VPN Network Extension
