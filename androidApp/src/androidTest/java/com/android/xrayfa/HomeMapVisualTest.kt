package com.android.xrayfa

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.xrayfa.datastore.ConnectMapOrigin
import com.android.xrayfa.model.Node
import com.android.xrayfa.shared.navigation.HomeComponent
import com.android.xrayfa.shared.navigation.HomeState
import com.android.xrayfa.shared.ui.SharedHomeSection
import com.android.xrayfa.shared.ui.home.HomeMapLocation
import com.android.xrayfa.shared.ui.home.HomeMapLocations
import com.android.xrayfa.shared.ui.home.HomeNetworkMap
import com.android.xrayfa.shared.ui.home.HomeTopBar
import com.android.xrayfa.shared.ui.home.HomeUiLabels
import com.android.xrayfa.ui.theme.V2rayForAndroidUITheme
import com.arkivanov.decompose.value.MutableValue
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Visual review fixtures only. No VPN session or network lookup is created. */
@RunWith(AndroidJUnit4::class)
class HomeMapVisualTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var currentState: MutableValue<HomeState>
    private val locations = HomeMapLocations(
        user = HomeMapLocation("", "上海", null, "中国", 31.23, 121.47),
        server = HomeMapLocation("203.0.113.24", "法兰克福", null, "德国", 50.11, 8.68),
    )

    @Before fun requireIsolatedPackage() {
        assumeTrue(InstrumentationRegistry.getInstrumentation().targetContext.packageName == "com.android.xrayfa.mappreview")
    }

    private fun showHome(dark: Boolean, connected: Boolean = true, availableHeight: Dp? = null, availableWidth: Dp? = null, fontScale: Float = 1f) {
        val component = object : HomeComponent {
            override val state = MutableValue(HomeState(
                connectMapEnabled = true,
                connectMapOrigin = ConnectMapOrigin(31.23, 121.47),
                isConnected = connected,
                selectedNode = Node(protocolPrefix = "vless", address = "node.example", port = 443,
                    subscriptionId = 0, url = "", remark = "DE · FRA 01", countryISO = "🇩🇪"),
                uploadSpeedKbps = 128.0, downloadSpeedKbps = 2368.0, delayMs = 38,
            ))
            override fun onConnectToggle() = Unit
            override fun onTestDelay() = Unit
        }
        currentState = component.state
        compose.setContent {
            V2rayForAndroidUITheme(darkTheme = dark, dynamicColor = false) {
                val background = MaterialTheme.colorScheme.background.toArgb()
                SideEffect {
                    compose.activity.window.statusBarColor = background
                    compose.activity.window.navigationBarColor = background
                    WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column {
                        HomeTopBar(onSettingsClick = {})
                        val density = LocalDensity.current
                        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                            SharedHomeSection(
                                component = component,
                                labels = HomeUiLabels(
                                    connectedLabel = "已连接", disconnectedLabel = "未连接",
                                    connectedHint = "轻触下方按钮断开连接", disconnectedHint = "轻触下方按钮开始连接",
                                    uploadLabel = "上传", downloadLabel = "下载",
                                    mapFromLabel = "我的位置", mapToLabel = "连接地点",
                                ),
                                mapLocations = if (connected) locations else HomeMapLocations(),
                                modifier = (if (availableHeight == null) Modifier.weight(1f).padding(bottom = 90.dp) else Modifier.height(availableHeight))
                                    .then(if (availableWidth == null) Modifier else Modifier.width(availableWidth)),
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        // Capture the rendered Compose window directly; UiAutomation can return an old activity frame.
        val roots = compose.onAllNodes(isRoot())
        val bitmap = roots[roots.fetchSemanticsNodes().lastIndex].captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun lightHomeAndLocationDetails() {
        showHome(dark = false)
        compose.onNodeWithText("法兰克福").assertIsDisplayed()
        screenshot("map-home-light.png")
        compose.onNodeWithText("法兰克福").performClick()
        compose.onNodeWithText("203.0.113.24").assertIsDisplayed()
        screenshot("map-home-details.png")
    }

    @Test fun darkHome() {
        showHome(dark = true)
        screenshot("map-home-dark.png")
    }

    @Test fun disconnectedHome() {
        showHome(dark = false, connected = false)
        screenshot("map-home-disconnected.png")
    }

    @Test fun idleMapDoesNotContinuouslyRedraw() {
        val draws = AtomicInteger()
        compose.setContent {
            V2rayForAndroidUITheme {
                HomeNetworkMap(locations, "我的位置", "连接地点",
                    Modifier.height(210.dp).drawWithContent { draws.incrementAndGet(); drawContent() })
            }
        }
        compose.waitForIdle()
        // Allow the one-off background land rasterization to finish before measuring idle.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(300)
        compose.waitForIdle()
        val before = draws.get()
        Thread.sleep(1_000)
        compose.waitForIdle()
        assertEquals("An idle map must not schedule frame-by-frame redraws", before, draws.get())
    }

    @Test fun buttonStaysAnchoredWhenDashboardContentChanges() {
        showHome(dark = false)
        val button = compose.onNodeWithContentDescription("Toggle VPN")
        val before = button.fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { currentState.value = currentState.value.copy(selectedNode = null) }
        compose.waitForIdle()
        button.assertIsDisplayed()
        val after = button.fetchSemanticsNode().boundsInRoot
        assertEquals(before.top, after.top, 1f)
        assertEquals(before.bottom, after.bottom, 1f)
    }

    @Test fun compactHomeScrollsInformationWithoutMovingButton() {
        showHome(dark = false, availableHeight = 400.dp, availableWidth = 360.dp, fontScale = 1.5f)
        val button = compose.onNodeWithContentDescription("Toggle VPN")
        button.assertIsDisplayed()
        val before = button.fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("DE · FRA 01").performScrollTo().assertIsDisplayed()
        button.assertIsDisplayed()
        val after = button.fetchSemanticsNode().boundsInRoot
        assertEquals(before.top, after.top, 1f)
        assertEquals(before.bottom, after.bottom, 1f)
        screenshot("map-home-compact.png")
    }

    @Test fun smallPhoneFitsAllPrimaryContentWithoutScrolling() {
        showHome(dark = false, availableHeight = 540.dp, availableWidth = 360.dp)
        compose.onNodeWithText("法兰克福").assertIsDisplayed()
        compose.onNodeWithText("DE · FRA 01").assertIsDisplayed()
        compose.onNodeWithContentDescription("Toggle VPN").assertIsDisplayed()
        val scrollable = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .fetchSemanticsNodes().single()
        assertTrue("A normal-font small phone must fit without scrolling",
            scrollable.config[SemanticsProperties.VerticalScrollAxisRange].maxValue() <= 1f)
        screenshot("map-home-small-phone.png")
    }

    @Test fun tallerPhoneKeepsComponentGapsBounded() {
        showHome(dark = false, availableHeight = 700.dp, availableWidth = 400.dp)
        val destination = compose.onNodeWithText("法兰克福").fetchSemanticsNode().boundsInRoot
        val status = compose.onNodeWithText("已连接").fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        val mapToStatus = (status.top - destination.bottom) / density
        assertTrue("Extra height must not stretch component gaps", mapToStatus in 8f..48f)
        screenshot("map-home-tall-phone.png")
    }
}
