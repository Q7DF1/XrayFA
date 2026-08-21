package com.android.xrayfa.datastore

enum class Theme(val code: Int) {
    LIGHT_MODE(0),
    DARK_MODE(1),
    AUTO_MODE(2);

    companion object {
        fun fromCode(code: Int): Theme = entries.firstOrNull { it.code == code } ?: AUTO_MODE
    }
}
