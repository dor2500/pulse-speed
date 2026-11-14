package com.dorp.pulse.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.horizontalScroll
import com.dorp.pulse.engine.ServerOption
import com.dorp.pulse.engine.TestSource
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.dorp.pulse.data.ShareLink
import com.dorp.pulse.data.TestResult
import com.dorp.pulse.data.SpeedUnit
import kotlin.math.max

/** Large/small corner radii for the asymmetric expressive cards. */
private val CornerLarge = 28.dp
private val CornerSmall = 12.dp

@Composable
fun TestScreen(
    state: TestUiState,
    unit: SpeedUnit,
    source: TestSource,
    onSourceChange: (TestSource) -> Unit,
    serverChoice: ServerOption?,
    onOpenServers: () -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onDetails: () -> Unit,
    contentPadding: PaddingValues,
) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state.phase) {
        if (state.phase == Phase.DONE) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Header(state)
        Spacer(Modifier.height(12.dp))
        SourcePicker(source, enabled = !state.running, onSourceChange)
        Spacer(Modifier.height(8.dp))
        ServerRow(source, serverChoice, enabled = !state.running, onOpen = onOpenServers)
        Spacer(Modifier.height(12.dp))
        MetricsRow(state, unit)
        Spacer(Modifier.height(8.dp))

        MorphingStage(
            state = state,
            unit = unit,
            onStart = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onStart()
            },
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .aspectRatio(1.08f),
        )

        ActionRow(state, unit, onStart, onCancel)
        if (state.phase == Phase.DONE && state.resultTime != null) {
            TextButton(onClick = onDetails) {
                Text(stringResource(R.string.details_open), style = MaterialTheme.typography.titleSmall)
            }
        }

        AnimatedVisibility(state.error != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            ErrorCard(state.error ?: TestError.SERVER)
        }
        AnimatedVisibility(state.downSamples.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            GraphCard(state, unit)
        }
        AnimatedVisibility(state.phase == Phase.DONE && state.bufferbloatGrade != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            BufferbloatCard(state)
        }
        ConnectionCard(state)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Header(state: TestUiState) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, start = 4.dp),
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
            shape = CircleShape,
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
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicCardHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val downValue = state.downloadMbps ?: state.liveMbps.takeIf { state.phase == Phase.DOWNLOAD }
        val upValue = state.uploadMbps ?: state.liveMbps.takeIf { state.phase == Phase.UPLOAD }
        MetricCard(
            label = stringResource(R.string.phase_ping),
            value = state.pingMs?.let { formatMs(it) } ?: "—",
            sub = state.jitterMs?.let { "${stringResource(R.string.jitter)} ${formatMs(it)}" } ?: stringResource(R.string.unit_ms),
            icon = Icons.Rounded.Timer,
            active = state.phase == Phase.PING,
            accent = Accent.SECONDARY,
        )
        MetricCard(
            label = stringResource(R.string.phase_download),
            value = downValue?.let { formatSpeed(it, unit) } ?: "—",
            sub = unit.label,
            icon = Icons.Rounded.ArrowDownward,
            active = state.phase == Phase.DOWNLOAD,
            accent = Accent.PRIMARY,
        )
        MetricCard(
            label = stringResource(R.string.phase_upload),
            value = upValue?.let { formatSpeed(it, unit) } ?: "—",
            sub = unit.label,
            icon = Icons.Rounded.ArrowUpward,
            active = state.phase == Phase.UPLOAD,
            accent = Accent.TERTIARY,
        )
    }
}

private val IntrinsicCardHeight = 150.dp

private enum class Accent { PRIMARY, SECONDARY, TERTIARY }

