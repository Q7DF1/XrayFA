package com.android.xrayfa.shared.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Draws both location pins and their labels from the same latitude/longitude projection. */
@Composable
fun HomeNetworkMap(
    locations: HomeMapLocations,
    fromLabel: String,
    toLabel: String,
    modifier: Modifier = Modifier,
) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val mapTint = if (darkTheme) Color(0xFF4ABBCB) else Color(0xFF398D9D)
    val fromAccent = if (darkTheme) Color(0xFFFFC66D) else Color(0xFF986000)
    val toAccent = if (darkTheme) Color(0xFF57D9E6) else Color(0xFF087E8B)
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val mapWidthPx = with(density) { maxWidth.toPx() }
        val mapHeightPx = with(density) { maxHeight.toPx() }
        val viewport = remember(mapWidthPx, mapHeightPx) { MapViewport.fit(mapWidthPx, mapHeightPx) }
        val calloutWidthPx = with(density) { CALLOUT_WIDTH.toPx() }
        val calloutHeightPx = with(density) { CALLOUT_HEIGHT.toPx() }
        val edgeInsetPx = with(density) { 4.dp.toPx() }
        val calloutGapPx = with(density) { 11.dp.toPx() }
        val markerTapRadiusPx = with(density) { MARKER_TAP_RADIUS.toPx() }
        val fromPoint = locations.user?.let { viewport.project(it.longitude.toFloat(), it.latitude.toFloat()) }
        val toPoint = locations.server?.let { viewport.project(it.longitude.toFloat(), it.latitude.toFloat()) }
        var selectedEndpoint by remember { mutableStateOf<MapEndpoint?>(null) }
        LaunchedEffect(fromPoint, toPoint) {
            if ((selectedEndpoint == MapEndpoint.FROM && fromPoint == null) ||
                (selectedEndpoint == MapEndpoint.TO && toPoint == null)
            ) {
                selectedEndpoint = null
            }
        }
        val selectedPoint = when (selectedEndpoint) {
            MapEndpoint.FROM -> fromPoint
            MapEndpoint.TO -> toPoint
            null -> null
        }
        val selectedLocation = when (selectedEndpoint) {
            MapEndpoint.FROM -> locations.user
            MapEndpoint.TO -> locations.server
            null -> null
        }
        val selectedAccent = when (selectedEndpoint) {
            MapEndpoint.FROM -> fromAccent
            MapEndpoint.TO -> toAccent
            null -> MaterialTheme.colorScheme.primary
        }
        val selectedLabel = when (selectedEndpoint) {
            MapEndpoint.FROM -> fromLabel
            MapEndpoint.TO -> toLabel
            null -> ""
        }
        val callout = selectedPoint?.let {
            placeCallout(
                it,
                mapWidthPx,
                mapHeightPx,
                calloutWidthPx,
                calloutHeightPx,
                edgeInsetPx,
                calloutGapPx,
            )
        }
        val transition = rememberInfiniteTransition(label = "home-map-route")
        val flowProgress =
            transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = ROUTE_ANIMATION_MILLIS, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "home-map-route-progress",
            ).value

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(fromPoint, toPoint) {
                    detectTapGestures { tap ->
                        val fromDistance = fromPoint?.distanceTo(tap)
                        val toDistance = toPoint?.distanceTo(tap)
                        selectedEndpoint = when {
                            fromDistance != null && fromDistance <= markerTapRadiusPx &&
                                (toDistance == null || fromDistance <= toDistance) ->
                                if (selectedEndpoint == MapEndpoint.FROM) null else MapEndpoint.FROM
                            toDistance != null && toDistance <= markerTapRadiusPx ->
                                if (selectedEndpoint == MapEndpoint.TO) null else MapEndpoint.TO
                            else -> null
                        }
                    }
                },
        ) {
            drawMapSurface(viewport, mapTint, darkTheme)

            val route = if (fromPoint != null && toPoint != null) {
                makeRoute(fromPoint, toPoint, viewport)
            } else {
                emptyList()
            }
            if (route.isNotEmpty()) {
                drawAnimatedRoute(route, fromAccent, toAccent, viewport, flowProgress)
            }

            fromPoint?.let { point ->
                if (selectedEndpoint == MapEndpoint.FROM) callout?.let { drawCalloutLeader(point, it, fromAccent) }
                drawLocationMarker(point, fromAccent, selectedEndpoint == MapEndpoint.FROM, flowProgress)
            }
            toPoint?.let { point ->
                if (selectedEndpoint == MapEndpoint.TO) callout?.let { drawCalloutLeader(point, it, toAccent) }
                drawLocationMarker(point, toAccent, selectedEndpoint == MapEndpoint.TO, (flowProgress + 0.5f) % 1f)
            }
        }

        if (selectedLocation != null && selectedEndpoint != null && callout != null) {
            HomeMapLocationCallout(
                label = selectedLabel,
                location = selectedLocation,
                accent = selectedAccent,
                darkTheme = darkTheme,
                modifier = Modifier
                    .offset { IntOffset(callout.x.roundToInt(), callout.y.roundToInt()) }
                    .width(CALLOUT_WIDTH),
            )
        }
    }
}

