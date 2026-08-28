# XrayFA KMP 移植「期中考试」评审报告

> 评审对象：分支 `feat/migrateToKMP`（HEAD `47ba3f3`，相对 `main` 共 75 次提交、521 文件、+23355/−4859）
> 评审日期：2026-08-11
> 评审基准：**对标一个「优秀的 KMP 成品项目」**，而非「相对计划的进度」。两套尺子的结论差别很大，见 §1。
> 所有结论均由阅读源码 / 实际执行构建得出，附 `路径:行号` 证据。

---

## 1. 总评

### 1.1 两把尺子

| 评价基准 | 得分 | 说明 |
|---|---|---|
| **对标「优秀 KMP 成品」** | **48 / 100** | 本报告主基准。iOS 无法连接 + 共享代码零测试是两个致命拖累 |
| 对标「迁移进度 vs `KMP_MIGRATION_PLAN.md`」 | 约 65 / 100 | 处于 Phase 4 / Stage E 中段，骨架与数据层确实推进到位 |

**一句话结论：骨架搭得像样，地基有真材实料，但"最后一厘米"全部悬空——iOS 这个移植的唯一目的至今无法建立连接，而共享代码一行测试都没有。**

### 1.2 分项评分

| # | 维度 | 权重 | 得分 | 核心依据 |
|---|---|---:|---:|---|
| 1 | 模块与目录结构 | 20% | 6.5 | 骨架方向正确；但无 convention plugin、`:domain` 反向依赖 `:core:datastore`、`:shared` 成为万能模块 |
| 2 | 功能还原度 / UI 保真 | 25% | 4.5 | iOS 端到端 ~58%；且连接功能在业务层即中断 |
| 3 | 共享代码有效性（DRY） | 15% | 5.0 | 存在逐字节重复文件；Android/iOS 两套 App 壳并行 |
| 4 | 性能与正确性 | 15% | 5.5 | 2 个 BLOCKER、28 个 MAJOR；但 Flow/Compose 基本功不差 |
| 5 | 测试与可验证性 | 15% | 2.5 | 零 `commonTest`、CI 不跑测试、22 个有效用例 |
| 6 | 工程化与文档 | 10% | 4.5 | 双端可编译；但文档漂移严重，`AGENT.md` 描述的是迁移前的项目 |

> 加权 = 6.5×0.20 + 4.5×0.25 + 5.0×0.15 + 5.5×0.15 + 2.5×0.15 + 4.5×0.10 = **4.83 → 48/100**

### 1.3 已实测通过的部分（不是所有事都糟）

```
:androidApp:compileDebugKotlin              BUILD SUCCESSFUL
:shared:compileKotlinIosSimulatorArm64      BUILD SUCCESSFUL
./gradlew test                              BUILD SUCCESSFUL (26 用例全绿)
```

值得肯定的工程决策：

- **Room KMP 迁移是本次移植质量最高的部分**：`@ConstructedBy` + `RoomDatabaseConstructor` expect/actual、`exportSchema = true` 且 `schemas/4.json` 已入库、1→2→3→4 migration 全部保留、**没有**使用 `fallbackToDestructiveMigration`。老用户升级不会丢数据。
- DataStore 22 个 key 的磁盘键名全部保持不变（`core/datastore/.../SettingsRepository.kt:47-71`），文件名常量还专门写了注释锁定。
- Dagger 2 **彻底**移除，Koin 完全接管，没有留下双 DI 共存的烂尾。
- Ktor 双引擎（Android OkHttp / iOS Darwin）；`RuleSerializationTest`、`RoutingObjectSerializationTest` 专门锁定了 Gson ↔ kotlinx 的 JSON 兼容性，这个意识很好。
- iOS 侧不是空壳：`PacketTunnelProvider.swift` 有真实的 `NEPacketTunnelNetworkSettings`、Xray `startLoop` + hev-socks5-tunnel 管线、App Group IPC；QR 扫码是真的 AVFoundation 实现。

---

## 2. 必须优先修的问题（BLOCKER）

### B-1　iOS 点「连接」在业务层就抛异常，VPN API 根本没被调用

`domain/src/iosMain/kotlin/com/android/xrayfa/config/IosXrayConfigEncoder.kt:9-13`

