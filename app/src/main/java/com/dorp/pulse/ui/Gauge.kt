package com.dorp.pulse.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.dorp.pulse.Phase
import com.dorp.pulse.R
import com.dorp.pulse.TestUiState
import com.dorp.pulse.data.SpeedUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private val SCALE_MBPS = listOf(0.0, 5.0, 10.0, 50.0, 100.0, 250.0, 500.0, 1000.0, 2500.0)
private val SCALE_MBYTES = listOf(0.0, 1.0, 2.0, 5.0, 10.0, 25.0, 50.0, 100.0, 300.0)

/** 0..1 position on a non-linear scale, so 5 Mbps and 1 Gbps both register visibly. */
fun gaugeFraction(value: Double, unit: SpeedUnit): Float {
    val scale = if (unit == SpeedUnit.MBYTES) SCALE_MBYTES else SCALE_MBPS
    if (value <= 0) return 0f
    if (value >= scale.last()) return 1f
    val i = scale.indexOfLast { it <= value }
    val local = (value - scale[i]) / (scale[i + 1] - scale[i])
    return ((i + local) / (scale.size - 1)).toFloat()
}

private const val TWO_PI = (2 * PI).toFloat()
private const val POINTS = 180

private enum class StageMode { IDLE, CONNECTING, RUNNING, DONE }

/**
 * The centre of the test screen: an organic four-petal blob that breathes while idle,
 * swells and spins faster with live throughput, carries the phase progress on an
 * outer organic ring, and finally morphs into a capsule holding the result.
 */
