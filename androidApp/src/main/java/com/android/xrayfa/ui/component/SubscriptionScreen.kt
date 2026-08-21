package com.android.xrayfa.ui.component

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.android.xrayfa.R
import com.android.xrayfa.model.Subscription
import com.android.xrayfa.shared.navigation.rememberSubscriptionComponent
import com.android.xrayfa.shared.ui.subscription.SharedSubscriptionScreen
import com.android.xrayfa.shared.ui.subscription.SubscriptionUiLabels
import com.android.xrayfa.ui.navigation.Config
import com.android.xrayfa.ui.navigation.NavigateDestination
import com.android.xrayfa.ui.navigation.ScanQR
import com.android.xrayfa.viewmodel.XrayViewmodel

@Composable
fun SubscriptionScreen(
    xrayViewmodel: XrayViewmodel,
    onNavigate: (NavigateDestination) -> Unit,
) {
    val context = LocalContext.current
    val component = rememberSubscriptionComponent()
    val labels =
        SubscriptionUiLabels(
            title = stringResource(R.string.menu_subscription),
            noSubscriptions = stringResource(R.string.no_subscriptions),
            addSubscription = stringResource(R.string.add_subscription),
            editSubscription = stringResource(R.string.edit_subscription),
            nickName = stringResource(R.string.nick_name),
            subscriptionUrl = stringResource(R.string.subscription_url),
            preNode = stringResource(R.string.pre_node),
            nextNode = stringResource(R.string.next_node),
            none = stringResource(R.string.none),
            cancel = stringResource(R.string.cancel),
            confirm = stringResource(R.string.confirm),
            delete = stringResource(R.string.delete),
            edit = stringResource(R.string.edit),
            subscribeFailed = stringResource(R.string.subscribe_failed),
            duplicateMarkError = stringResource(R.string.err_subscription_mark_duplicate),
            manageSubscriptions = stringResource(R.string.menu_subscription),
            importFromClipboard = stringResource(R.string.import_manually),
            shareSubscriptionUrl = stringResource(R.string.share_subscription),
            subscriptionUrlCopied = stringResource(R.string.copied),
            scanQr = stringResource(R.string.scan_qr_title),
        )

    SharedSubscriptionScreen(
        component = component,
        onBack = { onNavigate(Config) },
        onSubscriptionApplied = { subscriptionId ->
            xrayViewmodel.selectSubscription(subscriptionId)
            onNavigate(Config)
        },
        labels = labels,
        modifier = Modifier,
        onScanQr = {
            onNavigate(
                ScanQR { result ->
                    if (result.isEmpty()) {
                        Toast.makeText(context, R.string.cancel, Toast.LENGTH_SHORT).show()
                    } else {
                        component.addOrUpdateSubscription(
                            subscription =
                                Subscription(
                                    id = 0,
                                    url = result,
                                    mark = "",
                                ),
                            onSuccess = { id ->
                                xrayViewmodel.selectSubscription(id)
                                onNavigate(Config)
                            },
                        )
                    }
                },
            )
        },
    )
}
