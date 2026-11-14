package com.dorp.pulse.ui

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dorp.pulse.MainViewModel
import com.dorp.pulse.R
import com.dorp.pulse.ServiceState
import com.dorp.pulse.StatusLevel
import com.dorp.pulse.StatusUiState
import com.dorp.pulse.engine.OfficialLevel
import com.dorp.pulse.engine.Service
import com.dorp.pulse.engine.ServiceGroup
import com.dorp.pulse.engine.Services

/** Downdetector-style board: live reachability from this phone, official status pages and Pulse users' reports. */
@Composable
fun StatusScreen(s: StatusUiState, vm: MainViewModel, onBack: () -> Unit, pad: PaddingValues) {
    LaunchedEffect(Unit) { if (s.checkedAt == null || System.currentTimeMillis() - s.checkedAt > 120_000) vm.status.refresh() }
    val openService = s.open?.let { Services.byId(it) }
    BackHandler(enabled = openService != null) { vm.status.open(null) }
    if (openService != null) { ServiceDetail(openService, s, vm, pad); return }

    ToolScreen(stringResource(R.string.t_status), onBack, pad) {
        ToolField(s.query, vm.status::setQuery, stringResource(R.string.st_search), keyboard = KeyboardType.Text)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    s.refreshing -> stringResource(R.string.st_checking)
                    s.checkedAt != null -> stringResource(R.string.st_checked, DateUtils.getRelativeTimeSpanString(s.checkedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString())
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
            )
            if (s.refreshing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else TextButton(onClick = vm.status::refresh) { Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.t_refresh)) }
        }
        val q = s.query.trim().lowercase()
        val list = Services.ALL.filter { q.isEmpty() || q in it.name.lowercase() || q in it.id }
        // Problems first, so an outage is the first thing you see.
        val problems = list.filter { (s.services[it.id]?.level ?: StatusLevel.CHECKING) in setOf(StatusLevel.DOWN, StatusLevel.ISSUES) }
        if (problems.isNotEmpty()) {
            SectionLabel(stringResource(R.string.st_problems))
            ToolCard { problems.forEach { ServiceRow(it, s.services[it.id]) { vm.status.open(it.id) } } }
        }
        ServiceGroup.entries.forEach { g ->
            val items = list.filter { it.group == g && it !in problems }
            if (items.isNotEmpty()) {
                SectionLabel(stringResource(groupName(g)))
                ToolCard { items.forEach { ServiceRow(it, s.services[it.id]) { vm.status.open(it.id) } } }
            }
        }
        ToolNote(stringResource(if (s.reportsAvailable) R.string.st_note else R.string.st_note_no_reports))
    }
}

private fun groupName(g: ServiceGroup) = when (g) {
    ServiceGroup.ISRAEL -> R.string.st_g_israel; ServiceGroup.SOCIAL -> R.string.st_g_social
    ServiceGroup.STREAMING -> R.string.st_g_streaming; ServiceGroup.WORK -> R.string.st_g_work
}

@Composable
private fun ServiceRow(svc: Service, st: ServiceState?, onClick: () -> Unit) {
    val level = st?.level ?: StatusLevel.CHECKING
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(svc.name, levelColor(level))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(svc.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subline(st), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LevelPill(level)
    }
}

@Composable
private fun subline(st: ServiceState?): String {
    val live = st?.live ?: return stringResource(R.string.st_checking_one)
    val parts = ArrayList<String>()
    parts += if (live.reachable) stringResource(R.string.st_reachable, live.ms?.let { formatMs(it) } ?: "—") else stringResource(R.string.st_unreachable)
    st.official?.takeIf { it.level != OfficialLevel.OK }?.let { parts += it.description }
    st.reports?.takeIf { it > 0 }?.let { parts += reportsText(it.toInt()) }
    return parts.joinToString(" · ")
}

@Composable
private fun Avatar(name: String, ring: Color) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.size(40.dp).clip(CircleShape).background(scheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        Box(Modifier.align(Alignment.BottomEnd).size(12.dp).clip(CircleShape).background(scheme.surfaceContainerLow).padding(2.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(ring))
        }
    }
}

@Composable
private fun levelColor(l: StatusLevel): Color {
    val s = MaterialTheme.colorScheme
    return when (l) { StatusLevel.OK -> s.primary; StatusLevel.ISSUES -> Color(0xFFE6A23C); StatusLevel.DOWN -> s.error; StatusLevel.CHECKING -> s.outline }
}

@Composable
private fun LevelPill(l: StatusLevel) {
    val c = levelColor(l)
    val (icon, text) = when (l) {
        StatusLevel.OK -> Icons.Rounded.CheckCircle to R.string.st_ok
        StatusLevel.ISSUES -> Icons.Rounded.Warning to R.string.st_issues
        StatusLevel.DOWN -> Icons.Rounded.Error to R.string.st_down
        StatusLevel.CHECKING -> null to R.string.st_checking_short
    }
    Row(Modifier.clip(CircleShape).background(c.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, Modifier.size(14.dp), tint = c); Spacer(Modifier.width(4.dp)) }
        Text(stringResource(text), style = MaterialTheme.typography.labelMedium, color = c)
    }
}

