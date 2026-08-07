import Foundation
import LibXrayLite

/// Minimal callback handler for PacketTunnel Xray lifecycle.
final class TunnelCoreCallbackHandler: Libv2rayCoreCallbackHandler {
    override func onEmitStatus(_ p0: Int, p1: String?) -> Int {
        return 0
    }

    override func shutdown() -> Int {
        return 0
    }

    override func startup() -> Int {
        return 0
    }
}
