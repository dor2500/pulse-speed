package com.dorp.pulse.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NetworkPing
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dorp.pulse.PingCount
import com.dorp.pulse.PingError
import com.dorp.pulse.PingUiState
import com.dorp.pulse.R
import com.dorp.pulse.ScanUiState
import com.dorp.pulse.engine.PingMethod
import com.dorp.pulse.engine.PingMode
import com.dorp.pulse.engine.PublicDns
import java.util.Locale

@Composable
fun PingScreen(
    state: PingUiState,
    onHostChange: (String) -> Unit,
    onModeChange: (PingMode) -> Unit,
    onCountChange: (PingCount) -> Unit,
    onStart: (String?) -> Unit,
    onStop: () -> Unit,
    onForgetRecent: (String) -> Unit,
    onRefreshLocal: () -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    LaunchedEffect(Unit) { onRefreshLocal() }
    val focus = LocalFocusManager.current
    val go: (String?) -> Unit = { host -> focus.clearFocus(); onStart(host) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 20.dp),
    ) {
        ToolHeader(stringResource(R.string.tool_ping), onBack)

        LiveCard(state, onStart = { go(null) }, onStop = onStop)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.host,
            onValueChange = onHostChange,
            label = { Text(stringResource(R.string.ping_target_hint)) },
            singleLine = true,
            enabled = !state.running,
            shape = RoundedCornerShape(20.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onGo = { go(null) }),
            modifier = Modifier.fillMaxWidth(),
        )

        val local = state.local
        if (local.gateway != null || local.dns.isNotEmpty()) {
            ChipGroup(stringResource(R.string.ping_group_local)) {
                local.gateway?.let { gw ->
                    TargetChip(stringResource(R.string.ping_router), gw, Icons.Rounded.Router, state) { go(gw) }
                }
                local.dns.forEach { dns ->
                    TargetChip(stringResource(R.string.ping_your_dns), dns, Icons.Rounded.Dns, state) { go(dns) }
                }
            }
        }
        ChipGroup(stringResource(R.string.ping_group_dns)) {
            PublicDns.forEach { t -> TargetChip(t.label, t.host, Icons.Rounded.Public, state) { go(t.host) } }
        }
        if (state.recents.isNotEmpty()) {
            ChipGroup(stringResource(R.string.ping_group_recent)) {
                state.recents.forEach { host ->
                    InputChip(
                        selected = host == state.host,
                        onClick = { go(host) },
                        enabled = !state.running,
                        label = { Text(host, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Rounded.History, null, Modifier.size(18.dp)) },
                        trailingIcon = {
                            Icon(
                                Icons.Rounded.Close, stringResource(R.string.ping_forget),
                                Modifier.size(18.dp).clip(CircleShape).clickable { onForgetRecent(host) },
                            )
                        },
                        shape = CircleShape,
                    )
                }
            }
        }

        SectionLabel(stringResource(R.string.ping_method))
        Segmented(
            options = listOf(
                PingMode.AUTO to stringResource(R.string.ping_mode_auto),
                PingMode.TCP to "TCP",
                PingMode.DNS to "DNS",
            ),
            selected = state.mode,
            enabled = !state.running,
            onSelect = onModeChange,
        )
        Text(
            stringResource(
                when (state.mode) {
                    PingMode.AUTO -> R.string.ping_mode_auto_desc
                    PingMode.TCP -> R.string.ping_mode_tcp_desc
                    PingMode.DNS -> R.string.ping_mode_dns_desc
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp),
        )
        SectionLabel(stringResource(R.string.ping_count))
        Segmented(
            options = listOf(
                PingCount.TEN to "10",
                PingCount.THIRTY to "30",
                PingCount.ENDLESS to "∞",
            ),
            selected = state.count,
            enabled = !state.running,
            onSelect = onCountChange,
        )

        Spacer(Modifier.height(16.dp))
        StatsCard(state)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LiveCard(state: PingUiState, onStart: () -> Unit, onStop: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val last = state.samples.lastOrNull()
    val headline = state.stats.avgMs
    val tint by animateColorAsState(latencyColor(headline), label = "tint")
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 12.dp, bottomEnd = 28.dp, bottomStart = 12.dp),
        color = scheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(scheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.NetworkPing, null, tint = scheme.onSecondaryContainer) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        state.host.ifBlank { "—" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        statusLine(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    headline?.let { formatMs(it) } ?: "—",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (headline != null) tint else scheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.unit_ms) + " " + stringResource(R.string.ping_avg).lowercase(Locale.getDefault()),
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                Spacer(Modifier.weight(1f))
                if (state.samples.isNotEmpty()) {
                    Text(
                        if (last != null) stringResource(R.string.ping_last, formatMs(last)) else stringResource(R.string.ping_timeout),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (last != null) latencyColor(last) else scheme.error,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LatencyChart(state.samples, Modifier.fillMaxWidth().height(96.dp))
            AnimatedVisibility(state.error != null) {
                Row(
                    Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(scheme.errorContainer)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = scheme.onErrorContainer)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(
                            when (state.error) {
                                PingError.OFFLINE -> R.string.ping_err_offline
                                PingError.RESOLVE_FAILED -> R.string.ping_err_resolve
                                else -> R.string.ping_err_method
                            }
                        ),
                        color = scheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            if (state.running) {
                Button(
                    onClick = onStop,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Rounded.Stop, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ping_stop))
                }
            } else {
                Button(
                    onClick = onStart,
                    enabled = state.host.isNotBlank(),
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ping_start))
                }
            }
        }
    }
}

