package com.android.xrayfa.ui.component

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.android.xrayfa.shared.config.NodeFormEditor
import com.android.xrayfa.shared.ui.config.SharedEditScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

@Composable
fun EditScreen(
    nodeId: Int = 0,
    remark: String? = null,
    protocol: String? = null,
    initialContent: String? = null,
    sharedTransitionScope: SharedTransitionScope,
    onBack: () -> Unit = {},
) {
    val nodeFormEditor = remember { KoinPlatform.getKoin().get<NodeFormEditor>() }
    val scope = rememberCoroutineScope()
    with(sharedTransitionScope) {
        SharedEditScreen(
            nodeId = nodeId,
            protocol = protocol,
            initialContent = initialContent,
            initialRemark = remark,
            nodeFormEditor = nodeFormEditor,
            onBack = onBack,
            onSave = { form ->
                scope.launch(Dispatchers.IO) {
                    nodeFormEditor.saveForm(nodeId, form)
                }
                onBack()
            },
            modifier =
                Modifier.sharedElement(
                    sharedContentState = sharedTransitionScope.rememberSharedContentState(key = nodeId),
                    animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                ),
        )
    }
}
