import Foundation
import NetworkExtension

enum HevSocks5TunnelHelper {
    @discardableResult
    static func run(configPath: String, tunFd: Int32) -> Int32 {
        guard let cPath = configPath.cString(using: .utf8) else {
            return -1
        }
        return Int32(XrayFAHevSocks5TunnelRun(cPath, tunFd))
    }

    static func quit() {
        XrayFAHevSocks5TunnelQuit()
    }
}

/// Resolves utun fd after [NEPacketTunnelProvider.setTunnelNetworkSettings].
/// Uses NEPacketTunnelFlow socket fd (common NE integration pattern; not a public API).
enum UtunFileDescriptor {
    static func from(packetFlow: NEPacketTunnelFlow) -> Int32? {
        packetFlow.value(forKeyPath: "socket.fileDescriptor") as? Int32
    }
}
