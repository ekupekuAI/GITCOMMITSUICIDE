package com.rescuemesh.app.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.rescuemesh.app.protocol.ControlType
import com.rescuemesh.app.protocol.MeshMessage
import com.rescuemesh.app.protocol.ProtocolCodec
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.UUID

class GattClient(
    context: Context,
    private val localNodeId: ByteArray,
) {
    private val appContext = context.applicationContext
    private val connections = mutableMapOf<String, BluetoothGatt>()
    private val backoffUntilElapsedMs = mutableMapOf<String, Long>()
    private val sentMessageKeys = mutableSetOf<String>()
    private val peerNodeIdsByAddress = mutableMapOf<String, ByteArray>()

    private val _events = MutableSharedFlow<BleTransportEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<BleTransportEvent> = _events

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        val key = device.address ?: return
        if (connections.containsKey(key)) return
        if (connections.size >= MAX_CONNECTIONS) return
        if ((backoffUntilElapsedMs[key] ?: 0L) > SystemClock.elapsedRealtime()) return
        if (!hasConnectPermission()) {
            _events.tryEmit(BleTransportEvent.Error(device, "BLUETOOTH_CONNECT permission missing"))
            return
        }

        Log.i("BLE", "Reconnect/connect attempted: $key")
        connections[key] = device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun close() {
        if (hasConnectPermission()) {
            connections.values.forEach { it.close() }
        }
        connections.clear()
        sentMessageKeys.clear()
        peerNodeIdsByAddress.clear()
    }

    @SuppressLint("MissingPermission")
    fun sendMeshMessage(message: MeshMessage, excludePeerNodeId: ByteArray? = null): Int {
        if (!hasConnectPermission()) return 0
        var started = 0
        connections.values.forEach { gatt ->
            val peerNodeId = peerNodeIdsByAddress[gatt.device.address]
            if (excludePeerNodeId != null && peerNodeId?.contentEquals(excludePeerNodeId) == true) {
                Log.i("ROUTE", "Skipping previous hop ${gatt.device.address}")
                return@forEach
            }
            val key = "${gatt.device.address}:${message.messageId.toByteArray().toHexKey()}"
            if (!sentMessageKeys.add(key)) return@forEach
            val dataRx = gatt.getService(BleConstants.MeshServiceUuid)
                ?.getCharacteristic(BleConstants.DataRxCharacteristicUuid) ?: run {
                sentMessageKeys.remove(key)
                return@forEach
            }
            val payload = ProtocolCodec.encodeMeshMessage(message)
            val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(
                    dataRx,
                    payload,
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,
                ) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                dataRx.value = payload
                dataRx.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(dataRx)
            }
            if (ok) {
                started += 1
                Log.i("ROUTE", "Message forwarded to ${gatt.device.address}")
                _events.tryEmit(BleTransportEvent.MessageSent(gatt.device, message.messageId.toByteArray()))
            } else {
                sentMessageKeys.remove(key)
                Log.w("ROUTE", "Forwarding failed to start for ${gatt.device.address}")
                _events.tryEmit(BleTransportEvent.Error(gatt.device, "DATA write failed to start"))
            }
        }
        return started
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CLIENT_CONFIG_DESCRIPTOR)
        if (descriptor == null) {
            _events.tryEmit(BleTransportEvent.Error(gatt.device, "TX notification descriptor missing"))
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeHello(gatt: BluetoothGatt) {
        val service = gatt.getService(BleConstants.MeshServiceUuid)
        val control = service?.getCharacteristic(BleConstants.ControlCharacteristicUuid)
        if (control == null) {
            _events.tryEmit(BleTransportEvent.Error(gatt.device, "Control characteristic missing"))
            return
        }

        val payload = ProtocolCodec.encodeControl(ProtocolCodec.hello(localNodeId))

        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                control,
                payload,
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,
            ) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            control.value = payload
            control.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(control)
        }

        if (ok) {
            _events.tryEmit(BleTransportEvent.HelloSent(gatt.device))
        } else {
            _events.tryEmit(BleTransportEvent.Error(gatt.device, "HELLO write failed to start"))
        }
    }

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                closeGatt(gatt, "GATT status $status")
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i("BLE", "GATT client connected: ${gatt.device.address}")
                    _events.tryEmit(BleTransportEvent.Connected(gatt.device))
                    if (hasConnectPermission()) {
                        gatt.discoverServices()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> closeGatt(gatt, "GATT client disconnected")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                closeGatt(gatt, "Service discovery failed: $status")
                return
            }
            val tx = gatt.getService(BleConstants.MeshServiceUuid)
                ?.getCharacteristic(BleConstants.DataTxCharacteristicUuid)
            if (tx == null) {
                closeGatt(gatt, "Mesh TX characteristic missing")
                return
            }
            enableNotifications(gatt, tx)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && descriptor.uuid == CLIENT_CONFIG_DESCRIPTOR) {
                writeHello(gatt)
            } else {
                closeGatt(gatt, "Descriptor write failed: $status")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleNotification(gatt.device, characteristic.uuid, value)
        }

        @Deprecated("Used on Android 12L and below")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            @Suppress("DEPRECATION")
            handleNotification(gatt.device, characteristic.uuid, characteristic.value ?: ByteArray(0))
        }
    }

    private fun handleNotification(device: BluetoothDevice, characteristicUuid: UUID, value: ByteArray) {
        if (characteristicUuid != BleConstants.DataTxCharacteristicUuid) return
        ProtocolCodec.decodeControl(value)
            .onSuccess { message ->
                if (message.type == ControlType.CONTROL_TYPE_ACK) {
                    if (message.ackMessageId.isEmpty) {
                        peerNodeIdsByAddress[device.address] = message.nodeId.toByteArray()
                        Log.i("MESH", "ACK received from ${device.address}")
                        _events.tryEmit(BleTransportEvent.AckReceived(device, message.nodeId.toByteArray()))
                    } else {
                        Log.i("SOS", "Message delivered ACK from ${device.address}")
                        _events.tryEmit(
                            BleTransportEvent.MessageAckReceived(
                                device,
                                message.ackMessageId.toByteArray(),
                            ),
                        )
                    }
                }
            }
            .onFailure { error ->
                _events.tryEmit(BleTransportEvent.Error(device, "Malformed notification: ${error.message}"))
            }
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt(gatt: BluetoothGatt, reason: String) {
        val address = gatt.device.address
        connections.remove(address)
        peerNodeIdsByAddress.remove(address)
        backoffUntilElapsedMs[address] = SystemClock.elapsedRealtime() + RETRY_BACKOFF_MS
        Log.i("BLE", "GATT closed for $address: $reason; backoff scheduled")
        if (hasConnectPermission()) {
            gatt.close()
        }
        _events.tryEmit(BleTransportEvent.Disconnected(gatt.device, reason))
    }

    private fun hasConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val MAX_CONNECTIONS = 3
        const val RETRY_BACKOFF_MS = 5_000L
        val CLIENT_CONFIG_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}

private fun ByteArray.toHexKey(): String = joinToString(separator = "") { "%02x".format(it) }
