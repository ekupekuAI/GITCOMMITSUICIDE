package com.rescuemesh.app.mesh

import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanSettings

object MeshConfig {
    const val MAX_CLIENT_CONNECTIONS = 8
    const val PREFERRED_RELAY_FANOUT = 2
    const val NEIGHBOR_STALE_TIMEOUT_MS = 15_000L
    const val NEIGHBOR_LOST_TIMEOUT_MS = 45_000L
    const val RECONNECT_BACKOFF_MS = 10_000L
    const val MAX_HOPS = 10
    const val SOS_LIFETIME_MS = 60 * 60 * 1_000L

    enum class PowerMode {
        NORMAL, LOW_POWER, EMERGENCY
    }

    fun getScanMode(mode: PowerMode): Int = when (mode) {
        PowerMode.NORMAL -> ScanSettings.SCAN_MODE_BALANCED
        PowerMode.LOW_POWER -> ScanSettings.SCAN_MODE_LOW_POWER
        PowerMode.EMERGENCY -> ScanSettings.SCAN_MODE_LOW_LATENCY
    }

    fun getAdvertiseMode(mode: PowerMode): Int = when (mode) {
        PowerMode.NORMAL -> AdvertiseSettings.ADVERTISE_MODE_BALANCED
        PowerMode.LOW_POWER -> AdvertiseSettings.ADVERTISE_MODE_LOW_POWER
        PowerMode.EMERGENCY -> AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY
    }
}
