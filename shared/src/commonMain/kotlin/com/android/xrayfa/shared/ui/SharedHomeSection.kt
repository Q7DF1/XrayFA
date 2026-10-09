package com.android.xrayfa.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.xrayfa.shared.navigation.HomeComponent
import com.android.xrayfa.shared.ui.home.HomeBrandHeroLayout
import com.android.xrayfa.shared.ui.home.HomeLayoutCallbacks
import com.android.xrayfa.shared.ui.home.HomeUiLabels
import com.arkivanov.decompose.extensions.compose.subscribeAsState

@Composable
fun SharedHomeSection(
    component: HomeComponent,
    modifier: Modifier = Modifier,
    showNodeCard: Boolean = true,
    onConnectToggle: (() -> Unit)? = null,
    labels: HomeUiLabels = HomeUiLabels(),
    scrollEnabled: Boolean = true,
    largeStatusLabel: Boolean = false,
    bottomContentPadding: Dp = 0.dp,
) {
    val state by component.state.subscribeAsState()
    HomeBrandHeroLayout(
        state = state,
        labels = labels,
        showNodeCard = showNodeCard,
        callbacks =
            HomeLayoutCallbacks(
                onConnectToggle = onConnectToggle ?: component::onConnectToggle,
                onTestDelay = component::onTestDelay,
            ),
        scrollEnabled = scrollEnabled,
        largeStatusLabel = largeStatusLabel,
        bottomContentPadding = bottomContentPadding,
        modifier = modifier,
    )
}
