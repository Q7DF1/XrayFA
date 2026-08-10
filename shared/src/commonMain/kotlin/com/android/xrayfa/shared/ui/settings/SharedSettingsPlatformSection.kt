package com.android.xrayfa.shared.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Route
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SharedSettingsPlatformSection(
    labels: SettingsUiLabels = SettingsUiLabels(),
    modifier: Modifier = Modifier,
    onAppsClick: (() -> Unit)? = null,
    onLogcatClick: (() -> Unit)? = null,
    onRouteClick: (() -> Unit)? = null,
) {
    SharedSettingsGroup(groupName = labels.platformSectionTitle, modifier = modifier) {
        SharedSettingsFieldRow(
            title = labels.appsTitle,
            content =
                if (onAppsClick != null) {
                    labels.appsDescription
                } else {
                    labels.platformUnavailableDescription
                },
            icon = Icons.Outlined.Apps,
            trailingIcon = if (onAppsClick != null) Icons.AutoMirrored.Outlined.KeyboardArrowRight else null,
            enabled = onAppsClick != null,
            onClick = { onAppsClick?.invoke() },
        )
        SharedSettingsFieldRow(
            title = labels.logcatTitle,
            content =
                if (onLogcatClick != null) {
                    labels.logcatDescription
                } else {
                    labels.platformUnavailableDescription
                },
            icon = Icons.Outlined.BugReport,
            trailingIcon = if (onLogcatClick != null) Icons.AutoMirrored.Outlined.KeyboardArrowRight else null,
            enabled = onLogcatClick != null,
            onClick = { onLogcatClick?.invoke() },
        )
        SharedSettingsFieldRow(
            title = labels.routeSettingsTitle,
            content =
                if (onRouteClick != null) {
                    labels.routeSettingsDescription
                } else {
                    labels.platformUnavailableDescription
                },
            icon = Icons.Outlined.Route,
            trailingIcon = if (onRouteClick != null) Icons.AutoMirrored.Outlined.KeyboardArrowRight else null,
            enabled = onRouteClick != null,
            onClick = { onRouteClick?.invoke() },
        )
    }
}
