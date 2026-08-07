# iOS 平台实现指南

本文档详细说明 XrayFA iOS 端的平台特定实现方案。

---

## 1. 项目结构

### Xcode 项目组织

```
iosApp/
├── iosApp/                          # 主应用 Target
│   ├── AppDelegate.swift
│   ├── ContentView.swift            # SwiftUI → ComposeUIViewController
│   ├── Info.plist
│   ├── PrivacyInfo.xcprivacy        # App Store 隐私清单
│   ├── Entitlements/
│   │   └── iosApp.entitlements      # VPN, App Group, Network Extension
│   └── Assets.xcassets/
│
├── PacketTunnel/                    # Network Extension Target
│   ├── PacketTunnelProvider.swift   # NEPacketTunnelProvider 实现
│   ├── Info.plist
│   └── PacketTunnel.entitlements
│
├── XrayWidget/                      # WidgetKit Target
│   ├── XrayWidget.swift
│   └── Assets.xcassets/
│
├── Frameworks/                      # 预编译 Frameworks
│   ├── LibXrayLite.xcframework/     # Xray-core (gomobile)
│   └── Tun2socksKit.xcframework/    # tun2socks iOS 实现
│
└── iosApp.xcodeproj/
```

### Entitlements 配置

```xml
<!-- iosApp.entitlements -->
<?xml version="1.0" encoding="UTF-8"?>
<plist version="1.0">
<dict>
    <key>com.apple.developer.networking.vpn.api</key>
    <array>
        <string>allow-vpn</string>
    </array>
    <key>com.apple.security.application-groups</key>
    <array>
        <string>group.com.xrayfa</string>
    </array>
    <key>com.apple.developer.networking.networkextension</key>
    <array>
        <string>packet-tunnel-provider</string>
    </array>
</dict>
</plist>
```

---

## 2. VPN 实现 (NEPacketTunnelProvider)

### 核心架构

```
┌─────────────────┐     IPC      ┌─────────────────────────┐
│   Main App      │◄────────────►│   Network Extension     │
│                 │  (App Group)  │                         │
│  - UI (Compose) │              │  - PacketTunnelProvider  │
│  - Config mgmt  │              │  - Xray-core            │
│  - Start/Stop   │              │  - tun2socks            │
│                 │              │  - TUN packet handling  │
└─────────────────┘              └─────────────────────────┘
```

### PacketTunnelProvider 实现

