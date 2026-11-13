package com.dorp.pulse.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.NetworkPing
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dorp.pulse.R
import com.dorp.pulse.ScanUiState
import com.dorp.pulse.engine.DeviceKind
import com.dorp.pulse.engine.LanDevice

/** The "Devices" half of the Ping tab: scan the local subnet and list what answers. Tapping a device pings it. */
@Composable
fun ScanContent(
    state: ScanUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRefresh: () -> Unit,
    onPingDevice: (String) -> Unit,
) {
    LaunchedEffect(Unit) { onRefresh() }
    val scheme = MaterialTheme.colorScheme

    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 12.dp, bottomEnd = 28.dp, bottomStart = 12.dp),
        color = scheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(scheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(if (state.notLocal) Icons.Rounded.WifiOff else Icons.Rounded.Radar, null, tint = scheme.onSecondaryContainer) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.notLocal) stringResource(R.string.scan_not_local_title)
                        else state.subnet?.label ?: stringResource(R.string.scan_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        when {
                            state.notLocal -> stringResource(R.string.scan_not_local)
                            state.running -> stringResource(R.string.scan_progress, state.done, state.total, state.devices.size)
                            state.finishedAt != null -> stringResource(R.string.scan_found, state.devices.size)
                            else -> stringResource(R.string.scan_hint)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            if (state.running) {
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    strokeCap = StrokeCap.Round,
                )
            }
            Spacer(Modifier.height(16.dp))
            if (state.running) {
                Button(
                    onClick = onStop, shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Rounded.Stop, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.ping_stop))
                }
            } else {
                Button(
                    onClick = onStart, shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Rounded.Radar, null); Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (state.finishedAt != null) R.string.scan_again else R.string.scan_start))
                }
            }
        }
    }

    if (state.devices.isNotEmpty()) {
        Text(
            stringResource(R.string.scan_devices, state.devices.size),
            style = MaterialTheme.typography.titleSmall,
            color = scheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
        )
        Surface(shape = RoundedCornerShape(24.dp), color = scheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 6.dp)) {
                state.devices.forEach { d -> DeviceRow(d) { onPingDevice(d.ip) } }
            }
        }
        Text(
            stringResource(R.string.scan_note),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )
    }
}

@Composable
private fun DeviceRow(d: LanDevice, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val special = d.kind == DeviceKind.ROUTER || d.kind == DeviceKind.THIS_DEVICE
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (special) scheme.primaryContainer else scheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(d.kind.icon(), null, Modifier.size(22.dp), tint = if (special) scheme.onPrimaryContainer else scheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                d.name ?: d.kind.label(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val sub = buildList {
                add(d.ip)
                if (d.name != null && d.kind != DeviceKind.UNKNOWN) add(d.kind.label())
                if (d.openPorts.isNotEmpty()) add(d.openPorts.take(4).joinToString(", ") { portName(it) })
            }.joinToString(" · ")
            Text(sub, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier.clip(CircleShape).background(scheme.surfaceContainerHigh).padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Rounded.NetworkPing, null, Modifier.size(14.dp), tint = scheme.onSurfaceVariant)
            Text(
                d.latencyMs?.let { formatMs(it) + " ms" } ?: "—",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

private fun portName(p: Int) = when (p) {
    22 -> "SSH"; 80 -> "HTTP"; 443 -> "HTTPS"; 445, 139 -> "SMB"; 3389 -> "RDP"; 8008, 8009 -> "Cast"
    7000 -> "AirPlay"; 9100, 631 -> "Print"; 554 -> "RTSP"; 62078 -> "iOS"; else -> p.toString()
}

private fun DeviceKind.icon(): ImageVector = when (this) {
    DeviceKind.ROUTER -> Icons.Rounded.Router
    DeviceKind.THIS_DEVICE, DeviceKind.PHONE -> Icons.Rounded.Smartphone
    DeviceKind.COMPUTER -> Icons.Rounded.Computer
    DeviceKind.TV -> Icons.Rounded.Cast
    DeviceKind.PRINTER -> Icons.Rounded.Print
    DeviceKind.SPEAKER -> Icons.Rounded.Speaker
    DeviceKind.CAMERA -> Icons.Rounded.Videocam
    DeviceKind.SMART_HOME -> Icons.Rounded.Home
    DeviceKind.UNKNOWN -> Icons.Rounded.Devices
}

@Composable
private fun DeviceKind.label(): String = stringResource(
    when (this) {
        DeviceKind.ROUTER -> R.string.dev_router
        DeviceKind.THIS_DEVICE -> R.string.dev_this
        DeviceKind.COMPUTER -> R.string.dev_computer
        DeviceKind.PHONE -> R.string.dev_phone
        DeviceKind.TV -> R.string.dev_tv
        DeviceKind.PRINTER -> R.string.dev_printer
        DeviceKind.SPEAKER -> R.string.dev_speaker
        DeviceKind.CAMERA -> R.string.dev_camera
        DeviceKind.SMART_HOME -> R.string.dev_smart_home
        DeviceKind.UNKNOWN -> R.string.dev_unknown
    }
)
