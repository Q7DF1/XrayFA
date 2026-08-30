package com.android.xrayfa.ui.component

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.android.xrayfa.R
import com.android.xrayfa.repository.AppInfoRepository.PermissionState
import com.android.xrayfa.shared.ui.rememberSettingsUiLabels
import com.android.xrayfa.shared.ui.settings.SharedAppListItem
import com.android.xrayfa.shared.ui.settings.SharedAppsPickerScreen
import com.android.xrayfa.ui.navigation.Apps
import com.android.xrayfa.viewmodel.AppsViewmodel
import com.android.xrayfa.viewmodel.AppInfo

@Composable
fun AndroidAppsScreen(
    viewmodel: AppsViewmodel,
    onBack: () -> Unit,
) {
    AppsScreen(viewmodel = viewmodel, onBack = onBack)
}

@Composable
fun AppsScreen(
    viewmodel: AppsViewmodel,
    onBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    modifier: Modifier = Modifier,
) {
    val isLoading by viewmodel.loading.collectAsState()
    val permissionState by viewmodel.permissionState.collectAsState()
    val appInfos by viewmodel.displayedApps.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewmodel.recheckPermission()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(permissionState) {
        if (permissionState != PermissionState.DENIED) {
            viewmodel.load()
        }
    }

    val labels = rememberSettingsUiLabels()
    val pickerModifier = modifier.fillMaxSize()
    val sharedScope = sharedTransitionScope
    if (sharedScope != null) {
        with(sharedScope) {
            SharedAppsPickerScreen(
                items = appInfos.toSharedAppListItems(),
                labels = labels,
                modifier =
                    pickerModifier.sharedElement(
                        sharedContentState = rememberSharedContentState(key = Apps.route),
                        animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                    ),
                onBack = onBack,
                searchQuery = searchQuery,
                onSearchQueryChange = { query ->
                    searchQuery = query
                    viewmodel.onSearch(query)
                },
                onToggle = { packageName, selected ->
                    if (selected) {
                        viewmodel.addAllowPackage(packageName)
                    } else {
                        viewmodel.removeAllowPackage(packageName)
                    }
                },
                onClearAll = { viewmodel.setAllowedPackages(emptyList()) },
                isLoading = isLoading && appInfos.isEmpty(),
                showPermissionDenied = permissionState == PermissionState.DENIED,
                permissionDeniedContent = {
                    PermissionRequiredContent(onRetry = { viewmodel.recheckPermission() })
                },
                leadingContent = { item ->
                val painter = appInfos.firstOrNull { it.packageName == item.packageName }?.icon
                if (painter != null) {
                    Image(
                        painter = painter,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp).padding(start = 8.dp),
                    )
                }
            },
            )
        }
    } else {
        SharedAppsPickerScreen(
            items = appInfos.toSharedAppListItems(),
            labels = labels,
            modifier = pickerModifier,
            onBack = onBack,
            searchQuery = searchQuery,
            onSearchQueryChange = { query ->
                searchQuery = query
                viewmodel.onSearch(query)
            },
            onToggle = { packageName, selected ->
                if (selected) {
                    viewmodel.addAllowPackage(packageName)
                } else {
                    viewmodel.removeAllowPackage(packageName)
                }
            },
            onClearAll = { viewmodel.setAllowedPackages(emptyList()) },
            isLoading = isLoading && appInfos.isEmpty(),
            showPermissionDenied = permissionState == PermissionState.DENIED,
            permissionDeniedContent = {
                PermissionRequiredContent(onRetry = { viewmodel.recheckPermission() })
            },
            leadingContent = { item ->
                val painter = appInfos.firstOrNull { it.packageName == item.packageName }?.icon
                if (painter != null) {
                    Image(
                        painter = painter,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp).padding(start = 8.dp),
                    )
                }
            },
        )
    }
}

private fun List<AppInfo>.toSharedAppListItems() =
    map { info ->
        SharedAppListItem(
            packageName = info.packageName,
            appName = info.appName,
            selected = info.allow,
        )
    }
@Composable
private fun PermissionRequiredContent(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.PrivacyTip,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.apps_permission_required_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.apps_permission_required_desc, appName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                val intent =
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                runCatching { context.startActivity(intent) }
            },
        ) {
            Text(stringResource(R.string.apps_permission_open_settings))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onRetry) {
            Text(stringResource(R.string.apps_permission_retry))
        }
    }
}
