# 模块拆分与文件迁移映射

本文档列出现有代码文件到新 KMP 模块的具体迁移映射关系。

---

## 迁移映射总览

### core:model（纯数据模型，无平台依赖）

| 源文件 | 目标路径 | 备注 |
|--------|----------|------|
| `app/.../model/XrayConfiguration.kt` | `core/model/src/commonMain/.../XrayConfiguration.kt` | 添加 @Serializable |
| `app/.../model/OutboundObject.kt` | `core/model/src/commonMain/.../OutboundObject.kt` | @SerializedName → @SerialName |
| `app/.../model/InboundObject.kt` | `core/model/src/commonMain/.../InboundObject.kt` | |
| `app/.../model/RoutingObject.kt` | `core/model/src/commonMain/.../RoutingObject.kt` | |
| `app/.../model/DnsObject.kt` | `core/model/src/commonMain/.../DnsObject.kt` | |
| `app/.../model/LogObject.kt` | `core/model/src/commonMain/.../LogObject.kt` | |
| `app/.../model/PolicyObject.kt` | `core/model/src/commonMain/.../PolicyObject.kt` | |
| `app/.../model/StatsObject.kt` | `core/model/src/commonMain/.../StatsObject.kt` | |
| `app/.../model/ApiObject.kt` | `core/model/src/commonMain/.../ApiObject.kt` | |
| `app/.../model/ReverseObject.kt` | `core/model/src/commonMain/.../ReverseObject.kt` | |
| `app/.../model/ObservatoryObject.kt` | `core/model/src/commonMain/.../ObservatoryObject.kt` | |
| `app/.../model/BurstObservatoryObject.kt` | `core/model/src/commonMain/.../BurstObservatoryObject.kt` | |
| `app/.../model/MetricsObject.kt` | `core/model/src/commonMain/.../MetricsObject.kt` | |
| `app/.../model/FakeDNSObject.kt` | `core/model/src/commonMain/.../FakeDNSObject.kt` | |
| `app/.../model/Version.kt` | `core/model/src/commonMain/.../Version.kt` | |
| `app/.../model/BugReportData.kt` | `core/model/src/commonMain/.../BugReportData.kt` | |
| `app/.../model/protocol/Protocol.kt` | `core/model/src/commonMain/.../Protocol.kt` | |
| `app/.../model/stream/StreamSettingsObject.kt` | `core/model/src/commonMain/.../stream/StreamSettingsObject.kt` | |
| `app/.../model/stream/TlsSettings.kt` | `core/model/src/commonMain/.../stream/TlsSettings.kt` | |
| `app/.../model/stream/WsSettings.kt` | `core/model/src/commonMain/.../stream/WsSettings.kt` | |
| `app/.../model/stream/GrpcSettings.kt` | `core/model/src/commonMain/.../stream/GrpcSettings.kt` | |
| `app/.../model/stream/KcpSettings.kt` | `core/model/src/commonMain/.../stream/KcpSettings.kt` | |
| `app/.../model/stream/RealitySettings.kt` | `core/model/src/commonMain/.../stream/RealitySettings.kt` | |
| `app/.../model/stream/RowSettings.kt` | `core/model/src/commonMain/.../stream/RowSettings.kt` | |
| `app/.../dto/Node.kt` | `core/model/src/commonMain/.../Node.kt` | 分离 Entity 和 Domain Model |
| `app/.../dto/Subscription.kt` | `core/model/src/commonMain/.../Subscription.kt` | |
| `app/.../dto/Link.kt` | `core/model/src/commonMain/.../Link.kt` | |
| `app/.../dto/ProtocolConfigs.kt` | `core/model/src/commonMain/.../ProtocolConfigs.kt` | |

### domain（纯业务逻辑）

| 源文件 | 目标路径 | 备注 |
|--------|----------|------|
| `app/.../parser/AbstractConfigParser.kt` | `domain/src/commonMain/.../parser/ProtocolParser.kt` | 重构为 interface |
| `app/.../parser/VLESSConfigParser.kt` | `domain/src/commonMain/.../parser/VlessParser.kt` | 纯字符串解析 |
| `app/.../parser/VMESSConfigParser.kt` | `domain/src/commonMain/.../parser/VmessParser.kt` | |
| `app/.../parser/TrojanConfigParser.kt` | `domain/src/commonMain/.../parser/TrojanParser.kt` | |
| `app/.../parser/ShadowSocksConfigParser.kt` | `domain/src/commonMain/.../parser/ShadowsocksParser.kt` | |
| `app/.../parser/Hysteria2ConfigParser.kt` | `domain/src/commonMain/.../parser/Hysteria2Parser.kt` | |
| `app/.../parser/ParserFactory.kt` | `domain/src/commonMain/.../parser/ParserFactory.kt` | |
| `app/.../parser/SubscriptionParser.kt` | `domain/src/commonMain/.../subscription/SubscriptionDecoder.kt` | |
| `app/.../repository/NodeRepository.kt` | `domain/src/commonMain/.../repository/NodeRepository.kt` | 提取接口 |
| `app/.../repository/SubscriptionRepository.kt` | `domain/src/commonMain/.../repository/SubscriptionRepository.kt` | 提取接口 |

