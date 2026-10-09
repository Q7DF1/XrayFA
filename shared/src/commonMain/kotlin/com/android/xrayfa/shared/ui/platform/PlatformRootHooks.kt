package com.android.xrayfa.shared.ui.platform

import androidx.compose.foundation.layout.Box
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
    val supportsBootAutoStart: Boolean get() = true
    val supportsHideFromRecents: Boolean get() = true
    val agentFunctionsAvailable: Boolean get() = true

    @Composable
    fun ColumnScope.SettingsGeneralExtras(component: SettingsComponent)

    @Composable
    fun ColumnScope.SettingsNetworkExtras(component: SettingsComponent)

    // modifier 不能带默认值。接口在 :shared、实现在 androidApp，两边的 Compose 编译器
    // 会为默认参生成不同的 JVM 签名（实现多一个 default mask），调用时 AbstractMethodError。
    @Composable
    fun AppsScreen(
        component: SettingsComponent,
        onBack: () -> Unit,
        modifier: Modifier,
    )

    @Composable
    fun LogcatScreen(
        onBack: () -> Unit,
        modifier: Modifier,
    )

    @Composable
    fun QrScannerScreen(
        onResult: (String) -> Unit,
        onBack: () -> Unit,
        title: String,
        permissionRequiredMessage: String,
        modifier: Modifier,
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
        modifier: Modifier,
    ) {
        val labels = rememberSettingsUiLabels()
        SharedInDevelopmentScreen(
            title = labels.appsTitle,
            message = stringResource(Res.string.in_development_message),
            onBack = onBack,
            modifier = modifier,
            backContentDescription = labels.cancelLabel,
        )
    }

    @Composable
    override fun LogcatScreen(
        onBack: () -> Unit,
        modifier: Modifier,
    ) {
        SharedInProcessAppLogScreen(
            onBack = onBack,
            labels = rememberSettingsUiLabels(),
            modifier = modifier,
        )
    }

    @Composable
    override fun QrScannerScreen(
        onResult: (String) -> Unit,
        onBack: () -> Unit,
        title: String,
        permissionRequiredMessage: String,
        modifier: Modifier,
    ) {
        // `SharedQrScannerScreen` 是 expect/actual，没有 modifier 参数；包一层承载共享容器。
        Box(modifier = modifier) {
            SharedQrScannerScreen(
                onResult = onResult,
                onBack = onBack,
                title = title,
                permissionRequiredMessage = permissionRequiredMessage,
            )
        }
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
