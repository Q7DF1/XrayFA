package com.android.xrayfa.ui.component

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.xrayfa.R
import com.android.xrayfa.dto.Subscription
import com.android.xrayfa.viewmodel.SubscriptionViewmodel
import kotlinx.coroutines.flow.first
import java.net.URI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    viewmodel: SubscriptionViewmodel
) {
    val subscriptions by viewmodel.subscriptions.collectAsState()
    var isBottomSheetShow by remember { mutableStateOf(false) }

    val subscription by viewmodel.selectSubscription.collectAsState()
    var nickName  by remember(subscription) { mutableStateOf(subscription.mark) }
    var url by remember(subscription) { mutableStateOf(subscription.url) }
    var nickNameIsNull by remember { mutableStateOf(false) }
    var urlIsNullOrInvalid by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
            title = {Text("subscription")},
            navigationIcon = {}
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            LazyColumn(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(items = subscriptions, key = {it.id}) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                viewmodel.getSubscriptionByIdWithCallback(it.id){
                                    isBottomSheetShow = true
                                }
                            }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.Start,
                            modifier = Modifier.weight(0.8f)
                                .padding(start = 8.dp)
                                .padding(vertical = 16.dp)
                        ) {
                            Text(
                                text = it.mark,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1
                            )
                            Text(
                                text = it.url,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = {
                                //todo share
                            },
                            modifier = Modifier.weight(0.1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "share subscription"
                            )
                        }
                        IconButton(
                            onClick = {
                                //todo delete
                            },
                            modifier = Modifier.weight(0.1f)
                                .padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "delete subscription"
                            )
                        }
                    }
                }
            }
            FloatingActionButton(
                onClick = {
                    viewmodel.setSelectSubscriptionEmpty()
                    isBottomSheetShow = true
                          },
                modifier = Modifier.align(BiasAlignment(1f,0.9f))
                    .padding(horizontal = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = ""
                )
            }
            if (isBottomSheetShow) {
                ModalBottomSheet(
                    onDismissRequest = { isBottomSheetShow = false },
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(0.7f)
                            .align(Alignment.CenterHorizontally),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        OutlinedTextField(
                            value = nickName,
                            onValueChange = {
                                nickName = it
                                nickNameIsNull = nickName == ""
                            },
                            label = {Text(stringResource(R.string.nick_name))},
                            maxLines = 1,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                                .fillMaxWidth(),
                            isError = nickNameIsNull
                        )
                        OutlinedTextField(
                            value = url,
                            onValueChange = {
                                url = it
                                urlIsNullOrInvalid = validateUrl(url)
                            },
                            label = {Text(stringResource(R.string.subscription_url))},
                            maxLines = 1,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                                .fillMaxWidth(),
                            isError = urlIsNullOrInvalid
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(
                            onClick = {
                                isBottomSheetShow = false
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.cancel)
                            )
                        }
                        TextButton(
                            onClick = {
//                                validateThenConfirm(nickName,url) {
//                                    viewmodel.addSubscription(subscription = Subscription(
//                                        mark = nickName,
//                                        url = url
//                                    ))
//                                }.also {
//                                    nickNameIsNull = it.first
//                                    urlIsNullOrInvalid = it.second
//                                }
                                isBottomSheetShow = false
                            },
                            enabled = !urlIsNullOrInvalid && !nickNameIsNull,
                            modifier = Modifier.padding(end = 16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.confirm)
                            )
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditModalBottomSheet(
    showBottomSheet: Boolean = false,
    viewmodel: SubscriptionViewmodel,
) {
    val subscription by viewmodel.selectSubscription.collectAsState()
    var nickName by remember { mutableStateOf(subscription.mark) }
    var url by remember { mutableStateOf(subscription.url) }
    var isBottomSheetShow by remember { mutableStateOf(showBottomSheet)}
    var nickNameIsNull by remember { mutableStateOf(false) }
    var urlIsNullOrInvalid by remember { mutableStateOf(false) }
    if (isBottomSheetShow) {
        ModalBottomSheet(onDismissRequest = {
            isBottomSheetShow = false
        }) {
            OutlinedTextField(
                value = nickName,
                onValueChange = {
                    nickName = it
                    nickNameIsNull = nickName == ""
                },
                label = {Text(stringResource(R.string.nick_name))},
                maxLines = 1,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                isError = nickNameIsNull
            )
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    urlIsNullOrInvalid = validateUrl(url)
                },
                label = {Text(stringResource(R.string.subscription_url))},
                maxLines = 1,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                isError = urlIsNullOrInvalid
            )
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = { isBottomSheetShow = false}
                ) {
                    Text(
                        text = stringResource(R.string.cancel)
                    )
                }
                TextButton(
                    onClick = {
                        validateThenConfirm(nickName,url) {
                            viewmodel.addSubscription(subscription = Subscription(
                                mark = nickName,
                                url = url
                            ))
                        }.also {
                            nickNameIsNull = it.first
                            urlIsNullOrInvalid = it.second
                        }
                        isBottomSheetShow = false
                    },
                    enabled = !urlIsNullOrInvalid && !nickNameIsNull,
                    modifier = Modifier.padding(end = 16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.confirm)
                    )
                }
            }
        }
    }
}
@Preview
@Composable
fun ItemDemo() {

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .clickable {}
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.weight(0.8f)
                .padding(start = 8.dp)
                .padding(vertical = 16.dp)
        ) {
            Text(
                text = "nickname",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1
            )
            Text(
                text = "https://121kdjakdjakjdkalskjksjfkkfsjknsbfkshfakjskkjdakdjakbr",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = {
                //todo share
            },
            modifier = Modifier.weight(0.1f)
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "share subscription"
            )
        }
        IconButton(
            onClick = {
                //todo delete
            },
            modifier = Modifier.weight(0.1f)
                .padding(end = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "delete subscription"
            )
        }
    }
}

const val TAG = "SubscriptionScreen"
fun validateUrl(url: String): Boolean {
    if (url.isBlank()) return  false

    return try {
        val uri = URI(url.trim())
        val scheme = uri.scheme?.lowercase()

        (scheme == "http") || (scheme == "https") && !uri.host.isNotBlank()
    }catch (e: Exception) {
        Log.e(TAG, "validateUrl: url is illegal ${e.message}", )
        false
    }
}

fun validateThenConfirm(nickName: String, url: String,onConfirm: ()->Unit): Pair<Boolean,Boolean> {
    val isNickNameNull = nickName.isBlank()
    val isUrlIllegal = validateUrl(url)
    if (!isNickNameNull && !isUrlIllegal) {
        onConfirm()
    }
    return isNickNameNull to isUrlIllegal
}