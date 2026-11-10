package com.android.xrayfa.shared.ui.subscription

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.xrayfa.shared.resources.Res
import com.android.xrayfa.shared.resources.ic_subscription
import com.android.xrayfa.shared.ui.transitions.TransitionDestinations
import com.android.xrayfa.shared.ui.transitions.sharedIcon
import org.jetbrains.compose.resources.painterResource

/** The bottom action and empty state share one icon in both navigation directions. */
@Composable
fun SubscriptionIcon(
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Icon(
        painter = painterResource(Res.drawable.ic_subscription),
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.outline,
        modifier = modifier.sharedIcon(TransitionDestinations.SUBSCRIPTIONS),
    )
}