### core:database（Room KMP）

| 源文件 | 目标路径 | 备注 |
|--------|----------|------|
| `app/.../dao/XrayFADatabase.kt` | `core/database/src/commonMain/.../XrayDatabase.kt` | |
| `app/.../dao/NodeDao.kt` | `core/database/src/commonMain/.../NodeDao.kt` | |
| `app/.../dao/SubscriptionDao.kt` | `core/database/src/commonMain/.../SubscriptionDao.kt` | |
| `app/.../dao/LinkDao.kt` | `core/database/src/commonMain/.../LinkDao.kt` | |
| (新增) | `core/database/src/androidMain/.../DatabaseFactory.kt` | Android DB 工厂 |
| (新增) | `core/database/src/iosMain/.../DatabaseFactory.kt` | iOS DB 工厂 |

### core:network（Ktor）

| 源文件 | 目标路径 | 备注 |
|--------|----------|------|
| `app/.../di/NetworkModule.kt` (部分) | `core/network/src/commonMain/.../HttpClientFactory.kt` | 提取网络客户端创建 |
| (新增) | `core/network/src/commonMain/.../SubscriptionFetcher.kt` | 订阅拉取 |
| (新增) | `core/network/src/commonMain/.../DelayTester.kt` | 延迟测试 |
| (新增) | `core/network/src/androidMain/.../OkHttpEngine.kt` | Android 引擎 |
| (新增) | `core/network/src/iosMain/.../DarwinEngine.kt` | iOS 引擎 |

### core:datastore（DataStore KMP）

| 源文件 | 目标路径 | 备注 |
|--------|----------|------|
| `common/src/main/.../SettingsState.kt` (部分) | `core/datastore/src/commonMain/.../PreferencesDataSource.kt` | |
| (新增) | `core/datastore/src/androidMain/.../DataStoreFactory.kt` | |
| (新增) | `core/datastore/src/iosMain/.../DataStoreFactory.kt` | |

### core:native-bridge

| 源文件 | 目标路径 | 备注 |
|--------|----------|------|
| `app/.../core/XrayCoreManager.kt` | `core/native-bridge/src/commonMain/.../XrayBridge.kt` | 提取接口 |
| `tun2socks/src/...` | `core/native-bridge/src/commonMain/.../TunBridge.kt` | 提取接口 |
| `app/.../core/XrayCoreManager.kt` | `core/native-bridge/src/androidMain/.../AndroidXrayBridge.kt` | actual 实现 |
| `tun2socks/src/...` | `core/native-bridge/src/androidMain/.../AndroidTunBridge.kt` | actual 实现 |
| (新增) | `core/native-bridge/src/iosMain/.../IosXrayBridge.kt` | iOS actual |
| (新增) | `core/native-bridge/src/iosMain/.../IosTunBridge.kt` | iOS actual |

### platform:vpn

| 源文件 | 目标路径 | 备注 |
|--------|----------|------|
| `app/.../core/XrayBaseService.kt` | `platform/vpn/src/androidMain/.../XrayVpnService.kt` | 保留 Android 实现 |
| `app/.../core/XrayBaseServiceManager.kt` | `platform/vpn/src/androidMain/.../AndroidVpnController.kt` | 实现 VpnController |
| `app/.../core/TrafficDetector.kt` | `platform/vpn/src/androidMain/.../TrafficDetector.kt` | Android 特有 |
| `app/.../core/StartOptions.kt` | `platform/vpn/src/commonMain/.../VpnConfig.kt` | 可共享 |
| (新增) | `platform/vpn/src/commonMain/.../VpnController.kt` | 接口定义 |
| (新增) | `platform/vpn/src/commonMain/.../VpnState.kt` | 状态定义 |
| (新增) | `platform/vpn/src/iosMain/.../IosVpnController.kt` | iOS 实现 |

### platform:notification

| 源文件 | 目标路径 | 备注 |
|--------|----------|------|
| `app/.../helper/NotificationHelper.kt` | `platform/notification/src/androidMain/.../AndroidNotificationController.kt` | |
| (新增) | `platform/notification/src/commonMain/.../NotificationController.kt` | 接口 |
| (新增) | `platform/notification/src/iosMain/.../IosNotificationController.kt` | |

### platform:system

| 源文件 | 目标路径 | 备注 |
|--------|----------|------|
| `app/.../utils/Device.kt` | `platform/system/src/commonMain/.../DeviceInfo.kt` | expect/actual |
| (新增) | `platform/system/src/commonMain/.../ClipboardManager.kt` | 接口 |
| (新增) | `platform/system/src/commonMain/.../PermissionManager.kt` | 接口 |

### feature:home

