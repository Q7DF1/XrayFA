package com.android.xrayfa.ui.scene

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND

/**
 * Displays basic configuration information and detailed information
 */
class ConfigDetailScene<T:Any>(
    override val key: Any,
    override val previousEntries: List<NavEntry<T>>,
    val configEntry: NavEntry<T>,
    val detailEntry: NavEntry<T>?,
): Scene<T> {

    override val entries: List<NavEntry<T>> =
        if (detailEntry != null)
            listOf(configEntry,detailEntry)
        else
            listOf(configEntry)

    override val content: @Composable (() -> Unit) = {

        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(0.4f)) {
                configEntry.Content()
            }

            Column(modifier = Modifier.weight(0.6f)) {
                detailEntry?.Content()?: Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text("No Detail")
                }

            }
        }
    }
}

/**
 * Display settings interface and its sub-interface
 */
class SettingsSubScreenScene<T: Any>(

    override val key: Any,
    override val previousEntries: List<NavEntry<T>>,
    val settingsEntry: NavEntry<T>,
    val subEntry: NavEntry<T>?,
): Scene<T> {

    override val entries: List<NavEntry<T>> =
        if
            (subEntry == null )listOf(settingsEntry)
        else
            listOf(settingsEntry,subEntry)

    override val content: @Composable (() -> Unit) = {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(0.3f)) {
                settingsEntry.Content()
            }
            Column(modifier = Modifier.weight(0.7f)) {
                subEntry?.Content()?:Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {/*empty*/}
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

        internal const val SETTINGS_KEY = "SettingsScene"

        internal const val SUBSCREEN_KEY = "SubScreen"

        /**
         * Helper function to add metadata to a [NavEntry] indicating it can be displayed
         * as a list in the [com.android.xrayfa.ui.scene.XrayFASceneStrategy].
         */
        fun config() = mapOf(CONFIG_KEY to true)

        /**
         * Helper function to add metadata to a [NavEntry] indicating it can be displayed
         * as a list in the [com.android.xrayfa.ui.scene.XrayFASceneStrategy].
         */
        fun detail() = mapOf(DETAIL_KEY to true)

        fun settings() = mapOf(SETTINGS_KEY to true)

        fun subscreen() = mapOf(SUBSCREEN_KEY to true)
    }

    fun isTabletScene(): Boolean {
        return windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)
    }

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        if (!isTabletScene()) {
            return null
        }


        val configEntry =
            entries.findLast { it.metadata.containsKey(CONFIG_KEY) }
        val detailEntry =
            entries.lastOrNull()?.takeIf { it.metadata.containsKey(DETAIL_KEY) }

        if (configEntry != null) {
            val sceneKey = configEntry.contentKey

            return ConfigDetailScene(
                key = sceneKey,
                previousEntries = entries.dropLast(1),
                configEntry = configEntry,
                detailEntry = detailEntry
            )
        }
        val settingEntry =
            entries.findLast { it.metadata.containsKey(SETTINGS_KEY) }
        val subEntry = entries.lastOrNull()?.takeIf { it.metadata.containsKey(SUBSCREEN_KEY) }

        if (settingEntry != null) {
            val sceneKey = settingEntry.contentKey
            return SettingsSubScreenScene(
                key = sceneKey,
                previousEntries = entries.dropLast(1),
                settingsEntry = settingEntry,
                subEntry = subEntry
            )
        }

        return null
    }

}
