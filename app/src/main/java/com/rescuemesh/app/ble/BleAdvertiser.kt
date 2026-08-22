package com.rescuemesh.app.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BleAdvertiser(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val adapter = bluetoothManager?.adapter
    private val advertiser get() = adapter?.bluetoothLeAdvertiser

    private val _state = MutableStateFlow<AdvertiserState>(AdvertiserState.Idle)
    val state: StateFlow<AdvertiserState> = _state

    private var callback: AdvertiseCallback? = null

    @SuppressLint("MissingPermission")
    fun start() {
        if (callback != null) return

        val currentAdapter = adapter
        val currentAdvertiser = advertiser
        when {
            currentAdapter == null -> {
                _state.value = AdvertiserState.Unavailable("Bluetooth adapter unavailable")
                return
            }
            !currentAdapter.isEnabled -> {
                _state.value = AdvertiserState.Unavailable("Bluetooth is off")
                return
            }
            currentAdvertiser == null -> {
                _state.value = AdvertiserState.Unavailable("BLE advertising unsupported")
                return
            }
            !hasAdvertisePermission() -> {
                _state.value = AdvertiserState.PermissionMissing
                return
            }
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(BleConstants.MeshServiceParcelUuid)
            .build()

        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceData(BleConstants.MeshServiceParcelUuid, BleConstants.LOCAL_NAME.encodeToByteArray())
            .build()

        val advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                Log.i("BLE", "Advertising started")
                _state.value = AdvertiserState.Advertising
            }

            override fun onStartFailure(errorCode: Int) {
                callback = null
                Log.w("BLE", "Advertising failed: $errorCode")
                _state.value = AdvertiserState.Failed("Advertise failed: $errorCode")
            }
        }

        callback = advertiseCallback
        _state.value = AdvertiserState.Starting
        currentAdvertiser.startAdvertising(settings, data, scanResponse, advertiseCallback)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        val activeCallback = callback ?: return
        if (hasAdvertisePermission()) {
            advertiser?.stopAdvertising(activeCallback)
        }
        callback = null
        _state.value = AdvertiserState.Idle
    }

    private fun hasAdvertisePermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_ADVERTISE,
            ) == PackageManager.PERMISSION_GRANTED
    }
}

sealed interface AdvertiserState {
    data object Idle : AdvertiserState
    data object Starting : AdvertiserState
    data object Advertising : AdvertiserState
    data object PermissionMissing : AdvertiserState
    data class Unavailable(val reason: String) : AdvertiserState
    data class Failed(val reason: String) : AdvertiserState
}
