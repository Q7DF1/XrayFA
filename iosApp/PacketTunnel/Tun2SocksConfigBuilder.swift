import Foundation

/// Builds hev-socks5-tunnel YAML matching Android [Tun2SocksConfigUtil] defaults.
enum Tun2SocksConfigBuilder {
    private static let defaultSocksPort = 10808
    private static let tunnelIpv4 = "198.18.0.1"
    private static let tunnelMtu = 1500

    static func writeConfig(
        to directory: URL,
        xrayConfigJson: String,
        socksPort: Int? = nil
    ) throws -> String {
        let port = socksPort ?? extractSocksPort(from: xrayConfigJson) ?? defaultSocksPort
        let yaml = """
        misc:
          task-stack-size: 81920
        tunnel:
          mtu: \(tunnelMtu)
          ipv4: \(tunnelIpv4)
        socks5:
          port: \(port)
          address: '127.0.0.1'
          udp: 'udp'
        """
        let fileURL = directory.appendingPathComponent("tproxy.conf")
        try yaml.write(to: fileURL, atomically: true, encoding: .utf8)
        return fileURL.path
    }

    /// Reads the first SOCKS inbound port from generated Xray JSON, if present.
    private static func extractSocksPort(from configJson: String) -> Int? {
        guard
            let data = configJson.data(using: .utf8),
            let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
            let inbounds = root["inbounds"] as? [[String: Any]]
        else {
            return nil
        }
        for inbound in inbounds {
            guard let proto = inbound["protocol"] as? String, proto == "socks" else {
                continue
            }
            if let port = inbound["port"] as? Int {
                return port
            }
            if let port = inbound["port"] as? Double {
                return Int(port)
            }
        }
        return nil
    }
}
