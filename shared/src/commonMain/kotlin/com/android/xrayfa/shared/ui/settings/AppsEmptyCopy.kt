package com.android.xrayfa.shared.ui.settings

fun appsListEmptyMessage(
    itemsEmpty: Boolean,
    searchQuery: String,
    noPackagesMessage: String,
    noMatchesMessage: String,
): String? {
    if (!itemsEmpty) return null
    return if (searchQuery.isBlank()) noPackagesMessage else noMatchesMessage
}