@Composable
private fun RowScope.MetricCard(
    label: String,
    value: String,
    sub: String,
    icon: ImageVector,
    active: Boolean,
    accent: Accent,
) {
    val scheme = MaterialTheme.colorScheme
    val (activeBg, activeFg, iconBg) = when (accent) {
        Accent.PRIMARY -> Triple(scheme.primaryContainer, scheme.onPrimaryContainer, scheme.primary)
        Accent.SECONDARY -> Triple(scheme.secondaryContainer, scheme.onSecondaryContainer, scheme.secondary)
        Accent.TERTIARY -> Triple(scheme.tertiaryContainer, scheme.onTertiaryContainer, scheme.tertiary)
    }
    val bouncy = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
    val weight by animateFloatAsState(if (active) 1.25f else 1f, bouncy, label = "weight")
    val scale by animateFloatAsState(if (active) 1.04f else 1f, bouncy, label = "scale")
    val elevation by animateDpAsState(if (active) 8.dp else 0.dp, label = "elev")
    val height by animateDpAsState(if (active) 146.dp else 130.dp, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "h")
    // Asymmetric: diagonal 28/12 corners at rest; the running card rounds out fully.
    val small by animateDpAsState(if (active) CornerLarge else CornerSmall, label = "corner")
    val bg by animateColorAsState(if (active) activeBg else scheme.surfaceContainer, label = "bg")
    val fg by animateColorAsState(if (active) activeFg else scheme.onSurface, label = "fg")
    val badge by animateColorAsState(if (active) iconBg else scheme.surfaceContainerHighest, label = "badge")
    val badgeFg by animateColorAsState(if (active) scheme.surface else scheme.onSurfaceVariant, label = "badgeFg")

    Surface(
        modifier = Modifier
            .weight(weight)
            .height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(topStart = CornerLarge, topEnd = small, bottomEnd = CornerLarge, bottomStart = small),
        color = bg,
        contentColor = fg,
        shadowElevation = elevation,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.Center) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(badge),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(16.dp), tint = badgeFg)
            }
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1, softWrap = false)
            Text(
                sub,
                style = MaterialTheme.typography.labelSmall,
                color = fg.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ActionRow(state: TestUiState, unit: SpeedUnit, onStart: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (state.running) {
            val phaseIndex = when (state.phase) {
                Phase.CONNECTING, Phase.PING -> 0
                Phase.DOWNLOAD -> 1
                else -> 2
            }
            val overall = ((phaseIndex + state.progress) / 3f).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { overall },
                modifier = Modifier.fillMaxWidth(0.6f).height(8.dp).clip(CircleShape),
                strokeCap = StrokeCap.Round,
                gapSize = 4.dp,
            )
            Spacer(Modifier.height(8.dp))
            Spacer(Modifier.height(4.dp))
            // Prominent stop: closes every connection at once and discards the partial run.
            FilledTonalButton(
                onClick = onCancel,
                shape = CircleShape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                modifier = Modifier.height(52.dp),
                contentPadding = PaddingValues(horizontal = 28.dp),
            ) {
                Icon(Icons.Rounded.Stop, null, Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.stop_test), style = MaterialTheme.typography.titleMedium)
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
            val finishedAt = androidx.compose.runtime.remember(state.downloadMbps, state.uploadMbps) { System.currentTimeMillis() }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Primary: solid filled pill.
                Button(
                    onClick = onStart,
                    shape = CircleShape,
                    modifier = Modifier.weight(1.4f).height(56.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                ) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.test_again), style = MaterialTheme.typography.titleMedium)
                }
                // Secondary: outlined pill.
                OutlinedButton(
                    onClick = {
                        val result = TestResult(
                            time = finishedAt,
                            downloadMbps = state.downloadMbps ?: 0.0,
                            uploadMbps = state.uploadMbps ?: 0.0,
                            pingMs = state.pingMs ?: 0.0,
                            jitterMs = state.jitterMs ?: 0.0,
                            isp = state.server?.isp.orEmpty(),
                            server = state.server?.serverLabel.orEmpty(),
                            network = state.network,
                            source = state.source,
                            downLoadedPingMs = state.downLoadedPingMs,
                            upLoadedPingMs = state.upLoadedPingMs,
                            bufferbloatGrade = state.bufferbloatGrade,
                        )
                        val link = ShareLink.build(result, state.downSamples, state.upSamples, state.connection)
                        ShareLink.share(context, shareText, link, shareTitle)
                    },
                    shape = CircleShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Icon(Icons.Rounded.Share, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(shareTitle, style = MaterialTheme.typography.titleMedium)
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
        shape = RoundedCornerShape(topStart = CornerLarge, topEnd = CornerSmall, bottomEnd = CornerLarge, bottomStart = CornerSmall),
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
    ElevatedCard(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(CornerLarge),
        colors = CardDefaults.elevatedCardColors(containerColor = scheme.surfaceContainerLow),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.graph_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Legend(scheme.primary, stringResource(R.string.phase_download))
                if (state.upSamples.isNotEmpty()) {
                    Spacer(Modifier.width(12.dp))
                    Legend(scheme.tertiary, stringResource(R.string.phase_upload))
                }
            }
            Spacer(Modifier.height(14.dp))
            val peak = max(
                state.downSamples.maxOrNull() ?: 0f,
                state.upSamples.maxOrNull() ?: 0f,
            ).coerceAtLeast(1f)
            // The plot sits in its own softly rounded inset well.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(scheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 14.dp),
            ) {
                SparkLine(state.downSamples, peak, scheme.primary, Modifier.fillMaxSize())
                SparkLine(state.upSamples, peak, scheme.tertiary, Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(10.dp))
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
        Box(Modifier.size(width = 14.dp, height = 8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun SparkLine(samples: List<Float>, peak: Float, color: Color, modifier: Modifier) {
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
private fun BufferbloatCard(state: TestUiState) {
    val grade = state.bufferbloatGrade ?: return
    val scheme = MaterialTheme.colorScheme
    val gradeColor = when (grade) {
        "A+", "A" -> Color(0xFF2E7D32)
        "B" -> Color(0xFF388E3C)
        "C" -> Color(0xFFF57C00)
        "D" -> Color(0xFFE65100)
        else -> Color(0xFFD32F2F)
    }
    Surface(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(CornerLarge),
        color = scheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(stringResource(R.string.bufferbloat), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.loaded_latency),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(gradeColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        grade,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = gradeColor,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "↓ ${stringResource(R.string.phase_download)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        state.downLoadedPingMs?.let { "${formatMs(it)} ms" } ?: "—",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "↑ ${stringResource(R.string.phase_upload)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        state.upLoadedPingMs?.let { "${formatMs(it)} ms" } ?: "—",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionCard(state: TestUiState) {
    val scheme = MaterialTheme.colorScheme
    val server = state.server
    Card(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(CornerLarge),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                stringResource(R.string.connection_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
            InfoRow(Icons.Rounded.Business, stringResource(R.string.provider), server?.isp?.ifBlank { null } ?: "—")
            InfoRow(Icons.Rounded.Dns, stringResource(R.string.server), server?.serverLabel?.ifBlank { null } ?: "—")
            InfoRow(Icons.Rounded.Language, stringResource(R.string.ip_address), server?.ip?.ifBlank { null } ?: "—")
            InfoRow(state.network.icon(), stringResource(R.string.network), state.network.label())
            state.connection?.wifiLinkSpeedMbps?.let {
                InfoRow(Icons.Rounded.Speed, stringResource(R.string.link_speed), "$it Mbps")
            }
            state.connection?.wifiFrequencyBand?.let {
                InfoRow(Icons.Rounded.Wifi, stringResource(R.string.wifi_band), it)
            }
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
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
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
    if (!last) {
        HorizontalDivider(
            Modifier.padding(start = 70.dp, end = 20.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun SourcePicker(source: TestSource, enabled: Boolean, onChange: (TestSource) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TestSource.entries.forEach { s ->
                val selected = s == source
                FilterChip(
                    selected = selected,
                    enabled = enabled,
                    onClick = { onChange(s) },
                    label = { Text(s.title) },
                    shape = CircleShape,
                    leadingIcon = if (selected) {
                        { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                    } else null,
                )
            }
        }
        Text(
            stringResource(source.description()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 2.dp),
        )
    }
}

fun TestSource.description(): Int = when (this) {
    TestSource.CLOUDFLARE -> R.string.src_cloudflare_desc
    TestSource.OOKLA -> R.string.src_ookla_desc
    TestSource.FAST -> R.string.src_fast_desc
    TestSource.LIBRESPEED -> R.string.src_libre_desc
    TestSource.APPLE -> R.string.src_apple_desc
    TestSource.MLAB -> R.string.src_mlab_desc
}

/** Shown in the server row for sources that always pick their own nearest server. */
fun TestSource.autoNote(): Int = when (this) {
    TestSource.APPLE -> R.string.server_apple_note
    TestSource.MLAB -> R.string.server_mlab_note
    else -> R.string.server_cloudflare_note
}
