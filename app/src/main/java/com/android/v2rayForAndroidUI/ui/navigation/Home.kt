package com.android.v2rayForAndroidUI.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.android.v2rayForAndroidUI.model.Node
import com.android.v2rayForAndroidUI.ui.component.V2rayFAHeader
import com.android.v2rayForAndroidUI.ui.component.V2rayStarter
import com.android.v2rayForAndroidUI.viewmodel.XrayViewmodel
import kotlinx.serialization.Serializable

data class Home(
    val node: Node? = null
): NavigateDestination {
    override val icon: ImageVector
        get() = Icons.Default.Home
    override val route: String
        get() = "home"

}


@Composable
fun HomeScreen(
    node: Node? = null,
    xrayViewmodel: XrayViewmodel,
    modifier: Modifier
) {

    val context = LocalContext.current

    var config  by remember { mutableStateOf("111")}
    Column (
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        V2rayFAHeader()

        Button(
            onClick = {
                config = xrayViewmodel.addV2rayConfigFromClipboard(context)
            }
        ) {
            Text("input from clipboard")
        }

        Text(text = config)

        V2rayStarter(xrayViewmodel)
    }
}