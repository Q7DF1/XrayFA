package com.android.xrayfa.common.core

const val MAX_MMDB_BYTES = 256L * 1024 * 1024

/** Platform storage for atomic asset updates; download policy lives in AtomicAssetInstaller. */
expect object AssetFiles {
    fun read(path: String): ByteArray?
    fun replace(source: String, destination: String)
    fun delete(path: String)
}
