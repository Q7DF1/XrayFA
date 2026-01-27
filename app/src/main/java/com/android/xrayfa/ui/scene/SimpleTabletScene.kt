package com.android.xrayfa.ui.scene

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND

class SimpleTabletScene<T:Any>(
    override val key: Any,
    override val previousEntries: List<NavEntry<T>>,
    val leftEntry: NavEntry<T>,
    val rightEntry: NavEntry<T>,
): Scene<T> {

    override val entries: List<NavEntry<T>> = listOf()

    override val content: @Composable (() -> Unit) = {

        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(0.4f)) {
                leftEntry.Content()
            }
            Column(modifier = Modifier.weight(0.6f)) {
                rightEntry.Content()
            }
        }
    }
}
@Composable
fun <T: Any> rememberXrayFASceneStrategy(): XrayFASceneStrategy<T> {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

    return remember(windowSizeClass) {
        XrayFASceneStrategy(windowSizeClass)
    }
}
class XrayFASceneStrategy<T : Any>(val windowSizeClass: WindowSizeClass) : SceneStrategy<T> {

    companion object {
        internal const val CONFIG_KEY = "ConfigScene"
        internal const val DETAIL_KEY = "DetailScene"

        /**
         * Helper function to add metadata to a [NavEntry] indicating it can be displayed
         * as a list in the [com.android.xrayfa.ui.scene.XrayFASceneStrategy].
         */
        fun configPane() = mapOf(CONFIG_KEY to true)

        /**
         * Helper function to add metadata to a [NavEntry] indicating it can be displayed
         * as a list in the [com.android.xrayfa.ui.scene.XrayFASceneStrategy].
         */
        fun detailPane() = mapOf(DETAIL_KEY to true)
    }

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        if (!windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)) {
            return null
        }

        val configEntry =
            entries.findLast { it.metadata.containsKey(CONFIG_KEY) } ?: return null
        val detailEntry =
            entries.lastOrNull()?.takeIf { it.metadata.containsKey(DETAIL_KEY) } ?: return null

        val sceneKey = configEntry.contentKey

        return SimpleTabletScene(
            key = sceneKey,
            previousEntries = entries.dropLast(1),
            leftEntry = configEntry,
            rightEntry = detailEntry
        )


    }

}
