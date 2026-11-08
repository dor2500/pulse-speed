package com.dorp.pulse.ui

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dorp.pulse.Phase
import com.dorp.pulse.R
import com.dorp.pulse.TestError
import com.dorp.pulse.TestUiState
import com.dorp.pulse.data.NetworkKind
import com.dorp.pulse.data.SpeedUnit
import kotlin.math.max

@Composable
fun TestScreen(
    state: TestUiState,
    unit: SpeedUnit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    contentPadding: PaddingValues,
) {
    val haptics = LocalHapticFeedback.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Header(state)
        Spacer(Modifier.height(16.dp))
        MetricsRow(state, unit)
        Spacer(Modifier.height(12.dp))

        Box(
            Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .aspectRatio(1.2f),
            contentAlignment = Alignment.Center,
        ) {
            val showGo = state.phase == Phase.IDLE || state.phase == Phase.ERROR
            AnimatedContent(
                targetState = showGo,
                transitionSpec = {
                    (fadeIn(tween(350)) + scaleIn(initialScale = 0.85f)) togetherWith
                        (fadeOut(tween(200)) + scaleOut(targetScale = 0.9f))
                },
                label = "stage",
            ) { go ->
                if (go) {
                    GoButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStart()
                    })
                } else {
                    GaugeStage(state, unit)
                }
            }
        }

        ActionRow(state, unit, onStart, onCancel)

        AnimatedVisibility(state.error != null, enter = fadeIn() + expandVertically()) {
            ErrorCard(state.error ?: TestError.SERVER)
        }
        AnimatedVisibility(state.downSamples.isNotEmpty(), enter = fadeIn() + expandVertically()) {
            GraphCard(state, unit)
        }
        ConnectionCard(state)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Header(state: TestUiState) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        AssistChip(
            onClick = {},
            label = { Text(state.network.label()) },
            leadingIcon = { Icon(state.network.icon(), null, Modifier.size(AssistChipDefaults.IconSize)) },
        )
    }
}

fun NetworkKind.icon(): ImageVector = when (this) {
    NetworkKind.WIFI -> Icons.Rounded.Wifi
    NetworkKind.CELLULAR -> Icons.Rounded.SignalCellularAlt
    NetworkKind.ETHERNET -> Icons.Rounded.SettingsEthernet
    NetworkKind.VPN -> Icons.Rounded.VpnKey
    NetworkKind.NONE -> Icons.Rounded.WifiOff
    NetworkKind.OTHER -> Icons.Rounded.Router
}

