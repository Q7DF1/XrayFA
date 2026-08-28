import AVFoundation
import SwiftUI
import XrayFAShared

private struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

private enum QrCameraPermissionBridge {
    static func register() {
        IosQrCameraPermission.shared.requestHandler = { onGranted, onDenied in
            switch AVCaptureDevice.authorizationStatus(for: .video) {
            case .authorized:
                onGranted()
            case .notDetermined:
                AVCaptureDevice.requestAccess(for: .video) { granted in
                    DispatchQueue.main.async {
                        if granted {
                            onGranted()
                        } else {
                            onDenied()
                        }
                    }
                }
            default:
                onDenied()
            }
        }
    }
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
            .onAppear {
                QrCameraPermissionBridge.register()
            }
    }
}

#Preview {
    ContentView()
}