@Composable
private fun HomeMapLocationCallout(
    label: String,
    location: HomeMapLocation,
    accent: Color,
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(11.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                if (darkTheme) Color(0xEE171D29) else Color(0xF5FFFFFF),
                shape,
            )
            .border(1.dp, accent.copy(alpha = if (darkTheme) 0.52f else 0.40f), shape)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            maxLines = 1,
        )
        location.displayName.takeIf(String::isNotBlank)?.let { displayName ->
            Text(
                text = displayName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun DrawScope.drawMapSurface(viewport: MapViewport, tint: Color, darkTheme: Boolean) {
    val gridColor = tint.copy(alpha = if (darkTheme) 0.075f else 0.105f)
    for (longitude in -180..180 step 45) {
        val start = viewport.project(longitude.toFloat(), 90f)
        val end = viewport.project(longitude.toFloat(), -90f)
        drawLine(gridColor, start, end, 0.8.dp.toPx())
    }
    for (latitude in -60..60 step 30) {
        val start = viewport.project(-180f, latitude.toFloat())
        val end = viewport.project(180f, latitude.toFloat())
        drawLine(gridColor, start, end, 0.8.dp.toPx())
    }

    val land = Path().apply {
        fillType = PathFillType.EvenOdd
        for (polygon in NATURAL_EARTH_LAND_POLYGONS) {
            if (polygon.size < 6) continue
            val first = viewport.project(polygon[0], polygon[1])
            moveTo(first.x, first.y)
            var coordinateIndex = 2
            while (coordinateIndex + 1 < polygon.size) {
                val point = viewport.project(polygon[coordinateIndex], polygon[coordinateIndex + 1])
                lineTo(point.x, point.y)
                coordinateIndex += 2
            }
            close()
        }
    }
    drawPath(land, tint.copy(alpha = if (darkTheme) 0.045f else 0.055f))
    drawPath(land, tint.copy(alpha = if (darkTheme) 0.20f else 0.25f), style = Stroke(0.8.dp.toPx()))

    clipPath(land) {
        var row = 0
        for (latitude in -84..84 step 8) {
            val longitudeOffset = if (row % 2 == 0) 0 else 4
            for (longitude in -176 + longitudeOffset..176 step 8) {
                val bright = (row * 7 + longitude) % 5 == 0
                drawCircle(
                    color = tint.copy(alpha = if (bright) 0.52f else 0.30f),
                    radius = if (bright) 1.2.dp.toPx() else 0.82.dp.toPx(),
                    center = viewport.project(longitude.toFloat(), latitude.toFloat()),
                )
            }
            row++
        }
    }
}

private fun DrawScope.drawAnimatedRoute(
    route: List<RouteCurve>,
    fromColor: Color,
    toColor: Color,
    viewport: MapViewport,
    progress: Float,
) {
    val totalLength = route.sumOf { it.approximateLength().toDouble() }.toFloat()
    if (totalLength <= 0f) return

    val intervals = floatArrayOf(4.dp.toPx(), 5.dp.toPx())
    val dashPhase = progress * (intervals[0] + intervals[1])
    route.forEachIndexed { index, curve ->
        val path = Path().apply {
            moveTo(curve.start.x, curve.start.y)
            quadraticTo(curve.control.x, curve.control.y, curve.end.x, curve.end.y)
        }
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(fromColor.copy(alpha = 0.48f), toColor.copy(alpha = 0.84f)),
                start = curve.start,
                end = curve.end,
            ),
            style = Stroke(
                width = 1.6.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(intervals, -dashPhase),
            ),
        )
        if (index == route.lastIndex) drawRouteArrow(curve, toColor)
    }

    var particleDistance = progress * totalLength
    var particleCurve = route.last()
    for (curve in route) {
        val segmentLength = curve.approximateLength()
        if (particleDistance <= segmentLength) {
            particleCurve = curve
            break
        }
        particleDistance -= segmentLength
    }
    val particleT = (particleDistance / particleCurve.approximateLength().coerceAtLeast(1f)).coerceIn(0f, 1f)
    val particle = particleCurve.pointAt(particleT)
    drawCircle(toColor.copy(alpha = 0.24f), 7.dp.toPx(), particle)
    drawCircle(Color.White.copy(alpha = 0.96f), 2.dp.toPx(), particle)
}