```swift
import NetworkExtension
import LibXrayLite   // gomobile xcframework
import Tun2socksKit  // tun2socks for iOS

class PacketTunnelProvider: NEPacketTunnelProvider {

    private var xrayProcess: XrayProcess?
    private var tunHandler: Tun2socksHandler?

    // MARK: - Tunnel Lifecycle

    override func startTunnel(
        options: [String: NSObject]?,
        completionHandler: @escaping (Error?) -> Void
    ) {
        // 1. 从 App Group 读取配置
        guard let config = loadConfigFromAppGroup() else {
            completionHandler(TunnelError.noConfig)
            return
        }

        // 2. 配置 TUN 网络设置
        let settings = createTunnelSettings(config: config)
        setTunnelNetworkSettings(settings) { [weak self] error in
            if let error = error {
                completionHandler(error)
                return
            }

            // 3. 启动 Xray-core
            self?.startXrayCore(config: config) { xrayError in
                if let xrayError = xrayError {
                    completionHandler(xrayError)
                    return
                }

                // 4. 启动 tun2socks (处理 TUN 数据包)
                self?.startTun2socks(socksPort: config.socksPort) { tunError in
                    completionHandler(tunError)
                }
            }
        }
    }

    override func stopTunnel(
        with reason: NEProviderStopReason,
        completionHandler: @escaping () -> Void
    ) {
        tunHandler?.stop()
        xrayProcess?.stop()
        updateSharedState(connected: false)
        completionHandler()
    }

    // MARK: - TUN Settings

    private func createTunnelSettings(config: XrayConfig) -> NEPacketTunnelNetworkSettings {
        let settings = NEPacketTunnelNetworkSettings(tunnelRemoteAddress: "254.1.1.1")

        // IPv4
        settings.ipv4Settings = NEIPv4Settings(
            addresses: ["198.18.0.1"],
            subnetMasks: ["255.255.0.0"]
        )
        settings.ipv4Settings?.includedRoutes = [NEIPv4Route.default()]

        // DNS
        settings.dnsSettings = NEDNSSettings(servers: ["198.18.0.1"])

        // MTU
        settings.mtu = NSNumber(value: 1500)

        return settings
    }

    // MARK: - Xray Core

    private func startXrayCore(config: XrayConfig, completion: @escaping (Error?) -> Void) {
        DispatchQueue.global(qos: .userInitiated).async {
            do {
                // 写入配置文件
                let configPath = self.writeConfigFile(config.jsonString)

                // 设置 Go runtime 内存限制 (NE 有 15MB 限制)
                LibXrayLiteSetMemoryLimit(12 * 1024 * 1024)

                // 启动 Xray
                var error: NSError?
                LibXrayLiteStartXray(configPath, &error)
                if let error = error {
                    completion(error)
                } else {
                    completion(nil)
                }
            }
        }
    }

    // MARK: - Tun2socks

    private func startTun2socks(socksPort: Int, completion: @escaping (Error?) -> Void) {
        tunHandler = Tun2socksHandler(packetFlow: packetFlow)
        tunHandler?.start(
            socksAddress: "127.0.0.1",
            socksPort: UInt16(socksPort)
        ) { error in
            completion(error)
        }
    }

    // MARK: - App Group Communication

    private func loadConfigFromAppGroup() -> XrayConfig? {
        let defaults = UserDefaults(suiteName: "group.com.xrayfa")
        guard let jsonString = defaults?.string(forKey: "pending_config") else {
            return nil
        }
        return try? JSONDecoder().decode(XrayConfig.self, from: jsonString.data(using: .utf8)!)
    }

    private func updateSharedState(connected: Bool) {
        let defaults = UserDefaults(suiteName: "group.com.xrayfa")
        defaults?.set(connected, forKey: "vpn_connected")
        defaults?.set(Date().timeIntervalSince1970, forKey: "vpn_state_updated_at")

        // 通知主应用状态变化
        CFNotificationCenterPostNotification(
            CFNotificationCenterGetDarwinNotifyCenter(),
            CFNotificationName("com.xrayfa.vpnStateChanged" as CFString),
            nil, nil, true
        )
    }
}
```

### 内存管理策略

iOS Network Extension 有严格的 **15MB 内存限制**，超出即被系统杀死：

```swift
// 内存监控
class MemoryMonitor {
    static func currentUsage() -> UInt64 {
        var info = mach_task_basic_info()
        var count = mach_msg_type_number_t(MemoryLayout<mach_task_basic_info>.size) / 4
        let result = withUnsafeMutablePointer(to: &info) {
            $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
                task_info(mach_task_self_, task_flavor_t(MACH_TASK_BASIC_INFO), $0, &count)
            }
        }
        return result == KERN_SUCCESS ? info.resident_size : 0
    }

    static func isNearLimit() -> Bool {
        return currentUsage() > 13 * 1024 * 1024 // 13MB 预警
    }
}
```

**Go Runtime 调优**:
```go
// 在 Go 代码中设置
func SetMemoryLimit(limit int64) {
    debug.SetMemoryLimit(limit)
    debug.SetGCPercent(20) // 更积极的 GC
}
```

---

## 3. Kotlin → Swift 桥接

### Koin 在 iOS 中的使用

```kotlin
// shared/src/iosMain/kotlin/com/xrayfa/IosEntryPoint.kt

fun initKoin() {
    startKoin {
        modules(
            commonModules(),
            iosPlatformModule,
        )
    }
}

val iosPlatformModule = module {
    single<PlatformContext> { PlatformContext() }
    single<VpnController> { IosVpnController() }
    single<ClipboardManager> { IosClipboardManager() }
    single<NotificationController> { IosNotificationController() }
}

// 暴露给 Swift 的工厂方法
fun createRootComponent(componentContext: ComponentContext): RootComponent {
    return DefaultRootComponent(
        componentContext = componentContext,
        nodeRepository = KoinPlatform.getKoin().get(),
        vpnController = KoinPlatform.getKoin().get(),
        settingsRepository = KoinPlatform.getKoin().get(),
    )
}
```

