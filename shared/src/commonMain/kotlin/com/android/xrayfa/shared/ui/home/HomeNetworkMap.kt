package com.android.xrayfa.shared.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import com.android.xrayfa.datastore.ConnectMapOrigin
import com.android.xrayfa.shared.resources.Res
import com.android.xrayfa.shared.resources.close_window
import com.android.xrayfa.shared.ui.theme.AppRadius
import com.android.xrayfa.shared.ui.transitions.XrayMotion
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.graphics.Canvas as ImageCanvas

/** A quiet geographic overview. Land is rasterized off the UI thread; idle maps have no animation clock. */
@Composable
fun HomeNetworkMap(
    locations: HomeMapLocations,
    fromLabel: String,
    toLabel: String,
    modifier: Modifier = Modifier,
    onLocationSelected: ((ConnectMapOrigin) -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    var selectedEndpoint by remember(locations.user, locations.server) { mutableStateOf<MapEndpoint?>(null) }
    val selectedLocation = when (selectedEndpoint) {
        MapEndpoint.FROM -> locations.user
        MapEndpoint.TO -> locations.server
        null -> null
    }
    val picking = onLocationSelected != null
    Surface(
        modifier = modifier,
        shape = AppRadius.shapeMedium,
        color = colors.surfaceContainerLow,
    ) {
        Layout(
            content = {
                MapOverview(
                    locations = locations,
                    modifier = Modifier.layoutId(MapPart.Overview).fillMaxSize(),
                    onLocationSelected = onLocationSelected,
                    onEndpointSelected = { selectedEndpoint = it },
                )
                if (!picking && (locations.user != null || locations.server != null)) {
                    Column(Modifier.layoutId(MapPart.Summary)) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = colors.outlineVariant.copy(alpha = 0.45f))
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            EndpointSummary(locations.user, fromLabel, false, { selectedEndpoint = MapEndpoint.FROM }, Modifier.weight(1f))
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowForward,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier.size(16.dp),
                            )
                            EndpointSummary(locations.server, toLabel, true, { selectedEndpoint = MapEndpoint.TO }, Modifier.weight(1f))
                        }
                    }
                }
            },
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val summary = measurables.firstOrNull { it.layoutId == MapPart.Summary }
                ?.measure(Constraints.fixedWidth(width))
            val cropPoles = !picking && listOfNotNull(locations.user, locations.server).all { it.latitude in -55.0..85.0 }
            val aspectRatio = if (cropPoles) 360f / 140f else 2f
            val plotVerticalPadding = if (picking) 8.dp else 40.dp
            val preferredPlotHeight = ((width - 24.dp.toPx()).coerceAtLeast(1f) / aspectRatio + plotVerticalPadding.toPx()).roundToInt()
            // Caption height is measured separately; it no longer consumes a fixed world-map budget.
            val height = constraints.constrainHeight(preferredPlotHeight + (summary?.height ?: 0))
            val plotHeight = (height - (summary?.height ?: 0)).coerceAtLeast(1)
            val overview = measurables.first { it.layoutId == MapPart.Overview }
                .measure(Constraints.fixed(width, plotHeight))
            layout(width, height) {
                overview.placeRelative(0, 0)
                summary?.placeRelative(0, plotHeight)
            }
        }
    }

    if (!picking && selectedLocation != null) {
        AlertDialog(
            onDismissRequest = { selectedEndpoint = null },
            title = { Text(if (selectedEndpoint == MapEndpoint.FROM) fromLabel else toLabel) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selectedLocation.displayName.isNotBlank()) Text(selectedLocation.displayName)
                    if (selectedLocation.ipAddress.isNotBlank()) Text(selectedLocation.ipAddress)
                    Text(selectedLocation.coordinates())
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedEndpoint = null }) { Text(stringResource(Res.string.close_window)) }
            },
        )
    }
}

