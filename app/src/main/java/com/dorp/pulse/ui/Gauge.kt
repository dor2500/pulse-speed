package com.dorp.pulse.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.dorp.pulse.data.SpeedUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val START_ANGLE = 150f
private const val SWEEP = 240f

private val SCALE_MBPS = listOf(0.0, 5.0, 10.0, 50.0, 100.0, 250.0, 500.0, 1000.0, 2500.0)
private val SCALE_MBYTES = listOf(0.0, 1.0, 2.0, 5.0, 10.0, 25.0, 50.0, 100.0, 300.0)

private fun scaleFor(unit: SpeedUnit) = if (unit == SpeedUnit.MBYTES) SCALE_MBYTES else SCALE_MBPS

/** Piecewise-linear position on a non-linear scale, so 5 Mbps and 1 Gbps are both readable. */
fun gaugeFraction(value: Double, unit: SpeedUnit): Float {
    val scale = scaleFor(unit)
    if (value <= 0) return 0f
    if (value >= scale.last()) return 1f
    val i = scale.indexOfLast { it <= value }
    val local = (value - scale[i]) / (scale[i + 1] - scale[i])
    return ((i + local) / (scale.size - 1)).toFloat()
}

private fun tickLabel(v: Double): String = when {
    v >= 1000 -> if (v % 1000 == 0.0) "${(v / 1000).toInt()}G" else "${v / 1000}G"
    else -> v.toInt().toString()
}

@Composable
fun SpeedGauge(
    valueInUnit: Double,
    unit: SpeedUnit,
    accent: Color,
    accentEnd: Color,
    modifier: Modifier = Modifier,
    centerContent: @Composable () -> Unit,
) {
    val fraction by animateFloatAsState(
        targetValue = gaugeFraction(valueInUnit, unit),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessVeryLow),
        label = "gauge",
    )
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    val knob = MaterialTheme.colorScheme.surface
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = tickColor)
    val scale = scaleFor(unit)

    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val (stroke, radius, center) = arcGeometry(size.width, size.height)
            val topLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2, radius * 2)

            drawArc(track, START_ANGLE, SWEEP, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (fraction > 0.001f) {
                drawArc(
                    brush = Brush.linearGradient(
                        listOf(accent, accentEnd),
                        start = Offset(0f, center.y),
                        end = Offset(size.width, center.y),
                    ),
                    startAngle = START_ANGLE,
                    sweepAngle = SWEEP * fraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }

            // Tick labels just inside the arc.
            val labelRadius = radius - stroke * 1.6f
            scale.forEachIndexed { i, v ->
                val angle = (START_ANGLE + SWEEP * i / (scale.size - 1)) * PI / 180
                val layout = measurer.measure(tickLabel(v), labelStyle)
                val x = center.x + labelRadius * cos(angle).toFloat() - layout.size.width / 2f
                val y = center.y + labelRadius * sin(angle).toFloat() - layout.size.height / 2f
                drawText(layout, topLeft = Offset(x, y))
            }

            // Knob at the tip of the progress arc.
            val tip = (START_ANGLE + SWEEP * fraction) * PI / 180
            val tipCenter = Offset(
                center.x + radius * cos(tip).toFloat(),
                center.y + radius * sin(tip).toFloat(),
            )
            drawCircle(accentEnd, radius = stroke * 0.62f, center = tipCenter)
            drawCircle(knob, radius = stroke * 0.32f, center = tipCenter)
        }
        // Keep the readout centred on the arc's centre, not the box's.
        Box(
            Modifier.layout { measurable, constraints ->
                val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val c = arcGeometry(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()).third
                    p.place((c.x - p.width / 2f).roundToInt(), (c.y - p.height / 2f).roundToInt())
                }
            },
        ) { centerContent() }
    }
}

/** The arc spans 240°, so it only needs ~0.77 of its width in height; anchor it to the top. */
private fun arcGeometry(w: Float, h: Float): Triple<Float, Float, Offset> {
    val stroke = w * 0.08f
    val radius = min(w / 2 - stroke / 2, (h - stroke) / 1.5f)
    return Triple(stroke, radius, Offset(w / 2, stroke / 2 + radius))
}

/** A soft "cookie" outline in the spirit of Material 3 expressive shapes. */
class ScallopShape(private val lobes: Int = 10, private val depth: Float = 0.045f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val cx = size.width / 2
        val cy = size.height / 2
        val r = min(cx, cy)
        val steps = 360
        for (i in 0..steps) {
            val a = 2 * PI * i / steps
            val rr = r * (1 - depth + depth * cos(lobes * a)).toFloat()
            val x = cx + rr * cos(a).toFloat()
            val y = cy + rr * sin(a).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}
