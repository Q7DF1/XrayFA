package com.android.xrayfa.ui.component

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.nestedScrollModifierNode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.android.xrayfa.R
import com.android.xrayfa.dto.Subscription
import com.android.xrayfa.viewmodel.SubscriptionViewmodel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.net.URI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    viewmodel: SubscriptionViewmodel,
    onBack: ()->Unit
) {
    val context = LocalContext.current
    val subscriptions by viewmodel.subscriptions.collectAsState()
    var isBottomSheetShow by remember { mutableStateOf(false) }

    val subscription by viewmodel.selectSubscription.collectAsState()
    var nickName  by remember(subscription) { mutableStateOf(subscription.mark) }
    var url by remember(subscription) { mutableStateOf(subscription.url) }
    var nickNameIsNull by remember { mutableStateOf(false) }
    var urlIsNullOrInvalid by remember { mutableStateOf(false) }

    val deleteDialog by viewmodel.deleteDialog.collectAsState()

    val requesting by viewmodel.requesting.collectAsState()
    val subscribeError by viewmodel.subscribeError.collectAsState()
    val qrBitMap by viewmodel.qrBitmap.collectAsState()


    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
        rememberTopAppBarState()
    )
    // Observe the overlap fraction to determine if the list is scrolled
    val isScrolled by remember {
        derivedStateOf { scrollBehavior.state.overlappedFraction > 0f }
    }

    // Animate the shadow elevation for a smooth transition
    val appBarElevation by animateDpAsState(
        targetValue = if (isScrolled) 4.dp else 0.dp,
        label = "TopBarShadowElevation"
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = {Text("subscription")},
                navigationIcon = {
                    Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.subscription_icon),
                    contentDescription = "subscription icon",
                    modifier = Modifier.size(24.dp)
                    )
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.shadow(appBarElevation)
            )
        },
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            if (requesting) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }else {
                LazyColumn(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                ) {
                    items(items = subscriptions, key = {it.id}) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .background(color = MaterialTheme.colorScheme.surface)
                                .clickable {
                                    viewmodel.getSubscriptionWithCallback(it.url,it.id) {
                                        onBack()
                                    }
                                }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.Start,
                                modifier = Modifier.weight(0.7f)
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
                                    viewmodel.getSubscriptionByIdWithCallback(it.id){
                                        isBottomSheetShow = true
                                    }
                                },
                                modifier = Modifier.weight(0.1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = ""
                                )
                            }

                            IconButton(
                                onClick = {
                                    viewmodel.generateQRCode(id = it.id)
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
                                    viewmodel.showDeleteDialog(it)
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
                        HorizontalDivider()
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
            }


            if (isBottomSheetShow) {
                ModalBottomSheet(
                    onDismissRequest = { isBottomSheetShow = false },
                    containerColor = MaterialTheme.colorScheme.surface
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
                                validateThenConfirm(nickName,url) {
                                    viewmodel.addOrUpdateSubscription(subscription = Subscription(
                                        id = subscription.id,
                                        mark = nickName,
                                        url = url,
                                        isAutoUpdate = subscription.isAutoUpdate
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
            qrBitMap?.let {
                Dialog(onDismissRequest = { viewmodel.dismissQRCode() }) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 8.dp,
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Image(
                                bitmap = qrBitMap!!.asImageBitmap(),
                                contentDescription = "qrcode",
                                modifier = Modifier.size(200.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    viewmodel.exportConfigToClipboard(context)
                                    viewmodel.dismissQRCode()
                                }
                            ) {
                                Text(
                                    text = stringResource(R.string.clipboard_export)
                                )
                            }
                        }
                    }
                }
            }
            if (deleteDialog) {
                DeleteDialog(
                    onDismissRequest = { viewmodel.dismissDeleteDialog() },
                ) {
                    viewmodel.deleteSubscriptionWithDialog()
                }
            }
            ExceptionMessage(subscribeError,stringResource(R.string.subscribe_failed))
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