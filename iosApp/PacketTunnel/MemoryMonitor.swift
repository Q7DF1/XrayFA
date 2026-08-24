import Foundation
import Darwin

/// Resident memory sampling for Network Extension (15MB process limit).
enum MemoryMonitor {
    private static let warnThresholdBytes: UInt64 = 13 * 1024 * 1024

    static func residentBytes() -> UInt64 {
        var info = mach_task_basic_info()
        var count = mach_msg_type_number_t(MemoryLayout<mach_task_basic_info>.size) / 4
        let result: kern_return_t = withUnsafeMutablePointer(to: &info) {
            $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
                task_info(mach_task_self_, task_flavor_t(MACH_TASK_BASIC_INFO), $0, &count)
            }
        }
        guard result == KERN_SUCCESS else {
            return 0
        }
        return info.resident_size
    }

    static func isNearNetworkExtensionLimit() -> Bool {
        residentBytes() >= warnThresholdBytes
    }
}

enum GoRuntimeTuning {
    /// Soft Go heap cap; NE hard limit is ~15MB.
    static let memoryLimitBytes = 12 * 1024 * 1024
    static let gcPercent = 20

    static func applyForNetworkExtension() {
        setenv("GOMEMLIMIT", "\(memoryLimitBytes)", 1)
        setenv("GOGC", "\(gcPercent)", 1)
    }
}
