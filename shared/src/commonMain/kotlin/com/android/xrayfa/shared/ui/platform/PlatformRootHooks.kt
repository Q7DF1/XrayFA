package com.android.xrayfa.shared.ui.platform

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.android.xrayfa.model.Node
import com.android.xrayfa.shared.navigation.HomeComponent
import com.android.xrayfa.shared.navigation.SettingsComponent
import com.android.xrayfa.shared.resources.*
import com.android.xrayfa.shared.ui.SharedHomeSection
import com.android.xrayfa.shared.ui.home.HomeUiLabels
import com.android.xrayfa.shared.ui.qr.SharedQrScannerScreen
import com.android.xrayfa.shared.ui.rememberSettingsUiLabels
import com.android.xrayfa.shared.ui.settings.SharedInDevelopmentScreen
import com.android.xrayfa.shared.ui.settings.SharedInProcessAppLogScreen
import com.android.xrayfa.shared.ui.settings.SharedSettingsFieldRow
import org.jetbrains.compose.resources.stringResource

/**
 * Platform-specific hooks for [com.android.xrayfa.shared.ui.RootContent].
 * Android injects a full implementation (VPN prepare, CameraX, geo import, …).
 * iOS injects `IosPlatformRootHooks` from iosMain. This file's default is only the
 * CompositionLocal fallback.
 */
interface PlatformRootHooks {
    @Composable
    fun ColumnScope.SettingsGeneralExtras(component: SettingsComponent)

    @Composable
    fun ColumnScope.SettingsNetworkExtras(component: SettingsComponent)

    @Composable
    fun AppsScreen(
        component: SettingsComponent,
        onBack: () -> Unit,
    )

    @Composable
    fun LogcatScreen(onBack: () -> Unit)

    @Composable
    fun QrScannerScreen(
        onResult: (String) -> Unit,
        onBack: () -> Unit,
        title: String,
        permissionRequiredMessage: String,
    )

    @Composable
    fun HomeSection(
        component: HomeComponent,
        labels: HomeUiLabels,
        modifier: Modifier,
    )

    @Composable
    fun ShareNode(
        node: Node,
        onDismiss: () -> Unit,
    )

    @Composable
    fun BugReport(
        visible: Boolean,
        onDismiss: () -> Unit,
    )

    /**
     * When true, [com.android.xrayfa.shared.ui.RootContent] uses Decompose
     * [com.arkivanov.decompose.extensions.compose.stack.animation.predictiveback.predictiveBackAnimation]
     * and must not also install [SystemBackHandler] (that would steal the gesture).
     */
    val usesDecomposePredictiveBack: Boolean
        get() = false

    @Composable
    fun SystemBackHandler(
        enabled: Boolean,
        onBack: () -> Unit,
    ) {
    }
}

private object DefaultPlatformRootHooks : PlatformRootHooks {
    @Composable
    override fun ColumnScope.SettingsGeneralExtras(component: SettingsComponent) = Unit

    @Composable
    override fun ColumnScope.SettingsNetworkExtras(component: SettingsComponent) {
        InDevelopmentSettingsNetworkExtras()
    }

    @Composable
    override fun AppsScreen(
        component: SettingsComponent,
        onBack: () -> Unit,
    ) {
        val labels = rememberSettingsUiLabels()
        SharedInDevelopmentScreen(
            title = labels.appsTitle,
            message = stringResource(Res.string.in_development_message),
            onBack = onBack,
            backContentDescription = labels.cancelLabel,
        )
    }

    @Composable
    override fun LogcatScreen(onBack: () -> Unit) {
        SharedInProcessAppLogScreen(
            onBack = onBack,
            labels = rememberSettingsUiLabels(),
        )
    }

    @Composable
    override fun QrScannerScreen(
        onResult: (String) -> Unit,
        onBack: () -> Unit,
        title: String,
        permissionRequiredMessage: String,
    ) {
        SharedQrScannerScreen(
            onResult = onResult,
            onBack = onBack,
            title = title,
            permissionRequiredMessage = permissionRequiredMessage,
        )
    }

    @Composable
    override fun HomeSection(
        component: HomeComponent,
        labels: HomeUiLabels,
        modifier: Modifier,
    ) {
        SharedHomeSection(
            component = component,
            labels = labels,
            modifier = modifier,
        )
    }

    @Composable
    override fun ShareNode(
        node: Node,
        onDismiss: () -> Unit,
    ) {
        InDevelopmentDialog(onDismiss = onDismiss)
    }

    @Composable
    override fun BugReport(
        visible: Boolean,
        onDismiss: () -> Unit,
    ) {
        if (visible) {
            InDevelopmentDialog(onDismiss = onDismiss)
        }
    }
}

@Composable
internal fun ColumnScope.InDevelopmentSettingsNetworkExtras() {
    var showInDevelopment by remember { mutableStateOf(false) }
    val inDevelopmentLabel = stringResource(Res.string.in_development)
    SharedSettingsFieldRow(
        title = stringResource(Res.string.geo_ip),
        content = inDevelopmentLabel,
        icon = Icons.Outlined.Language,
        onClick = { showInDevelopment = true },
    )
    SharedSettingsFieldRow(
        title = stringResource(Res.string.geo_site),
        content = inDevelopmentLabel,
        icon = Icons.Outlined.Public,
        onClick = { showInDevelopment = true },
    )
    SharedSettingsFieldRow(
        title = stringResource(Res.string.enable_hextun_title),
        content = inDevelopmentLabel,
        icon = Icons.Outlined.Security,
        onClick = { showInDevelopment = true },
    )
    if (showInDevelopment) {
        InDevelopmentDialog(onDismiss = { showInDevelopment = false })
    }
}

@Composable
internal fun InDevelopmentDialog(onDismiss: () -> Unit) {
    val inDevelopmentLabel = stringResource(Res.string.in_development)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(inDevelopmentLabel) },
        text = { Text(stringResource(Res.string.in_development_message)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.confirm))
            }
        },
    )
}

val LocalPlatformRootHooks = staticCompositionLocalOf<PlatformRootHooks> { DefaultPlatformRootHooks }
