package com.dorp.pulse.ui

import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dorp.pulse.R
import com.dorp.pulse.data.NetworkKind
import com.dorp.pulse.data.SpeedUnit
import com.dorp.pulse.data.TestResult

private enum class HistoryFilter { ALL, WIFI, CELLULAR }

@Composable
fun HistoryScreen(
    items: List<TestResult>,
    unit: SpeedUnit,
    onDelete: (Long) -> Unit,
    onClear: () -> Unit,
    onExportCsv: () -> Unit,
    onOpen: (Long) -> Unit,
    contentPadding: PaddingValues,
) {
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Long?>(null) }
    var filter by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }
    val shown = when (filter) {
        HistoryFilter.ALL -> items
        HistoryFilter.WIFI -> items.filter { it.network == NetworkKind.WIFI }
        HistoryFilter.CELLULAR -> items.filter { it.network == NetworkKind.CELLULAR }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(Icons.Rounded.DeleteSweep, null) },
            title = { Text(stringResource(R.string.clear_history)) },
            text = { Text(stringResource(R.string.clear_history_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClear() }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    confirmDelete?.let { time ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            icon = { Icon(Icons.Rounded.Delete, null) },
            title = { Text(stringResource(R.string.delete_result_title)) },
            text = { Text(stringResource(R.string.delete_result_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = null; onDelete(time) }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.tab_history),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (items.isNotEmpty()) {
                    IconButton(onClick = onExportCsv) {
                        Icon(Icons.Rounded.Share, stringResource(R.string.export_csv))
                    }
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Rounded.DeleteSweep, stringResource(R.string.clear_history))
                    }
                }
            }
        }
        if (items.isEmpty()) {
            item { EmptyHistory(R.string.history_empty_title, R.string.history_empty_body) }
            return@LazyColumn
        }
        item { FilterRow(filter) { filter = it } }
        if (shown.isEmpty()) {
            item { EmptyHistory(R.string.history_empty_filter_title, R.string.history_empty_filter_body) }
            return@LazyColumn
        }
        item { SummaryCard(shown, unit) }
        // No swipe-to-delete: deleting needs the explicit button (and a confirmation) on each card.
        items(shown, key = { it.time }) { r ->
            Box(Modifier.animateItem()) {
                HistoryItem(r, unit, onOpen = { onOpen(r.time) }, onDelete = { confirmDelete = r.time })
            }
        }
    }
}

@Composable
private fun FilterRow(filter: HistoryFilter, onSelect: (HistoryFilter) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 4.dp)) {
        listOf(
            Triple(HistoryFilter.ALL, stringResource(R.string.filter_all), null),
            Triple(HistoryFilter.WIFI, stringResource(R.string.net_wifi), Icons.Rounded.Wifi),
            Triple(HistoryFilter.CELLULAR, stringResource(R.string.net_cellular), Icons.Rounded.SignalCellularAlt),
        ).forEach { (value, label, icon) ->
            val selected = filter == value
            FilterChip(
                selected = selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
                shape = CircleShape,
                leadingIcon = when {
                    selected -> {
                        { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                    }
                    icon != null -> {
                        { Icon(icon, null, Modifier.size(FilterChipDefaults.IconSize)) }
                    }
                    else -> null
                },
            )
        }
    }
}

