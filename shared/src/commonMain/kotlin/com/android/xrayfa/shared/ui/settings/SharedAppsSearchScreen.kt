package com.android.xrayfa.shared.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.android.xrayfa.shared.ui.chrome.SharedSearchChrome
import com.android.xrayfa.shared.ui.transitions.TransitionDestinations

/** A stack destination in the same window as the app list, like Config search. */
@Composable
fun SharedAppsSearchScreen(
    items: List<SharedAppListItem>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onBack: () -> Unit,
    labels: SettingsUiLabels = SettingsUiLabels(),
    isLoading: Boolean = false,
    leadingContent: (@Composable (SharedAppListItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    SharedSearchChrome(
        query = searchQuery,
        onQueryChange = onSearchQueryChange,
        searchLabel = labels.appsSearchLabel,
        onImeSearch = {
            focusManager.clearFocus()
            keyboard?.hide()
        },
        onBack = {
            focusManager.clearFocus()
            keyboard?.hide()
            onBack()
        },
        backContentDescription = labels.cancelLabel,
        modifier = modifier,
        transitionDestination = TransitionDestinations.APPS_SEARCH,
    ) {
        val emptyMessage = appsListEmptyMessage(
            itemsEmpty = items.isEmpty(),
            searchQuery = searchQuery,
            noPackagesMessage = labels.appsNoPackagesMessage,
            noMatchesMessage = labels.appsNoMatchesMessage,
        )
        Box(
            Modifier.fillMaxSize().navigationBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isLoading -> CircularProgressIndicator()
                emptyMessage != null -> Text(
                    emptyMessage,
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> SharedAppsList(
                    items = items,
                    onToggle = onToggle,
                    leadingContent = leadingContent,
                )
            }
        }
    }
}