@Composable
private fun reportsText(n: Int) = if (n == 1) stringResource(R.string.st_reports_hour_one) else stringResource(R.string.st_reports_hour, n)

// ---------------------------------------------------------------- one service

private val KINDS = listOf("app" to R.string.st_k_app, "site" to R.string.st_k_site, "login" to R.string.st_k_login, "connection" to R.string.st_k_connection, "other" to R.string.st_k_other)

@Composable
private fun ServiceDetail(svc: Service, s: StatusUiState, vm: MainViewModel, pad: PaddingValues) {
    val context = LocalContext.current
    val st = s.services[svc.id]
    val level = st?.level ?: StatusLevel.CHECKING
    var asking by remember { mutableStateOf(false) }
    var kind by remember { mutableStateOf("app") }

    if (asking) AlertDialog(
        onDismissRequest = { asking = false },
        icon = { Icon(Icons.Rounded.Flag, null) },
        title = { Text(stringResource(R.string.st_report_title, svc.name)) },
        text = {
            Column {
                KINDS.forEach { (k, label) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).selectable(selected = kind == k, role = Role.RadioButton) { kind = k }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { RadioButton(selected = kind == k, onClick = null); Spacer(Modifier.width(8.dp)); Text(stringResource(label)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { asking = false; vm.status.report(svc.id, kind) }) { Text(stringResource(R.string.st_report_send)) } },
        dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(R.string.cancel)) } },
    )

    ToolScreen(svc.name, { vm.status.open(null) }, pad) {
        val c = levelColor(level)
        Surface(shape = RoundedCornerShape(topStart = 28.dp, topEnd = 12.dp, bottomEnd = 28.dp, bottomStart = 12.dp), color = c.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                LevelPill(level)
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(when (level) {
                        StatusLevel.OK -> R.string.st_headline_ok; StatusLevel.ISSUES -> R.string.st_headline_issues
                        StatusLevel.DOWN -> R.string.st_headline_down; StatusLevel.CHECKING -> R.string.st_checking_one
                    }, svc.name),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }

        SectionLabel(stringResource(R.string.st_from_here))
        ToolCard {
            val live = st?.live
            InfoRow(stringResource(R.string.st_reach), when {
                live == null -> "…"
                live.reachable -> stringResource(R.string.st_reachable, live.ms?.let { formatMs(it) } ?: "—")
                else -> stringResource(R.string.st_unreachable)
            })
            live?.httpCode?.let { InfoRow("HTTP", it.toString()) }
        }

        if (svc.statusApi != null) {
            SectionLabel(stringResource(R.string.st_official))
            ToolCard {
                InfoRow(stringResource(R.string.st_official_now), st?.official?.description ?: "…")
                svc.statusPage?.let { url ->
                    Row(
                        Modifier.fillMaxWidth().clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(url.removePrefix("https://").removePrefix("http://"), color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.OpenInNew, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        if (s.reportsAvailable) {
            SectionLabel(stringResource(R.string.st_user_reports))
            ToolCard {
                Column(Modifier.padding(16.dp)) {
                    Text(reportsText((st?.reports ?: 0).toInt()), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.st_last_24h), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    ReportChart(s.history, Modifier.fillMaxWidth().height(110.dp))
                }
            }
            RunButton(
                running = false,
                startLabel = stringResource(if (svc.id in s.reported) R.string.st_reported else R.string.st_report_btn, svc.name),
                onStart = { asking = true }, onStop = {},
                enabled = !s.reporting && svc.id !in s.reported,
                icon = Icons.Rounded.Flag,
            )
            if (s.reportFailed) ToolError(stringResource(R.string.st_report_failed))
        }
        ToolNote(stringResource(R.string.st_detail_note))
    }
}

/** 24 hourly bars, newest on the right, with the hour labels at both ends. */
@Composable
private fun ReportChart(times: List<Long>?, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    if (times == null) {
        Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
        return
    }
    val now = System.currentTimeMillis()
    val buckets = IntArray(24)
    times.forEach { t -> val ago = ((now - t) / 3_600_000).toInt(); if (ago in 0..23) buckets[23 - ago]++ }
    val peak = (buckets.maxOrNull() ?: 0).coerceAtLeast(3)
    val bar = scheme.error
    val track = scheme.surfaceContainerHighest
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val gap = 3.dp.toPx()
            val w = (size.width - gap * 23) / 24
            for (i in 0 until 24) {
                val x = i * (w + gap)
                drawRoundRect(track, Offset(x, size.height - 3.dp.toPx()), Size(w, 3.dp.toPx()), CornerRadius(w / 2))
                if (buckets[i] > 0) {
                    val h = (buckets[i].toFloat() / peak) * size.height
                    drawRoundRect(bar, Offset(x, size.height - h), Size(w, h), CornerRadius(w / 3))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(stringResource(R.string.st_24h_ago), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.st_now), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
    }
}