@Composable
private fun EmptyHistory(title: Int, body: Int) {
    Column(
        Modifier.fillMaxWidth().padding(top = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(112.dp).clip(ScallopShape(lobes = 8, depth = 0.06f))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.History, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SummaryCard(items: List<TestResult>, unit: SpeedUnit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 12.dp, bottomEnd = 28.dp, bottomStart = 12.dp),
        color = scheme.secondaryContainer,
        contentColor = scheme.onSecondaryContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pluralStringResource(R.plurals.tests_count, items.size, items.size),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Legend(scheme.primary, stringResource(R.string.phase_download))
            }
            Spacer(Modifier.height(12.dp))
            // Download trend, oldest on the left.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(scheme.surface.copy(alpha = 0.45f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                MiniSparkline(
                    values = items.take(30).reversed().map { unit.convert(it.downloadMbps).toFloat() },
                    color = scheme.primary,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat(stringResource(R.string.history_avg), formatSpeed(items.map { it.downloadMbps }.average(), unit), unit.label, Modifier.weight(1f))
                Stat(stringResource(R.string.history_best), formatSpeed(items.maxOf { it.downloadMbps }, unit), unit.label, Modifier.weight(1f))
                Stat(stringResource(R.string.phase_ping), formatMs(items.map { it.pingMs }.average()), stringResource(R.string.unit_ms), Modifier.weight(1f))
            }
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
private fun MiniSparkline(values: List<Float>, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val peak = values.max().coerceAtLeast(0.01f)
        val low = values.min()
        val span = (peak - low).coerceAtLeast(peak * 0.2f)
        fun pt(i: Int): Offset {
            val x = if (values.size == 1) size.width / 2 else i * size.width / (values.size - 1)
            val y = size.height - ((values[i] - low) / span) * size.height * 0.85f - size.height * 0.075f
            return Offset(x, y)
        }
        if (values.size == 1) {
            drawCircle(color, 4.dp.toPx(), pt(0))
            return@Canvas
        }
        val line = Path().apply {
            moveTo(pt(0).x, pt(0).y)
            for (i in 1 until values.size) {
                val a = pt(i - 1)
                val b = pt(i)
                val mid = (a.x + b.x) / 2
                cubicTo(mid, a.y, mid, b.y, b.x, b.y)
            }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.3f), color.copy(alpha = 0f))))
        drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, 3.5.dp.toPx(), pt(values.size - 1))
    }
}

@Composable
private fun Stat(label: String, value: String, unit: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(unit, style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryItem(r: TestResult, unit: SpeedUnit, onOpen: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(24.dp), color = scheme.surfaceContainer, onClick = onOpen) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(r.network.icon(), null, Modifier.size(16.dp), tint = scheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(
                    DateUtils.formatDateTime(
                        context, r.time,
                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                val shareTitle = stringResource(R.string.share_link)
                val summary = stringResource(
                    R.string.share_text,
                    "${formatSpeed(r.downloadMbps, unit)} ${unit.label}",
                    "${formatSpeed(r.uploadMbps, unit)} ${unit.label}",
                    formatMs(r.pingMs),
                    formatMs(r.jitterMs),
                )
                androidx.compose.material3.IconButton(
                    onClick = { com.dorp.pulse.data.ShareLink.share(context, summary, com.dorp.pulse.data.ShareLink.build(r), shareTitle) },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Rounded.Share, shareTitle, Modifier.size(20.dp), tint = scheme.onSurfaceVariant)
                }
                androidx.compose.material3.IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Delete, stringResource(R.string.delete), Modifier.size(20.dp), tint = scheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(6.dp))
            // Download is the hero number.
            Row(verticalAlignment = Alignment.Bottom) {
                Icon(Icons.Rounded.ArrowDownward, null, Modifier.size(28.dp).padding(bottom = 6.dp), tint = scheme.primary)
                Spacer(Modifier.width(4.dp))
                Text(
                    formatSpeed(r.downloadMbps, unit),
                    style = MaterialTheme.typography.displaySmall,
                    color = scheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    unit.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DataLabel(Icons.Rounded.ArrowUpward, "${formatSpeed(r.uploadMbps, unit)} ${unit.label}", scheme.tertiaryContainer, scheme.onTertiaryContainer)
                DataLabel(Icons.Rounded.Timer, "${formatMs(r.pingMs)} ${stringResource(R.string.unit_ms)}", scheme.secondaryContainer, scheme.onSecondaryContainer)
                r.bufferbloatGrade?.let { bbg ->
                    DataLabel(Icons.Rounded.Speed, "${stringResource(R.string.bufferbloat)} $bbg", scheme.primaryContainer, scheme.onPrimaryContainer)
                }
                val place = r.server.ifBlank { r.isp }
                if (place.isNotBlank()) {
                    DataLabel(Icons.Rounded.Place, place, scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** A compact tonal label (icon + text), used for the secondary metrics on a history card. */
@Composable
private fun DataLabel(icon: ImageVector, text: String, container: Color, content: Color) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(14.dp), tint = content)
        Spacer(Modifier.width(6.dp))
        // Isolate as LTR so "100 ms" doesn't flip to "ms 100" in Hebrew.
        Text(
            "\u2066$text\u2069",
            style = MaterialTheme.typography.labelMedium,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
