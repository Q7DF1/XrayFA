package com.android.xrayfa.shared.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

internal data class MapRouteCurve(val start: Offset, val control: Offset, val end: Offset) {
    val path by lazy {
        Path().apply {
            moveTo(start.x, start.y)
            quadraticTo(control.x, control.y, end.x, end.y)
        }
    }
    val measure by lazy { PathMeasure().apply { setPath(path, false) } }
}

/** Split one continuous curve at the date line so both halves retain the same tangent. */
internal fun makeMapRoute(from: Offset, to: Offset, viewport: MapViewport): List<MapRouteCurve> {
    val dx = to.x - from.x
    val shift = if (abs(dx) <= viewport.width * 0.5f) 0f else if (dx > 0f) -viewport.width else viewport.width
    val end = to + Offset(shift, 0f)
    val distance = (end - from).getDistance()
    val lift = (distance * 0.4f).coerceIn(viewport.height * 0.16f, viewport.height * 0.55f)
    val control = Offset(
        (from.x + end.x) * 0.5f,
        ((from.y + end.y) * 0.5f - lift).coerceAtLeast(viewport.top + viewport.height * 0.02f),
    )
    if (shift == 0f) return listOf(MapRouteCurve(from, control, end))
    val seamX = if (shift > 0f) viewport.left + viewport.width else viewport.left
    val t = ((seamX - from.x) / (end.x - from.x)).coerceIn(0f, 1f)
    fun mix(a: Offset, b: Offset) = a + (b - a) * t
    val firstControl = mix(from, control)
    val secondControl = mix(control, end)
    val seam = mix(firstControl, secondControl)
    val offset = Offset(shift, 0f)
    return listOf(
        MapRouteCurve(from, firstControl, seam),
        MapRouteCurve(seam - offset, secondControl - offset, to),
    )
}

internal fun DrawScope.drawMapConnection(
    route: List<MapRouteCurve>,
    progress: Float,
    source: Color,
    target: Color,
    surface: Color,
) {
    val total = route.sumOf { it.measure.length.toDouble() }.toFloat()
    if (total <= 1f) return
    val visibleLength = total * progress.coerceIn(0f, 1f)
    val arrowDistance = total * 0.68f
    var traversed = 0f
    route.forEach { curve ->
        val length = curve.measure.length
        if (length > 0f) {
            val startColor = lerp(source, target, traversed / total)
            val endColor = lerp(source, target, (traversed + length) / total)
            val brush = Brush.linearGradient(listOf(startColor, endColor), curve.start, curve.end)
            // A quiet full route remains visible while the foreground is being revealed.
            drawPath(curve.path, surface, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            drawPath(curve.path, brush, alpha = 0.22f, style = Stroke(1.dp.toPx(), cap = StrokeCap.Round))
            val visible = (visibleLength - traversed).coerceIn(0f, length)
            if (visible > 0f) {
                val path = if (visible >= length) curve.path else Path().also {
                    curve.measure.getSegment(0f, visible, it)
                }
                drawPath(path, brush, alpha = 0.07f, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
                drawPath(path, brush, alpha = 0.14f, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
                drawPath(path, brush, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
            if (total >= 64.dp.toPx() && arrowDistance >= traversed && arrowDistance < traversed + visible) {
                val distance = arrowDistance - traversed
                val tip = curve.measure.getPosition(distance)
                val tangent = curve.measure.getTangent(distance)
                val back = tip - tangent * 5.dp.toPx()
                val wing = Offset(-tangent.y, tangent.x) * 3.dp.toPx()
                val arrow = Path().apply {
                    moveTo((back + wing).x, (back + wing).y)
                    lineTo(tip.x, tip.y)
                    lineTo((back - wing).x, (back - wing).y)
                }
                drawPath(arrow, surface, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                drawPath(arrow, lerp(source, target, 0.68f), style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
            }
            if (progress > 0f && progress < 1f && visibleLength >= traversed && visibleLength < traversed + length) {
                val head = curve.measure.getPosition(visible)
                drawCircle(lerp(startColor, endColor, visible / length).copy(alpha = 0.15f), 7.dp.toPx(), head)
                drawCircle(surface, 3.dp.toPx(), head)
                drawCircle(lerp(startColor, endColor, visible / length), 2.dp.toPx(), head)
            }
        }
        traversed += length
    }
}

internal fun DrawScope.drawMapEndpoint(
    center: Offset,
    color: Color,
    surface: Color,
    destination: Boolean,
    arrival: Float = 1f,
) {
    val emphasis = if (destination) 0.5f + 0.5f * arrival else 1f
    val radius = (if (destination) 22.dp else 16.dp).toPx()
    drawCircle(
        Brush.radialGradient(listOf(color.copy(alpha = 0.18f * emphasis), Color.Transparent), center, radius),
        radius, center,
    )
    drawCircle(color.copy(alpha = 0.25f * emphasis), 9.dp.toPx(), center, style = Stroke(1.dp.toPx()))
    drawCircle(surface, 6.dp.toPx(), center)
    if (destination) {
        drawCircle(color, 4.dp.toPx(), center)
        drawCircle(surface.copy(alpha = 0.9f), 1.3.dp.toPx(), center)
    } else {
        drawCircle(color, 4.dp.toPx(), center, style = Stroke(1.8.dp.toPx()))
        drawCircle(color, 1.4.dp.toPx(), center)
    }
}
