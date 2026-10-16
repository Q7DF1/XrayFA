package com.android.xrayfa.widget

import android.util.TypedValue
import android.widget.RemoteViews
import com.android.xrayfa.R
import com.android.xrayfa.ui.theme.Typography

/** Native text appearances bridge typeface/weight; sizes stay sourced from the app Typography. */
internal fun RemoteViews.applyWidgetTypography(compact: Boolean) {
    val roles = if (compact) {
        listOf(R.id.widget_node to Typography.titleMedium, R.id.widget_status to Typography.bodyMedium)
    } else {
        listOf(
            R.id.widget_brand to Typography.labelLarge,
            R.id.widget_node to Typography.titleLarge,
            R.id.widget_status to Typography.labelLarge,
            R.id.widget_hint to Typography.bodyMedium,
            R.id.widget_duration to Typography.bodyMedium,
            R.id.widget_download to Typography.titleMedium,
            R.id.widget_upload to Typography.titleMedium,
            R.id.widget_switch to Typography.labelLarge,
        )
    }
    roles.forEach { (id, style) -> setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, style.fontSize.value) }
}
