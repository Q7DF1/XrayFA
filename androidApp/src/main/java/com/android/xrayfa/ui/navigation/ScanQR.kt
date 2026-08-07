package com.android.xrayfa.ui.navigation

import com.android.xrayfa.R
import kotlinx.serialization.Serializable


@Serializable
data class ScanQR(
    val onResult: (String) -> Unit,
):NavigateDestination {
    override val route: String
        get() = "scanQR"
    override val title: Int
        get() = R.string.scan_qr_title
}