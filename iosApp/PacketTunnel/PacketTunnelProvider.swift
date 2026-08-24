import NetworkExtension
import LibXrayLite

private enum AppGroup {
    static let suiteName = "group.com.android.xrayfa"
    static let pendingConfigKey = "pending_vpn_config_json"
    static let uploadSpeedKbpsKey = "vpn_upload_speed_kbps"
    static let downloadSpeedKbpsKey = "vpn_download_speed_kbps"
}

private enum TrafficStats {
    static let proxyTag = "proxy"
    static let uplink = "uplink"
    static let downlink = "downlink"
    static let pollIntervalSec: TimeInterval = 3
}

/// Packet tunnel: App Group config, TUN, Xray startLoop (tunFd=0) + hev-socks5-tunnel on utun.
/// Mirrors Android hexTun path: Xray SOCKS inbound + tun2socks on TUN fd.
final class PacketTunnelProvider: NEPacketTunnelProvider {
    private var coreController: Libv2rayCoreController?
    private var callbackHandler: TunnelCoreCallbackHandler?
    private var tun2SocksQueue: DispatchQueue?
    private var trafficTimer: DispatchSourceTimer?
    private var lastTrafficSampleTime: Date?

    override func startTunnel(
        options: [String: NSObject]?,
        completionHandler: @escaping (Error?) -> Void
    ) {
        AppGroupIpc.clearLastError()
        AppGroupIpc.setTunnelConnected(false)

        guard let configJson = loadPendingConfig(), !configJson.isEmpty else {
            failStart(TunnelError.noConfig, completionHandler: completionHandler)
            return
        }

        let settings = createTunnelSettings()
        setTunnelNetworkSettings(settings) { [weak self] error in
            if let error = error {
                self?.failStart(error, completionHandler: completionHandler)
                return
            }

            do {
                try self?.startVpnPipeline(configJson: configJson)
                self?.setTunnelConnected(true)
                self?.startTrafficPolling()
                completionHandler(nil)
            } catch {
                self?.failStart(error, completionHandler: completionHandler)
            }
        }
    }

    override func stopTunnel(
        with reason: NEProviderStopReason,
        completionHandler: @escaping () -> Void
    ) {
        stopVpnPipeline()
        setTunnelConnected(false)
        AppGroupIpc.writeMemoryBytes(0)
        completionHandler()
    }

    private func startVpnPipeline(configJson: String) throws {
        guard let container = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: AppGroup.suiteName
        ) else {
            throw TunnelError.noAppGroup
        }

        GoRuntimeTuning.applyForNetworkExtension()
        AppGroupIpc.writeMemoryBytes(MemoryMonitor.residentBytes())

        Libv2rayInitCoreEnv(container.path, "ios-device")

        let handler = TunnelCoreCallbackHandler()
        callbackHandler = handler
        guard let controller = Libv2rayNewCoreController(handler) else {
            throw TunnelError.xrayInitFailed
        }
        coreController = controller

        var startError: NSError?
        let started = controller.startLoop(configJson, tunFd: 0, error: &startError)
        if !started {
            throw startError ?? TunnelError.xrayStartFailed
        }

        guard let tunFd = UtunFileDescriptor.from(packetFlow: packetFlow) else {
            throw TunnelError.noUtunFd
        }

        let configPath = try Tun2SocksConfigBuilder.writeConfig(
            to: container,
            xrayConfigJson: configJson
        )