@Composable
private fun statusLine(state: PingUiState): String {
    val method = state.method?.let {
        when (it) {
            PingMethod.ICMP -> "ICMP"
            PingMethod.SYSTEM -> "ICMP (system)"
            PingMethod.TCP -> "TCP"
            PingMethod.DNS -> "DNS"
        }
    }
    return when {
        state.running && state.address == null -> stringResource(R.string.ping_resolving)
        state.address != null && method != null -> {
            val ip = if (state.address != state.host) " · ${state.address}" else ""
            "$method$ip"
        }
        else -> stringResource(R.string.ping_idle)
    }
}

/** One rounded bar per probe, coloured by latency; a lost probe is a short red stub on the baseline. */
@Composable
private fun LatencyChart(samples: List<Double?>, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val good = latencyColor(10.0)
    val mid = latencyColor(60.0)
    val bad = latencyColor(200.0)
    val track = scheme.surfaceContainerHighest
    val lost = scheme.error
    Canvas(modifier.clip(RoundedCornerShape(12.dp)).background(scheme.surfaceContainer)) {
        val slots = 40
        val shown = samples.takeLast(slots)
        val gap = 3.dp.toPx()
        val barW = (size.width - gap * (slots + 1)) / slots
        val peak = (shown.filterNotNull().maxOrNull() ?: 1.0).coerceAtLeast(20.0)
        val r = CornerRadius(barW / 2, barW / 2)
        for (i in 0 until slots) {
            val x = gap + i * (barW + gap)
            val v = shown.getOrNull(i - (slots - shown.size))
            val inRange = i >= slots - shown.size
            if (!inRange) {
                drawRoundRect(track, Offset(x, size.height - barW - gap), Size(barW, barW), r)
                continue
            }
            if (v == null) {
                val h = size.height * 0.18f
                drawRoundRect(lost, Offset(x, size.height - h - gap), Size(barW, h), r)
            } else {
                val h = ((v / peak).toFloat() * (size.height - gap * 2)).coerceAtLeast(barW)
                val c = when { v < 30 -> good; v < 100 -> mid; else -> bad }
                drawRoundRect(c, Offset(x, size.height - h - gap), Size(barW, h), r)
            }
        }
    }
}

@Composable
private fun StatsCard(state: PingUiState) {
    val s = state.stats
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(24.dp), color = scheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 16.dp, horizontal = 8.dp)) {
            Row(Modifier.fillMaxWidth()) {
                StatCell(stringResource(R.string.ping_min), s.minMs?.let(::formatMs), stringResource(R.string.unit_ms))
                StatCell(stringResource(R.string.ping_avg), s.avgMs?.let(::formatMs), stringResource(R.string.unit_ms))
                StatCell(stringResource(R.string.ping_max), s.maxMs?.let(::formatMs), stringResource(R.string.unit_ms))
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                StatCell(stringResource(R.string.jitter), s.jitterMs?.let(::formatMs), stringResource(R.string.unit_ms))
                StatCell(
                    stringResource(R.string.ping_loss),
                    if (s.sent == 0) null else String.format(Locale.US, "%.0f", s.lossPercent), "%",
                    tint = if (s.sent > 0 && s.received < s.sent) scheme.error else null,
                )
                StatCell(stringResource(R.string.ping_replies), if (s.sent == 0) null else "${s.received}/${s.sent}", "")
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StatCell(label: String, value: String?, unit: String, tint: Color? = null) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value ?: "—",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = tint ?: MaterialTheme.colorScheme.onSurface,
            )
            if (value != null && unit.isNotEmpty()) {
                Spacer(Modifier.width(3.dp))
                Text(unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

@Composable
private fun ChipGroup(title: String, content: @Composable () -> Unit) {
    SectionLabel(title)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun TargetChip(label: String, host: String, icon: ImageVector, state: PingUiState, onClick: () -> Unit) {
    val selected = host == state.host
    val scheme = MaterialTheme.colorScheme
    AssistChip(
        onClick = onClick,
        enabled = !state.running,
        label = {
            Column(Modifier.padding(vertical = 6.dp)) {
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Text(host, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1)
            }
        },
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
        shape = RoundedCornerShape(16.dp),
        colors = if (selected) AssistChipDefaults.assistChipColors(
            containerColor = scheme.secondaryContainer,
            labelColor = scheme.onSecondaryContainer,
            leadingIconContentColor = scheme.onSecondaryContainer,
        ) else AssistChipDefaults.assistChipColors(),
        border = if (selected) null else AssistChipDefaults.assistChipBorder(enabled = !state.running),
    )
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
internal fun <T> Segmented(options: List<Pair<T, String>>, selected: T, enabled: Boolean, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                label = { Text(label, maxLines = 1) },
            )
        }
    }
}

/** Green-ish under 30 ms, amber under 100 ms, red above; tuned to read well on light, dark and OLED. */
@Composable
private fun latencyColor(ms: Double?): Color {
    val scheme = MaterialTheme.colorScheme
    return when {
        ms == null -> scheme.onSurfaceVariant
        ms < 30 -> scheme.primary
        ms < 100 -> scheme.tertiary
        else -> scheme.error
    }
}
