package com.android.xrayfa.shared.ui.platform

import androidx.compose.foundation.Image
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.android.xrayfa.common.utils.ShareLinkCleaner
import com.android.xrayfa.model.Node
import com.android.xrayfa.shared.navigation.HomeComponent
import com.android.xrayfa.shared.navigation.SettingsComponent
import com.android.xrayfa.shared.platform.ClipboardWriter
import com.android.xrayfa.shared.platform.qr.encodeQrImageBitmap
import com.android.xrayfa.shared.resources.*
import com.android.xrayfa.shared.ui.SharedHomeSection
import com.android.xrayfa.shared.ui.home.HomeUiLabels
import com.android.xrayfa.shared.ui.qr.SharedQrScannerScreen
import com.android.xrayfa.shared.ui.rememberConfigUiLabels
import com.android.xrayfa.shared.ui.rememberSettingsUiLabels
import com.android.xrayfa.shared.ui.settings.SharedBugReport
import com.android.xrayfa.shared.ui.settings.SharedInDevelopmentScreen
import com.android.xrayfa.shared.ui.settings.SharedInProcessAppLogScreen
import org.jetbrains.compose.resources.stringResource
import org.koin.mp.KoinPlatform

/**
 * iOS [PlatformRootHooks]. Unimplemented Android-only slots stay on the
 * in-development UI; ShareNode and BugReport are real.
 */
object IosPlatformRootHooks : PlatformRootHooks {
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
        val labels = rememberConfigUiLabels()
        val clipboardWriter = remember { KoinPlatform.getKoin().get<ClipboardWriter>() }
        val shareUrl = remember(node.url) { ShareLinkCleaner.cleanUrlForSharing(node.url) }
        val qrBitmap = remember(shareUrl) { encodeQrImageBitmap(shareUrl) }
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 8.dp,
                modifier = Modifier.padding(16.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp),
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap,
                            contentDescription = labels.shareLabel,
                            modifier = Modifier.size(250.dp),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    Button(
                        onClick = {
                            if (shareUrl.isNotEmpty()) {
                                clipboardWriter.writeText(shareUrl)
                            }
                            onDismiss()
                        },
                    ) {
                        Text(text = labels.shareLabel)
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
        SharedBugReport(visible = visible, onDismiss = onDismiss)
    }
}