private fun DrawScope.drawRouteArrow(curve: RouteCurve, color: Color) {
    val tip = curve.pointAt(0.88f)
    val before = curve.pointAt(0.80f)
    val dx = tip.x - before.x
    val dy = tip.y - before.y
    val magnitude = hypot(dx, dy).coerceAtLeast(1f)
    val unitX = dx / magnitude
    val unitY = dy / magnitude
    val arrowLength = 6.dp.toPx()
    val wing = 3.dp.toPx()
    val base = Offset(tip.x - unitX * arrowLength, tip.y - unitY * arrowLength)
    val normalX = -unitY
    val normalY = unitX
    val arrow = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(base.x + normalX * wing, base.y + normalY * wing)
        moveTo(tip.x, tip.y)
        lineTo(base.x - normalX * wing, base.y - normalY * wing)
    }
    drawPath(arrow, color, style = Stroke(width = 1.5.dp.toPx()))
}

private fun DrawScope.drawCalloutLeader(point: Offset, placement: CalloutPlacement, color: Color) {
    val end = Offset(
        x = point.x.coerceIn(placement.x, placement.x + placement.width),
        y = point.y.coerceIn(placement.y, placement.y + placement.height),
    )
    drawLine(
        color = color.copy(alpha = 0.58f),
        start = point,
        end = end,
        strokeWidth = 1.dp.toPx(),
    )
}

private fun DrawScope.drawLocationMarker(point: Offset, accent: Color, selected: Boolean, pulse: Float) {
    val pulseRadius = 8.dp.toPx() + 18.dp.toPx() * pulse
    drawCircle(accent.copy(alpha = 0.14f), 9.dp.toPx(), point)
    drawCircle(
        accent.copy(alpha = 0.34f * (1f - pulse)),
        pulseRadius,
        point,
        style = Stroke(width = 1.5.dp.toPx()),
    )
    drawCircle(
        accent.copy(alpha = if (selected) 0.86f else 0.66f),
        (if (selected) 14.dp else 10.dp).toPx(),
        point,
        style = Stroke(width = 1.dp.toPx()),
    )
    drawCircle(accent.copy(alpha = 0.98f), 4.dp.toPx(), point)
    drawCircle(Color.White.copy(alpha = 0.96f), 1.5.dp.toPx(), point)
}