```kotlin
class IosXrayConfigEncoder : XrayConfigEncoder {
    override fun encode(config: XrayConfiguration): String {
        throw UnsupportedOperationException("Xray config encoding on iOS is not implemented yet")
    }
}
```

调用链（`shared/.../IosVpnConnectCoordinator.kt:13-17` → `domain/.../AbstractConfigParser.kt:323`）在 `encode()` 处终止，`IosVpnController.connect()` 之后的一切代码都是死代码。

**根因**：Gson → kotlinx.serialization 只做了开头。`domain/.../model/` 下 22 个配置模型文件**只有 3 个**标注了 `@Serializable`（`RoutingObject`、`InboundObject`、`PolicyObject`）。Android 靠 Gson 反射绕过了这个问题，iOS 没有反射可用。

**这意味着**：所谓"共享领域层"并没有共享这个 App 最核心的能力——生成 Xray 配置。

> 修复要求：给整个 `XrayConfiguration` 图补 `@Serializable`，并**先写 Gson↔kotlinx 输出对比测试**（照 `RoutingObjectSerializationTest` 的模式），否则字段名/null 处理/默认值省略的差异会静默产生错误配置。

### B-2　共享代码零测试，且 CI 不跑任何测试

- 全仓库**不存在** `commonTest` / `iosTest` 目录，但 `domain/build.gradle.kts:32-34`、`common/build.gradle.kts:30-32` 已经声明了 `commonTest.dependencies` —— 脚手架搭好了却没用。
- 26 个用例全在 `androidUnitTest`，其中 3 个是 `ExampleUnitTest` 模板。约 1388 行的 `parser/` 与 357 行的 `AbstractConfigParser` **没有任何配置生成测试**。
- `android.yml` 与 `ios-shared.yml` 都没有 `./gradlew test`。CI 提供的自动化验证等于零。
- `AGENT.md:104` 声称"最有价值的测试是 `AbstractConfigParserTest.kt`"——**该文件在 git 全历史中从未存在**。`androidApp/src/test/.../ProtocolParserTest.java` 是个 9 行空类，零 `@Test`。

### B-3　`println` 把含凭据的完整配置明文打印出来

`domain/src/commonMain/kotlin/com/android/xrayfa/parser/AbstractConfigParser.kt:323-325`

```kotlin
val config = configEncoder.encode(xrayConfig)
println(config)
return config
```

每次连接都会把完整 Xray 配置（含 UUID / password / 服务器地址）写进 logcat 与 iOS 控制台。而 App 自带**日志导出**功能（`LogcatScreen.kt` + `XrayFAContainer.kt:327-339`），用户一旦把日志发给他人即泄露全部凭据。这是安全问题，不只是性能问题。

### B-4　Release 混淆规则不完整

`androidApp/build.gradle.kts:60-61` 开启了 `isMinifyEnabled = true` + `isShrinkResources = true`，但 `proguard-rules.pro` 只有 29 行，仅 keep 了 `com.android.xrayfa.model.**` 与一条已失效的 `common.repository.**`。Koin / Decompose 的 `RootTab.serializer()` / kotlinx-serialization 生成物均无显式规则。

> 本次评审**未实际执行** `assembleRelease` 验证，因此这条是"高风险未验证"而非"已确认崩溃"。请务必跑一次 minify release 包并冒烟测试导航与订阅解析。

---

## 3. 三个结构性判断（这才是 UI 还原不全的真正原因）

### 3.1 Android 从未使用共享的 UI 根——实际维护着两套 App

`AppShell` / `RootContent` 的**唯一**引用方是 `shared/src/iosMain/.../MainViewController.kt:22`。Android 走的是 `MainActivity` → `XrayFAContainer`（Navigation3 + Pager + SharedTransitionLayout），只挑选性地嵌入了几个 `Shared*Section`。

后果是共享 UI 退化成"iOS 专用的简化重写"，两侧必然持续漂移：