@Composable
private fun EndpointSummary(
    location: HomeMapLocation?,
    label: String,
    destination: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.clickable(enabled = location != null, role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(Modifier.size(12.dp)) {
            val color = if (location == null) colors.outlineVariant else if (destination) colors.tertiary else colors.primary
            drawCircle(color, radius = 4.dp.toPx(), style = if (destination) androidx.compose.ui.graphics.drawscope.Fill else Stroke(1.5.dp.toPx()))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = location?.let { it.city?.takeIf(String::isNotBlank) ?: it.country?.takeIf(String::isNotBlank) ?: it.coordinates() } ?: "—",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MapOverview(
    locations: HomeMapLocations,
    modifier: Modifier,
    onLocationSelected: ((ConnectMapOrigin) -> Unit)?,
    onEndpointSelected: (MapEndpoint) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val landFill = colors.primary.copy(alpha = 0.10f)
    val coastline = colors.outlineVariant.copy(alpha = 0.65f)
    val accent = colors.primary
    val destinationAccent = colors.tertiary
    val markerSurface = colors.surfaceContainerLow
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        val width = with(density) { maxWidth.toPx() }.roundToInt().coerceAtLeast(1)
        val height = with(density) { maxHeight.toPx() }.roundToInt().coerceAtLeast(1)
        val horizontalInset = with(density) { 12.dp.toPx() }
        val verticalInset = with(density) { 4.dp.toPx() }
        val cropPoles = onLocationSelected == null && listOfNotNull(locations.user, locations.server).all {
            it.latitude in -55.0..85.0
        }
        val viewport = remember(width, height, horizontalInset, verticalInset, cropPoles) {
            MapViewport.fit(
                (width - horizontalInset * 2).coerceAtLeast(1f), (height - verticalInset * 2).coerceAtLeast(1f),
                north = if (cropPoles) 85f else 90f,
                south = if (cropPoles) -55f else -90f,
            ).let {
                it.copy(left = it.left + horizontalInset, top = it.top + verticalInset)
            }
        }
        val bitmap by produceState<ImageBitmap?>(null, width, height, landFill, coastline, density, layoutDirection, viewport, onLocationSelected != null) {
            value = withContext(Dispatchers.Default) {
                ImageBitmap(width, height).also { image ->
                    CanvasDrawScope().draw(density, layoutDirection, ImageCanvas(image), Size(width.toFloat(), height.toFloat())) {
                        val land = Path().apply {
                            fillType = PathFillType.EvenOdd
                            for (polygon in NATURAL_EARTH_LAND_POLYGONS) {
                                if (polygon.size < 6) continue
                                // Keep the small home overview focused on inhabited continents.
                                if (onLocationSelected == null && polygon.indices.filter { it % 2 == 1 }.all { polygon[it] < -60f }) continue
                                val first = viewport.project(polygon[0], polygon[1])
                                moveTo(first.x, first.y)
                                var index = 2
                                while (index + 1 < polygon.size) {
                                    val point = viewport.project(polygon[index], polygon[index + 1])
                                    lineTo(point.x, point.y)
                                    index += 2
                                }
                                close()
                            }
                        }
                        drawPath(land, landFill)
                        drawPath(land, coastline, style = Stroke(0.6.dp.toPx()))
                    }
                }
            }
        }
        val from = locations.user?.let { viewport.project(it.longitude.toFloat(), it.latitude.toFloat()) }
        val to = locations.server?.let { viewport.project(it.longitude.toFloat(), it.latitude.toFloat()) }
        val route = remember(from, to, viewport) {
            if (onLocationSelected == null && from != null && to != null && from.distanceTo(to) > 1f) {
                makeMapRoute(from, to, viewport)
            } else emptyList()
        }
        val reveal = remember(route) { Animatable(if (route.isEmpty()) 1f else 0f) }
        LaunchedEffect(reveal) {
            if (route.isNotEmpty()) reveal.animateTo(1f, XrayMotion.MapConnectionReveal)
        }
        val tapRadius = with(density) { 24.dp.toPx() }
        Canvas(Modifier.fillMaxSize().pointerInput(from, to, viewport, onLocationSelected, onEndpointSelected) {
            detectTapGestures { tap ->
                if (onLocationSelected != null) {
                    viewport.unproject(tap)?.let(onLocationSelected)
                } else {
                    val fromDistance = from?.distanceTo(tap) ?: Float.MAX_VALUE
                    val toDistance = to?.distanceTo(tap) ?: Float.MAX_VALUE
                    when {
                        fromDistance <= tapRadius && fromDistance <= toDistance -> onEndpointSelected(MapEndpoint.FROM)
                        toDistance <= tapRadius -> onEndpointSelected(MapEndpoint.TO)
                    }
                }
            }
        }) {
            bitmap?.let { drawImage(it) }
            drawMapConnection(route, reveal.value, accent, destinationAccent, markerSurface)
            from?.let {
                drawMapEndpoint(it, accent, markerSurface, destination = false)
            }
            to?.let {
                drawMapEndpoint(it, destinationAccent, markerSurface, destination = true, arrival = reveal.value)
            }
        }
    }
}

private fun HomeMapLocation.coordinates(): String =
    "${(latitude * 10).roundToInt() / 10.0}°, ${(longitude * 10).roundToInt() / 10.0}°"

private enum class MapPart { Overview, Summary }
private enum class MapEndpoint { FROM, TO }
private fun Offset.distanceTo(other: Offset): Float = hypot(x - other.x, y - other.y)
internal data class MapViewport(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val north: Float = 90f,
    val south: Float = -90f,
) {
    fun project(longitude: Float, latitude: Float): Offset = Offset(
        x = left + ((longitude + 180f) / 360f).coerceIn(0f, 1f) * width,
        y = top + ((north - latitude) / (north - south)).coerceIn(0f, 1f) * height,
    )

    fun unproject(point: Offset): ConnectMapOrigin? {
        if (width <= 0f || height <= 0f || !point.x.isFinite() || !point.y.isFinite() ||
            point.x !in left..(left + width) || point.y !in top..(top + height)
        ) return null
        return ConnectMapOrigin.fromCoordinates(
            (north.toDouble() - (point.y - top) / height * (north - south)).coerceIn(-90.0, 90.0),
            ((point.x - left) / width * 360.0 - 180.0).coerceIn(-180.0, 180.0),
        )
    }

    companion object {
        fun fit(width: Float, height: Float, north: Float = 90f, south: Float = -90f): MapViewport {
            require(north > south && north <= 90f && south >= -90f)
            val aspectRatio = 360f / (north - south)
            val mapWidth = minOf(width, height * aspectRatio)
            val mapHeight = mapWidth / aspectRatio
            return MapViewport(
                left = (width - mapWidth) * 0.5f,
                top = (height - mapHeight) * 0.5f,
                width = mapWidth,
                height = mapHeight,
                north = north,
                south = south,
            )
        }
    }
}
