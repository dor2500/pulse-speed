package com.dorp.pulse.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dorp.pulse.R
import com.dorp.pulse.data.NetworkKind
import com.dorp.pulse.data.SpeedUnit
import java.util.Locale

fun SpeedUnit.convert(mbps: Double): Double = if (this == SpeedUnit.MBYTES) mbps / 8.0 else mbps

val SpeedUnit.label: String get() = if (this == SpeedUnit.MBYTES) "MB/s" else "Mbps"

fun formatNumber(value: Double): String = when {
    value < 10 -> String.format(Locale.US, "%.2f", value)
    value < 100 -> String.format(Locale.US, "%.1f", value)
    else -> String.format(Locale.US, "%.0f", value)
}

fun formatSpeed(mbps: Double, unit: SpeedUnit): String = formatNumber(unit.convert(mbps))

fun formatMs(ms: Double): String =
    if (ms < 10) String.format(Locale.US, "%.1f", ms) else String.format(Locale.US, "%.0f", ms)

@Composable
fun NetworkKind.label(): String = stringResource(
    when (this) {
        NetworkKind.WIFI -> R.string.net_wifi
        NetworkKind.CELLULAR -> R.string.net_cellular
        NetworkKind.ETHERNET -> R.string.net_ethernet
        NetworkKind.VPN -> R.string.net_vpn
        NetworkKind.NONE -> R.string.net_none
        NetworkKind.OTHER -> R.string.net_other
    }
)