| 屏幕 | Android 实现 | 共享实现 | 关系 |
|---|---:|---:|---|
| 订阅管理 | `SubscriptionScreen.kt` 650 行 | `SharedSubscriptionScreen.kt` 508 行 | **两份并行实现**，Android 侧完全没引用共享版 |
| 底部导航 | `XrayBottomNav.kt` 590 行 | `XrayFloatingNav.kt` 196 行 | 共享版缺拖拽切换、模糊、展开态 |
| 分应用代理 | `AppsScreen.kt` 375 行（可搜索勾选） | `SharedAppsInfoScreen.kt` 122 行（**只读包名列表**） | 功能级缺失 |
| 日志 | `LogcatScreen.kt` 200 行（录制时长/起停/智能滚动/导出） | `SharedAppLogScreen.kt` 139 行（仅进程内 `AppLogStore`） | 名不副实 |
| 路由设置 | 41 行薄封装 | `SharedRouteSettingsScreen.kt` 671 行 | **正确的样板** |
| 节点编辑 | 89 行薄封装 | `SharedEditScreen.kt` 350 行 | **正确的样板** |

路由设置与节点编辑证明这条路走得通：**Android 退化为薄封装 + 注入本地化标签**。问题在于只有这两个屏幕做到了。

### 3.2 存在逐字节复制的重复代码

```
androidApp/.../dto/EntityMappers.kt          56 行
shared/.../dto/EntityMappers.kt              56 行   ← diff 完全一致
androidApp/.../repository/RoomNodeRepository.kt   70 行
shared/.../repository/RoomNodeRepository.kt       71 行   ← 只多一行注释
```

而那行注释写的是 `Android keeps app-local copy` —— 明知重复仍然复制。`androidApp` 已经 `implementation(project(":shared"))` 了，完全没有理由再抄一份。同类还有 `KoinQualifiers`（`AppDataDiModule.kt:34` vs `shared/.../KoinQualifiers.kt:4`）、`AndroidSubscriptionRepository` vs `KmpSubscriptionRepository`。

`androidApp` 至今 **10,914 行**（全仓最大模块），而计划里它应该是个薄壳。

### 3.3 实际架构与计划架构的偏离

`KMP_MIGRATION_PLAN.md:77-167` 规划的目标结构中，以下**一个都不存在**：

| 计划 | 现状 |
|---|---|
| `build-logic/convention/` | 不存在，也没有 `buildSrc`。10 个模块各自重复 `compileSdk = 36` / `minSdk = 28` / 三个 iOS target / JVM 11，约 200 行复制粘贴 |
| `feature/{home,nodes,subscriptions,settings,logs,qrcode}` | 不存在，全部 UI 堆进 `:shared` 单模块 |
| `core/model` | 模型放在 `:domain/model` |
| `platform/{notification,system}` | 不存在 |
| `domain/src/commonTest`「全平台单元测试」 | 不存在 |
| Kermit 日志、kotlinx-datetime | 未引入 |

另有反向依赖：`domain/build.gradle.kts:24` 依赖 `:core:datastore`，导致 `AbstractConfigParser.kt:3-9` 直接 import `datastore.DomainStrategy` / `RoutingMode` / `decodeRules`。领域层依赖基础设施层是典型的分层倒置。

---

## 4. 性能与正确性问题清单（节选高价值项）

