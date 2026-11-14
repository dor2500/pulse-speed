package com.dorp.pulse.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.NetworkPing
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiFind
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dorp.pulse.DnsBenchState
import com.dorp.pulse.DnsLookupState
import com.dorp.pulse.MainViewModel
import com.dorp.pulse.NetInfoState
import com.dorp.pulse.PortPreset
import com.dorp.pulse.PortScanState
import com.dorp.pulse.R
import com.dorp.pulse.TraceState
import com.dorp.pulse.WhoisState
import com.dorp.pulse.WifiState
import com.dorp.pulse.WolState
import com.dorp.pulse.engine.DnsType
import com.dorp.pulse.engine.PortScanner
import java.util.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue

enum class ToolId(val icon: ImageVector, val title: Int, val desc: Int) {
    STATUS(Icons.Rounded.MonitorHeart, R.string.t_status, R.string.td_status),
    PING(Icons.Rounded.NetworkPing, R.string.tool_ping, R.string.td_ping),
    SCAN(Icons.Rounded.Radar, R.string.tool_devices, R.string.td_scan),
    TRACE(Icons.Rounded.Route, R.string.t_trace, R.string.td_trace),
    DNS(Icons.Rounded.Dns, R.string.t_dns, R.string.td_dns),
    BENCH(Icons.Rounded.Leaderboard, R.string.t_bench, R.string.td_bench),
    PORTS(Icons.Rounded.Lan, R.string.t_ports, R.string.td_ports),
    INFO(Icons.Rounded.Info, R.string.t_info, R.string.td_info),
    WIFI(Icons.Rounded.WifiFind, R.string.t_wifi, R.string.td_wifi),
    WOL(Icons.Rounded.PowerSettingsNew, R.string.t_wol, R.string.td_wol),
    WHOIS(Icons.Rounded.PersonSearch, R.string.t_whois, R.string.td_whois),
}

