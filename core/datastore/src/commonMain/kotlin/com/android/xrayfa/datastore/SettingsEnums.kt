package com.android.xrayfa.datastore

enum class Theme(val code: Int) {
    LIGHT_MODE(0),
    DARK_MODE(1),
    AUTO_MODE(2);

    /** Whether Material / system chrome should use the dark palette. */
    fun resolvesToDark(systemDark: Boolean): Boolean =
        when (this) {
            LIGHT_MODE -> false
            DARK_MODE -> true
            AUTO_MODE -> systemDark
        }

    companion object {
        fun fromCode(code: Int): Theme = entries.firstOrNull { it.code == code } ?: AUTO_MODE
    }
}
