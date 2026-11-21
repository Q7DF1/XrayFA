package com.android.xrayfa

import android.graphics.Bitmap
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.core.graphics.ColorUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.xrayfa.ui.theme.resolveAndroidColorScheme
import com.android.xrayfa.ui.theme.withSubtleDynamicSurfaces
import java.io.File
import kotlin.math.hypot
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Use APPLICATION_ID=com.android.xrayfa.colortest for isolated visual previews. */
@RunWith(AndroidJUnit4::class)
class DynamicSurfaceColorsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun requireSupportedIsolatedPackage() {
        assumeTrue(context.packageName == "com.android.xrayfa.colortest")
        assumeTrue(Build.VERSION.SDK_INT >= 31)
        compose.activityRule.scenario.onActivity {
            it.setTurnScreenOn(true)
            it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun original(dark: Boolean): ColorScheme =
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

    @Test
    fun dynamicNeutralsKeepLightnessHierarchyAndTextContrast() {
        for (dark in listOf(false, true)) {
            val before = original(dark)
            val after = resolveAndroidColorScheme(context, dark)
            val beforeSurfaces = surfaces(before)
            val afterSurfaces = surfaces(after)
            beforeSurfaces.zip(afterSurfaces).forEach { (old, new) ->
                val oldLab = lab(old)
                val newLab = lab(new)
                assertEquals("Surface lightness must be retained", oldLab[0], newLab[0], 0.6)
                assertTrue(
                    "Surface chroma must be reduced",
                    hypot(newLab[1], newLab[2]) <= hypot(oldLab[1], oldLab[2]) * 0.45 + 0.8,
                )
                assertTrue("Body text contrast", contrast(after.onSurface, new) >= 4.5)
                assertTrue("Supporting text contrast", contrast(after.onSurfaceVariant, new) >= 4.5)
            }
            val ladder = listOf(
                after.surfaceContainerLowest,
                after.surfaceContainerLow,
                after.surfaceContainer,
                after.surfaceContainerHigh,
                after.surfaceContainerHighest,
            ).map { lab(it)[0] }
            ladder.zipWithNext().forEach { (a, b) ->
                assertTrue("Surface hierarchy must remain ordered", if (dark) a <= b else a >= b)
            }
            assertEquals(before.primary, after.primary)
            assertEquals(before.onPrimary, after.onPrimary)
            assertEquals(before.primaryContainer, after.primaryContainer)
            assertEquals(before.onPrimaryContainer, after.onPrimaryContainer)
            assertEquals(before.secondary, after.secondary)
            assertEquals(before.onSecondary, after.onSecondary)
            assertEquals(before.secondaryContainer, after.secondaryContainer)
            assertEquals(before.onSecondaryContainer, after.onSecondaryContainer)
            assertEquals(before.tertiary, after.tertiary)
            assertEquals(before.onTertiary, after.onTertiary)
            assertEquals(before.tertiaryContainer, after.tertiaryContainer)
            assertEquals(before.onTertiaryContainer, after.onTertiaryContainer)
            assertEquals(before.error, after.error)
            assertEquals(before.onError, after.onError)
            assertEquals(before.errorContainer, after.errorContainer)
            assertEquals(before.onErrorContainer, after.onErrorContainer)
        }
    }

    @Test
    fun differentNeutralHuesStaySubtleWithoutFlatteningDarkTheme() {
        val samples = listOf(
            Color(0xFFFFE6C0), Color(0xFFE0EDCB), Color(0xFFD6E4FF),
            Color(0xFF34231D), Color(0xFF233426), Color(0xFF232A3C),
        )
        for (sample in samples) {
            val scheme = lightColorScheme(surface = sample).withSubtleDynamicSurfaces()
            val before = lab(sample)
            val after = lab(scheme.surface)
            assertEquals(before[0], after[0], 0.6)
            assertTrue(hypot(after[1], after[2]) < hypot(before[1], before[2]) * 0.45)
            assertTrue("Wallpaper hue should remain", before[1] * after[1] + before[2] * after[2] > 0)
        }
    }

    @Test
    fun previewSystemPaletteBeforeAndAfterInLightAndDark() {
        var dark by mutableStateOf(false)
        var adjusted by mutableStateOf(false)
        compose.setContent {
            val system = original(dark)
            val colors = if (adjusted) system.withSubtleDynamicSurfaces() else system
            MaterialTheme(colorScheme = colors) {
                Preview(dark, adjusted)
            }
        }
        for (mode in listOf(false, true)) {
            for (subtle in listOf(false, true)) {
                compose.runOnIdle { dark = mode; adjusted = subtle }
                screenshot("dynamic-${if (mode) "dark" else "light"}-${if (subtle) "after" else "before"}.png")
            }
        }
    }

    @Composable
    private fun Preview(dark: Boolean, adjusted: Boolean) {
        val colors = MaterialTheme.colorScheme
        Column(
            Modifier.fillMaxSize().background(colors.background)
                .statusBarsPadding().navigationBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                "${if (dark) "Dark" else "Light"} · ${if (adjusted) "Subtle dynamic surfaces" else "System dynamic surfaces"}",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface,
            )
            Text("Settings", style = MaterialTheme.typography.titleMedium, color = colors.primary)
            Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainerLowest) {
                Column {
                    ListItem(
                        headlineContent = { Text("Connect Map") },
                        supportingContent = { Text("Show connections on the map") },
                        leadingContent = { Icon(Icons.Outlined.Public, contentDescription = null) },
                        trailingContent = { Switch(checked = true, onCheckedChange = {}) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    ListItem(
                        headlineContent = { Text("App routing") },
                        supportingContent = { Text("Only selected apps use the proxy") },
                        trailingContent = { Switch(checked = false, onCheckedChange = {}) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
            Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Connection", style = MaterialTheme.typography.titleMedium)
                    Text("The surface stays quiet; actions keep their dynamic color.", color = colors.onSurfaceVariant)
                    Button(onClick = {}) { Text("Connect") }
                }
            }
            Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainerHigh) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Dialog surface", style = MaterialTheme.typography.titleMedium)
                    Text("A separate tone provides depth without an extra accent overlay.", color = colors.onSurfaceVariant)
                    FilterChip(
                        selected = true,
                        onClick = {},
                        label = { Text("Selected") },
                        leadingIcon = { Icon(Icons.Outlined.Check, contentDescription = null) },
                    )
                }
            }
            Row(Modifier.fillMaxWidth()) {
                surfaces(colors).forEach { color ->
                    Box(Modifier.weight(1f).height(48.dp).background(color))
                }
            }
        }
    }

    private fun surfaces(colors: ColorScheme) = listOf(
        colors.background, colors.surface, colors.surfaceContainerLowest,
        colors.surfaceContainerLow, colors.surfaceContainer,
        colors.surfaceContainerHigh, colors.surfaceContainerHighest,
    )

    private fun lab(color: Color): DoubleArray =
        DoubleArray(3).also { ColorUtils.colorToLAB(color.toArgb(), it) }

    private fun contrast(foreground: Color, background: Color): Double =
        ColorUtils.calculateContrast(foreground.toArgb(), background.toArgb())

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.cacheDir, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
