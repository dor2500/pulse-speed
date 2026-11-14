package com.dorp.pulse.engine

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat

data class AccessPoint(
    val ssid: String,
    val bssid: String,
    val freq: Int,
    val channel: Int,
    val band: String,
    val rssi: Int,
    val widthMhz: Int,
    val security: String,
    val connected: Boolean,
)

/** Nearby Wi‑Fi networks via WifiManager scans. Needs precise location permission and Location turned on. */
class WifiScanner(private val context: Context) {
    private val wm = context.applicationContext.getSystemService(WifiManager::class.java)

    /** Requested together: Android 12+ refuses a request for precise location without approximate. */
    val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun requiredPermission(): String = Manifest.permission.ACCESS_FINE_LOCATION

    /** Scan results also need the system Location switch on. */
    val locationOn: Boolean get() = runCatching {
        val lm = context.getSystemService(android.location.LocationManager::class.java)
        if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled else true
    }.getOrDefault(true)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, requiredPermission()) == PackageManager.PERMISSION_GRANTED

    val wifiEnabled: Boolean get() = wm?.isWifiEnabled == true

    /** Asks for a fresh scan; Android throttles apps to 4 scans per 2 minutes, after which cached results come back. */
    @Suppress("DEPRECATION")
    fun startScan(onResults: () -> Unit): () -> Unit {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = onResults()
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        runCatching { wm?.startScan() }
        return { runCatching { context.unregisterReceiver(receiver) } }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun results(): List<AccessPoint> {
        if (!hasPermission()) return emptyList()
        val connected = runCatching { wm?.connectionInfo?.bssid }.getOrNull()
        return runCatching { wm?.scanResults.orEmpty() }.getOrDefault(emptyList()).map { r ->
            val ssid = if (Build.VERSION.SDK_INT >= 33) r.wifiSsid?.toString()?.removeSurrounding("\"") ?: "" else r.SSID ?: ""
            AccessPoint(
                ssid = ssid.ifBlank { "" },
                bssid = r.BSSID ?: "",
                freq = r.frequency,
                channel = channel(r.frequency),
                band = band(r.frequency),
                rssi = r.level,
                widthMhz = when (r.channelWidth) {
                    ScanResult.CHANNEL_WIDTH_20MHZ -> 20; ScanResult.CHANNEL_WIDTH_40MHZ -> 40; ScanResult.CHANNEL_WIDTH_80MHZ -> 80
                    ScanResult.CHANNEL_WIDTH_160MHZ, ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 160; 5 -> 320; else -> 20
                },
                security = security(r.capabilities ?: ""),
                connected = connected != null && connected.equals(r.BSSID, ignoreCase = true),
            )
        }.sortedByDescending { it.rssi }
    }

    companion object {
        fun channel(f: Int): Int = when {
            f == 2484 -> 14
            f in 2412..2472 -> (f - 2407) / 5
            f in 5150..5925 -> (f - 5000) / 5
            f in 5925..7125 -> (f - 5950) / 5
            else -> 0
        }
        fun band(f: Int) = when { f < 3000 -> "2.4 GHz"; f < 5925 -> "5 GHz"; else -> "6 GHz" }
        fun security(caps: String) = when {
            "WPA3" in caps || "SAE" in caps -> "WPA3"
            "WPA2" in caps || "RSN" in caps -> "WPA2"
            "WPA" in caps -> "WPA"
            "WEP" in caps -> "WEP"
            "OWE" in caps -> "OWE"
            else -> "Open"
        }
    }
}
