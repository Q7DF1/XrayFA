# KMP 迁移交接文档 — Step 76 / 期中 R-2 去重（2026-08-20）

本文档记录期中评审 **R-2**：跨平台代码只允许存在一份。删除 `androidApp` 里与 `:shared` 重复的 repository / mapper / Koin qualifier，Android 改为直接使用共享实现。

**前置**：Step 73–75 已 commit。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `androidApp/.../dto/EntityMappers.kt` | **删除** — 与 `shared/.../dto/EntityMappers.kt` 逐字节重复 |
| `androidApp/.../repository/RoomNodeRepository.kt` | **删除** — 与 shared 仅差一行过时注释 |
| `androidApp/.../repository/AndroidSubscriptionRepository.kt` | **删除** — 与 `KmpSubscriptionRepository` 仅差 `Log.i` vs `Logger` |
| `androidApp/.../di/AppDataDiModule.kt` | `NodeRepository` 仍 `RoomNodeRepository`；`SubscriptionRepository` 改为 `KmpSubscriptionRepository(logger = get())`；去掉内联 `KoinQualifiers` |
| `androidApp/.../di/AppCoreDiModule.kt` 等 | `import com.android.xrayfa.shared.di.KoinQualifiers` |
| `shared/.../RoomNodeRepository.kt` | 注释改为双端共用，去掉 “Android keeps app-local copy” |
| `shared/.../KmpSubscriptionRepository.kt` | 注释改为双端共用 |

### 未改动

- 未新建 `:core:data`（期中建议的模块搬家，下一步）
- 未给 `RoomNodeRepository` 加 `distinctUntilChanged`（期中 MAJOR，不塞进本步）
- `KoinQualifiers` 仍住在 `:shared`（`shared.di`），未下沉到 `:common`

---

## 设计决策

1. **先删副本，再拆模块** — R-2 清单可以立刻停掉漂移；`:core:data` 是分层问题（R-7），单独一步。
2. **Android 接 `KmpSubscriptionRepository`** — `Logger` 已在 `appPlatformDiModule` 绑 `AndroidLogger`，不必再留一套 `Log.i` 实现。
3. **Qualifier 用 shared 的对象** — 字符串仍是 `MainScope` / `BackgroundScope`，与 iOS 一致。Android `Dispatchers.Main.immediate` 仍只在 `appCoroutineDiModule`。

---

## 验证状态

```bash
./gradlew :androidApp:compileDebugKotlin :domain:testDebugUnitTest
# BUILD SUCCESSFUL（exit 0）

# 期中 R-2 重复检查
for f in $(cd shared/src/commonMain/kotlin && find . -name '*.kt'); do
  a="androidApp/src/main/java/${f#./}"
  [ -f "$a" ] && echo "DUPLICATE: $a"
done
# 无输出（no androidApp copies of shared/commonMain files）
```

- [x] `:androidApp:compileDebugKotlin` 通过
- [x] `:domain:testDebugUnitTest` 通过
- [x] shared/commonMain 相对 androidApp 的同路径 `.kt` 副本为 0
- [ ] 真机/模拟器：订阅更新、节点列表、收藏（人工）

---

## 期中后进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| 73 | domain commonTest parser 黄金用例 | ✅ |
| 74 | CI 跑共享模块测试 | ✅ |
| 75 | unpublished submodule SHA + SSH URL | ✅ |
| 76 | R-2 去重 androidApp 副本 | ✅ 本步 |
| 77 | 拆 `:core:data` / 断 `:domain → :core:datastore` | ⬜ |

---

## 手动验证清单

### Android
- [ ] 启动后节点列表加载
- [ ] 订阅 URL 更新能写入节点
- [ ] 选中 / 收藏 / 删除节点
- [ ] 连接 VPN（回归：DI 图仍能解析 `NodeRepository` / `SubscriptionRepository` / Logger）

### iOS
- [ ] 无需新构建即可（本步未改 iosMain 逻辑；`KmpSubscriptionRepository` 原本就是 iOS 在用）

---

## Commit 建议（确认后执行）

```
refactor(kmp): delete androidApp copies of shared repositories
```
