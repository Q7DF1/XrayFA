package com.android.xrayfa.shared.ui.chrome

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.android.xrayfa.shared.resources.Res
import com.android.xrayfa.shared.resources.search_clear
import org.jetbrains.compose.resources.stringResource

/**
 * 全屏搜索外壳。作为 Decompose 栈目的地的内容渲染，**不是** Dialog —— shared element 的 overlay
 * 穿不过 Dialog 的独立窗口。
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
    searchLabel: String,
    onImeSearch: (String) -> Unit,
    onBack: () -> Unit,
    backContentDescription: String,
    modifier: Modifier = Modifier,
    results: @Composable ColumnScope.() -> Unit,
) {
    val clearLabel = stringResource(Res.string.search_clear)
    Surface(
        modifier = modifier.fillMaxSize(),
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
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = backContentDescription,
                        )
                    }
                },
                trailingIcon =
                    if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Outlined.Close, contentDescription = clearLabel)
                            }
                        }
                    } else {
                        {
                            Icon(Icons.Outlined.Search, contentDescription = searchLabel)
                        }
                    },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onImeSearch(query) }),
            )
            results()
        }
    }
}