@Composable
private fun MetricsRow(state: TestUiState, unit: SpeedUnit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val pingText = state.pingMs?.let { formatMs(it) } ?: "—"
        val downValue = state.downloadMbps ?: state.liveMbps.takeIf { state.phase == Phase.DOWNLOAD }
        val upValue = state.uploadMbps ?: state.liveMbps.takeIf { state.phase == Phase.UPLOAD }
        MetricCard(
            label = stringResource(R.string.phase_ping),
            value = pingText,
            unit = stringResource(R.string.unit_ms),
            icon = Icons.Rounded.Timer,
            active = state.phase == Phase.PING,
            sub = state.jitterMs?.let { "${stringResource(R.string.jitter)} ${formatMs(it)}" },
            modifier = Modifier.weight(1f),
        )
        MetricCard(
            label = stringResource(R.string.phase_download),
            value = downValue?.let { formatSpeed(it, unit) } ?: "—",
            unit = unit.label,
            icon = Icons.Rounded.ArrowDownward,
            active = state.phase == Phase.DOWNLOAD,
            modifier = Modifier.weight(1f),
        )
        MetricCard(
            label = stringResource(R.string.phase_upload),
            value = upValue?.let { formatSpeed(it, unit) } ?: "—",
            unit = unit.label,
            icon = Icons.Rounded.ArrowUpward,
            active = state.phase == Phase.UPLOAD,
            tertiary = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    unit: String,
    icon: ImageVector,
    active: Boolean,
    modifier: Modifier = Modifier,
    sub: String? = null,
    tertiary: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val activeBg = if (tertiary) scheme.tertiaryContainer else scheme.primaryContainer
    val activeFg = if (tertiary) scheme.onTertiaryContainer else scheme.onPrimaryContainer
    val bg by animateColorAsState(if (active) activeBg else scheme.surfaceContainer, label = "bg")
    val fg by animateColorAsState(if (active) activeFg else scheme.onSurface, label = "fg")
    Surface(modifier, shape = MaterialTheme.shapes.large, color = bg, contentColor = fg) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
            Text(
                sub ?: unit,
                style = MaterialTheme.typography.labelMedium,
                color = fg.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GoButton(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "go")
    val rotation by transition.animateFloat(
        0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "rot",
    )
    val pulse by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2_400, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "pulse",
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Expanding rings
        Canvas(Modifier.fillMaxSize()) {
            val base = size.minDimension * 0.30f
            for (k in 0..1) {
                val p = (pulse + k * 0.5f) % 1f
                drawCircle(
                    color = scheme.primary.copy(alpha = (1f - p) * 0.25f),
                    radius = base + (size.minDimension * 0.2f) * p,
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
        }
        Box(
            Modifier
                .size(210.dp)
                .graphicsLayer { rotationZ = rotation }
                .clip(ScallopShape())
                .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary)))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.graphicsLayer { rotationZ = -rotation },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.go),
                    style = MaterialTheme.typography.displayLarge,
                    color = scheme.onPrimary,
                )
            }
        }
        Text(
            stringResource(R.string.tap_to_start),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp),
        )
    }
}