```swift
// iOS AppDelegate
import SharedKit

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        // 初始化 Koin
        IosEntryPointKt.initKoin()
        return true
    }
}
```

### ComposeUIViewController 集成

```kotlin
// shared/src/iosMain/kotlin/com/xrayfa/MainViewController.kt
import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController(): UIViewController {
    val lifecycle = LifecycleRegistry()
    val componentContext = DefaultComponentContext(lifecycle = lifecycle)
    val rootComponent = createRootComponent(componentContext)

    return ComposeUIViewController {
        XrayFATheme {
            RootContent(component = rootComponent)
        }
    }
}
```

```swift
// ContentView.swift
import SwiftUI
import SharedKit

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
    }
}
```

---

## 4. VPN 管理器 (主应用端)

```kotlin
// platform/vpn/src/iosMain/kotlin/
class IosVpnController : VpnController {

    private val _state = MutableStateFlow<VpnState>(VpnState.Disconnected)
    override val state: StateFlow<VpnState> = _state.asStateFlow()

    private var manager: NETunnelProviderManager? = null

    init {
        // 监听 VPN 状态变化
        observeVpnStatus()
        // 监听 Darwin Notification (来自 Network Extension)
        observeDarwinNotifications()
    }

    override suspend fun connect(config: XrayConfiguration): Result<Unit> = runCatching {
        _state.value = VpnState.Connecting

        // 1. 将配置写入 App Group
        writeConfigToAppGroup(config)

        // 2. 获取/创建 VPN 管理器
        val mgr = loadOrCreateManager()

        // 3. 启动 VPN Tunnel
        val session = mgr.connection as NETunnelProviderSession
        try {
            session.startVPNTunnel()
        } catch (e: Exception) {
            _state.value = VpnState.Error(e.message ?: "Unknown error")
            throw e
        }
    }

    override suspend fun disconnect(): Result<Unit> = runCatching {
        _state.value = VpnState.Disconnecting
        manager?.connection?.stopVPNTunnel()
    }

    override fun isVpnPermissionGranted(): Boolean = true  // iOS 系统自动弹出授权

    override suspend fun requestVpnPermission(): Boolean = true

    private suspend fun loadOrCreateManager(): NETunnelProviderManager {
        // 加载已有配置或创建新的
        val managers = NETunnelProviderManager.loadAllFromPreferences()
        val existing = managers.firstOrNull()

        val mgr = existing ?: NETunnelProviderManager()
        val proto = NETunnelProviderProtocol()
        proto.providerBundleIdentifier = "com.xrayfa.PacketTunnel"
        proto.serverAddress = "XrayFA"  // 显示名称

        mgr.protocolConfiguration = proto
        mgr.isEnabled = true
        mgr.localizedDescription = "XrayFA VPN"
        mgr.saveToPreferences()

        manager = mgr
        return mgr
    }

    private fun observeVpnStatus() {
        // 监听 NEVPNStatusDidChange 通知
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = "NEVPNStatusDidChangeNotification",
            object = null,
            queue = NSOperationQueue.mainQueue
        ) { notification ->
            val connection = manager?.connection ?: return@addObserverForName
            _state.value = when (connection.status) {
                NEVPNStatus.connected -> VpnState.Connected(
                    serverAddress = getCurrentServerAddress(),
                    connectedAt = Clock.System.now().toEpochMilliseconds()
                )
                NEVPNStatus.connecting -> VpnState.Connecting
                NEVPNStatus.disconnecting -> VpnState.Disconnecting
                NEVPNStatus.disconnected -> VpnState.Disconnected
                NEVPNStatus.invalid -> VpnState.Error("Invalid configuration")
                else -> VpnState.Disconnected
            }
        }
    }
}
```

---

## 5. On-Demand VPN (替代开机自启)

