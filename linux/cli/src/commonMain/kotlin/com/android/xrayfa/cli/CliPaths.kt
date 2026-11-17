package com.android.xrayfa.cli

/** Paths belong to the passwd caller, even when the effective UID is root. */
internal data class CliPaths(
    val passwdHome: String,
    val dataDir: String,
    val stateDir: String,
    val legacyDataDir: String?,
    val legacyLogDir: String?,
    val uid: UInt,
) {
    val logDir
        get() = "$stateDir/logs"

    val systemLogDir
        get() = "/var/log/xrayfa/$uid"

    companion object {
        const val JOURNAL = "/run/xrayfa/session.json"

        fun resolve(
            passwdHome: String,
            uid: UInt,
            isolationRoot: String?,
            standardParentUsable: Boolean,
        ): CliPaths {
            require(passwdHome.startsWith('/'))
            require(isolationRoot == null || isolationRoot.startsWith('/'))
            val root =
                isolationRoot
                    ?: if (standardParentUsable) passwdHome else "$passwdHome/Xray/.xrayfa"
            // Older builds treated the final data directory as a home and appended it twice.
            val oldRoot =
                isolationRoot
                    ?: if (standardParentUsable) "$passwdHome/.local/share/xrayfa" else root
            return CliPaths(
                passwdHome = passwdHome,
                dataDir = "$root/.local/share/xrayfa",
                stateDir = "$root/.local/state/xrayfa",
                legacyDataDir =
                    "$oldRoot/.local/share/xrayfa".takeIf { it != "$root/.local/share/xrayfa" },
                legacyLogDir =
                    "$oldRoot/.local/state/xrayfa/logs"
                        .takeIf { it != "$root/.local/state/xrayfa/logs" },
                uid = uid,
            )
        }
    }
}

internal enum class StorePresence {
    MISSING,
    PRESENT,
    UNSAFE,
}

/** Continue using an existing legacy store in place; never move or overwrite operator data. */
internal fun CliPaths.storeDirectory(presence: (String) -> StorePresence): String {
    val current = presence(dataDir)
    val legacy = legacyDataDir?.let(presence) ?: StorePresence.MISSING
    require(current != StorePresence.UNSAFE && legacy != StorePresence.UNSAFE) {
        "Unsafe node store; original preserved"
    }
    require(current != StorePresence.PRESENT || legacy != StorePresence.PRESENT) {
        "Both current and legacy node stores exist; resolve explicitly before continuing"
    }
    return if (legacy == StorePresence.PRESENT) legacyDataDir!! else dataDir
}

internal object CoreLocator {
    /** Prefer the documented app path; retain the older Xray path as a fallback. */
    fun locate(passwdHome: String, explicit: String?, exists: (String) -> Boolean): String {
        explicit?.let {
            return it
        }
        val candidates = listOf("$passwdHome/app/Xray/xray", "$passwdHome/Xray/xray")
        return candidates.firstOrNull(exists) ?: candidates.first()
    }
}
