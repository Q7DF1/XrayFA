package com.android.xrayfa.shared.ui.chrome

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.xrayfa.shared.resources.Res
import com.android.xrayfa.shared.resources.search_clear
import org.jetbrains.compose.resources.stringResource

/**
 * Circular collapsed search control plus a fullscreen overlay.
 *
 * Do not use Material3 [androidx.compose.material3.SearchBar] /
 * [androidx.compose.material3.DockedSearchBar] in commonMain — CMP material3 vs
 * androidx material3 is a NoSuchMethodError on Android, same class of issue as
 * [com.android.xrayfa.shared.ui.widgets.SharedModalBottomSheet].
 */
@Composable
internal fun SharedSearchChrome(
    query: String,
    onQueryChange: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    searchLabel: String,
    onImeSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    results: @Composable ColumnScope.() -> Unit,
) {
    val clearLabel = stringResource(Res.string.search_clear)
    if (expanded) {
        Dialog(
            onDismissRequest = { onExpandedChange(false) },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .imePadding(),
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .focusRequester(focusRequester),
                        placeholder = { Text(searchLabel) },
                        leadingIcon = {
                            Icon(Icons.Outlined.Search, contentDescription = searchLabel)
                        },
                        trailingIcon =
                            if (query.isNotEmpty()) {
                                {
                                    IconButton(
                                        onClick = { onQueryChange("") },
                                    ) {
                                        Icon(Icons.Outlined.Close, contentDescription = clearLabel)
                                    }
                                }
                            } else {
                                null
                            },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions =
                            KeyboardActions(
                                onSearch = { onImeSearch(query) },
                            ),
                    )
                    results()
                }
            }
        }
    } else {
        Surface(
            modifier =
                modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .clickable { onExpandedChange(true) },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = searchLabel,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}