```kotlin
class IosOnDemandConfigurator {

    fun configureAlwaysOn(manager: NETunnelProviderManager, enabled: Boolean) {
        if (enabled) {
            // 任何网络可用时自动连接
            val connectRule = NEOnDemandRuleConnect()
            connectRule.interfaceTypeMatch = NEOnDemandRuleInterfaceType.Any

            manager.onDemandRules = listOf(connectRule)
            manager.isOnDemandEnabled = true
        } else {
            manager.isOnDemandEnabled = false
            manager.onDemandRules = emptyList()
        }
        manager.saveToPreferences()
    }

    fun configureWifiOnly(manager: NETunnelProviderManager) {
        val wifiRule = NEOnDemandRuleConnect()
        wifiRule.interfaceTypeMatch = NEOnDemandRuleInterfaceType.WiFi

        val cellularRule = NEOnDemandRuleDisconnect()
        cellularRule.interfaceTypeMatch = NEOnDemandRuleInterfaceType.Cellular

        manager.onDemandRules = listOf(wifiRule, cellularRule)
        manager.isOnDemandEnabled = true
        manager.saveToPreferences()
    }
}
```

---

## 6. iOS Widget (替代 Quick Settings Tile)

```swift
import WidgetKit
import SwiftUI

// 数据模型
struct VpnStatusEntry: TimelineEntry {
    let date: Date
    let isConnected: Bool
    let serverName: String?
    let connectedSince: Date?
}

// Timeline Provider
struct VpnStatusProvider: TimelineProvider {
    func placeholder(in context: Context) -> VpnStatusEntry {
        VpnStatusEntry(date: .now, isConnected: false, serverName: nil, connectedSince: nil)
    }

    func getSnapshot(in context: Context, completion: @escaping (VpnStatusEntry) -> Void) {
        completion(getCurrentEntry())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<VpnStatusEntry>) -> Void) {
        let entry = getCurrentEntry()
        // 每 5 分钟刷新一次
        let nextUpdate = Calendar.current.date(byAdding: .minute, value: 5, to: .now)!
        let timeline = Timeline(entries: [entry], policy: .after(nextUpdate))
        completion(timeline)
    }

    private func getCurrentEntry() -> VpnStatusEntry {
        let defaults = UserDefaults(suiteName: "group.com.xrayfa")
        let isConnected = defaults?.bool(forKey: "vpn_connected") ?? false
        let serverName = defaults?.string(forKey: "current_server")
        return VpnStatusEntry(
            date: .now,
            isConnected: isConnected,
            serverName: serverName,
            connectedSince: nil
        )
    }
}

// Widget View
struct VpnWidgetView: View {
    let entry: VpnStatusEntry

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: entry.isConnected ? "shield.checkered" : "shield.slash")
                .font(.system(size: 32))
                .foregroundColor(entry.isConnected ? .green : .gray)

            Text(entry.isConnected ? "已连接" : "未连接")
                .font(.caption)
                .fontWeight(.medium)

            if let server = entry.serverName {
                Text(server)
                    .font(.caption2)
                    .foregroundColor(.secondary)
                    .lineLimit(1)
            }
        }
        .widgetURL(URL(string: "xrayfa://toggle"))
    }
}

// Widget 定义
@main
struct XrayFAWidget: Widget {
    let kind = "VpnStatusWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: VpnStatusProvider()) { entry in
            VpnWidgetView(entry: entry)
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("VPN 状态")
        .description("查看和控制 VPN 连接状态")
        .supportedFamilies([.systemSmall, .accessoryCircular, .accessoryRectangular])
    }
}
```

---

## 7. QR 扫描 (AVFoundation)

