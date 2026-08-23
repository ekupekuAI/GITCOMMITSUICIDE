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
import com.rescuemesh.app.mesh.MeshConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BleAdvertiser(
    context: Context,
    private val localNodeId: ByteArray,
) {
    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val adapter = bluetoothManager?.adapter
    private val advertiser get() = adapter?.bluetoothLeAdvertiser

    private val _state = MutableStateFlow<AdvertiserState>(AdvertiserState.Idle)
    val state: StateFlow<AdvertiserState> = _state

    private var callback: AdvertiseCallback? = null

    @SuppressLint("MissingPermission")
    fun start(powerMode: MeshConfig.PowerMode = MeshConfig.PowerMode.NORMAL) {
        Log.d("BLE_ADV", "Start requested (Mode: $powerMode). Current state: ${_state.value}")
        if (callback != null) {
            Log.d("BLE_ADV", "Already advertising, ignoring start")
            return
        }

        val currentAdapter = adapter
        val currentAdvertiser = advertiser
        when {
            currentAdapter == null -> {
                Log.e("BLE_ADV", "Start failed: Bluetooth adapter unavailable")
                _state.value = AdvertiserState.Unavailable("Bluetooth adapter unavailable")
                return
            }
            !currentAdapter.isEnabled -> {
                Log.e("BLE_ADV", "Start failed: Bluetooth is off")
                _state.value = AdvertiserState.Unavailable("Bluetooth is off")
                return
            }
            currentAdvertiser == null -> {
                Log.e("BLE_ADV", "Start failed: BLE advertising unsupported")
                _state.value = AdvertiserState.Unavailable("BLE advertising unsupported")
                return
            }
            !hasAdvertisePermission() -> {
                Log.e("BLE_ADV", "Start failed: Permission missing")
                _state.value = AdvertiserState.PermissionMissing
                return
            }
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY) // High frequency
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH) // Max range for emergency
            .setConnectable(true)
            .setTimeout(0) // Run until stopped
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(BleConstants.MeshServiceParcelUuid)
            .build()

        val arbitrationToken = localNodeId.take(4).toByteArray()

        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceData(BleConstants.MeshServiceParcelUuid, arbitrationToken)
            .build()

        val advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                Log.i("BLE_ADV", "Advertising started successfully. Settings: $settingsInEffect")
                _state.value = AdvertiserState.Advertising
            }

            override fun onStartFailure(errorCode: Int) {
                callback = null
                Log.e("BLE_ADV", "Advertising failed to start. Error code: $errorCode")
                val reason = when (errorCode) {
                    ADVERTISE_FAILED_ALREADY_STARTED -> "Already started"
                    ADVERTISE_FAILED_DATA_TOO_LARGE -> "Data too large"
                    ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported"
                    ADVERTISE_FAILED_INTERNAL_ERROR -> "Internal error"
                    ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too many advertisers"
                    else -> "Unknown error $errorCode"
                }
                _state.value = AdvertiserState.Failed("Advertise failed: $reason")
            }
        }

        callback = advertiseCallback
        _state.value = AdvertiserState.Starting
        Log.d("BLE_ADV", "Calling startAdvertising...")
        currentAdvertiser.startAdvertising(settings, data, scanResponse, advertiseCallback)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        Log.d("BLE_ADV", "Stop requested")
        val activeCallback = callback ?: return
        if (hasAdvertisePermission()) {
            Log.d("BLE_ADV", "Stopping advertiser")
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
