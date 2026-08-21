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
import com.android.xrayfa.shared.ui.rememberSubscriptionUiLabels
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
    val labels = rememberSubscriptionUiLabels()

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
