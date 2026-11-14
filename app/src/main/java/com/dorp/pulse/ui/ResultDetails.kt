package com.dorp.pulse.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dorp.pulse.R
import com.dorp.pulse.data.NetworkKind
import com.dorp.pulse.data.SpeedUnit
import com.dorp.pulse.data.TestResult
import com.dorp.pulse.engine.TestSource
import java.util.Locale
import kotlin.math.max

/** Everything about one saved test: speeds, data used, responsiveness per phase, loss and the connection. */
@Composable
fun ResultDetailsScreen(
    r: TestResult,
    unit: SpeedUnit,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val d = r.details
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Rounded.Delete, null) },
            title = { Text(stringResource(R.string.delete_result_title)) },
            text = { Text(stringResource(R.string.delete_result_body)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.t_back), tint = scheme.primary) }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(stringResource(R.string.details_title), style = MaterialTheme.typography.headlineSmall, color = scheme.primary)
                Text(
                    DateUtils.formatDateTime(context, r.time, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_ABBREV_MONTH) +
                        " · " + sourceName(r.source),
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onShare) { Icon(Icons.Rounded.Share, stringResource(R.string.share_link), tint = scheme.onSurfaceVariant) }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete), tint = scheme.onSurfaceVariant) }
        }

        // ---------------------------------------------------------------- speed
        SectionLabel(stringResource(R.string.details_speed))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SpeedTile(
                stringResource(R.string.phase_download), Icons.Rounded.ArrowDownward, r.downloadMbps, unit,
                d?.downBytes?.let { stringResource(R.string.details_data_used, formatBytes(it)) },
                scheme.primaryContainer, scheme.onPrimaryContainer,
                RoundedCornerShape(topStart = 28.dp, topEnd = 12.dp, bottomEnd = 28.dp, bottomStart = 12.dp),
            )
            SpeedTile(
                stringResource(R.string.phase_upload), Icons.Rounded.ArrowUpward, r.uploadMbps, unit,
                d?.upBytes?.let { stringResource(R.string.details_data_used, formatBytes(it)) },
                scheme.tertiaryContainer, scheme.onTertiaryContainer,
                RoundedCornerShape(topStart = 12.dp, topEnd = 28.dp, bottomEnd = 12.dp, bottomStart = 28.dp),
            )
        }
        if (d != null && (d.downSamples.size > 1 || d.upSamples.size > 1)) {
            Surface(shape = RoundedCornerShape(24.dp), color = scheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.graph_title), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(10.dp))
                    val peak = max(d.downSamples.maxOrNull() ?: 0f, d.upSamples.maxOrNull() ?: 0f).coerceAtLeast(1f)
                    Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(16.dp)).background(scheme.surfaceContainerHigh).padding(10.dp)) {
                        SparkLine(d.downSamples.map { unit.convert(it.toDouble()).toFloat() }, unit.convert(peak.toDouble()).toFloat(), scheme.primary, Modifier.fillMaxSize())
                        SparkLine(d.upSamples.map { unit.convert(it.toDouble()).toFloat() }, unit.convert(peak.toDouble()).toFloat(), scheme.tertiary, Modifier.fillMaxSize())
                    }
                }
            }
        }

        // ---------------------------------------------------------------- responsiveness
        SectionLabel(stringResource(R.string.details_responsiveness))
        Surface(shape = RoundedCornerShape(24.dp), color = scheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 16.dp, horizontal = 8.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    LatencyColumn(stringResource(R.string.details_idle), Icons.Rounded.Timer, scheme.secondary, r.pingMs, d?.idleMinMs, d?.idleMaxMs, r.jitterMs)
                    LatencyColumn(stringResource(R.string.phase_download), Icons.Rounded.ArrowDownward, scheme.primary, r.downLoadedPingMs, d?.downMinMs, d?.downMaxMs, d?.downJitterMs)
                    LatencyColumn(stringResource(R.string.phase_upload), Icons.Rounded.ArrowUpward, scheme.tertiary, r.upLoadedPingMs, d?.upMinMs, d?.upMaxMs, d?.upJitterMs)
                }
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    d?.lossPercent?.let { loss ->
                        BigStat(stringResource(R.string.ping_loss), String.format(Locale.US, "%.1f", loss), "%", if (loss > 0) scheme.error else scheme.onSurface)
                    }
                    r.bufferbloatGrade?.let { g -> BigStat(stringResource(R.string.bufferbloat), g, "", gradeColor(g)) }
                }
            }
        }

        // ---------------------------------------------------------------- connection
        SectionLabel(stringResource(R.string.connection_title))
        ToolCard(Modifier.padding(top = 0.dp)) {
            DetailRow(Icons.Rounded.SettingsEthernet, stringResource(R.string.details_connection_type), connectionText(r))
            if (r.isp.isNotBlank()) DetailRow(Icons.Rounded.Business, stringResource(R.string.provider), r.isp)
            DetailRow(Icons.Rounded.Dns, stringResource(R.string.server), r.server)
            d?.protocol?.let { DetailRow(Icons.Rounded.Language, stringResource(R.string.protocol), it) }
            if (d?.downStreams != null) DetailRow(
                Icons.Rounded.CallSplit, stringResource(R.string.details_connections),
                if (d.downStreams <= 1 && (d.upStreams ?: 1) <= 1) stringResource(R.string.details_streams_single)
                else stringResource(R.string.details_streams, d.downStreams, d.upStreams ?: 0),
            )
            d?.device?.let { DetailRow(Icons.Rounded.PhoneAndroid, stringResource(R.string.details_device), it + (d.android?.let { a -> " · Android $a" } ?: "")) }
            d?.durationSec?.let { DetailRow(Icons.Rounded.Timer, stringResource(R.string.details_duration), stringResource(R.string.details_seconds, it)) }
            d?.localIp?.let { DetailRow(Icons.Rounded.Home, stringResource(R.string.details_internal_ip), it) }
            d?.externalIp?.let { DetailRow(Icons.Rounded.Public, stringResource(R.string.details_external_ip), it) }
        }
        if (d == null) ToolNote(stringResource(R.string.details_old))
        else ToolNote(stringResource(R.string.details_ip_note))
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RowScope.SpeedTile(
    label: String, icon: ImageVector, mbps: Double, unit: SpeedUnit, sub: String?, bg: Color, fg: Color, shape: RoundedCornerShape,
) {
    Surface(shape = shape, color = bg, contentColor = fg, modifier = Modifier.weight(1f)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(6.dp))
            Text(formatSpeed(mbps, unit), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            Text(unit.label, style = MaterialTheme.typography.titleSmall, color = fg.copy(alpha = 0.75f))
            if (sub != null) {
                Spacer(Modifier.height(8.dp))
                Text(sub, style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = 0.75f))
            }
        }
    }
}

