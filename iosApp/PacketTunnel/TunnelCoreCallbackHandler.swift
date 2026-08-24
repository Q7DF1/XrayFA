import Foundation
import LibXrayLite

/// Forwards Xray core status to App Group so the host app can surface tunnel errors.
final class TunnelCoreCallbackHandler: Libv2rayCoreCallbackHandler {
    override func onEmitStatus(_ code: Int, message: String?) -> Int {
        let text = message?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !text.isEmpty else {
            return 0
        }
        AppGroupIpc.writeStatus(text)
        if looksLikeFailure(code: code, message: text) {
            AppGroupIpc.writeLastError(text)
        }
        return 0
    }

    override func shutdown() -> Int {
        AppGroupIpc.writeStatus("Core shutdown")
        return 0
    }

    override func startup() -> Int {
        AppGroupIpc.writeStatus("Core startup")
        AppGroupIpc.clearLastError()
        return 0
    }

    private func looksLikeFailure(code: Int, message: String) -> Bool {
        if code != 0 {
            return true
        }
        let lower = message.lowercased()
        return lower.contains("fail")
            || lower.contains("error")
            || lower.contains("stopped unexpectedly")
    }
}
