package com.android.v2rayForAndroidUI.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.getString
import com.android.v2rayForAndroidUI.R
import com.android.v2rayForAndroidUI.model.Node
import com.android.v2rayForAndroidUI.ui.component.NodeCard
import com.android.v2rayForAndroidUI.viewmodel.XrayViewmodel

data object Config: NavigateDestination {
    override val icon: ImageVector
        get() = Icons.Default.Build
    override val route: String
        get() = "config"

}


@Composable
fun ConfigScreen(
    onNavigate2Home: (Node) -> Unit,
    xrayViewmodel: XrayViewmodel
) {
    val nodes by xrayViewmodel.getAllNodes().collectAsState(initial = emptyList())
    Box(
        modifier = Modifier.fillMaxSize()
            .padding(16.dp)
    ) {
        LazyColumn() {
            items(nodes) {node ->
                NodeCard(node = node, modifier = Modifier)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        AddConfigButton(
            xrayViewmodel = xrayViewmodel,
            modifier = Modifier.align(BiasAlignment(1f,0.8f))
        )
    }

}


@Composable
fun AddConfigButton(
    xrayViewmodel: XrayViewmodel,
    modifier: Modifier = Modifier
) {
    var toggle by remember { mutableStateOf(false) }
    val context = LocalContext.current
    FloatingActionButton(
        onClick = {
            //
            xrayViewmodel.addV2rayConfigFromClipboard(context = context)
        },
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = ""
            )

            AnimatedVisibility(visible = toggle) {
                Text(
                    text = getString(LocalContext.current,R.string.add_config),
                    modifier = Modifier.padding(start = 8.dp, end = 3.dp)
                )
            }
        }
    }

}