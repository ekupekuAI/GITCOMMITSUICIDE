package com.rescuemesh.app.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.rescuemesh.app.mesh.MeshConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class BleScanner(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val adapter = bluetoothManager?.adapter
    private val scanner get() = adapter?.bluetoothLeScanner

    private val _state = MutableStateFlow<ScannerState>(ScannerState.Idle)
    val state: StateFlow<ScannerState> = _state

    private val _observations = MutableSharedFlow<ScanObservation>(
        extraBufferCapacity = 64,
    )
    val observations: SharedFlow<ScanObservation> = _observations

    private var callback: ScanCallback? = null

    @SuppressLint("MissingPermission")
    fun start(powerMode: MeshConfig.PowerMode = MeshConfig.PowerMode.NORMAL) {
        Log.d("BLE_SCAN", "Start requested (Mode: $powerMode). Current state: ${_state.value}")
        if (callback != null) {
            Log.d("BLE_SCAN", "Already scanning, ignoring start")
            return
        }

        val currentAdapter = adapter
        val currentScanner = scanner
        when {
            currentAdapter == null -> {
                Log.e("BLE_SCAN", "Start failed: Bluetooth adapter unavailable")
                _state.value = ScannerState.Unavailable("Bluetooth adapter unavailable")
                return
            }
            !currentAdapter.isEnabled -> {
                Log.e("BLE_SCAN", "Start failed: Bluetooth is off")
                _state.value = ScannerState.Unavailable("Bluetooth is off")
                return
            }
            currentScanner == null -> {
                Log.e("BLE_SCAN", "Start failed: BLE scanning unsupported")
                _state.value = ScannerState.Unavailable("BLE scanning unsupported")
                return
            }
            !hasScanPermission() -> {
                Log.e("BLE_SCAN", "Start failed: Permission missing")
                _state.value = ScannerState.PermissionMissing
                return
            }
        }

        val filter = ScanFilter.Builder()
            .setServiceUuid(BleConstants.MeshServiceParcelUuid)
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // High performance
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .setReportDelay(0) // Immediate reporting
            .build()

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                Log.v("BLE_SCAN", "onScanResult: ${result.device.address} RSSI=${result.rssi}")
                emit(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                Log.v("BLE_SCAN", "onBatchScanResults: ${results.size} results")
                results.forEach(::emit)
            }

            override fun onScanFailed(errorCode: Int) {
                callback = null
                Log.e("BLE_SCAN", "Scan failed. Error code: $errorCode")
                _state.value = ScannerState.Failed("Scan failed: $errorCode")
            }

            private fun emit(result: ScanResult) {
                Log.i("BLE_SCAN", "DISCOVERED: ${result.device.address} (${result.device.name ?: "Unknown"}) RSSI=${result.rssi}")
                val token = result.scanRecord?.getServiceData(BleConstants.MeshServiceParcelUuid)
                
                _observations.tryEmit(
                    ScanObservation(
                        device = result.device,
                        rssi = result.rssi,
                        arbitrationToken = token,
                        observedAtElapsedMs = SystemClock.elapsedRealtime(),
                    ),
                )
            }
        }

        callback = scanCallback
        _state.value = ScannerState.Scanning
        Log.i("BLE_SCAN", "Starting scan with service filter ${BleConstants.MeshServiceUuid}")
        currentScanner.startScan(listOf(filter), settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        Log.d("BLE_SCAN", "Stop requested")
        val activeCallback = callback ?: return
        if (hasScanPermission()) {
            Log.d("BLE_SCAN", "Stopping scanner")
            scanner?.stopScan(activeCallback)
        }
        callback = null
        _state.value = ScannerState.Idle
    }

    private fun hasScanPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_SCAN,
            ) == PackageManager.PERMISSION_GRANTED
    }
}

data class ScanObservation(
    val device: BluetoothDevice,
    val rssi: Int,
    val arbitrationToken: ByteArray?,
    val observedAtElapsedMs: Long,
)

sealed interface ScannerState {
    data object Idle : ScannerState
    data object Scanning : ScannerState
    data object PermissionMissing : ScannerState
    data class Unavailable(val reason: String) : ScannerState
    data class Failed(val reason: String) : ScannerState
}
