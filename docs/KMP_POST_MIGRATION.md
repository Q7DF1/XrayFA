# KMP 移植后 backlog

> Phase 9 已宣告移植收尾。本文只记**不要在发版收尾里做**的后续项。  
> 日期：2026-08-28。

## 规则

- 新功能只写 `:shared` + `PlatformRootHooks`。不要再开 Android 平行 UI。
- Android 是参考实现；iOS 对齐 Android。做不到的继续登记 [`IOS_STUBS.md`](./IOS_STUBS.md)。
- 不做 Agent Phase C / C1、iOS AppFunctions，除非产品单独立项。

## iOS（原 Step 112–116）

| 原步骤 | 内容 | 说明 |
|--------|------|------|
| 112 | geoip.dat / geosite.dat 文件导入 | 现为「开发中」 |
| 113 | 扫码相册 + 闪光灯 | 现仅 AVFoundation 实时相机 |
| 114 / 119 | Bug report | ✅ 共享表单 + GitHub issue URL |
| 115 | 分应用代理 | 系统 API 受限，先拍板 |
| 116 | overlay 系统返回 / NE 日志桥 | 靠手势；日志是进程内 `AppLogStore` |
| — | GeoLite 手动放入 App Group | 下载钮已关（宿主到不了 NE SOCKS） |

## Android 不做

- Home screen Widget、On-Demand VPN、Share Extension
- Agent Phase C（删节点/订阅、改路由、剪贴板导入、日志导出给 Agent）
- 三星 Live Update / Now Bar：One UI 开发者选项「允许所有应用的动态通知」；不改 `NotificationHelper`

## 页面保真（若以后要 90%）

- Config 全屏搜索条
- 滚动隐藏底栏
- Navigation3 共享元素转场
- 平板 Config list–detail

## 工程债

- `build-logic` convention plugin
- 拆 `feature/{home,nodes,subscriptions,settings,logs,qrcode}`
