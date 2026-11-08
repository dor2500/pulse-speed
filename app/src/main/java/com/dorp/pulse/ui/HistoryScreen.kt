package com.dorp.pulse.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dorp.pulse.R
import com.dorp.pulse.data.SpeedUnit
import com.dorp.pulse.data.TestResult

@Composable
fun HistoryScreen(
    items: List<TestResult>,
    unit: SpeedUnit,
    onDelete: (Long) -> Unit,
    onClear: () -> Unit,
    contentPadding: PaddingValues,
) {
    var confirmClear by remember { mutableStateOf(false) }
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

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.tab_history),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (items.isNotEmpty()) {
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Rounded.DeleteSweep, stringResource(R.string.clear_history))
                    }
                }
            }
        }
        if (items.isEmpty()) {
            item { EmptyHistory() }
        } else {
            item { SummaryCard(items, unit) }
            items(items, key = { it.time }) { r ->
                val state = rememberSwipeToDismissBoxState(confirmValueChange = {
                    if (it != SwipeToDismissBoxValue.Settled) {
                        onDelete(r.time)
                        true
                    } else false
                })
                SwipeToDismissBox(
                    state = state,
                    modifier = Modifier.animateItem(),
                    backgroundContent = {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(MaterialTheme.shapes.large)
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(horizontal = 24.dp),
                            contentAlignment = if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd)
                                Alignment.CenterStart else Alignment.CenterEnd,
                        ) {
                            Icon(Icons.Rounded.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    },
                ) {
                    HistoryItem(r, unit)
                }
            }
        }
    }
}

@Composable
private fun EmptyHistory() {
    Column(
        Modifier.fillMaxWidth().padding(top = 96.dp),
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
        Text(stringResource(R.string.history_empty_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.history_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SummaryCard(items: List<TestResult>, unit: SpeedUnit) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.extraLarge, color = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(pluralStringResource(R.plurals.tests_count, items.size, items.size), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat(stringResource(R.string.history_avg), formatSpeed(items.map { it.downloadMbps }.average(), unit), unit.label, Modifier.weight(1f))
                Stat(stringResource(R.string.history_best), formatSpeed(items.maxOf { it.downloadMbps }, unit), unit.label, Modifier.weight(1f))
                Stat(stringResource(R.string.phase_ping), formatMs(items.map { it.pingMs }.average()), stringResource(R.string.unit_ms), Modifier.weight(1f))
            }
        }
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

@Composable
private fun HistoryItem(r: TestResult, unit: SpeedUnit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.large, color = scheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(scheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(r.network.icon(), null, Modifier.size(16.dp), tint = scheme.onSecondaryContainer)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        DateUtils.formatDateTime(
                            context, r.time,
                            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    val sub = listOf(r.isp, r.server).filter { it.isNotBlank() }.joinToString(" · ")
                    if (sub.isNotEmpty()) {
                        Text(
                            sub, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row {
                MiniStat(Icons.Rounded.ArrowDownward, formatSpeed(r.downloadMbps, unit), unit.label, scheme.primary, Modifier.weight(1f))
                MiniStat(Icons.Rounded.ArrowUpward, formatSpeed(r.uploadMbps, unit), unit.label, scheme.tertiary, Modifier.weight(1f))
                MiniStat(Icons.Rounded.Timer, formatMs(r.pingMs), stringResource(R.string.unit_ms), scheme.secondary, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MiniStat(icon: ImageVector, value: String, unit: String, tint: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = tint)
        Spacer(Modifier.width(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(3.dp))
        Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
