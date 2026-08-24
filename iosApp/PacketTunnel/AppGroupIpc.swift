import Foundation

private enum AppGroup {
    static let suiteName = "group.com.android.xrayfa"
    static let pendingConfigKey = "pending_vpn_config_json"
    static let tunnelConnectedKey = "vpn_tunnel_connected"
    static let uploadSpeedKbpsKey = "vpn_upload_speed_kbps"
    static let downloadSpeedKbpsKey = "vpn_download_speed_kbps"
    static let lastErrorKey = "vpn_tunnel_last_error"
    static let statusKey = "vpn_tunnel_status_message"
    static let memoryBytesKey = "vpn_tunnel_memory_bytes"
}

enum AppGroupIpc {
    static var defaults: UserDefaults? {
        UserDefaults(suiteName: AppGroup.suiteName)
    }

    static func writeLastError(_ message: String) {
        defaults?.set(message, forKey: AppGroup.lastErrorKey)
    }

    static func clearLastError() {
        defaults?.removeObject(forKey: AppGroup.lastErrorKey)
    }

    static func writeStatus(_ message: String) {
        defaults?.set(message, forKey: AppGroup.statusKey)
    }

    static func writeMemoryBytes(_ bytes: UInt64) {
        defaults?.set(Double(bytes), forKey: AppGroup.memoryBytesKey)
    }

    static func setTunnelConnected(_ connected: Bool) {
        defaults?.set(connected ? "true" : "false", forKey: AppGroup.tunnelConnectedKey)
    }
}