```kotlin
// feature/qrcode/src/iosMain/kotlin/
@Composable
actual fun QrScannerView(onScanned: (String) -> Unit, modifier: Modifier) {
    UIKitView(
        factory = {
            QrScannerUIView(onScanned = onScanned)
        },
        modifier = modifier,
    )
}

class QrScannerUIView(private val onScanned: (String) -> Unit) : UIView() {

    private val captureSession = AVCaptureSession()
    private var previewLayer: AVCaptureVideoPreviewLayer? = null

    init {
        setupCamera()
    }

    private fun setupCamera() {
        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) ?: return
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, error = null) ?: return

        captureSession.addInput(input)

        val output = AVCaptureMetadataOutput()
        captureSession.addOutput(output)
        output.setMetadataObjectsDelegate(metadataDelegate, queue = dispatch_get_main_queue())
        output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)

        previewLayer = AVCaptureVideoPreviewLayer(session = captureSession).apply {
            videoGravity = AVLayerVideoGravityResizeAspectFill
            frame = bounds
        }
        layer.addSublayer(previewLayer!!)

        captureSession.startRunning()
    }

    private val metadataDelegate = object : AVCaptureMetadataOutputObjectsDelegateProtocol {
        override fun captureOutput(
            output: AVCaptureOutput,
            didOutputMetadataObjects: List<*>,
            fromConnection: AVCaptureConnection
        ) {
            val metadataObj = didOutputMetadataObjects.firstOrNull() as? AVMetadataMachineReadableCodeObject
            metadataObj?.stringValue?.let { qrContent ->
                captureSession.stopRunning()
                onScanned(qrContent)
            }
        }
    }
}
```

---

## 8. 剪贴板实现

```kotlin
// platform/system/src/iosMain/kotlin/
class IosClipboardManager : ClipboardManager {
    override fun copyToClipboard(text: String) {
        UIPasteboard.generalPasteboard.string = text
    }

    override fun getFromClipboard(): String? {
        return UIPasteboard.generalPasteboard.string
    }
}
```

---

## 9. 通知实现

```kotlin
// platform/notification/src/iosMain/kotlin/
class IosNotificationController : NotificationController {

    init {
        // 请求通知权限
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound
        ) { granted, error ->
            // 处理结果
        }
    }

    override fun showConnectionNotification(serverName: String) {
        val content = UNMutableNotificationContent().apply {
            title = "XrayFA"
            body = "已连接到 $serverName"
            sound = UNNotificationSound.defaultSound()
        }

        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = "vpn_connected",
            content = content,
            trigger = null  // 立即显示
        )

        UNUserNotificationCenter.currentNotificationCenter()
            .addNotificationRequest(request, withCompletionHandler = null)
    }

    override fun updateTrafficStats(bytesIn: Long, bytesOut: Long) {
        // iOS 不支持更新已显示的通知内容（不像 Android Foreground Service）
        // 可以通过 Live Activity (iOS 16.1+) 实现类似效果
    }

    override fun cancelConnectionNotification() {
        UNUserNotificationCenter.currentNotificationCenter()
            .removeDeliveredNotificationsWithIdentifiers(listOf("vpn_connected"))
    }
}
```

---

## 10. App Store 审核要点

### VPN 类应用审核注意事项

1. **VPN 权限申请**: 需在 Apple Developer Portal 中申请 `com.apple.developer.networking.vpn.api` entitlement，需提供详细的应用用途说明

2. **隐私政策**: 必须有清晰的隐私政策，说明不收集/存储用户流量数据

3. **内容**: 不得用于绕过地理限制访问受版权保护的内容

4. **Privacy Manifest**: iOS 17+ 必须声明使用的 API 及用途

```xml
<!-- PrivacyInfo.xcprivacy -->
<?xml version="1.0" encoding="UTF-8"?>
<plist version="1.0">
<dict>
    <key>NSPrivacyTracking</key>
    <false/>
    <key>NSPrivacyAccessedAPITypes</key>
    <array>
        <dict>
            <key>NSPrivacyAccessedAPIType</key>
            <string>NSPrivacyAccessedAPICategoryUserDefaults</string>
            <key>NSPrivacyAccessedAPITypeReasons</key>
            <array>
                <string>CA92.1</string>
            </array>
        </dict>
    </array>
</dict>
</plist>
```

5. **Export Compliance**: 使用加密需声明（XrayFA 使用 TLS/加密协议）

### 必备条件清单
- [ ] Apple Developer Program 会员
- [ ] VPN Entitlement 获批
- [ ] Network Extension Entitlement
- [ ] App Group 配置
- [ ] 隐私政策 URL
- [ ] 加密合规自我分类
- [ ] TestFlight 测试通过
