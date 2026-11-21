package com.android.xrayfa

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.xrayfa.shared.navigation.RootComponent
import com.android.xrayfa.shared.navigation.RootStackConfig
import com.android.xrayfa.shared.navigation.SettingsComponent
import com.android.xrayfa.shared.navigation.createRootComponent
import com.android.xrayfa.shared.ui.RootContent
import com.android.xrayfa.shared.ui.platform.LocalPlatformRootHooks
import com.android.xrayfa.shared.ui.platform.PlatformRootHooks
import com.android.xrayfa.shared.ui.settings.SettingsUiLabels
import com.android.xrayfa.shared.ui.settings.SharedAppListItem
import com.android.xrayfa.shared.ui.settings.SharedAppsPickerScreen
import com.android.xrayfa.shared.ui.settings.SharedAppsSearchScreen
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated UI data, with the real root stack and shared-element transition. */
@RunWith(AndroidJUnit4::class)
class AppsListInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun searchTransitionKeepsSelectionAndReturnsToApps() {
        assumeTrue(InstrumentationRegistry.getInstrumentation().targetContext.packageName == "com.android.xrayfa.appstest")
        val lifecycle = LifecycleRegistry()
        lateinit var root: RootComponent
        var query by mutableStateOf("")
        var selected by mutableStateOf(emptySet<String>())
        var toggles = 0
        val labels = SettingsUiLabels(
            appsInfoTitle = "All apps",
            appsSearchLabel = "Search apps",
            cancelLabel = "Back",
            appsNoMatchesMessage = "No matching apps",
        )
        val source = listOf(
            SharedAppListItem("org.example.alpha", "Alpha", false),
            SharedAppListItem("org.example.beta", "Beta", false),
            SharedAppListItem(
                "org.example.long.application.identifier.with.many.segments",
                "A very long application name that should remain on one line",
                false,
            ),
        )
        val toggle: (String, Boolean) -> Unit = { name, checked ->
            toggles++
            selected = if (checked) selected + name else selected - name
        }
        compose.runOnUiThread {
            lifecycle.resume()
            root = createRootComponent(lifecycle)
            root.openSettings()
            root.openApps()
        }
        try {
            compose.setContent {
                val defaults = LocalPlatformRootHooks.current
                val items = source.filter {
                    it.appName.contains(query, true) || it.packageName.contains(query, true)
                }.map { it.copy(selected = it.packageName in selected) }
                val hooks = object : PlatformRootHooks by defaults {
                    @Composable
                    override fun AppsScreen(
                        component: SettingsComponent,
                        onOpenSearch: () -> Unit,
                        onBack: () -> Unit,
                        modifier: Modifier,
                    ) {
                        SharedAppsPickerScreen(
                            items = items,
                            labels = labels,
                            searchQuery = query,
                            onOpenSearch = onOpenSearch,
                            onBack = onBack,
                            onToggle = toggle,
                            modifier = modifier,
                        )
                    }

                    @Composable
                    override fun AppsSearchScreen(
                        component: SettingsComponent,
                        onBack: () -> Unit,
                        modifier: Modifier,
                    ) {
                        SharedAppsSearchScreen(
                            items = items,
                            searchQuery = query,
                            onSearchQueryChange = { query = it },
                            onToggle = toggle,
                            onBack = onBack,
                            labels = labels,
                            modifier = modifier,
                        )
                    }
                }
                CompositionLocalProvider(LocalPlatformRootHooks provides hooks) {
                    MaterialTheme { RootContent(root) }
                }
            }
            // A row is one checkbox target, including its label and package name.
            val alpha = hasText("Alpha") and isToggleable()
            compose.onNode(alpha).assertIsOff().performClick().assertIsOn()
            assertEquals(1, toggles)
            screenshot("apps-list.png")

            compose.mainClock.autoAdvance = false
            compose.onNodeWithContentDescription("Search apps").performClick()
            compose.mainClock.advanceTimeBy(96)
            screenshot("apps-search-transition.png")
            compose.mainClock.advanceTimeBy(600)
            compose.mainClock.autoAdvance = true
            compose.onNode(hasSetTextAction()).assertIsDisplayed()
            compose.onNode(alpha).assertIsOn()
            compose.onNode(hasText("Beta") and isToggleable()).performClick().assertIsOn()
            assertEquals(2, toggles)
            // Multi-selection stays in search rather than dismissing after each tap.
            compose.onNode(hasSetTextAction()).assertExists().performTextReplacement("org.example")
            screenshot("apps-search.png")
            compose.onNodeWithContentDescription("Back").performClick()
            compose.runOnIdle { assertEquals(RootStackConfig.Apps, root.stack.value.active.configuration) }
            compose.onNode(alpha).assertIsOn()
            compose.onNode(hasText("Beta") and isToggleable()).assertIsOn()

            // Rapid edits survive an immediate return; clear and empty states work.
            compose.onNodeWithContentDescription("Search apps").performClick()
            compose.onNode(hasSetTextAction()).performTextReplacement("no-such-package")
            compose.onNodeWithText("No matching apps").assertIsDisplayed()
            compose.onNode(hasSetTextAction()).performTextClearance()
            compose.onNode(alpha).assertIsOn()
            compose.onNode(hasSetTextAction()).performTextReplacement("Alpha")
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNode(alpha).assertIsOn()
            compose.onNodeWithText("Beta").assertDoesNotExist()
            compose.runOnIdle { assertEquals("Alpha", query) }
        } finally {
            compose.runOnUiThread { lifecycle.destroy() }
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(instrumentation.targetContext.cacheDir, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
