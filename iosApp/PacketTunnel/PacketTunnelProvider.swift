import NetworkExtension
import LibXrayLite

private enum AppGroup {
    static let suiteName = "group.com.android.xrayfa"
    static let pendingConfigKey = "pending_vpn_config_json"
    static let tunnelConnectedKey = "vpn_tunnel_connected"
}

/// Packet tunnel: App Group config, TUN, Xray startLoop (tunFd=0) + hev-socks5-tunnel on utun.
/// Mirrors Android hexTun path: Xray SOCKS inbound + tun2socks on TUN fd.
final class PacketTunnelProvider: NEPacketTunnelProvider {
    private var coreController: Libv2rayCoreController?
    private var callbackHandler: TunnelCoreCallbackHandler?
    private var tun2SocksQueue: DispatchQueue?

    override func startTunnel(
        options: [String: NSObject]?,
        completionHandler: @escaping (Error?) -> Void
    ) {
        guard let configJson = loadPendingConfig(), !configJson.isEmpty else {
            completionHandler(TunnelError.noConfig)
            return
        }

        let settings = createTunnelSettings()
        setTunnelNetworkSettings(settings) { [weak self] error in
            if let error = error {
                completionHandler(error)
                return
            }

            do {
                try self?.startVpnPipeline(configJson: configJson)
                self?.setTunnelConnected(true)
                completionHandler(nil)
            } catch {
                self?.setTunnelConnected(false)
                self?.stopVpnPipeline()
                completionHandler(error)
            }
        }
    }

    override func stopTunnel(
        with reason: NEProviderStopReason,
        completionHandler: @escaping () -> Void
    ) {
        stopVpnPipeline()
        setTunnelConnected(false)
        completionHandler()
    }

    private func startVpnPipeline(configJson: String) throws {
        guard let container = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: AppGroup.suiteName
        ) else {
            throw TunnelError.noAppGroup
        }

        Libv2rayInitCoreEnv(container.path, "ios-device")

        let handler = TunnelCoreCallbackHandler()
        callbackHandler = handler
        guard let controller = Libv2rayNewCoreController(handler) else {
            throw TunnelError.xrayInitFailed
        }
        coreController = controller

        try controller.startLoop(configJson, tunFd: 0)

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
        HevSocks5TunnelHelper.quit()
        tun2SocksQueue = nil

        if let controller = coreController {
            try? controller.stopLoop()
        }
        coreController = nil
        callbackHandler = nil
    }

    private func loadPendingConfig() -> String? {
        UserDefaults(suiteName: AppGroup.suiteName)?
            .string(forKey: AppGroup.pendingConfigKey)
    }

    private func setTunnelConnected(_ connected: Bool) {
        UserDefaults(suiteName: AppGroup.suiteName)?
            .set(connected ? "true" : "false", forKey: AppGroup.tunnelConnectedKey)
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
