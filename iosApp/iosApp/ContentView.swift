import SwiftUI
import XrayFAShared

struct ContentView: View {
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
        }
        .padding()
    }
}

#Preview {
    ContentView()
}