@Composable
private fun GaugeStage(state: TestUiState, unit: SpeedUnit) {
    val scheme = MaterialTheme.colorScheme
    val upload = state.phase == Phase.UPLOAD
    val accent by animateColorAsState(if (upload) scheme.tertiary else scheme.primary, label = "accent")
    val shown = when (state.phase) {
        Phase.DOWNLOAD, Phase.UPLOAD -> state.liveMbps
        Phase.DONE -> state.downloadMbps ?: 0.0
        else -> 0.0
    }
    SpeedGauge(
        valueInUnit = unit.convert(shown),
        unit = unit,
        accent = accent,
        accentEnd = if (upload) scheme.primary else scheme.tertiary,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val (title, icon) = when (state.phase) {
                Phase.CONNECTING -> stringResource(R.string.phase_connecting) to Icons.Rounded.Language
                Phase.PING -> stringResource(R.string.phase_ping) to Icons.Rounded.Timer
                Phase.DOWNLOAD -> stringResource(R.string.phase_download) to Icons.Rounded.ArrowDownward
                Phase.UPLOAD -> stringResource(R.string.phase_upload) to Icons.Rounded.ArrowUpward
                else -> stringResource(R.string.phase_done) to Icons.Rounded.ArrowDownward
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(18.dp), tint = accent)
                Spacer(Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = accent)
            }
            val big = when (state.phase) {
                Phase.PING -> state.pingMs?.let { formatMs(it) } ?: "…"
                Phase.CONNECTING -> "…"
                else -> formatSpeed(shown, unit)
            }
            val bigUnit = if (state.phase == Phase.PING || state.phase == Phase.CONNECTING)
                stringResource(R.string.unit_ms) else unit.label
            AnimatedContent(targetState = big, transitionSpec = { fadeIn(tween(90)) togetherWith fadeOut(tween(90)) }, label = "num") {
                Text(it, style = MaterialTheme.typography.displayLarge, color = scheme.onSurface)
            }
            Text(bigUnit, style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActionRow(state: TestUiState, unit: SpeedUnit, onStart: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (state.running) {
            val phaseIndex = when (state.phase) {
                Phase.CONNECTING -> 0; Phase.PING -> 0; Phase.DOWNLOAD -> 1; else -> 2
            }
            val overall = ((phaseIndex + state.progress) / 3f).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { overall },
                modifier = Modifier.fillMaxWidth(0.7f).height(6.dp).clip(CircleShape),
                strokeCap = StrokeCap.Round,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onCancel) {
                Icon(Icons.Rounded.Close, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.cancel))
            }
        } else if (state.phase == Phase.DONE) {
            val shareText = stringResource(
                R.string.share_text,
                "${formatSpeed(state.downloadMbps ?: 0.0, unit)} ${unit.label}",
                "${formatSpeed(state.uploadMbps ?: 0.0, unit)} ${unit.label}",
                formatMs(state.pingMs ?: 0.0),
                formatMs(state.jitterMs ?: 0.0),
            )
            val shareTitle = stringResource(R.string.share)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onStart, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.test_again))
                }
                FilledTonalButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
                        context.startActivity(Intent.createChooser(send, shareTitle))
                    },
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    Icon(Icons.Rounded.Share, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(shareTitle)
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(error: TestError) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (error == TestError.OFFLINE) Icons.Rounded.WifiOff else Icons.Rounded.ErrorOutline, null)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(stringResource(R.string.error_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(if (error == TestError.OFFLINE) R.string.error_offline else R.string.error_generic),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun GraphCard(state: TestUiState, unit: SpeedUnit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(stringResource(R.string.graph_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend(scheme.primary, stringResource(R.string.phase_download))
                if (state.upSamples.isNotEmpty()) Legend(scheme.tertiary, stringResource(R.string.phase_upload))
            }
            Spacer(Modifier.height(12.dp))
            val peak = max(
                state.downSamples.maxOrNull() ?: 0f,
                state.upSamples.maxOrNull() ?: 0f,
            ).coerceAtLeast(1f)
            Box(Modifier.fillMaxWidth().height(140.dp)) {
                SparkLine(state.downSamples, peak, scheme.primary, Modifier.fillMaxSize())
                SparkLine(state.upSamples, peak, scheme.tertiary, Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${stringResource(R.string.history_best)}: ${formatSpeed(peak.toDouble(), unit)} ${unit.label}",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SparkLine(samples: List<Float>, peak: Float, color: Color, modifier: Modifier) {
    if (samples.size < 2) return
    Canvas(modifier) {
        val stepX = size.width / (samples.size - 1)
        fun pt(i: Int) = Offset(i * stepX, size.height - (samples[i] / peak) * size.height * 0.92f)
        val line = Path()
        line.moveTo(pt(0).x, pt(0).y)
        for (i in 1 until samples.size) {
            val prev = pt(i - 1)
            val cur = pt(i)
            val midX = (prev.x + cur.x) / 2
            line.cubicTo(midX, prev.y, midX, cur.y, cur.x, cur.y)
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0f))))
        drawPath(line, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun ConnectionCard(state: TestUiState) {
    val scheme = MaterialTheme.colorScheme
    val server = state.server
    Card(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                stringResource(R.string.connection_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            )
            InfoRow(Icons.Rounded.Business, stringResource(R.string.provider), server?.isp?.ifBlank { null } ?: "—")
            InfoRow(Icons.Rounded.Dns, stringResource(R.string.server), server?.serverLabel?.ifBlank { null } ?: "—")
            InfoRow(Icons.Rounded.Language, stringResource(R.string.ip_address), server?.ip?.ifBlank { null } ?: "—")
            InfoRow(state.network.icon(), stringResource(R.string.network), state.network.label())
            InfoRow(
                Icons.AutoMirrored.Rounded.ArrowForward,
                stringResource(R.string.protocol),
                server?.protocol?.ifBlank { null } ?: "—",
                last = true,
            )
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String, last: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 200.dp),
        )
    }
    if (!last) HorizontalDivider(Modifier.padding(start = 68.dp, end = 18.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}