@Composable
private fun RowScope.LatencyColumn(label: String, icon: ImageVector, accent: Color, median: Double?, low: Double?, high: Double?, jitter: Double?) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(14.dp), tint = accent); Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        }
        Text(median?.let { formatMs(it) } ?: "—", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = accent)
        Text(stringResource(R.string.unit_ms), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        MiniStat(stringResource(R.string.details_low), low)
        MiniStat(stringResource(R.string.details_high), high)
        MiniStat(stringResource(R.string.jitter), jitter)
    }
}

@Composable
private fun MiniStat(label: String, v: Double?) {
    Row {
        Text(v?.let { formatMs(it) } ?: "—", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BigStat(label: String, value: String, unit: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = color)
            if (unit.isNotEmpty()) Text(" $unit", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun connectionText(r: TestResult): String {
    val d = r.details
    val base = r.network.label()
    val extra = when (r.network) {
        NetworkKind.CELLULAR -> d?.carrier
        NetworkKind.WIFI -> listOfNotNull(d?.wifiBand, d?.wifiLinkMbps?.let { "$it Mbps link" }).joinToString(" · ").ifBlank { null }
        else -> null
    }
    return if (extra != null) "$base · $extra" else base
}

@Composable
private fun gradeColor(g: String): Color {
    val s = MaterialTheme.colorScheme
    return when { g.startsWith("A") -> s.primary; g == "B" || g == "C" -> s.tertiary; else -> s.error }
}

private fun sourceName(s: TestSource) = when (s) {
    TestSource.CLOUDFLARE -> "Cloudflare"; TestSource.OOKLA -> "Ookla"; TestSource.FAST -> "FAST"; TestSource.LIBRESPEED -> "LibreSpeed"
    TestSource.APPLE -> "Apple"; TestSource.MLAB -> "M-Lab"
}

fun formatBytes(b: Long): String = when {
    b >= 1_000_000_000 -> String.format(Locale.US, "%.2f GB", b / 1e9)
    b >= 1_000_000 -> String.format(Locale.US, "%.0f MB", b / 1e6)
    else -> String.format(Locale.US, "%.0f KB", b / 1e3)
}