| 级别 | 问题 | 位置 | 修法 |
|---|---|---|---|
| BLOCKER | `runBlocking` 阻塞在 Koin 单例工厂内读 DataStore | `androidApp/.../AppNetworkDiModule.kt:25` | 改为 suspend provider，把 settings 读取推到下载协程内 |
| MAJOR | 组合期改 ViewModel 状态（`isAtBottom` 回调里 `hide/showNavigationBar`），滚动每帧触发全树重组 | `ConfigScreen.kt:417-418` | 移到 `snapshotFlow{}.distinctUntilChanged()` |
| MAJOR | `ORDER BY :subscriptionId ASC` 按绑定常量排序，等于没排序 | `NodeDao.kt:55` | 改 `ORDER BY subscriptionId ASC` 或删掉 |
| MAJOR | `NodeEntity` 对 `subscriptionId` / `favorite` / `selected` **无索引**，全表扫描 | `core/database/.../NodeEntity.kt:6` | 补 `@Index` |
| MAJOR | Room 全表 Flow + 内存过滤排序（`.reversed().filter{}`），搜索每次按键 O(n) 拷贝 | `XrayViewmodel.kt:104-124`、`DefaultConfigComponent.kt:40-57` | 下推到 DAO `@Query` |
| MAJOR | `RoomNodeRepository` 的 `map{}` 无 `distinctUntilChanged`，Room 重发即触发下游 combine 风暴 | `RoomNodeRepository.kt:14-18` | 加 `distinctUntilChanged()` |
| MAJOR | iOS 流量统计 3 秒轮询 App Group，**断开也不停**，跑满 App 生命周期 | `IosTrafficStatsSource.kt:22-28` | 随 VPN 状态起停 + `WhileSubscribed` |
| MAJOR | 批量测速并发 32，每个都做完整 config parse + native 测延迟 | `XrayViewmodel.kt:520-548` | 降到 4–8，断开时取消 |
| MAJOR | `AppLogStore` 每行日志 `(current + line).takeLast(N)`，每行 O(n) 拷贝 | `common/.../AppLogStore.kt:21` | 换 `ArrayDeque` 环形缓冲 |
| MAJOR | `AppInfoRepository` 在 `init` 注册 BroadcastReceiver，永不反注册 | `AppInfoRepository.kt:86-108` | 提供 `dispose()` |
| MAJOR | iOS VPN 状态观察者 `addObserverForName` 永不移除 | `IosVpnController.kt:161-169` | 保存 token 并移除 |
| MAJOR | `connect()` 返回 `runCatching{}.isSuccess`，未检查 `startTunnelWithOptions` 的 `NSError` 出参，永远报成功 | `IosVpnController.kt:58-68,122-128` | 检查 NSError 并上抛 |
| MAJOR | 订阅解析串行，大订阅长时间占用协程 | `KmpSubscriptionRepository.kt:78-87` | 限流 `async`/`awaitAll` |
| MAJOR | iOS Room 数据库落在 `NSDocumentDirectory`，**不在** App Group；而 Settings/geo 都在 App Group | `IosXrayDatabaseFactory.kt:33-41` | 统一到 App Group 容器 |
| MAJOR | utun fd 依赖私有 KVC `socket.fileDescriptor` | `HevSocks5TunnelHelper.swift:21-22` | 高 iOS 版本可能失效，需兜底 |
| MINOR | 多处 `items()` 缺 `key=` | `AppsScreen.kt:257`、`LogcatScreen.kt:189`、`SharedAppLogScreen.kt:128`、`SharedAppsInfoScreen.kt:107`、`SharedRouteSettingsScreen.kt:283` | 补稳定 key |
| 待定 | `fetchAndSaveNodes` 里 `deleteLinkBySubscriptionId(0)` 意图不明 | `KmpSubscriptionRepository.kt:57-58` | `SUB_MANUAL=-1`、Room 自增从 1 起，故 `0` 只可能是遗留孤儿行。**不是数据丢失**，但需注释说明 + 补测试 |

`stateIn(WhileSubscribed(5000))`、搜索 `debounce(300)`、主列表 `key=`、日志正则预编译、Swift 侧 `[weak self]` 都做对了——基本功不差，问题集中在"没有全局一致性检查"。

---

## 5. i18n：iOS 目前是英文单语

Android 有 4 套语言（默认 203 条 / zh-rCN 187 / ko 199 / ru-rRU 199）。共享 UI 采用 `*UiLabels` 数据类 + 硬编码英文默认值的方案，共 191 个字段：

```
shared/.../ui/home/HomeUiLabels.kt:3-14
data class HomeUiLabels(
    val connectedLabel: String = "Connected",
    ...
)
```

Android 通过 `stringResource` 注入本地化值（`SettingsScreen.kt:174-199` 是正确样板），而 iOS 的 `RootContent.kt:122-124` 直接用 `SettingsUiLabels()` / `ConfigUiLabels()` / `EditUiLabels()` 的默认构造 —— **zh-CN / ko / ru 用户在 iOS 上看到的全是英文**。

值得注意：`compose-resources` 插件其实已经随 Compose Multiplatform 生效（构建日志里有 `generateResourceAccessorsForIosMain` 任务，只是 `NO-SOURCE`）。基础设施在，只是没用。

