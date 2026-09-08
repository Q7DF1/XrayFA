package com.android.xrayfa.shared.ui.chrome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedListScaffold(
    title: String,
    modifier: Modifier = Modifier,
    largeTitle: Boolean = true,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    footerUnderBar: @Composable () -> Unit = {},
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (innerBottomPadding: androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    val scrollBehavior =
        if (largeTitle) {
            TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
        } else {
            null
        }
    Scaffold(
        modifier =
            if (scrollBehavior != null) {
                modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
            } else {
                modifier
            },
        contentWindowInsets = contentWindowInsets,
        floatingActionButton = floatingActionButton,
        topBar = {
            Column {
                if (largeTitle) {
                    LargeTopAppBar(
                        title = { Text(title, fontWeight = FontWeight.Bold) },
                        navigationIcon = navigationIcon,
                        actions = actions,
                        scrollBehavior = scrollBehavior,
                        colors =
                            TopAppBarDefaults.largeTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ),
                    )
                } else {
                    TopAppBar(
                        title = { Text(title, fontWeight = FontWeight.Bold) },
                        navigationIcon = navigationIcon,
                        actions = actions,
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ),
                    )
                }
                footerUnderBar()
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            content(innerPadding)
        }
    }
}
