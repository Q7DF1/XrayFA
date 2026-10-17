package com.android.xrayfa

import android.appwidget.AppWidgetHost
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.android.xrayfa.shared.navigation.defaultSettingsComponentFactory
import com.android.xrayfa.shared.ui.rememberSettingsUiLabels
import com.android.xrayfa.shared.ui.settings.SettingsUiLabels
import com.android.xrayfa.shared.ui.settings.SharedSettingsGeneralSection
import com.android.xrayfa.shared.ui.platform.LocalPlatformRootHooks
import com.android.xrayfa.shared.ui.platform.PlatformRootHooks
import com.android.xrayfa.ui.theme.V2rayForAndroidUITheme
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.destroy
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.res.Configuration
import android.util.TypedValue
import android.widget.ImageView
import androidx.compose.ui.graphics.toArgb
import com.android.xrayfa.datastore.SettingsRepository
import com.android.xrayfa.datastore.Theme
import com.android.xrayfa.shared.ui.theme.AppRadius
import com.android.xrayfa.ui.theme.Typography
import com.android.xrayfa.ui.theme.resolveAndroidColorScheme
import android.graphics.Rect
import android.os.Bundle
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.xrayfa.common.core.StartupFailureKind
import com.android.xrayfa.common.core.StartupStage
import com.android.xrayfa.common.core.VpnStartupFailure
import com.android.xrayfa.common.core.VpnStartupStateStore
import com.android.xrayfa.core.XrayBaseService
import com.android.xrayfa.model.Node
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.widget.ConnectionWidgetProvider
import com.android.xrayfa.widget.ConnectionWidgetUpdater
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/** Uses a real system AppWidgetHost; run only with APPLICATION_ID=com.android.xrayfa.widgettest. */
@RunWith(AndroidJUnit4::class)
class ConnectionWidgetInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val repository get() = GlobalContext.get().get<NodeRepository>()
    private lateinit var host: AppWidgetHost
    private lateinit var hostView: AppWidgetHostView
    private var id = 0

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { fd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).readBytes()
        }
    }

    private fun waitUntil(description: String, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var success = false
            instrumentation.runOnMainSync { success = predicate() }
            if (success) return
            SystemClock.sleep(100)
        }
        fail(description)
    }

    private fun text(id: Int): String? = hostView.findViewById<TextView>(id)?.text?.toString()

    private fun resize(width: Int, height: Int) {
        instrumentation.runOnMainSync {
            val density = context.resources.displayMetrics.density
            hostView.layoutParams = FrameLayout.LayoutParams((width * density).toInt(), (height * density).toInt())
            hostView.updateAppWidgetSize(Bundle(), width, height, width, height)
        }
    }

    private fun assertNotClipped(id: Int) {
        instrumentation.runOnMainSync {
            val view = requireNotNull(hostView.findViewById<View>(id))
            val rect = Rect()
            assertTrue(view.getGlobalVisibleRect(rect))
            assertEquals("Widget child must not be clipped vertically", view.height, rect.height())
        }
    }

    private fun assertThemeTokens(darkTheme: Boolean, compact: Boolean = false) {
        instrumentation.runOnMainSync {
            val colors = resolveAndroidColorScheme(context, darkTheme)
            assertEquals(colors.onSurface.toArgb(), hostView.findViewById<TextView>(R.id.widget_node).currentTextColor)
            assertEquals(colors.primary.toArgb(), hostView.findViewById<TextView>(R.id.widget_status).currentTextColor)
            val roles = if (compact) listOf(
                R.id.widget_node to Typography.titleMedium,
                R.id.widget_status to Typography.bodyMedium,
            ) else listOf(
                R.id.widget_node to Typography.titleLarge,
                R.id.widget_status to Typography.labelLarge,
                R.id.widget_hint to Typography.bodyMedium,
                R.id.widget_download to Typography.titleMedium,
                R.id.widget_switch to Typography.labelLarge,
            )
            roles.forEach { (id, style) ->
                val view = hostView.findViewById<TextView>(id)
                val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, style.fontSize.value, context.resources.displayMetrics)
                assertEquals("Widget font size must match its app Typography role", px, view.textSize, 0.1f)
                assertEquals("Widget typeface must match its app Typography weight", style.fontWeight!!.weight, view.typeface.weight)
            }
            listOf(R.id.widget_surface to colors.surfaceContainerLow, R.id.widget_power_surface to colors.primary).forEach { (id, color) ->
                val image = hostView.findViewById<ImageView>(id)
                val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
                image.draw(Canvas(bitmap))
                assertEquals("Widget surface must use the app color token", color.toArgb(), bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
                bitmap.recycle()
            }
        }
    }

    @Test fun nativePreviewBridgesMatchProjectTokens() {
        for (dark in listOf(false, true)) {
            val configuration = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val previewContext = context.createConfigurationContext(configuration)
            val colors = resolveAndroidColorScheme(previewContext, dark, dynamicColor = false)
            listOf(
                R.color.widget_surface_container_low to colors.surfaceContainerLow,
                R.color.widget_on_surface to colors.onSurface,
                R.color.widget_on_surface_variant to colors.onSurfaceVariant,
                R.color.widget_primary to colors.primary,
                R.color.widget_secondary to colors.secondary,
                R.color.widget_error to colors.error,
                R.color.widget_secondary_container to colors.secondaryContainer,
                R.color.widget_on_secondary_container to colors.onSecondaryContainer,
                R.color.widget_on_primary to colors.onPrimary,
                R.color.widget_outline_variant to colors.outlineVariant,
            ).forEach { (id, color) -> assertEquals(color.toArgb(), previewContext.getColor(id)) }
        }
        val density = context.resources.displayMetrics.density
        assertEquals(AppRadius.field.value, context.resources.getDimension(R.dimen.widget_radius_field) / density, 0.01f)
        assertEquals(AppRadius.medium.value, context.resources.getDimension(R.dimen.widget_radius_medium) / density, 0.01f)
    }

    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(300)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(context.cacheDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun respondToVpnConsent(allow: Boolean) {
        waitUntil("VPN consent must be requested") {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root?.packageName?.toString() != "com.android.vpndialogs") false else {
                root.findAccessibilityNodeInfosByViewId(if (allow) "android:id/button1" else "android:id/button2")
                    .firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            }
        }
        waitUntil("VPN consent dialog must close") {
            instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() != "com.android.vpndialogs"
        }
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().forEach { it.finish() }
        }
        instrumentation.waitForIdleSync()
    }

    @Test fun releaseWidgetLoadsInAnotherProcessAndOpensTheApp() {
        assumeTrue(context.packageName == "com.android.xrayfa.widgettest")
        val releasePackage = InstrumentationRegistry.getArguments().getString("release_widget_package")
        assumeTrue(releasePackage != null)
        val packageName = requireNotNull(releasePackage)
        assumeTrue(packageName == "com.android.xrayfa.widgetrelease")
        if (Build.VERSION.SDK_INT >= 33) shell("pm grant $packageName android.permission.POST_NOTIFICATIONS")
        shell("appwidget grantbind --package ${context.packageName} --user 0")
        val manager = AppWidgetManager.getInstance(context)
        val releaseContext = context.createPackageContext(packageName, 0)
        val rootId = releaseContext.resources.getIdentifier("widget_root", "id", packageName)
        val nodeId = releaseContext.resources.getIdentifier("widget_node", "id", packageName)
        assertTrue(rootId != 0 && nodeId != 0)
        try {
            instrumentation.runOnMainSync {
                host = AppWidgetHost(compose.activity, 7092)
                id = host.allocateAppWidgetId()
                assertTrue(manager.bindAppWidgetIdIfAllowed(id,
                    ComponentName(packageName, ConnectionWidgetProvider::class.java.name)))
                host.startListening()
                hostView = host.createView(compose.activity, id, manager.getAppWidgetInfo(id))
                val root = FrameLayout(compose.activity).apply {
                    setBackgroundColor(0xffd2ddba.toInt())
                    setPadding(32, 110, 32, 32)
                    addView(hostView)
                }
                compose.activity.setContentView(root)
            }
            val largerText = context.resources.configuration.fontScale > 1.1f
            resize(if (largerText) 340 else 300, if (largerText) 220 else 180)
            waitUntil("Release provider must render without RemoteViews errors") {
                hostView.findViewById<View>(rootId) != null && hostView.findViewById<View>(nodeId) != null
            }
            screenshot("widget-release.png")
            instrumentation.runOnMainSync { hostView.findViewById<View>(rootId).performClick() }
            waitUntil("Release widget must open the app") {
                instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == packageName
            }
        } finally {
            if (::host.isInitialized) instrumentation.runOnMainSync { host.stopListening(); host.deleteHost() }
        }
    }

    @Test fun settingsSwitchControlsTheSystemWidgetCatalogAndExistingInstances() = runBlocking {
        assumeTrue(context.packageName == "com.android.xrayfa.widgettest")
        shell("appwidget grantbind --package ${context.packageName} --user 0")
        val settings = GlobalContext.get().get<SettingsRepository>()
        val originalEnabled = settings.settingsFlow.first().homeScreenWidgetsEnabled
        val manager = AppWidgetManager.getInstance(context)
        val provider = ComponentName(context, ConnectionWidgetProvider::class.java)
        val lifecycle = LifecycleRegistry()
        try {
            settings.setHomeScreenWidgetsEnabled(true)
            waitUntil("Enabled widget must be listed by the system") { manager.installedProviders.any { it.provider == provider } }
            instrumentation.runOnMainSync {
                host = AppWidgetHost(compose.activity, 7093)
                id = host.allocateAppWidgetId()
                assertTrue(manager.bindAppWidgetIdIfAllowed(id, provider))
                host.startListening()
                lifecycle.resume()
            }
            lateinit var component: com.android.xrayfa.shared.navigation.SettingsComponent
            instrumentation.runOnMainSync { component = defaultSettingsComponentFactory()(DefaultComponentContext(lifecycle)) }
            var labels: SettingsUiLabels? = null
            compose.setContent {
                val defaults = LocalPlatformRootHooks.current
                val hooks = object : PlatformRootHooks by defaults { override val supportsHomeScreenWidgets = true }
                labels = rememberSettingsUiLabels()
                V2rayForAndroidUITheme {
                    CompositionLocalProvider(LocalPlatformRootHooks provides hooks) {
                        SharedSettingsGeneralSection(component = component, labels = requireNotNull(labels))
                    }
                }
            }
            compose.waitForIdle()
            val title = requireNotNull(labels).homeScreenWidgetsTitle
            compose.onNodeWithText(title).performScrollTo().performClick()
            waitUntil("Disabled widget must disappear from the launcher's system catalog") {
                manager.installedProviders.none { it.provider == provider }
            }
            assertFalse(settings.settingsFlow.first().homeScreenWidgetsEnabled)
            waitUntil("Disabling must remove already added instances") { manager.getAppWidgetInfo(id) == null }
            val rejectedId = host.allocateAppWidgetId()
            try {
                try {
                    assertFalse("Disabled provider must reject new bindings", manager.bindAppWidgetIdIfAllowed(rejectedId, provider))
                } catch (_: IllegalArgumentException) {
                    // Some Android versions reject missing providers with an exception instead of false.
                }
            } finally { host.deleteAppWidgetId(rejectedId) }
            val startup = GlobalContext.get().get<VpnStartupStateStore>()
            val before = startup.state.value.attemptId
            val finished = java.util.concurrent.CountDownLatch(1)
            GlobalContext.get().get<ConnectionWidgetUpdater>().performAction(connect = true) { finished.countDown() }
            assertTrue(finished.await(10, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals("Stale widget actions must be ignored while disabled", before, startup.state.value.attemptId)
            screenshot("widget-settings-disabled.png")
            compose.onNodeWithText(title).performScrollTo().performClick()
            waitUntil("Re-enabling must restore the provider to the system catalog") { manager.installedProviders.any { it.provider == provider } }
            assertTrue(settings.settingsFlow.first().homeScreenWidgetsEnabled)
            assertNull("Old widget instances must not silently return", manager.getAppWidgetInfo(id))
            val addedAgain = host.allocateAppWidgetId()
            assertTrue("Users must be able to add the widget again", manager.bindAppWidgetIdIfAllowed(addedAgain, provider))
            screenshot("widget-settings-enabled.png")
        } finally {
            settings.setHomeScreenWidgetsEnabled(originalEnabled)
            waitUntil("Original widget availability must be restored") {
                manager.installedProviders.any { it.provider == provider } == originalEnabled
            }
            instrumentation.runOnMainSync {
                lifecycle.destroy()
                if (::host.isInitialized) { host.stopListening(); host.deleteHost() }
            }
        }
    }

    @Test fun realHostRendersResizesAndControlsTheVpn() = runBlocking {
        assumeTrue(context.packageName == "com.android.xrayfa.widgettest")
        shell("appwidget grantbind --package ${context.packageName} --user 0")
        shell("appops set ${context.packageName} ACTIVATE_VPN allow")
        repository.deleteAllNodes()
        val updater = GlobalContext.get().get<ConnectionWidgetUpdater>()
        val startup = GlobalContext.get().get<VpnStartupStateStore>()
        val manager = AppWidgetManager.getInstance(context)
        val settings = GlobalContext.get().get<SettingsRepository>()
        val originalMode = settings.settingsFlow.first().darkMode
        val routingModeString = if (settings.settingsFlow.first().routingMode == 0) {
            R.string.widget_mode_global
        } else {
            R.string.widget_mode_route
        }
        fun statusWithRoutingMode(status: Int) = context.getString(
            R.string.widget_status_mode_format,
            context.getString(status),
            context.getString(routingModeString),
        )
        val originalWidgetsEnabled = settings.settingsFlow.first().homeScreenWidgetsEnabled
        settings.setHomeScreenWidgetsEnabled(true)
        waitUntil("Provider must be enabled before binding") {
            manager.installedProviders.any { it.provider == ComponentName(context, ConnectionWidgetProvider::class.java) }
        }
        settings.setDarkMode(Theme.AUTO_MODE)
        val systemDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        try {
            instrumentation.runOnMainSync {
                host = AppWidgetHost(compose.activity, 7091)
                id = host.allocateAppWidgetId()
                val provider = ComponentName(context, ConnectionWidgetProvider::class.java)
                assertTrue(manager.bindAppWidgetIdIfAllowed(id, provider))
                host.startListening()
                hostView = host.createView(compose.activity, id, manager.getAppWidgetInfo(id))
                val root = FrameLayout(compose.activity).apply {
                    setBackgroundColor(0xffd2ddba.toInt())
                    setPadding(32, 110, 32, 32)
                    addView(hostView)
                }
                compose.activity.setContentView(root)
            }
            val largerText = context.resources.configuration.fontScale > 1.1f
            resize(if (largerText) 340 else 300, if (largerText) 220 else 180)
            updater.refresh {}
            waitUntil("Expanded widget must inflate and show setup") {
                text(R.id.widget_node) == context.getString(R.string.widget_add_node) &&
                    hostView.findViewById<View>(R.id.widget_switch) != null
            }
            screenshot("widget-empty.png")
            repository.addNode(Node(protocolPrefix = "socks", address = "127.0.0.1", port = 9,
                subscriptionId = 0, selected = true, remark = "Hong Kong · HK 01", url = "socks://127.0.0.1:9#WidgetTest"))
            waitUntil("Node selection must update the widget") { text(R.id.widget_node) == "Hong Kong · HK 01" }
            screenshot("widget-disconnected.png")
            val attempt = startup.begin()
            waitUntil("Connecting must disable repeated taps") {
                text(R.id.widget_status) == statusWithRoutingMode(R.string.widget_connecting) &&
                    hostView.findViewById<View>(R.id.widget_power)?.isEnabled == false
            }
            startup.fail(VpnStartupFailure(StartupFailureKind.CORE_INITIALIZATION, StartupStage.CORE, "Test failure"), attempt)
            waitUntil("Failure must offer retry") { text(R.id.widget_status) == statusWithRoutingMode(R.string.widget_failed) }
            screenshot("widget-failed.png")
            instrumentation.runOnMainSync { hostView.findViewById<View>(R.id.widget_power).performClick() }
            waitUntil("Widget power must start the real VPN") { XrayBaseService.statusFlow.value }
            waitUntil("Running service must update the widget") {
                text(R.id.widget_status) == statusWithRoutingMode(R.string.connected)
            }
            assertNotNull(hostView.findViewById<View>(R.id.widget_duration))
            assertEquals("127.0.0.1:9", text(R.id.widget_endpoint))
            assertNotClipped(R.id.widget_power)
            assertNotClipped(R.id.widget_duration)
            assertNotClipped(R.id.widget_node)
            assertNotClipped(R.id.widget_endpoint)
            assertNotClipped(R.id.widget_status)
            assertNotClipped(R.id.widget_download)
            assertNotClipped(R.id.widget_switch)
            assertThemeTokens(systemDark)
            screenshot("widget-connected.png")
            val forcedMode = if (systemDark) Theme.LIGHT_MODE else Theme.DARK_MODE
            settings.setDarkMode(forcedMode)
            val forcedColors = resolveAndroidColorScheme(context, !systemDark)
            waitUntil("Widget must follow the app's explicit dark-mode setting") {
                hostView.findViewById<TextView>(R.id.widget_node)?.currentTextColor == forcedColors.onSurface.toArgb()
            }
            assertThemeTokens(!systemDark)
            screenshot("widget-forced-theme.png")
            settings.setDarkMode(Theme.AUTO_MODE)
            val automaticColors = resolveAndroidColorScheme(context, systemDark)
            waitUntil("Automatic theme must restore system appearance") {
                hostView.findViewById<TextView>(R.id.widget_node)?.currentTextColor == automaticColors.onSurface.toArgb()
            }
            resize(150, 80)
            waitUntil("Resizing must select the compact layout") { hostView.findViewById<View>(R.id.widget_switch) == null && text(R.id.widget_node) != null }
            instrumentation.waitForIdleSync()
            assertNotClipped(R.id.widget_power)
            assertNotClipped(R.id.widget_node)
            assertNotClipped(R.id.widget_status)
            assertThemeTokens(systemDark, compact = true)
            screenshot("widget-compact.png")
            instrumentation.runOnMainSync { hostView.findViewById<View>(R.id.widget_power).performClick() }
            waitUntil("Compact power must stop the real VPN") { !XrayBaseService.statusFlow.value }
            waitUntil("Disconnected state must be reflected") { text(R.id.widget_status) == context.getString(R.string.widget_compact_disconnected) }
            assertEquals("Hong Kong · HK 01", repository.querySelectedNode().first()?.remark)
            if (Build.VERSION.SDK_INT >= 33) shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
            shell("appops set ${context.packageName} ACTIVATE_VPN deny")
            val refreshed = java.util.concurrent.CountDownLatch(1)
            updater.refresh { refreshed.countDown() }
            assertTrue(refreshed.await(10, java.util.concurrent.TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            SystemClock.sleep(300)
            instrumentation.runOnMainSync { hostView.findViewById<View>(R.id.widget_power).performClick() }
            respondToVpnConsent(allow = false)
            assertFalse("Denying permission must not start the VPN", XrayBaseService.statusFlow.value)
            instrumentation.runOnMainSync { hostView.findViewById<View>(R.id.widget_power).performClick() }
            respondToVpnConsent(allow = true)
            waitUntil("Granting permission must complete the original widget start") { XrayBaseService.statusFlow.value }
            waitUntil("Permission start must synchronize widget state") { text(R.id.widget_status) == context.getString(R.string.widget_compact_connected) }
            instrumentation.runOnMainSync { hostView.findViewById<View>(R.id.widget_power).performClick() }
            waitUntil("Widget must stop the VPN after consent flow") { !XrayBaseService.statusFlow.value }
        } finally {
            GlobalContext.get().get<com.android.xrayfa.core.XrayBaseServiceManager>().stopXrayBaseService()
            if (::host.isInitialized) instrumentation.runOnMainSync { host.stopListening(); host.deleteHost() }
            repository.deleteAllNodes()
            settings.setDarkMode(Theme.fromCode(originalMode))
            settings.setHomeScreenWidgetsEnabled(originalWidgetsEnabled)
        }
    }
}
