import NetworkExtension

/// Network Extension stub — Xray + tun2socks wiring lands in E.5c+.
final class PacketTunnelProvider: NEPacketTunnelProvider {
    override func startTunnel(
        options: [String: NSObject]?,
        completionHandler: @escaping (Error?) -> Void
    ) {
        completionHandler(NSError(
            domain: "com.android.xrayfa.tunnel",
            code: 1,
            userInfo: [NSLocalizedDescriptionKey: "Packet tunnel not implemented yet (E.5c+)"]
        ))
    }

    override func stopTunnel(
        with reason: NEProviderStopReason,
        completionHandler: @escaping () -> Void
    ) {
        completionHandler()
    }
}
