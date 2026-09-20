# 共享元素转场 —— 交付验机清单

对应分支实现：`8ff516d..HEAD`（12 个提交，Task 1–10）
计划：`docs/superpowers/plans/2026-09-19-shared-element-transitions.md`
设计：`docs/superpowers/specs/2026-09-19-shared-element-transitions-design.md`

## 为什么需要这份清单

这个分支的**全部装机验证都被推迟到最后一次性做**，目前一次都没跑过。编译和单元测试是绿的，
但它们覆盖不到任何转场行为——这个项目没有 Compose UI 测试设施，转场的唯一可观测形式是渲染出来的动画。
计划里有两个检查点被明确标记为「可能推翻架构」，都在下面的 P0 里。

安装包（已构建，对应当前 HEAD）：

```
androidApp\build\outputs\apk\debug\XrayFA-arm64-v8a-debug.apk
androidApp\build\outputs\apk\debug\XrayFA-universal-debug.apk
```

全程建议挂着 logcat，过滤 `NoSuchMethodError` 和 `NoSuchFieldError`。这两个是本项目记录在案的
风险：`androidApp` 依赖 androidx Compose BOM，Android 运行时由 androidx material3（1.5.0-alpha15）
胜出，而 `:shared` 是按 JetBrains material3 编译的。整个分支为此刻意避开了
`MaterialTheme.motionScheme`，把动效数值硬编码成 spring 常量。

---

## P0 —— 可能推翻架构的两项

这两项不过，后面的都不用看。

### 1. 返回动画起始卡顿（计划 Task 5 Step 4 第 7 项）

反复「首页 → 进设置 → 返回」十余次，**紧盯返回动画的起始瞬间**。

为什么危险：`ChildPages` 现在嵌在 `ChildStack` 的 `Idle` 分支里，每次压栈 `Idle` 都会被整棵释放，
返回时是一次全新组合。整个 tab 树的重建撞在返回动画的第一帧上。

如果卡顿明显：**停下，不要再往下测**。收敛办法见设计文档第 10 节「残余风险」——退回
`sharedElementWithCallerManagedVisibility` 方案，把 `ChildPages` 留在 `ChildStack` 外面。

### 2. 七个转场是否真的配对上了

key 不匹配是**静默失败**：没有动画，也没有任何报错，看起来就是普通的淡入淡出。
逐个确认「扩张成全屏」与「回缩到原位」：

1. Config 列表某行的编辑图标 → 全屏编辑页
2. 底栏放大镜 → 全屏搜索
3. 首页齿轮 → 设置页
4. Config 溢出按钮 →（菜单）→ 订阅页
5. Config 溢出按钮 →（菜单）→ 二维码扫描
6. Config 顶栏 Edit 图标 → 新建节点
7. 设置页的 Apps / 日志 / 路由 三行 → 各自子页

第 4 和第 5 项最可疑：这两个转场共用同一个按钮，靠在它上面**叠两层 `sharedContainer`**
（`ConfigTabScreen.kt:128-137`）实现。一个布局节点上挂两个 `sharedBounds`、任一时刻只有一个 key
能匹配——这个构造没有经过 API 探针验证，是整个分支里最不确定的地方。
**如果订阅和二维码里只有一个有动画，第一个该看的就是这里。**

排查手段（计划 Task 7 Step 7 给的）：在起点侧临时打日志

```kotlin
rememberSharedContentState(SharedContainerKey(TransitionDestinations.APPS)).isMatchFound
```

---

## P1 —— 验证前面几个任务的状态改动

这几项表面上测的是转场，实际测的是 Task 3 和 Task 5 的状态处理。

3. **列表滚动位置**：Config 列表滚到中部 → 点某行编辑 → 返回。
   回缩必须落回**原来那一行**。若列表悄悄回到顶部，容器会飞向屏幕外的错误位置。
   这是 Task 3 状态提升的直接检验。

4. **标题折叠状态**：滚动使标题折叠 → 进任意全屏页 → 返回 → 折叠状态没有重置。

5. **tab 保持**：停在 Config tab → 进设置 → 返回 → 仍在 Config tab。

6. **连接按钮不再乱弹**：首页 → 进设置 → 返回 → 连接按钮**没有**重放入场弹跳。
   Task 5 为此加了 `skipInitialBounce` 首帧保护（`HomeConnectButton.kt`）。

7. **key 唯一性**：点开两个不同的节点行 → 各自回缩到各自的位置，不会串。

8. **搜索页的输入法**：点放大镜 → 输入法自动弹出、光标在输入框。
   搜索页在 Task 2 从 `Dialog` 改成了栈目的地，改完之后就没在设备上见过。

9. **搜索查询不丢**：在搜索框输入 → 立刻返回 → Config 列表按最后输入的内容过滤。
   Task 2 曾有一个「防抖被取消导致最后几个按键丢失」的回归，已修（`SharedSearchScreen.kt`
   的 `DisposableEffect` 在销毁时把本地 query 冲刷给 component）。

---

## P2 —— 底栏与手势

10. **底栏 overlay**：进入任意全屏页时底栏**向下滑出 + 淡出**，返回时滑入。
    关键是它**不能跟着整页一起缩小**——底栏被
    `renderInSharedTransitionScopeOverlay` 提到了共享转场的 overlay 层，就是为了躲开
    `Idle` 子节点的 scale。若看到底栏跟着页面缩放，说明这个修饰符没生效。

11. **底栏不被容器盖住**：放大镜的圆形容器升起时，底栏应该在它上面（`zIndexInOverlay = 1f`）。

12. **预测返回手势**：在设置页、编辑页、搜索页、订阅页从屏幕边缘右划，**拖到一半松手**——
    容器要跟手回弹，不跳变。Task 5 把预测返回从 `predictiveBackAnimation` 换成了实验版
    `stackAnimation(predictiveBackParams = ...)` + `materialPredictiveBackAnimatable`。

13. **降级路径不崩**（这两处是**刻意**不加共享容器的，走淡入淡出是正确行为）：
    - 订阅页里点「扫码」进二维码
    - Config 空状态点「添加」进新建节点
    - 把列表滚到很远、让源行离开视口，再从编辑页返回

---

## P3 —— 收尾

14. 反复快速进出十余次：无残影、无元素卡在屏幕上、无 ANR。
15. 深色模式下逐个过一遍：容器底色正确，转场中途无白闪。
16. logcat 全程无 `NoSuchMethodError` / `NoSuchFieldError`。

---

## 仍然欠缺的验证（非装机）

- **iOS 一次都没编译过。** Windows 上 iOS target 默认关闭。
  `shared/src/iosMain/.../IosPlatformRootHooks.kt` 在 Task 7 改了三个 `override` 的签名，
  只做了肉眼比对。需要在 macOS 上执行：

  ```
  ./gradlew :shared:compileKotlinIosSimulatorArm64
  ```

  然后在 iOS 模拟器上把上面七个转场再过一遍。
- 已静态确认 `commonMain` 新增/改动文件没有 `^import android\.` 开头的 Android 专有引用。
