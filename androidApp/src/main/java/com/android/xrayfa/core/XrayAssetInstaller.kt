package com.android.xrayfa.core

import com.android.xrayfa.common.GEO_IP
import com.android.xrayfa.common.GEO_SITE
import com.android.xrayfa.common.core.XrayAssetPaths
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Only publish complete geodata; keep valid downloaded/custom datasets. */
class XrayAssetInstaller(
    private val paths: XrayAssetPaths,
    private val openAsset: (String) -> InputStream,
    private val report: (String) -> Unit = {},
) {
    @Synchronized
    fun ensureInstalled(forceRestore: Boolean = false) {
        for ((name, path) in listOf(GEO_IP to paths.geoIpPath, GEO_SITE to paths.geoSitePath)) {
            val target = File(path)
            val codes = readCodes(target)
            if (codes != null && !forceRestore) {
                report("$name verified: ${target.length()} bytes, ${codes.size} categories" +
                    if (name == GEO_SITE) ", TELEGRAM=${"TELEGRAM" in codes}" else "")
                continue
            }
            target.parentFile!!.mkdirs()
            val temporary = File.createTempFile("$name-", ".tmp", target.parentFile)
            try {
                openAsset(name).use { input ->
                    FileOutputStream(temporary).use { output ->
                        input.copyTo(output)
                        output.fd.sync()
                    }
                }
                check(readCodes(temporary) != null) { "Bundled $name is incomplete or invalid" }
                if (target.exists()) {
                    // Preserve the replaced file for recovery or diagnosis.
                    val backup = File(target.parentFile, "$name.${if (codes == null) "corrupt" else "backup"}-${System.nanoTime()}")
                    target.copyTo(backup)
                }
                publish(temporary, target)
                report("Restored $name from bundled asset")
            } finally {
                temporary.delete()
            }
        }
    }

    companion object {
        /** Both GeoIPList and GeoSiteList are length-delimited protobuf entries. */
        fun readCodes(file: File): Set<String>? = runCatching {
            RandomAccessFile(file, "r").use { input ->
                val size = input.length()
                val codes = mutableSetOf<String>()
                fun varint(): Long {
                    var value = 0L
                    for (shift in 0..63 step 7) {
                        val byte = input.readUnsignedByte()
                        check(shift != 63 || byte <= 1) { "Invalid geodata length" }
                        value = value or ((byte and 127).toLong() shl shift)
                        if (byte and 128 == 0) return value
                    }
                    error("Invalid geodata length")
                }
                while (input.filePointer < size) {
                    check(input.readUnsignedByte() == 10) { "Invalid geodata entry" }
                    val length = varint()
                    check(length > 0 && length <= size - input.filePointer) { "Truncated geodata entry" }
                    val end = input.filePointer + length
                    // The current Xray loader expects the country/category code first.
                    check(input.readUnsignedByte() == 10) { "Missing geodata code" }
                    val codeLength = varint()
                    check(codeLength in 1..127 && codeLength <= end - input.filePointer) { "Invalid geodata code" }
                    val code = ByteArray(codeLength.toInt())
                    input.readFully(code)
                    codes.add(code.toString(Charsets.UTF_8))
                    input.seek(end)
                }
                check(codes.isNotEmpty()) { "Empty geodata" }
                codes
            }
        }.getOrNull()

        fun publish(temporary: File, target: File) {
            check(temporary.length() > 0) { "Downloaded asset is empty" }
            if (target.extension.equals("dat", ignoreCase = true)) {
                check(readCodes(temporary) != null) { "Downloaded geodata is incomplete or invalid" }
            }
            Files.move(temporary.toPath(), target.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