| 源文件 | 目标路径 | 备注 |
|--------|----------|------|
| `app/.../ui/component/HomeScreen.kt` | `feature/home/src/commonMain/.../ui/HomeScreen.kt` | Compose Multiplatform |
| `app/.../ui/component/HomeScreenV2.kt` | `feature/home/src/commonMain/.../ui/HomeScreenV2.kt` | |
| `app/.../viewmodel/XrayViewModel.kt` (部分) | `feature/home/src/commonMain/.../DefaultHomeComponent.kt` | ViewModel → Component |

### feature:nodes

| 源文件 | 目标路径 | 备注 |
|--------|----------|------|
| `app/.../ui/component/NodeCard.kt` | `feature/nodes/src/commonMain/.../ui/NodeCard.kt` | |
| `app/.../ui/component/EditScreen.kt` | `feature/nodes/src/commonMain/.../ui/EditScreen.kt` | |
| `app/.../ui/component/ConfigScreen.kt` | `feature/nodes/src/commonMain/.../ui/ConfigScreen.kt` | |

### feature:settings

| 源文件 | 目标路径 | 备注 |
|--------|----------|------|
| `app/.../ui/component/SettingsScreen.kt` | `feature/settings/src/commonMain/.../ui/SettingsScreen.kt` | |
| `app/.../ui/component/RouteSettingsScreen.kt` | `feature/settings/src/commonMain/.../ui/RouteSettingsScreen.kt` | |
| `app/.../ui/component/AppsScreen.kt` | `feature/settings/src/commonMain/.../ui/AppsScreen.kt` | 需 expect/actual |

### feature:subscriptions

| 源文件 | 目标路径 | 备注 |
|--------|----------|------|
| `app/.../ui/component/SubscriptionScreen.kt` | `feature/subscriptions/src/commonMain/.../ui/SubscriptionScreen.kt` | |

### feature:logs

| 源文件 | 目标路径 | 备注 |
|--------|----------|------|
| `app/.../ui/component/LogcatScreen.kt` | `feature/logs/src/commonMain/.../ui/LogsScreen.kt` | logcat → 通用日志 |

### feature:qrcode

| 源文件 | 目标路径 | 备注 |
|--------|----------|------|
| `app/.../ui/component/ScanQRScreen.kt` | `feature/qrcode/src/commonMain/.../ui/QrScannerScreen.kt` | 壳 |
| `app/.../utils/BarcodeUtils.kt` | `feature/qrcode/src/commonMain/.../QrCodeGenerator.kt` | 生成逻辑可共享 |
| (新增) | `feature/qrcode/src/androidMain/.../AndroidQrScanner.kt` | CameraX |
| (新增) | `feature/qrcode/src/iosMain/.../IosQrScanner.kt` | AVFoundation |

### app-android（应用壳，保留 Android 特有代码）

| 源文件 | 保留/迁移 | 备注 |
|--------|-----------|------|
| `app/.../MainActivity.kt` | **保留** (重构) | 入口，Koin 获取 RootComponent |
| `app/.../XrayFAApplication.kt` | **保留** (重构) | Koin 初始化 |
| `app/.../BootBroadcastReceiver.kt` | **保留** | Android 特有 |
| `app/.../core/QuickStartTileService.kt` | **保留** | Android Quick Settings |
| `app/.../ComponentResolver.kt` | **删除** | Dagger 相关，Koin 替代 |
| `app/.../XrayAppCompatFactory.kt` | **删除** | Dagger 相关 |
| `app/.../di/` (全部) | **删除** | 被 Koin 模块替代 |

### 共享 UI 资源

| 源文件 | 目标路径 | 备注 |
|--------|----------|------|
| `app/.../ui/theme/Color.kt` | `feature/home/src/commonMain/.../theme/Color.kt` | |
| `app/.../ui/theme/Theme.kt` | `feature/home/src/commonMain/.../theme/Theme.kt` | 调整为 Compose Multiplatform |
| `app/.../ui/theme/Type.kt` | `feature/home/src/commonMain/.../theme/Type.kt` | |
| `app/.../ui/component/CommonWedigt.kt` | `feature/home/src/commonMain/.../ui/CommonWidgets.kt` | 通用组件 |
| `app/.../ui/component/ArcBottomShape.kt` | `feature/home/src/commonMain/.../ui/ArcBottomShape.kt` | 纯 Compose |
| `app/.../ui/component/EditTextDialog.kt` | `feature/home/src/commonMain/.../ui/EditTextDialog.kt` | 纯 Compose |
| `app/.../ui/component/BugReportDialog.kt` | `feature/home/src/commonMain/.../ui/BugReportDialog.kt` | 纯 Compose |

---

## 迁移顺序建议

```
第 1 批 (Phase 1): core:model → core:common → domain
第 2 批 (Phase 2): core:database → core:datastore → core:network
第 3 批 (Phase 3): core:native-bridge → platform:vpn → platform:notification → platform:system
第 4 批 (Phase 4): feature:home → feature:nodes → feature:settings → feature:subscriptions → feature:logs → feature:qrcode
第 5 批 (Phase 4): shared (汇聚模块)
第 6 批 (Phase 5): androidApp (精简) + iosApp (新建)
```

每一批完成后，确保 Android 应用仍可正常运行。
