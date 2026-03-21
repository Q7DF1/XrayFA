package com.android.xrayfa.ui.navigation

import com.android.xrayfa.R
import kotlinx.serialization.Serializable

@Serializable
data class Detail(
    val id: Int,
    val remark: String?,
    val protocol: String,
    val content: String
): NavigateDestination{
    override val route: String
        get() = "detail"
    override val title: Int
        get() = R.string.detail_title

}