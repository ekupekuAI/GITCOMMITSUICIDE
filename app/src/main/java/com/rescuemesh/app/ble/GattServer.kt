package com.rescuemesh.app.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.rescuemesh.app.protocol.ControlMessage
import com.rescuemesh.app.protocol.ControlType
import com.rescuemesh.app.protocol.ProtocolCodec
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.UUID

class GattServer(
    context: Context,
    private val localNodeId: ByteArray,
) {
    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val subscribedDevices = linkedSetOf<BluetoothDevice>()
    private val peerNodeIdsByAddress = mutableMapOf<String, ByteArray>()

    private var server: BluetoothGattServer? = null
    private var dataTxCharacteristic: BluetoothGattCharacteristic? = null

    private val _events = MutableSharedFlow<BleTransportEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<BleTransportEvent> = _events

    @SuppressLint("MissingPermission")
    fun start() {
        if (server != null) return
        if (!hasConnectPermission()) {
            _events.tryEmit(BleTransportEvent.Error(null, "BLUETOOTH_CONNECT permission missing"))
            return
        }

        val openedServer = bluetoothManager?.openGattServer(appContext, callback)
        if (openedServer == null) {
            _events.tryEmit(BleTransportEvent.Error(null, "Unable to open GATT server"))
            return
        }

        server = openedServer
        openedServer.addService(createMeshService())
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (hasConnectPermission()) {
            server?.close()
        }
        server = null
        dataTxCharacteristic = null
        subscribedDevices.clear()
        peerNodeIdsByAddress.clear()
    }

    @SuppressLint("MissingPermission")
    fun acknowledgeMessagePersisted(device: BluetoothDevice, messageId: ByteArray) {
        notifyControl(device, ProtocolCodec.ackMessage(localNodeId, messageId))
    }

    @SuppressLint("MissingPermission")
    private fun notifyControl(device: BluetoothDevice, message: ControlMessage) {
        val activeServer = server ?: return
        val characteristic = dataTxCharacteristic ?: return
        val bytes = ProtocolCodec.encodeControl(message)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activeServer.notifyCharacteristicChanged(device, characteristic, false, bytes)
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = bytes
            @Suppress("DEPRECATION")
            activeServer.notifyCharacteristicChanged(device, characteristic, false)
        }
    }

    private fun createMeshService(): BluetoothGattService {
        val service = BluetoothGattService(
            BleConstants.MeshServiceUuid,
            BluetoothGattService.SERVICE_TYPE_PRIMARY,
        )

        val control = BluetoothGattCharacteristic(
            BleConstants.ControlCharacteristicUuid,
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )
        val dataRx = BluetoothGattCharacteristic(
            BleConstants.DataRxCharacteristicUuid,
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )
        val dataTx = BluetoothGattCharacteristic(
            BleConstants.DataTxCharacteristicUuid,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )
        dataTx.addDescriptor(
            BluetoothGattDescriptor(
                CLIENT_CONFIG_DESCRIPTOR,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
            ),
        )

        dataTxCharacteristic = dataTx
        service.addCharacteristic(control)
        service.addCharacteristic(dataRx)
        service.addCharacteristic(dataTx)
        return service
    }

    private val callback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i("BLE", "GATT server connected: ${device.address}")
                    _events.tryEmit(BleTransportEvent.Connected(device))
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    subscribedDevices.remove(device)
                    peerNodeIdsByAddress.remove(device.address)
                    Log.i("BLE", "GATT server disconnected: ${device.address} status=$status")
                    _events.tryEmit(BleTransportEvent.Disconnected(device, "GATT server disconnected: $status"))
                }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            if (responseNeeded && hasConnectPermission()) {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            }
            if (characteristic.uuid == BleConstants.ControlCharacteristicUuid) {
                handleControlWrite(device, value)
            } else if (characteristic.uuid == BleConstants.DataRxCharacteristicUuid) {
                handleDataWrite(device, value)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            if (descriptor.uuid == CLIENT_CONFIG_DESCRIPTOR) {
                subscribedDevices += device
            }
            if (responseNeeded && hasConnectPermission()) {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }
    }

    private fun handleControlWrite(device: BluetoothDevice, value: ByteArray) {
        ProtocolCodec.decodeControl(value)
            .onSuccess { message ->
                if (message.type == ControlType.CONTROL_TYPE_HELLO) {
                    val peerNodeId = message.nodeId.toByteArray()
                    peerNodeIdsByAddress[device.address] = peerNodeId
                    Log.i("MESH", "HELLO received from ${device.address}")
                    _events.tryEmit(BleTransportEvent.HelloReceived(device, peerNodeId))
                    notifyControl(
                        device,
                        ProtocolCodec.ack(localNodeId),
                    )
                }
            }
            .onFailure { error ->
                _events.tryEmit(BleTransportEvent.Error(device, "Malformed control message: ${error.message}"))
            }
    }

    private fun handleDataWrite(device: BluetoothDevice, value: ByteArray) {
        ProtocolCodec.decodeMeshMessage(value)
            .onSuccess { message ->
                Log.i("PROTO", "DATA received from ${device.address}: ${message.messageId.toByteArray().size} byte id")
                _events.tryEmit(
                    BleTransportEvent.MessageReceived(
                        device = device,
                        message = message,
                        sourcePeerNodeId = peerNodeIdsByAddress[device.address],
                    ),
                )
            }
            .onFailure { error ->
                Log.w("PROTO", "Malformed DATA frame from ${device.address}: ${error.message}")
                _events.tryEmit(BleTransportEvent.Error(device, "Malformed DATA frame: ${error.message}"))
            }
    }

    private fun hasConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        val CLIENT_CONFIG_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
