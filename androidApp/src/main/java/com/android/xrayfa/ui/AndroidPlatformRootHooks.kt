package com.android.xrayfa.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.window.core.layout.WindowWidthSizeClass
import com.android.xrayfa.R
import com.android.xrayfa.model.Node
import com.android.xrayfa.shared.navigation.HomeComponent
import com.android.xrayfa.shared.navigation.SettingsComponent
import com.android.xrayfa.shared.ui.home.HomeUiLabels
import com.android.xrayfa.shared.ui.platform.PlatformRootHooks
import com.android.xrayfa.ui.component.AndroidAppsScreen
import com.android.xrayfa.ui.component.AndroidLogcatScreen
import com.android.xrayfa.ui.component.AndroidSettingsGeneralViewModelExtras
import com.android.xrayfa.ui.component.AndroidSettingsNetworkViewModelExtras
import com.android.xrayfa.ui.component.BugReportDialog
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
    @Composable
    override fun ColumnScope.SettingsGeneralExtras(component: SettingsComponent) {
        AndroidSettingsGeneralViewModelExtras(settingsViewmodel)
    }

    @Composable
    override fun ColumnScope.SettingsNetworkExtras(component: SettingsComponent) {
        AndroidSettingsNetworkViewModelExtras(settingsViewmodel)
    }

    @Composable
    override fun AppsScreen(
        component: SettingsComponent,
        onBack: () -> Unit,
    ) {
        AndroidAppsScreen(viewmodel = appsViewmodel, onBack = onBack)
    }

    @Composable
    override fun LogcatScreen(onBack: () -> Unit) {
        AndroidLogcatScreen(viewmodel = xrayViewmodel, onBack = onBack)
    }

    @Composable
    override fun QrScannerScreen(
        onResult: (String) -> Unit,
        onBack: () -> Unit,
        title: String,
        permissionRequiredMessage: String,
    ) {
        QRCodeScannerScreen(onBack = onBack, onResult = onResult)
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
        val context = LocalContext.current
        val qrBitMap by xrayViewmodel.qrBitmap.collectAsState()
        LaunchedEffect(node.id) {
            xrayViewmodel.generateQRCode(node.id)
        }
        DisposableEffect(node.id) {
            onDispose { xrayViewmodel.dismissDialog() }
        }
        qrBitMap?.let { bitmap ->
            Dialog(
                onDismissRequest = {
                    xrayViewmodel.dismissDialog()
                    onDismiss()
                },
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 8.dp,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "qrcode",
                            modifier = Modifier.size(250.dp),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                xrayViewmodel.exportConfigToClipboard(context)
                                xrayViewmodel.dismissDialog()
                                onDismiss()
                            },
                        ) {
                            Text(text = stringResource(R.string.clipboard_export))
                        }
                    }
                }
            }
        }
    }

    @Composable
    override fun BugReport(
        visible: Boolean,
        onDismiss: () -> Unit,
    ) {
        if (!visible) {
            return
        }
        val context = LocalContext.current
        BugReportDialog(
            onDismiss = onDismiss,
            onSubmit = { data ->
                xrayViewmodel.submitBugReport(context, data)
                onDismiss()
            },
        )
    }

    @Composable
    override fun SystemBackHandler(
        enabled: Boolean,
        onBack: () -> Unit,
    ) {
        BackHandler(enabled = enabled, onBack = onBack)
    }
}
