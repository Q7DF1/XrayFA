import SwiftUI
import XrayFAShared

struct ContentView: View {
    @State private var statusText = "Disconnected"
    @State private var isBusy = false

    var body: some View {
        VStack(spacing: 12) {
            Text("XrayFA iOS")
                .font(.title)
            Text("KMP: \(XrayFAShared.shared.VERSION)")
                .font(.caption)
                .foregroundStyle(.secondary)
            Text("Platform: \(IosSharedInit.shared.platformName())")
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(statusText)
                .font(.subheadline)
                .multilineTextAlignment(.center)
            HStack(spacing: 16) {
                Button("Connect (trial)") {
                    trialConnect()
                }
                .disabled(isBusy)
                Button("Disconnect") {
                    IosSharedInit.shared.disconnectVpn()
                    statusText = "Disconnected"
                }
                .disabled(isBusy)
            }
            Text("Requires VPN entitlement + valid Xray JSON in App Group.")
                .font(.caption2)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding()
    }

    private func trialConnect() {
        isBusy = true
        statusText = "Connecting…"
        // Minimal SOCKS inbound; replace with parsed config from KMP when UI lands (E.6).
        let trialConfig = """
        {"log":{"loglevel":"warning"},"inbounds":[{"port":10808,"protocol":"socks","listen":"127.0.0.1","settings":{"udp":true}}],"outbounds":[{"protocol":"freedom","tag":"direct"}]}
        """
        IosSharedInit.shared.setPendingVpnConfig(configJson: trialConfig)
        IosSharedInit.shared.connectVpn { ok in
            isBusy = false
            statusText = ok.boolValue ? "Tunnel start requested" : "Connect failed (config / entitlement?)"
        }
    }
}

#Preview {
    ContentView()
}