@Composable
fun MorphingStage(
    state: TestUiState,
    unit: SpeedUnit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val mode = when (state.phase) {
        Phase.IDLE, Phase.ERROR -> StageMode.IDLE
        Phase.CONNECTING -> StageMode.CONNECTING
        Phase.DONE -> StageMode.DONE
        else -> StageMode.RUNNING
    }
    val upload = state.phase == Phase.UPLOAD

    val liveFraction = if (state.phase == Phase.DOWNLOAD || state.phase == Phase.UPLOAD)
        gaugeFraction(unit.convert(state.liveMbps), unit) else 0f
    val energy by animateFloatAsState(liveFraction, spring(stiffness = Spring.StiffnessLow), label = "energy")
    val morph by animateFloatAsState(
        if (mode == StageMode.DONE) 1f else 0f,
        spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessVeryLow),
        label = "morph",
    )
    val ringProgress by animateFloatAsState(
        if (mode == StageMode.RUNNING) state.progress else 0f,
        tween(220),
        label = "progress",
    )
    val ringAlpha by animateFloatAsState(
        if (mode == StageMode.RUNNING || mode == StageMode.CONNECTING) 1f else 0f,
        label = "ring",
    )
    val fill by animateColorAsState(
        when {
            mode == StageMode.IDLE -> scheme.primary
            upload -> scheme.tertiaryContainer
            else -> scheme.primaryContainer
        },
        label = "fill",
    )
    val accent by animateColorAsState(if (upload) scheme.tertiary else scheme.primary, label = "accent")
    val track = scheme.surfaceContainerHighest

    // Frame clock: rotation and breathing speed up with live throughput.
    var rotation by remember { mutableFloatStateOf(0f) }
    var breath by remember { mutableFloatStateOf(0f) }
    val energyNow by rememberUpdatedState(energy)
    val activeNow by rememberUpdatedState(mode == StageMode.RUNNING || mode == StageMode.CONNECTING)
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                val spin = if (activeNow) 0.35f + 1.9f * energyNow else 0.18f
                val pace = if (activeNow) 2.4f + 5.5f * energyNow else 1.6f
                rotation = (rotation + dt * spin) % TWO_PI
                breath = (breath + dt * pace) % TWO_PI
            }
        }
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        if (pressed) 0.92f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )

    Box(
        modifier.clickable(
            enabled = mode == StageMode.IDLE,
            interactionSource = interaction,
            indication = null,
            onClick = onStart,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale },
        ) {
            val base = min(size.width, size.height) * 0.40f
            val amp = when (mode) {
                StageMode.IDLE -> 0.20f
                StageMode.CONNECTING -> 0.17f
                else -> 0.15f + 0.16f * energy
            }
            val breathe = 1f + (if (activeNow) 0.018f + 0.035f * energy else 0.03f) * sin(breath)
            val geo = BlobGeometry(
                c = center,
                amp = amp,
                rotation = rotation,
                breath = breath,
                morph = morph,
                capHalfW = min(size.width * 0.46f, base * 2.6f),
                capHalfH = base * 0.56f,
            )

            // Soft halo that pulses opposite to the body.
            val haloScale = 1.08f + 0.06f * (0.5f + 0.5f * sin(breath))
            drawPath(blobPath(geo, base * haloScale, outer = true), accent.copy(alpha = 0.12f * (1f - morph)))

            // Organic progress ring.
            if (ringAlpha > 0.01f) {
                val ring = blobPath(geo, base * 1.16f, outer = true)
                val strokeW = 7.dp.toPx()
                drawPath(ring, track.copy(alpha = ringAlpha), style = Stroke(strokeW, cap = StrokeCap.Round))
                if (ringProgress > 0.002f) {
                    val measure = PathMeasure()
                    measure.setPath(ring, false)
                    val seg = Path()
                    measure.getSegment(0f, measure.length * ringProgress, seg, true)
                    drawPath(seg, accent.copy(alpha = ringAlpha), style = Stroke(strokeW, cap = StrokeCap.Round))
                }
            }

            // Body.
            drawPath(blobPath(geo, base * breathe, outer = false), fill)
        }

        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                (fadeIn(tween(320, delayMillis = 120)) + scaleIn(initialScale = 0.8f)) togetherWith fadeOut(tween(150))
            },
            label = "centre",
        ) { m ->
            when (m) {
                StageMode.IDLE -> Text(
                    stringResource(R.string.go),
                    style = MaterialTheme.typography.displayLarge,
                    color = scheme.onPrimary,
                    modifier = Modifier.graphicsLayer { scaleX = pressScale; scaleY = pressScale },
                )
                StageMode.CONNECTING -> CentreReadout(
                    title = stringResource(R.string.phase_connecting),
                    icon = Icons.Rounded.Language,
                    value = "…",
                    unit = "",
                    tint = scheme.onPrimaryContainer,
                )
                StageMode.RUNNING -> {
                    val ping = state.phase == Phase.PING
                    CentreReadout(
                        title = stringResource(
                            when (state.phase) {
                                Phase.PING -> R.string.phase_ping
                                Phase.UPLOAD -> R.string.phase_upload
                                else -> R.string.phase_download
                            }
                        ),
                        icon = when (state.phase) {
                            Phase.PING -> Icons.Rounded.Timer
                            Phase.UPLOAD -> Icons.Rounded.ArrowUpward
                            else -> Icons.Rounded.ArrowDownward
                        },
                        value = if (ping) state.pingMs?.let { formatMs(it) } ?: "…" else formatSpeed(state.liveMbps, unit),
                        unit = if (ping) stringResource(R.string.unit_ms) else unit.label,
                        tint = if (upload) scheme.onTertiaryContainer else scheme.onPrimaryContainer,
                    )
                }
                StageMode.DONE -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ArrowDownward, null, Modifier.size(28.dp), tint = scheme.onPrimaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        formatSpeed(state.downloadMbps ?: 0.0, unit),
                        style = MaterialTheme.typography.displayMedium,
                        color = scheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(unit.label, style = MaterialTheme.typography.titleMedium, color = scheme.onPrimaryContainer)
                        Text(
                            stringResource(R.string.phase_download),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onPrimaryContainer.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }

        if (mode == StageMode.IDLE) {
            Text(
                stringResource(R.string.tap_to_start),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun CentreReadout(title: String, icon: ImageVector, value: String, unit: String, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp), tint = tint)
            Spacer(Modifier.width(4.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, color = tint)
        }
        Text(value, style = MaterialTheme.typography.displayMedium, color = tint)
        if (unit.isNotEmpty()) Text(unit, style = MaterialTheme.typography.titleSmall, color = tint.copy(alpha = 0.8f))
    }
}

private class BlobGeometry(
    val c: Offset,
    val amp: Float,
    val rotation: Float,
    val breath: Float,
    val morph: Float,
    val capHalfW: Float,
    val capHalfH: Float,
)

/**
 * Samples a rounded four-petal squircle (with a small travelling wobble) and blends
 * each point toward the matching point on a capsule as the morph goes 0→1.
 * The path starts at 12 o'clock so progress strokes read clockwise from the top.
 */
private fun blobPath(g: BlobGeometry, radius: Float, outer: Boolean): Path {
    val path = Path()
    // The ring and halo grow with the capsule so they never cut through it.
    val capScale = if (outer) 1.12f else 1f
    for (i in 0 until POINTS) {
        val th = -PI.toFloat() / 2 + TWO_PI * i / POINTS
        // Clover-like lobes: flat-ish round tops with softened (not pointed) valleys.
        val c2 = cos(2f * (th - g.rotation))
        val lobe = ((c2 * c2 + 0.035f) / 1.035f).pow(0.25f)
        val petal = 1f - g.amp * (1f - lobe) + 0.012f * sin(3f * th + g.breath)
        var x = g.c.x + radius * petal * cos(th)
        var y = g.c.y + radius * petal * sin(th)
        if (g.morph > 0f) {
            val (qx, qy) = stadiumPoint(th, g.capHalfW * capScale, g.capHalfH * capScale)
            x += (g.c.x + qx - x) * g.morph
            y += (g.c.y + qy - y) * g.morph
        }
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/** Where a ray from the centre at angle [th] leaves a horizontal capsule. */
private fun stadiumPoint(th: Float, halfW: Float, halfH: Float): Pair<Float, Float> {
    val dx = cos(th)
    val dy = sin(th)
    val seg = max(0f, halfW - halfH)
    var lo = 0f
    var hi = halfW + halfH
    repeat(22) {
        val t = (lo + hi) / 2
        if (hypot(max(abs(t * dx) - seg, 0f), t * dy) < halfH) lo = t else hi = t
    }
    return lo * dx to lo * dy
}

/** A soft "cookie" outline in the spirit of Material 3 expressive shapes. */
class ScallopShape(private val lobes: Int = 10, private val depth: Float = 0.045f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val cx = size.width / 2
        val cy = size.height / 2
        val r = min(cx, cy)
        for (i in 0..360) {
            val a = 2 * PI * i / 360
            val rr = r * (1 - depth + depth * cos(lobes * a)).toFloat()
            val x = cx + rr * cos(a).toFloat()
            val y = cy + rr * sin(a).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}