---

## 6. 文档健康度：漂移严重

### 6.1 `AGENT.md` 描述的是迁移**前**的项目

| 章节 | 声称 | 实际 |
|---|---|---|
| §1 | "Android VPN client"、MVVM + **Dagger 2** | KMP 多平台；Koin 完全接管 |
| §1 | 版本 `1.6.3` / `32` | `gradle.properties:29-30` → **`1.6.4` / `33`** |
| §2 | Kotlin **2.0.21**、KSP **2.0.21-1.0.27** | `libs.versions.toml:9,17` → **2.1.10**、**2.1.10-1.0.31** |
| §4 | 最有价值的测试是 `AbstractConfigParserTest.kt` | **该文件从未存在** |
| §5 | `parser/`、`model/`、`dao/`、`repository/` 在 `androidApp/` 下 | 已迁至 `:domain`、`:core:database`（androidApp 下这些目录已空） |
| §5 | 模块依赖只有 `:androidApp → :tun2socks → :common` | 还有 `:shared`、`:domain`、`:core:*`、`:platform:vpn` |
| §7 | DI：在 `di/` 下注册 **Dagger** module | Koin module |
| §8 | CI 只有 3 个 workflow | 漏了 `ios-shared.yml` |

讽刺的是 `AGENT.md` §11 自己规定"同一个 PR 内必须更新本文档"，而这条规则在 75 次提交里被完整违反。

### 6.2 72 份 handover + 1045 行计划 = 虚假的完整感

- `FILE_MIGRATION_MAP.md` 抽查 10 条命中率约 **3/10**：仍在引用 `app/` 旧路径、`core:model`、`feature/home` 等从未建立的目标。
- 计划里 Phase 0 的 checkbox（`:234-237`）至今未勾选，而 Phase 0 本该是最先完成的基础设施。
- 每一份 handover 的"人工验证清单"都是空的（`STEP69:58-67`、`STEP72:68-77`）。
- Stage E.6x 在计划表里标 ✅，但对应 handover 的验证项全未勾。

**结论：文档体量掩盖了验证缺口。** 建议把 72 份 handover 归档，收敛成一份"活的状态文档"。

### 6.3 CI 形同虚设

| Workflow | 实际验证内容 | 判断 |
|---|---|---|
| `android.yml` | 只 `assembleRelease`，不跑测试；且**只在 `main` 触发**，`feat/migrateToKMP` 上根本不跑 | 不足 |
| `ios-shared.yml` | 仅 `:shared:compileKotlinIosSimulatorArm64` | 象征性；且很可能在干净检出上**直接失败**——`core/native-bridge/build.gradle.kts:8-12` 强制要求 `LibXrayLite.xcframework`，该文件被 gitignore 且 workflow 未构建它 |

### 6.4 `.gitignore` 缺 `.kotlin/`

`git status` 有近百个未跟踪的 `.kotlin/**.klib` 构建产物，根 `.gitignore` 只覆盖了 `.gradle/` 与 `build/`。

---

## 7. 后续移植必须遵守的规则（本报告的核心交付物）

以下每条都对应上面一个已发生的具体问题。**建议把本节内容摘进 `AGENT.md`，让后续 AI 会话默认遵守。**

### R-1　先让"共享"这件事名副其实，再继续加屏幕

> 现状是每加一个屏幕就同时增加"Android 一份 + shared 一份"的漂移面积。在把 Android 收敛到 `RootContent` 之前，新增共享屏幕的边际收益是负的。

- **不要**再新建"Android 版 + Shared 版"并行实现。新屏幕一律：逻辑进 `:shared`，Android 侧写成**薄封装 + 注入 `stringResource` 标签**（照 `RouteSettingsScreen.kt` 41 行 / `EditScreen.kt` 89 行）。
- 已存在的并行实现按此顺序收敛：`SubscriptionScreen` → `XrayBottomNav` → `LogcatScreen` → `AppsScreen`。
- 提交前自查：`rg -l 'com.android.xrayfa.shared.ui' androidApp/src/main/java` 的结果应只增不减。

### R-2　跨平台代码只允许存在一份

