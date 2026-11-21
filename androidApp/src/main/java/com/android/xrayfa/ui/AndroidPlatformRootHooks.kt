package com.android.xrayfa.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.window.core.layout.WindowWidthSizeClass
import com.android.xrayfa.R
import com.android.xrayfa.model.Node
import com.android.xrayfa.model.isJsonConfig
import com.android.xrayfa.model.isManagedXDrive
import com.android.xrayfa.model.canShareConfiguration
import com.android.xrayfa.shared.navigation.HomeComponent
import com.android.xrayfa.shared.navigation.SettingsComponent
import com.android.xrayfa.shared.ui.home.HomeUiLabels
import com.android.xrayfa.shared.ui.platform.PlatformRootHooks
import com.android.xrayfa.ui.component.AndroidAppsSearchScreen
import com.android.xrayfa.ui.component.AndroidAppsScreen
import com.android.xrayfa.ui.component.AndroidLogcatScreen
import com.android.xrayfa.ui.component.AndroidSettingsGeneralViewModelExtras
import com.android.xrayfa.ui.component.AndroidSettingsNetworkViewModelExtras
import com.android.xrayfa.shared.ui.qr.ShareQrDialog
import com.android.xrayfa.shared.ui.settings.SharedBugReport
import com.android.xrayfa.ui.component.CompactHomeContent
import com.android.xrayfa.ui.component.ExpandedHomeContent
import com.android.xrayfa.ui.component.QRCodeScannerScreen
import com.android.xrayfa.viewmodel.AppsViewmodel
import com.android.xrayfa.viewmodel.SettingsViewmodel
import com.android.xrayfa.viewmodel.XrayViewmodel

internal class AndroidPlatformRootHooks(
    private val settingsViewmodel: SettingsViewmodel,
    private val appsViewmodel: AppsViewmodel,
    private val xrayViewmodel: XrayViewmodel,
) : PlatformRootHooks {
    override val usesDecomposePredictiveBack: Boolean = true
    override val supportsHomeScreenWidgets: Boolean = true
    @Composable
    override fun ColumnScope.SettingsGeneralExtras(component: SettingsComponent) {
        AndroidSettingsGeneralViewModelExtras(settingsViewmodel)
    }

    @Composable
    override fun ColumnScope.SettingsNetworkExtras(component: SettingsComponent) {
        AndroidSettingsNetworkViewModelExtras(settingsViewmodel)
    }

    // Keep root transition modifiers outside the platform-specific content.
    @Composable
    override fun AppsScreen(
        component: SettingsComponent,
        onOpenSearch: () -> Unit,
        onBack: () -> Unit,
        modifier: Modifier,
    ) {
        Box(modifier = modifier) {
            AndroidAppsScreen(viewmodel = appsViewmodel, onOpenSearch = onOpenSearch, onBack = onBack)
        }
    }

    @Composable
    override fun AppsSearchScreen(
        component: SettingsComponent,
        onBack: () -> Unit,
        modifier: Modifier,
    ) {
        Box(modifier = modifier) {
            AndroidAppsSearchScreen(viewmodel = appsViewmodel, onBack = onBack)
        }
    }

    @Composable
    override fun LogcatScreen(
        onBack: () -> Unit,
        modifier: Modifier,
    ) {
        Box(modifier = modifier) {
            AndroidLogcatScreen(viewmodel = xrayViewmodel, onBack = onBack)
        }
    }

    @Composable
    override fun QrScannerScreen(
        onResult: (String) -> Unit,
        onBack: () -> Unit,
        title: String,
        permissionRequiredMessage: String,
        modifier: Modifier,
    ) {
        Box(modifier = modifier) {
            QRCodeScannerScreen(onBack = onBack, onResult = onResult)
        }
    }

    @Composable
    override fun HomeSection(
        component: HomeComponent,
        labels: HomeUiLabels,
        modifier: Modifier,
    ) {
        val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
        val isWide =
            windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED ||
                windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.MEDIUM
        Box(modifier = modifier) {
            if (isWide) {
                ExpandedHomeContent(homeComponent = component)
            } else {
                CompactHomeContent(homeComponent = component)
            }
        }
    }

    @Composable
    override fun ShareNode(
        node: Node,
        onDismiss: () -> Unit,
    ) {
        if (!node.canShareConfiguration) return

        val context = LocalContext.current
        val qrBitMap by xrayViewmodel.qrBitmap.collectAsState()
        LaunchedEffect(node.id) {
            xrayViewmodel.generateQRCode(node.id)
        }
        DisposableEffect(node.id) {
            onDispose { xrayViewmodel.dismissDialog() }
        }
        qrBitMap?.let { bitmap ->
            ShareQrDialog(
                bitmap = bitmap.asImageBitmap(),
                showStorageHint = node.isManagedXDrive,
                copyLabel = stringResource(R.string.clipboard_export),
                onCopy = { xrayViewmodel.exportConfigToClipboard(context, onDone = onDismiss) },
                onDismiss = {
                    xrayViewmodel.dismissDialog()
                    onDismiss()
                },
            )
        }
    }

    @Composable
    override fun BugReport(
        visible: Boolean,
        onDismiss: () -> Unit,
    ) {
        SharedBugReport(visible = visible, onDismiss = onDismiss)
    }

    @Composable
    override fun SystemBackHandler(
        enabled: Boolean,
        onBack: () -> Unit,
    ) {
        // Predictive back is owned by Decompose BackDispatcher + PredictiveBackGestureOverlay.
    }
}