@Composable
fun ToolsHub(onOpen: (ToolId) -> Unit, contentPadding: PaddingValues) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 16.dp),
    ) {
        Text(
            stringResource(R.string.tab_tools),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 12.dp),
        )
        ToolId.entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t -> ToolTile(t, Modifier.weight(1f)) { onOpen(t) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ToolTile(t: ToolId, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val i = t.ordinal
    // Alternate the asymmetric corners like the metric cards on the Test screen.
    val shape = if (i % 2 == 0) RoundedCornerShape(topStart = 28.dp, topEnd = 12.dp, bottomEnd = 28.dp, bottomStart = 12.dp)
    else RoundedCornerShape(topStart = 12.dp, topEnd = 28.dp, bottomEnd = 12.dp, bottomStart = 28.dp)
    Surface(shape = shape, color = scheme.surfaceContainerLow, modifier = modifier.heightIn(min = 132.dp).clip(shape).clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(if (i == 0) scheme.primaryContainer else scheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(t.icon, null, tint = if (i == 0) scheme.onPrimaryContainer else scheme.onSecondaryContainer) }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(t.title), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(t.desc), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Every tool screen except Ping, which keeps its own richer layout. */
@Composable
fun ToolDetail(tool: ToolId, vm: MainViewModel, onBack: () -> Unit, onPing: (String) -> Unit, contentPadding: PaddingValues) {
    when (tool) {
        ToolId.SCAN -> {
            val s by vm.scan.state.collectAsStateWithLifecycle()
            ToolScreen(stringResource(R.string.tool_devices), onBack, contentPadding) {
                ScanContent(s, vm.scan::start, vm.scan::stop, vm.scan::refresh, onPingDevice = onPing)
            }
        }
        ToolId.TRACE -> { val s by vm.trace.state.collectAsStateWithLifecycle(); TraceScreen(s, vm, onBack, contentPadding) }
        ToolId.DNS -> { val s by vm.dns.state.collectAsStateWithLifecycle(); DnsScreen(s, vm, onBack, contentPadding) }
        ToolId.BENCH -> { val s by vm.bench.state.collectAsStateWithLifecycle(); BenchScreen(s, vm, onBack, contentPadding) }
        ToolId.PORTS -> { val s by vm.ports.state.collectAsStateWithLifecycle(); PortsScreen(s, vm, onBack, contentPadding) }
        ToolId.INFO -> { val s by vm.netInfo.state.collectAsStateWithLifecycle(); InfoScreen(s, vm, onBack, onPing, contentPadding) }
        ToolId.WIFI -> { val s by vm.wifi.state.collectAsStateWithLifecycle(); WifiScreen(s, vm, onBack, contentPadding) }
        ToolId.WOL -> { val s by vm.wol.state.collectAsStateWithLifecycle(); WolScreen(s, vm, onBack, contentPadding) }
        ToolId.WHOIS -> { val s by vm.whois.state.collectAsStateWithLifecycle(); WhoisScreen(s, vm, onBack, contentPadding) }
        ToolId.STATUS -> { val s by vm.status.state.collectAsStateWithLifecycle(); StatusScreen(s, vm, onBack, contentPadding) }
        ToolId.PING -> Unit
    }
}

// ---------------------------------------------------------------- Traceroute

@Composable
private fun TraceScreen(s: TraceState, vm: MainViewModel, onBack: () -> Unit, pad: PaddingValues) =
    ToolScreen(stringResource(R.string.t_trace), onBack, pad) {
        ToolField(s.host, vm.trace::setHost, stringResource(R.string.ping_target_hint), enabled = !s.running, onGo = vm.trace::start)
        RunButton(s.running, stringResource(R.string.t_trace_start), vm.trace::start, vm.trace::stop, enabled = s.host.isNotBlank(), icon = Icons.Rounded.Route)
        if (s.error) ToolError(stringResource(R.string.ping_err_resolve))
        if (s.hops.isNotEmpty()) ToolCard {
            s.hops.forEach { h ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(if (h.reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${h.ttl}", style = MaterialTheme.typography.labelMedium, color = if (h.reached) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(h.ip ?: "* * *", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                        h.name?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    Pill(h.rttMs?.let { formatMs(it) + " ms" } ?: "—", strong = h.reached)
                }
            }
        }
        ToolNote(stringResource(R.string.t_trace_note))
    }

// ---------------------------------------------------------------- DNS lookup

@Composable
private fun DnsScreen(s: DnsLookupState, vm: MainViewModel, onBack: () -> Unit, pad: PaddingValues) =
    ToolScreen(stringResource(R.string.t_dns), onBack, pad) {
        ToolField(s.name, vm.dns::setName, stringResource(R.string.t_dns_name), onGo = vm.dns::lookup)
        SectionLabel(stringResource(R.string.t_dns_type))
        ChipRow(DnsType.entries.map { it to it.name }, s.type, vm.dns::setType)
        SectionLabel(stringResource(R.string.t_dns_server))
        ChipRow(
            listOf("system" to stringResource(R.string.ping_your_dns), "1.1.1.1" to "Cloudflare", "8.8.8.8" to "Google", "9.9.9.9" to "Quad9", "custom" to stringResource(R.string.t_custom)),
            s.server, vm.dns::setServer,
        )
        if (s.server == "custom") ToolField(s.customServer, vm.dns::setCustom, stringResource(R.string.t_dns_server_ip), keyboard = KeyboardType.Uri, onGo = vm.dns::lookup)
        RunButton(s.loading, stringResource(R.string.t_dns_go), vm.dns::lookup, vm.dns::stop, enabled = s.name.isNotBlank(), icon = Icons.Rounded.Search)
        s.error?.let { ToolError(stringResource(R.string.t_dns_failed, it)) }
        s.answer?.let { a ->
            ToolCard {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = ToolRowArrangement) {
                    Pill(a.status, strong = a.status == "NOERROR"); Pill("${formatMs(a.ms)} ms"); Pill(a.server); if (a.viaTcp) Pill("TCP")
                }
                if (a.records.isEmpty()) InfoRow(stringResource(R.string.t_dns_none), "")
                a.records.forEach { r -> InfoRow("${r.type} · TTL ${r.ttl}", r.value, mono = true) }
            }
        }
    }

// ---------------------------------------------------------------- DNS benchmark

@Composable
private fun BenchScreen(s: DnsBenchState, vm: MainViewModel, onBack: () -> Unit, pad: PaddingValues) =
    ToolScreen(stringResource(R.string.t_bench), onBack, pad) {
        Text(stringResource(R.string.t_bench_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        RunButton(s.running, stringResource(R.string.t_bench_go), vm.bench::start, vm.bench::stop, icon = Icons.Rounded.Leaderboard)
        if (s.running) ToolProgress(s.done, s.total)
        if (s.scores.isNotEmpty()) ToolCard {
            val best = s.scores.firstOrNull { it.medianMs != null }?.medianMs ?: 1.0
            val worst = s.scores.mapNotNull { it.medianMs }.maxOrNull() ?: 1.0
            s.scores.forEachIndexed { i, sc ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(28.dp))
                        Column(Modifier.weight(1f)) {
                            Text(sc.resolver.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(sc.resolver.ip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Pill(sc.medianMs?.let { formatMs(it) + " ms" } ?: stringResource(R.string.ping_timeout), strong = i == 0 && sc.medianMs != null)
                    }
                    sc.medianMs?.let { ms ->
                        val frac = (ms / worst).toFloat().coerceIn(0.04f, 1f)
                        val color = if (ms <= best * 1.3) MaterialTheme.colorScheme.primary else if (ms < 100) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                        Box(Modifier.padding(start = 28.dp, top = 6.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                            Box(Modifier.fillMaxWidth(frac).height(6.dp).clip(CircleShape).background(color))
                        }
                        if (sc.okPercent < 100) Text(stringResource(R.string.t_bench_ok, sc.okPercent), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 28.dp, top = 2.dp))
                    }
                }
            }
        }
        ToolNote(stringResource(R.string.t_bench_note))
    }

// ---------------------------------------------------------------- Port scanner

@Composable
private fun PortsScreen(s: PortScanState, vm: MainViewModel, onBack: () -> Unit, pad: PaddingValues) =
    ToolScreen(stringResource(R.string.t_ports), onBack, pad) {
        ToolField(s.host, vm.ports::setHost, stringResource(R.string.ping_target_hint), enabled = !s.running, onGo = vm.ports::start)
        SectionLabel(stringResource(R.string.t_ports_which))
        Segmented(
            listOf(PortPreset.COMMON to stringResource(R.string.t_ports_common), PortPreset.WELL_KNOWN to "1–1024", PortPreset.CUSTOM to stringResource(R.string.t_custom)),
            s.preset, !s.running, vm.ports::setPreset,
        )
        if (s.preset == PortPreset.CUSTOM) ToolField(s.custom, vm.ports::setCustom, stringResource(R.string.t_ports_custom_hint), enabled = !s.running, keyboard = KeyboardType.Uri)
        RunButton(s.running, stringResource(R.string.t_ports_go), vm.ports::start, vm.ports::stop, enabled = s.host.isNotBlank(), icon = Icons.Rounded.Lan)
        if (s.running) { ToolProgress(s.done, s.total); ToolNote(stringResource(R.string.scan_progress, s.done, s.total, s.open.size)) }
        if (s.error) ToolError(stringResource(R.string.t_ports_error))
        if (s.open.isNotEmpty()) ToolCard {
            s.open.forEach { p ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill("${p.port}", strong = true); Spacer(Modifier.width(12.dp))
                    Text(p.service.ifBlank { stringResource(R.string.t_ports_unknown) }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("${formatMs(p.ms)} ms", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (s.finished && !s.running) ToolNote(stringResource(R.string.t_ports_none))
        ToolNote(stringResource(R.string.t_ports_note))
    }

// ---------------------------------------------------------------- Network info

@Composable
private fun InfoScreen(s: NetInfoState, vm: MainViewModel, onBack: () -> Unit, onPing: (String) -> Unit, pad: PaddingValues) {
    LaunchedEffect(Unit) { vm.netInfo.refresh() }
    ToolScreen(stringResource(R.string.t_info), onBack, pad) {
        val i = s.info
        val p = s.public
        SectionLabel(stringResource(R.string.t_info_public))
        ToolCard {
            InfoRow("IPv4", p?.ipv4 ?: if (s.loadingPublic) "…" else null, mono = true)
            InfoRow("IPv6", p?.ipv6 ?: if (s.loadingPublic) "…" else stringResource(R.string.t_info_no_v6), mono = true)
            InfoRow(stringResource(R.string.provider), p?.isp)
            InfoRow("ASN", p?.asn)
            InfoRow(stringResource(R.string.t_info_location), p?.location)
        }
        SectionLabel(stringResource(R.string.t_info_local))
        ToolCard {
            InfoRow(stringResource(R.string.network), i?.transport)
            if (i?.wifiSsid != null) InfoRow("SSID", i.wifiSsid)
            InfoRow(stringResource(R.string.t_info_ip), i?.localIpv4, mono = true)
            InfoRow(stringResource(R.string.t_info_mask), i?.subnetMask?.let { "$it (/${i.prefixLength})" }, mono = true)
            InfoRow(stringResource(R.string.ping_router), i?.gateway, mono = true)
            InfoRow("DNS", i?.dns?.joinToString("\n"), mono = true)
            i?.privateDns?.let { InfoRow(stringResource(R.string.t_info_private_dns), it) }
            InfoRow("IPv6 (${stringResource(R.string.t_info_local_short)})", i?.localIpv6?.joinToString("\n")?.ifBlank { null }, mono = true)
            InfoRow(stringResource(R.string.t_info_iface), i?.iface, mono = true)
            InfoRow("MTU", i?.mtu?.toString())
            InfoRow(stringResource(R.string.t_info_metered), i?.let { stringResource(if (it.metered) R.string.t_yes else R.string.t_no) })
            InfoRow(stringResource(R.string.t_info_internet), i?.let { stringResource(if (it.validated) R.string.t_yes else R.string.t_no) })
        }
        if (i?.wifiRssi != null || i?.wifiLinkMbps != null) {
            SectionLabel("Wi‑Fi")
            ToolCard {
                InfoRow(stringResource(R.string.t_info_signal), i.wifiRssi?.let { "$it dBm · ${signalWord(it)}" })
                InfoRow(stringResource(R.string.link_speed), i.wifiLinkMbps?.let { "$it Mbps" })
                InfoRow(stringResource(R.string.wifi_band), i.wifiFreq?.let { "$it MHz · ch ${com.dorp.pulse.engine.WifiScanner.channel(it)}" })
                InfoRow(stringResource(R.string.t_info_standard), i.wifiStandard)
            }
        }
        RunButton(false, stringResource(R.string.t_refresh), vm.netInfo::refresh, {}, icon = Icons.Rounded.Refresh)
        i?.gateway?.let { gw ->
            Button(onClick = { onPing(gw) }, shape = CircleShape, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp),
                colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors()) {
                Icon(Icons.Rounded.NetworkPing, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.t_info_ping_router))
            }
        }
        ToolNote(stringResource(R.string.t_copy_hint))
    }
}

@Composable
private fun signalWord(rssi: Int) = stringResource(
    when { rssi >= -55 -> R.string.t_sig_excellent; rssi >= -67 -> R.string.t_sig_good; rssi >= -75 -> R.string.t_sig_fair; else -> R.string.t_sig_weak }
)

// ---------------------------------------------------------------- Wi-Fi analyzer

@Composable
private fun WifiScreen(s: WifiState, vm: MainViewModel, onBack: () -> Unit, pad: PaddingValues) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.wifi.refresh() }
    LaunchedEffect(Unit) { vm.wifi.refresh() }
    DisposableEffect(Unit) { onDispose { vm.wifi.stop() } }
    ToolScreen(stringResource(R.string.t_wifi), onBack, pad) {
        when {
            !s.hasPermission -> {
                ToolCard {
                    Text(stringResource(R.string.t_wifi_perm), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                }
                RunButton(false, stringResource(R.string.t_wifi_allow), { launcher.launch(vm.wifi.scanner.permissions) }, {}, icon = Icons.Rounded.Wifi)
                Text(
                    stringResource(R.string.t_open_settings),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 12.dp, start = 4.dp).clickable {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    },
                )
            }
            !s.wifiOn -> ToolError(stringResource(R.string.t_wifi_off))
            !s.locationOn -> {
                ToolError(stringResource(R.string.t_wifi_location_off))
                RunButton(false, stringResource(R.string.t_open_location), {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }, {}, icon = Icons.Rounded.Wifi)
            }
            else -> {
                Segmented(listOf("2.4 GHz" to "2.4 GHz", "5 GHz" to "5 GHz", "6 GHz" to "6 GHz"), s.band, true, vm.wifi::setBand)
                val inBand = s.aps.filter { it.band == s.band }
                ToolCard { ChannelChart(inBand, s.band, Modifier.fillMaxWidth().height(170.dp).padding(horizontal = 12.dp, vertical = 8.dp)) }
                RunButton(s.scanning, stringResource(R.string.t_wifi_scan), vm.wifi::refresh, {}, icon = Icons.Rounded.WifiFind)
                SectionLabel(stringResource(R.string.t_wifi_networks, inBand.size))
                ToolCard {
                    if (inBand.isEmpty()) InfoRow(stringResource(R.string.t_wifi_none), "")
                    inBand.forEach { ap ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    ap.ssid.ifBlank { stringResource(R.string.t_wifi_hidden) } + if (ap.connected) " · ${stringResource(R.string.t_wifi_connected)}" else "",
                                    style = MaterialTheme.typography.bodyLarge, fontWeight = if (ap.connected) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Text("ch ${ap.channel} · ${ap.widthMhz} MHz · ${ap.security}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Pill("${ap.rssi} dBm", strong = ap.connected)
                        }
                    }
                }
                ToolNote(stringResource(R.string.t_wifi_note, bestChannel(s)))
            }
        }
    }
}

/** The least crowded non-overlapping channel in the selected band, weighting neighbours by signal. */
@Composable
private fun bestChannel(s: WifiState): String {
    val candidates = when (s.band) { "2.4 GHz" -> listOf(1, 6, 11); "5 GHz" -> listOf(36, 40, 44, 48, 149, 153, 157, 161); else -> listOf(5, 21, 37, 53, 69, 85) }
    val load = candidates.associateWith { c ->
        s.aps.filter { it.band == s.band }.sumOf { ap ->
            val span = if (s.band == "2.4 GHz") 4 else ap.widthMhz / 5 / 2
            if (kotlin.math.abs(ap.channel - c) <= span) (ap.rssi + 100).coerceAtLeast(1) else 0
        }
    }
    return load.minByOrNull { it.value }?.key?.toString() ?: "—"
}

@Composable
private fun ChannelChart(aps: List<com.dorp.pulse.engine.AccessPoint>, band: String, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val palette = listOf(scheme.primary, scheme.tertiary, scheme.error, Color(0xFF7CB342), Color(0xFFF9A825), Color(0xFF8E24AA), Color(0xFF00897B), Color(0xFF5C6BC0))
    val grid = scheme.outlineVariant
    val labelColor = scheme.onSurfaceVariant.toArgb()
    val (lo, hi) = when (band) { "2.4 GHz" -> 1 to 13; "5 GHz" -> 32 to 177; else -> 1 to 233 }
    val ticks = when (band) { "2.4 GHz" -> listOf(1, 6, 11, 13); "5 GHz" -> listOf(36, 52, 100, 116, 132, 149, 165); else -> listOf(1, 37, 69, 101, 133, 165, 197, 229) }
    Canvas(modifier) {
        val w = size.width; val h = size.height - 18.dp.toPx() // room for channel numbers
        val paint = android.graphics.Paint().apply { color = labelColor; textSize = 11.dp.toPx(); textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true }
        fun x(ch: Float) = (ch - lo + 2) / (hi - lo + 4) * w
        fun y(rssi: Int) = h - ((rssi + 100).coerceIn(0, 70) / 70f) * h
        listOf(-90, -70, -50).forEach { r -> drawLine(grid, Offset(0f, y(r)), Offset(w, y(r)), strokeWidth = 1f) }
        ticks.forEach { t -> drawContext.canvas.nativeCanvas.drawText(t.toString(), x(t.toFloat()), size.height - 2.dp.toPx(), paint) }
        aps.forEachIndexed { i, ap ->
            val half = if (band == "2.4 GHz") 2f else ap.widthMhz / 10f
            val c = palette[i % palette.size]
            val l = x(ap.channel - half); val r = x(ap.channel + half); val top = y(ap.rssi)
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(l, h); cubicTo(l, top, l + (r - l) * 0.15f, top, (l + r) / 2, top); cubicTo(r - (r - l) * 0.15f, top, r, top, r, h)
            }
            drawPath(path, c.copy(alpha = 0.18f))
            drawPath(path, c, style = Stroke(width = if (ap.connected) 5f else 2.5f))
        }
    }
}

// ---------------------------------------------------------------- Wake-on-LAN

@Composable
private fun WolScreen(s: WolState, vm: MainViewModel, onBack: () -> Unit, pad: PaddingValues) =
    ToolScreen(stringResource(R.string.t_wol), onBack, pad) {
        s.sent?.let { (name, ok) ->
            if (ok) ToolCard { InfoRow(stringResource(R.string.t_wol_sent), name) } else ToolError(stringResource(R.string.t_wol_bad_mac))
        }
        if (s.devices.isNotEmpty()) {
            SectionLabel(stringResource(R.string.t_wol_saved))
            ToolCard {
                s.devices.forEach { d ->
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(d.name, style = MaterialTheme.typography.bodyLarge)
                            Text("${d.mac} → ${d.broadcast}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(onClick = { vm.wol.wake(d) }, shape = CircleShape) { Icon(Icons.Rounded.PowerSettingsNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.t_wol_wake)) }
                        IconButton(onClick = { vm.wol.remove(d) }) { Icon(Icons.Rounded.Close, stringResource(R.string.delete)) }
                    }
                }
            }
        }
        SectionLabel(stringResource(R.string.t_wol_add))
        ToolField(s.name, vm.wol::setName, stringResource(R.string.t_wol_name), keyboard = KeyboardType.Text)
        ToolField(s.mac, vm.wol::setMac, stringResource(R.string.t_wol_mac), keyboard = KeyboardType.Ascii)
        ToolField(s.broadcast, vm.wol::setBroadcast, stringResource(R.string.t_wol_broadcast), keyboard = KeyboardType.Uri)
        RunButton(false, stringResource(R.string.t_wol_save_wake), vm.wol::saveAndWake, {}, enabled = s.mac.isNotBlank(), icon = Icons.Rounded.PowerSettingsNew)
        ToolNote(stringResource(R.string.t_wol_note))
    }

// ---------------------------------------------------------------- Whois

@Composable
private fun WhoisScreen(s: WhoisState, vm: MainViewModel, onBack: () -> Unit, pad: PaddingValues) =
    ToolScreen(stringResource(R.string.t_whois), onBack, pad) {
        ToolField(s.query, vm.whois::setQuery, stringResource(R.string.t_whois_hint), onGo = { vm.whois.lookup() })
        RunButton(s.loading, stringResource(R.string.t_dns_go), { vm.whois.lookup() }, vm.whois::stop, enabled = s.query.isNotBlank(), icon = Icons.Rounded.Search)
        if (s.error) ToolError(stringResource(R.string.t_whois_failed))
        s.info?.let { info ->
            ToolCard {
                info.fields.forEach { (k, v) -> InfoRow(whoisLabel(k), v, mono = k in setOf("range", "nameservers", "asn")) }
            }
        }
        ToolNote(stringResource(R.string.t_whois_note))
    }

@Composable
private fun whoisLabel(k: String): String = stringResource(
    when (k) {
        "location" -> R.string.t_info_location; "isp" -> R.string.provider; "org" -> R.string.t_w_org; "asn" -> R.string.t_w_asn
        "timezone" -> R.string.t_w_tz; "network" -> R.string.t_w_network; "netname" -> R.string.t_w_netname; "range" -> R.string.t_w_range
        "country" -> R.string.t_w_country; "registrant" -> R.string.t_w_registrant; "domain" -> R.string.t_w_domain
        "registered" -> R.string.t_w_registered; "expires" -> R.string.t_w_expires; "updated" -> R.string.t_w_updated
        "registrar" -> R.string.t_w_registrar; "nameservers" -> R.string.t_w_ns; "status" -> R.string.t_w_status
        else -> R.string.t_w_other
    }
)

// ---------------------------------------------------------------- shared

@Composable
private fun <T> ChipRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (v, label) ->
            FilterChip(selected = v == selected, onClick = { onSelect(v) }, label = { Text(label) }, shape = CircleShape)
        }
    }
}