- 新增 `commonMain` 代码后，**必须**删除 `androidApp` 里的对应副本，而不是留一份"app-local copy"。
- 每次提交前跑一次重复检查：
  ```bash
  for f in $(cd shared/src/commonMain/kotlin && find . -name '*.kt'); do
    a="androidApp/src/main/java/${f#./}"
    [ -f "$a" ] && echo "DUPLICATE: $a"
  done
  ```
- 待清理清单：`dto/EntityMappers.kt`、`repository/RoomNodeRepository.kt`、`KoinQualifiers`、`AndroidSubscriptionRepository` vs `KmpSubscriptionRepository`。

### R-3　任何"iOS 侧 actual"不得以抛异常/返回常量结案

`IosXrayConfigEncoder`（抛异常）、`IosStubTunBridge`（恒 false）、`IosGeoIpProvider`（恒 `""`）、`IosDigestCalculatorStub`（空摘要）、`measureOutboundDelay`（恒 `-1L`）——这类桩会让上层"看起来接通了"，实际功能是死的，且**编译器不会报警**。

- 桩必须在同一 PR 里登记进一份 `docs/IOS_STUBS.md` 清单，注明阻塞的用户可见功能。
- 严禁在桩存在的情况下把对应 Stage 标记为 ✅。

### R-4　业务逻辑进 `commonTest`，而不是 `androidUnitTest`

- `domain` / `common` 的 `commonTest.dependencies` 已声明，**直接建目录用起来**。
- 优先补：`AbstractConfigParser` 及 8 个协议 parser 的配置生成测试（这是 App 的核心资产，目前 0 覆盖）。
- 每个 parser 至少一条"分享链接 → 期望 JSON"的黄金用例。
- Gson → kotlinx 的每一步迁移都要先有输出对比测试，照 `RoutingObjectSerializationTest` 的写法。
- 验收命令是 `./gradlew allTests`（含 iOS 模拟器），不是 `./gradlew test`。

### R-5　CI 必须真正拦得住问题

最小改造：

1. `android.yml` 的 `on.push.branches` 加上 `feat/**`，并加 `./gradlew test`。
2. `ios-shared.yml` 先跑 `scripts/build_libxray_ios.sh`（或缓存 xcframework），再编译；否则这个 job 的绿灯没有意义。
3. 增加 `./gradlew allTests` 与 iOS 端 Xcode 构建（至少 `xcodebuild -scheme iosApp -sdk iphonesimulator build`）。

### R-6　依赖与构建配置的硬规矩

- 版本号**只**写在 `gradle/libs.versions.toml`（`AGENT.md` §7 的规定）。当前违规共 10 处：
  - `kotlinx-coroutines-core:1.9.0` 硬编码在 **7 个模块**——`common:22`、`domain:27`、`core/network:25`、`core/database:24`、`core/datastore:25`、`core/native-bridge:53`、`platform/vpn:23`。而这个依赖**根本没进 catalog**，是最该先补的一条。
  - `kotlinx-serialization-json:1.7.3` 硬编码 3 处——`domain:26`、`core/datastore:26`、`androidApp:200`；而 catalog 里已有 `libs.kotlinx.serialization.json`，`shared/build.gradle.kts:51` 用的就是正确写法。
  - （`androidApp:218` 的 LeakCanary 是 `AGENT.md` §7 明确豁免的既有例外，不计入违规。）
- 尽早引入 `build-logic/convention`，把 8 个模块重复的 KMP + Android 配置块收敛掉；每新增一个模块，这笔债就多一份。
- 动 R8 相关代码（Koin/Decompose/序列化/反射）后，必须实跑一次 `assembleRelease` 并冒烟测试，不能只看 debug。

### R-7　分层纪律

- `:domain` 不得依赖任何 `:core:*` / `:platform:*`。当前 `:domain → :core:datastore` 需拆解：把 `DomainStrategy` / `RoutingMode` / 规则编解码的**接口**下沉到 `:domain` 或 `:common`，DataStore 实现留在 `:core:datastore`。
- Repository 实现不应住在 UI 伞模块 `:shared` 里。建议新建 `:core:data` 承接 `RoomNodeRepository` / `KmpSubscriptionRepository` / `EntityMappers`。
- 组件工厂避免 `KoinPlatform.getKoin().get()` 服务定位（`HomeComponentFactory.kt:8-11`、`RootContent.kt:128`），改构造注入；并补 Koin `verify()`，目前 DI 图正确性完全没有校验。

