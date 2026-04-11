package com.android.xrayfa.ui.component

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.platform.LocalContext
import com.android.xrayfa.viewmodel.AppsViewmodel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.android.xrayfa.R
import com.android.xrayfa.ui.navigation.Apps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppsScreen(
    viewmodel: AppsViewmodel,
    sharedTransitionScope: SharedTransitionScope,
) {

    val listState = rememberLazyListState()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
        rememberTopAppBarState()
    )
    // Observe the overlap fraction to determine if the list is scrolled
    val isScrolled by remember {
        derivedStateOf { scrollBehavior.state.overlappedFraction > 0f }
    }
    val searchAppInfoCompleted by remember { derivedStateOf { viewmodel.searchAppCompleted } }
    // Animate the shadow elevation for a smooth transition
    val appBarElevation by animateDpAsState(
        targetValue = if (isScrolled) 4.dp else 0.dp,
        label = "TopBarShadowElevation"
    )
    val context = LocalContext.current
    with(sharedTransitionScope) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        val searchBarState = rememberSearchBarState()
                        val textFieldState = rememberTextFieldState()
                        LaunchedEffect(searchBarState.targetValue) {
                            if (searchBarState.targetValue == SearchBarValue.Collapsed
                                && textFieldState.text.isEmpty()) {
                                viewmodel.onSearch(textFieldState.text.toString())     // Trigger logic
                            }
                        }

                        val scope = rememberCoroutineScope()
                        val focusManager = LocalFocusManager.current
                        val keyboardController = LocalSoftwareKeyboardController.current
                        var isInputEnabled by remember { mutableStateOf(true) }
                        val inputField =
                            @Composable {
                                SearchBarDefaults.InputField(
                                    textFieldState = textFieldState,
                                    searchBarState = searchBarState,
                                    enabled = isInputEnabled,
                                    onSearch = {
                                        isInputEnabled = false
                                        focusManager.clearFocus(force = true)
                                        keyboardController?.hide()
                                        scope.launch {
                                            delay(400)
                                            if (searchAppInfoCompleted) {
                                                searchBarState.animateToCollapsed()
                                                viewmodel.onSearch(it)
                                            }
                                            isInputEnabled = true
                                        }

                                    },
                                    placeholder = {
                                        Text(modifier = Modifier.clearAndSetSemantics {}, text = "Search")
                                    },
                                    leadingIcon = { Icon(
                                        imageVector = Icons.Outlined.Search,
                                        contentDescription = "search_lab"
                                    ) },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Mic,
                                            contentDescription = "voice_search_lab"
                                        )
                                    },
                                )
                            }
                        SearchBar(
                            state = searchBarState,
                            inputField = inputField,
                            colors = SearchBarDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.background
                            ),
                        )
                        ExpandedFullScreenSearchBar(
                            state = searchBarState,
                            inputField = inputField,
                            colors = SearchBarDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        ) {
                            //todo recommended search
                        }
                    },
//                navigationIcon = {
//                    Icon(
//                        imageVector = Icons.Default.Settings,
//                        contentDescription = "all_app_settings_lab"
//                    )
//                },
                    actions = {
                        IconButton(
                            onClick = {
                                viewmodel.setAllowedPackages(emptyList()) {
                                    viewmodel.getInstalledPackages(context)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteSweep,
                                contentDescription = "unselect all app"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    scrollBehavior = scrollBehavior,
                    modifier = Modifier
                        .shadow(appBarElevation)
                )
            },
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                .sharedElement(
                    sharedTransitionScope.rememberSharedContentState(key = Apps.route),
                    animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                )
        ) { paddingValue ->


            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    viewmodel.getInstalledPackages(context)
                }
            }
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(top = paddingValue.calculateTopPadding())
            ) {

                if (!searchAppInfoCompleted) {
                    LoadingIndicator(
                        modifier = Modifier.align(Alignment.Center)
                            .size(68.dp)
                    )
                }else {
                    val appInfos by viewmodel.appInfos.collectAsState()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                    ) {
                        items(appInfos) { appInfo ->
                            ApkInfoItem(
                                appName = appInfo.appName,
                                painter = appInfo.icon,
                                initChecked = appInfo.allow,
                                onCheck = { checked ->
                                    if (checked) viewmodel.addAllowPackage(appInfo.packageName)
                                    else viewmodel.removeAllowPackage(appInfo.packageName)
                                }
                            )
                            HorizontalDivider(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 48.dp, end = 48.dp),
                                thickness = 1.dp
                            )
                        }
                    }
                }
            }
        }
    }

}

@Composable
fun ApkInfoItem(
    appName: String,
    painter: Painter,
    onCheck: (Boolean) -> Unit,
    initChecked: Boolean
) {
    var checked by remember { mutableStateOf(initChecked) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clickable {
                checked = !checked
                onCheck(checked)
            }
    ) {
        Image(
            painter = painter,
            contentDescription = "app_icon",
            modifier = Modifier.weight(2f)
                .size(24.dp)
        )
        Text(
            text = appName,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(6f)
                .padding(vertical = 16.dp)
        )
        Checkbox(
            checked = checked,
            onCheckedChange = {
                checked = it
                onCheck(checked)
            },
            modifier = Modifier.weight(2f)
        )
    }
}