        let queue = DispatchQueue(label: "com.android.xrayfa.tun2socks", qos: .userInitiated)
        tun2SocksQueue = queue
        queue.async {
            _ = HevSocks5TunnelHelper.run(configPath: configPath, tunFd: tunFd)
        }
    }

    private func stopVpnPipeline() {
        stopTrafficPolling()
        HevSocks5TunnelHelper.quit()
        tun2SocksQueue = nil

        if let controller = coreController {
            var stopError: NSError?
            _ = controller.stopLoop(&stopError)
        }
        coreController = nil
        callbackHandler = nil
    }

    private func loadPendingConfig() -> String? {
        UserDefaults(suiteName: AppGroup.suiteName)?
            .string(forKey: AppGroup.pendingConfigKey)
    }

    private func setTunnelConnected(_ connected: Bool) {
        AppGroupIpc.setTunnelConnected(connected)
    }

    private func failStart(_ error: Error, completionHandler: @escaping (Error?) -> Void) {
        let message = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
        AppGroupIpc.writeLastError(message)
        AppGroupIpc.setTunnelConnected(false)
        stopVpnPipeline()
        completionHandler(error)
    }

    private func startTrafficPolling() {
        stopTrafficPolling()
        writeTrafficSpeedsKbps(upload: 0, download: 0)
        lastTrafficSampleTime = Date()

        let timer = DispatchSource.makeTimerSource(queue: DispatchQueue.global(qos: .utility))
        timer.schedule(deadline: .now() + TrafficStats.pollIntervalSec, repeating: TrafficStats.pollIntervalSec)
        timer.setEventHandler { [weak self] in
            self?.sampleTrafficSpeeds()
        }
        timer.resume()
        trafficTimer = timer
    }

    private func stopTrafficPolling() {
        trafficTimer?.cancel()
        trafficTimer = nil
        lastTrafficSampleTime = nil
        writeTrafficSpeedsKbps(upload: 0, download: 0)
    }

    private func sampleTrafficSpeeds() {
        AppGroupIpc.writeMemoryBytes(MemoryMonitor.residentBytes())
        if MemoryMonitor.isNearNetworkExtensionLimit() {
            AppGroupIpc.writeStatus("NE memory warning: \(MemoryMonitor.residentBytes() / 1024 / 1024) MB resident")
        }

        guard let controller = coreController, controller.isRunning else {
            writeTrafficSpeedsKbps(upload: 0, download: 0)
            return
        }

        let now = Date()
        let last = lastTrafficSampleTime ?? now
        lastTrafficSampleTime = now
        let deltaSec = now.timeIntervalSince(last)
        guard deltaSec > 0 else {
            return
        }

        let upBytes = controller.queryStats(TrafficStats.proxyTag, direct: TrafficStats.uplink)
        let downBytes = controller.queryStats(TrafficStats.proxyTag, direct: TrafficStats.downlink)
        let upKbps = Double(upBytes) / deltaSec / 1024.0
        let downKbps = Double(downBytes) / deltaSec / 1024.0
        writeTrafficSpeedsKbps(upload: upKbps, download: downKbps)
    }

    private func writeTrafficSpeedsKbps(upload: Double, download: Double) {
        guard let defaults = UserDefaults(suiteName: AppGroup.suiteName) else {
            return
        }
        defaults.set(upload, forKey: AppGroup.uploadSpeedKbpsKey)
        defaults.set(download, forKey: AppGroup.downloadSpeedKbpsKey)
    }

    private func createTunnelSettings() -> NEPacketTunnelNetworkSettings {
        let settings = NEPacketTunnelNetworkSettings(tunnelRemoteAddress: "254.1.1.1")
        settings.ipv4Settings = NEIPv4Settings(
            addresses: ["198.18.0.1"],
            subnetMasks: ["255.255.0.0"]
        )
        settings.ipv4Settings?.includedRoutes = [NEIPv4Route.default()]
        settings.dnsSettings = NEDNSSettings(servers: ["198.18.0.1"])
        settings.mtu = NSNumber(value: 1500)
        return settings
    }
}

private enum TunnelError: LocalizedError {
    case noConfig
    case noAppGroup
    case xrayInitFailed
    case xrayStartFailed
    case noUtunFd

    var errorDescription: String? {
        switch self {
        case .noConfig:
            return "No pending VPN config in App Group"
        case .noAppGroup:
            return "App Group container unavailable"
        case .xrayInitFailed:
            return "Failed to create Xray core controller"
        case .xrayStartFailed:
            return "Xray startLoop failed"
        case .noUtunFd:
            return "Could not resolve utun file descriptor"
        }
    }
}