### R-8　iOS 平台一致性

- 所有跨进程共享的持久化都必须落在 App Group 容器。Room 数据库现在没有（`IosXrayDatabaseFactory.kt:33-41`），需与 `SettingsDataStoreFactory.ios.kt:29-35` 对齐。
- NE 侧任何失败都要能传回 App：检查 `startTunnelWithOptions` 的 `NSError`，并考虑用 `sendProviderMessage` 回传日志/状态（目前 `TunnelCoreCallbackHandler.swift:6-16` 把 Xray 状态直接吞掉）。
- 注意 NE 的 15MB 内存上限，Go runtime 需要调 GOGC；目前没有任何内存监控。

### R-9　i18n 从第一天就做，不要"先硬编码回头再说"

- `*UiLabels` 的英文默认值是技术债的温床：Android 注入了本地化、iOS 静默 fallback 到英文，而**编译和测试都不会报错**。
- 建议改用 `compose-resources`（插件已生效，只是无资源），把 4 套 `strings.xml` 迁进 `commonMain/composeResources`，双端共用同一套 key。
- 过渡期底线：新增 `UiLabels` 字段时，同一 PR 内必须同时更新 4 个 `strings.xml` 与 iOS 的标签注入点。

### R-10　文档同 PR 更新，并停止增产 handover

- `AGENT.md` §1/§2/§4/§5/§6/§7/§8 已全面失效，需要一次性重写（这是 §11 自己定的规则）。
- `FILE_MIGRATION_MAP.md` 命中率约 3/10，要么修正要么标记废弃。
- 把 72 份 handover 移进 `docs/archive/`，只维护一份含真实勾选状态的活文档。
- 任何 Stage 标 ✅ 前，对应的人工验证清单必须真的勾完。

---

## 8. 建议的修复顺序

| 阶段 | 内容 | 解锁的价值 |
|---|---|---|
| **P0** | 补全 `@Serializable` + 实现 iOS `XrayConfigEncoder`（先写 Gson 对比测试）；删掉 `println(config)` | iOS 首次真正能连接，同时堵住凭据泄露 |
| **P0** | 建 `commonTest`，补 parser / 配置生成黄金用例 | 后续所有重构才有安全网 |
| **P1** | CI 加 `allTests` + 修 `ios-shared.yml` 的 xcframework 前置 | 回归能被自动拦住 |
| **P1** | 去重（R-2 清单）+ 拆 `:core:data` + 断 `:domain → :core:datastore` | 停止债务增长 |
| **P2** | Android 收敛到 `RootContent`，按 R-1 顺序迁移四个屏幕 | UI 还原度与 iOS 同步提升，两套壳合一 |
| **P2** | 引入 `build-logic`；补 Room 索引 / DAO 下推过滤 / iOS 轮询起停 / 内存泄漏修复 | 性能与可维护性 |
| **P3** | `compose-resources` 统一 i18n；iOS 深色模式接 `state.darkMode`；重写 `AGENT.md` | 体验与文档收口 |

---

## 附：量化快照

| 模块 | 文件数 | Kotlin 行数 |
|---|---:|---:|
| `androidApp` | 80 | 10,914 |
| `shared` | 105 | 7,963（commonMain 7,235 / iosMain 605 / androidMain 123） |
| `domain` | 52 | 2,459 |
| `common` | 25 | 1,272 |
| `core/datastore` | 8 | 589 |
| `core/network` | 11 | 319 |
| `platform/vpn` | 5 | 302 |
| `core/database` | 9 | 276 |
| `core/native-bridge` | 13 | 267 |
| `tun2socks` | 5 | 178 |

- `@Composable` 数量：androidApp 77 / shared 63
- `expect` 声明仅 9 个（平台抽象主要靠接口 + Koin，这个选择本身是现代且合理的）
- 单元测试 26 个（3 个为模板），`commonTest` 0 个
- Xray 配置模型 22 个文件中仅 3 个 `@Serializable`