private fun makeRoute(from: Offset, to: Offset, viewport: MapViewport): List<RouteCurve> {
    val deltaX = to.x - from.x
    if (abs(deltaX) <= viewport.width * 0.5f) {
        return listOf(curveBetween(from, to, viewport.height * 0.16f, viewport))
    }

    val routeInset = viewport.height * 0.025f
    val seamY = ((from.y + to.y) * 0.5f - viewport.height * 0.12f)
        .coerceIn(viewport.top + routeInset, viewport.top + viewport.height - routeInset)
    return if (deltaX < 0f) {
        val rightEdge = Offset(viewport.left + viewport.width, seamY)
        val leftEdge = Offset(viewport.left, seamY)
        listOf(
            curveBetween(from, rightEdge, viewport.height * 0.12f, viewport),
            curveBetween(leftEdge, to, viewport.height * 0.12f, viewport),
        )
    } else {
        val leftEdge = Offset(viewport.left, seamY)
        val rightEdge = Offset(viewport.left + viewport.width, seamY)
        listOf(
            curveBetween(from, leftEdge, viewport.height * 0.12f, viewport),
            curveBetween(rightEdge, to, viewport.height * 0.12f, viewport),
        )
    }
}

private fun curveBetween(start: Offset, end: Offset, arcHeight: Float, viewport: MapViewport): RouteCurve {
    val control = Offset(
        x = (start.x + end.x) * 0.5f,
        y = ((start.y + end.y) * 0.5f - arcHeight)
            .coerceIn(viewport.top + viewport.height * 0.01f, viewport.top + viewport.height * 0.99f),
    )
    return RouteCurve(start, control, end)
}

private fun placeCallout(
    point: Offset,
    width: Float,
    height: Float,
    cardWidth: Float,
    cardHeight: Float,
    edgeInset: Float,
    gap: Float,
): CalloutPlacement {
    val preferredX = if (point.x + gap + cardWidth <= width - edgeInset) {
        point.x + gap
    } else {
        point.x - gap - cardWidth
    }
    val x = preferredX.coerceIn(edgeInset, (width - cardWidth - edgeInset).coerceAtLeast(edgeInset))
    val preferredY = if (point.y >= cardHeight + gap) {
        point.y - cardHeight - gap
    } else {
        point.y + gap
    }
    val y = preferredY.coerceIn(edgeInset, (height - cardHeight - edgeInset).coerceAtLeast(edgeInset))
    return CalloutPlacement(x, y, cardWidth, cardHeight)
}

private data class CalloutPlacement(val x: Float, val y: Float, val width: Float, val height: Float)

private data class MapViewport(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    fun project(longitude: Float, latitude: Float): Offset = Offset(
        x = left + ((longitude + 180f) / 360f).coerceIn(0f, 1f) * width,
        y = top + ((90f - latitude) / 180f).coerceIn(0f, 1f) * height,
    )

    companion object {
        fun fit(width: Float, height: Float): MapViewport {
            val mapWidth = minOf(width, height * MAP_ASPECT_RATIO)
            val mapHeight = mapWidth / MAP_ASPECT_RATIO
            return MapViewport(
                left = (width - mapWidth) * 0.5f,
                top = (height - mapHeight) * 0.5f,
                width = mapWidth,
                height = mapHeight,
            )
        }
    }
}

private data class RouteCurve(val start: Offset, val control: Offset, val end: Offset) {
    fun pointAt(t: Float): Offset {
        val inverse = 1f - t
        return Offset(
            x = inverse * inverse * start.x + 2f * inverse * t * control.x + t * t * end.x,
            y = inverse * inverse * start.y + 2f * inverse * t * control.y + t * t * end.y,
        )
    }

    fun approximateLength(): Float {
        var length = 0f
        var previous = start
        for (step in 1..16) {
            val next = pointAt(step / 16f)
            length += hypot(next.x - previous.x, next.y - previous.y)
            previous = next
        }
        return length
    }
}

private const val MAP_ASPECT_RATIO = 2f
private const val ROUTE_ANIMATION_MILLIS = 1_500
private val CALLOUT_WIDTH = 148.dp
private val CALLOUT_HEIGHT = 46.dp
private val MARKER_TAP_RADIUS = 28.dp

private enum class MapEndpoint {
    FROM,
    TO,
}

private fun Offset.distanceTo(other: Offset): Float = hypot(x - other.x, y - other.y)
