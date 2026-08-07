import NetworkExtension
import LibXrayLite

private enum AppGroup {
    static let suiteName = "group.com.android.xrayfa"
    static let pendingConfigKey = "pending_vpn_config_json"
    static let tunnelConnectedKey = "vpn_tunnel_connected"
}

/// Packet tunnel: App Group config + TUN settings + LibXrayLite env init.
/// Full startLoop / tun2socks lands when Tun2socksKit is wired (E.5d+).
final class PacketTunnelProvider: NEPacketTunnelProvider {
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
                try self?.bootstrapXrayEnvironment(configJson: configJson)
                self?.setTunnelConnected(true)
                completionHandler(nil)
            } catch {
                self?.setTunnelConnected(false)
                completionHandler(error)
            }
        }
    }

    override func stopTunnel(
        with reason: NEProviderStopReason,
        completionHandler: @escaping () -> Void
    ) {
        setTunnelConnected(false)
        completionHandler()
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

    private func bootstrapXrayEnvironment(configJson: String) throws {
        guard let container = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: AppGroup.suiteName
        ) else {
            throw TunnelError.noAppGroup
        }

        Libv2rayInitCoreEnv(container.path, "ios-device")

        // startLoop needs a TUN fd; iOS NE uses packetFlow — Tun2socksKit in a follow-up step.
        _ = Libv2rayCheckVersionX()
        _ = configJson
    }
}

private enum TunnelError: LocalizedError {
    case noConfig
    case noAppGroup

    var errorDescription: String? {
        switch self {
        case .noConfig:
            return "No pending VPN config in App Group"
        case .noAppGroup:
            return "App Group container unavailable"
        }
    }
}
