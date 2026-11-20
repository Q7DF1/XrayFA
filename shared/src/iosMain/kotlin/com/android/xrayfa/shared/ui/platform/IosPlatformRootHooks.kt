package com.android.xrayfa.shared.ui.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.xrayfa.model.configurationShareLink
import com.android.xrayfa.model.Node
import com.android.xrayfa.model.isManagedXDrive
import com.android.xrayfa.model.isJsonConfig
import com.android.xrayfa.model.canShareConfiguration
import com.android.xrayfa.shared.navigation.HomeComponent
import com.android.xrayfa.shared.navigation.SettingsComponent
import com.android.xrayfa.shared.platform.ClipboardWriter
import com.android.xrayfa.shared.platform.qr.encodeQrImageBitmap
import com.android.xrayfa.shared.resources.*
import org.jetbrains.compose.resources.stringResource
import com.android.xrayfa.shared.ui.SharedHomeSection
import com.android.xrayfa.shared.ui.nav.rememberFloatingNavClearance
import com.android.xrayfa.shared.ui.home.HomeUiLabels
import com.android.xrayfa.shared.ui.qr.SharedQrScannerScreen
import com.android.xrayfa.shared.ui.rememberConfigUiLabels
import com.android.xrayfa.shared.ui.rememberSettingsUiLabels
import com.android.xrayfa.shared.ui.qr.ShareQrDialog
import com.android.xrayfa.shared.ui.settings.SharedBugReport
import com.android.xrayfa.shared.ui.settings.SharedInDevelopmentScreen
import com.android.xrayfa.shared.ui.settings.SharedInProcessAppLogScreen
import org.koin.mp.KoinPlatform

/**
 * iOS [PlatformRootHooks]. Unimplemented Android-only slots stay on the
 * in-development UI; ShareNode and BugReport are real.
 */
object IosPlatformRootHooks : PlatformRootHooks {
    override val supportsBootAutoStart = false
    override val supportsHideFromRecents = false
    override val agentFunctionsAvailable = false

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
            bottomContentPadding = rememberFloatingNavClearance(extraAboveBar = 8.dp),
        )
    }

    @Composable
    override fun ShareNode(
        node: Node,
        onDismiss: () -> Unit,
    ) {
        if (!node.canShareConfiguration) return

        val labels = rememberConfigUiLabels()
        val clipboardWriter = remember { KoinPlatform.getKoin().get<ClipboardWriter>() }
        val shareUrl = remember(node) { node.configurationShareLink() } ?: return
        val qrBitmap = remember(shareUrl) { encodeQrImageBitmap(shareUrl) }
        ShareQrDialog(
            bitmap = qrBitmap,
            showStorageHint = node.isManagedXDrive,
            copyLabel = labels.shareLabel,
            onCopy = {
                if (shareUrl.isNotEmpty()) clipboardWriter.writeText(shareUrl)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
    }

    @Composable
    override fun BugReport(
        visible: Boolean,
        onDismiss: () -> Unit,
    ) {
        SharedBugReport(visible = visible, onDismiss = onDismiss)
    }
}
