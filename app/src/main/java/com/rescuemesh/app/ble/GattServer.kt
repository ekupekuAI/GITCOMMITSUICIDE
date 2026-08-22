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
import android.bluetooth.BluetoothStatusCodes
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
    private val identityProvider: com.rescuemesh.app.identity.NodeIdentityProvider,
) {
    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val subscribedDevices = linkedSetOf<BluetoothDevice>()
    private val peerNodeIdsByAddress = mutableMapOf<String, ByteArray>()
    
    // Per-device notification queue
    private val notificationQueues = mutableMapOf<String, ArrayDeque<ByteArray>>()
    private val isNotifying = mutableMapOf<String, Boolean>()

    private var server: BluetoothGattServer? = null
    private var dataTxCharacteristic: BluetoothGattCharacteristic? = null

    private val _events = MutableSharedFlow<BleTransportEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<BleTransportEvent> = _events

    @SuppressLint("MissingPermission")
    fun start() {
        Log.d("GATT_SERVER", "Start requested")
        if (server != null) {
            Log.d("GATT_SERVER", "Server already running, ignoring start")
            return
        }
        if (!hasConnectPermission()) {
            Log.e("GATT_SERVER", "Permission missing for start")
            _events.tryEmit(BleTransportEvent.Error(null, "BLUETOOTH_CONNECT permission missing"))
            return
        }

        val openedServer = bluetoothManager?.openGattServer(appContext, callback)
        if (openedServer == null) {
            Log.e("GATT_SERVER", "Unable to open GATT server")
            _events.tryEmit(BleTransportEvent.Error(null, "Unable to open GATT server"))
            return
        }

        Log.i("GATT_SERVER", "Server opened successfully")
        server = openedServer
        openedServer.addService(createMeshService())
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        Log.d("GATT_SERVER", "Stop requested")
        if (hasConnectPermission()) {
            server?.close()
        }
        server = null
        dataTxCharacteristic = null
        subscribedDevices.clear()
        peerNodeIdsByAddress.clear()
        notificationQueues.clear()
        isNotifying.clear()
    }

    @SuppressLint("MissingPermission")
    fun acknowledgeMessagePersisted(device: BluetoothDevice, messageId: ByteArray) {
        val messageIdHex = messageId.toHexKey()
        Log.d("GATT_SERVER", "Sending ACK for message $messageIdHex to ${device.address}")
        notifyControl(device, ProtocolCodec.ackMessage(localNodeId, messageId))
    }

    @SuppressLint("MissingPermission")
    private fun notifyControl(device: BluetoothDevice, message: ControlMessage) {
        val address = device.address
        val bytes = ProtocolCodec.encodeControl(message)
        val queue = notificationQueues.getOrPut(address) { ArrayDeque() }
        queue.addLast(bytes)
        processNextNotification(device)
    }

    @SuppressLint("MissingPermission")
    private fun processNextNotification(device: BluetoothDevice) {
        val address = device.address
        if (isNotifying[address] == true) return
        
        val activeServer = server ?: return
        val characteristic = dataTxCharacteristic ?: return
        val queue = notificationQueues[address] ?: return
        if (queue.isEmpty()) return
        
        val next = queue.removeFirst()
        isNotifying[address] = true
        
        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activeServer.notifyCharacteristicChanged(device, characteristic, false, next) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = next
            @Suppress("DEPRECATION")
            activeServer.notifyCharacteristicChanged(device, characteristic, false)
        }
        
        if (!ok) {
            Log.e("GATT_SERVER", "Notification failed to start for $address")
            isNotifying[address] = false
            processNextNotification(device)
        }
    }

    private fun createMeshService(): BluetoothGattService {
        Log.d("GATT_SERVER", "Creating Mesh service with UUID ${BleConstants.MeshServiceUuid}")
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
            Log.i("GATT_SERVER", "onConnectionStateChange: ${device.address} status=$status newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i("GATT_SERVER", "CONNECTED: ${device.address}")
                    _events.tryEmit(BleTransportEvent.Connected(device))
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    subscribedDevices.remove(device)
                    peerNodeIdsByAddress.remove(device.address)
                    notificationQueues.remove(device.address)
                    isNotifying.remove(device.address)
                    Log.i("GATT_SERVER", "DISCONNECTED: ${device.address}")
                    _events.tryEmit(BleTransportEvent.Disconnected(device, "GATT server disconnected: $status"))
                }
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            Log.i("GATT_SERVER", "onMtuChanged: ${device.address} mtu=$mtu")
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            Log.d("GATT_SERVER", "onNotificationSent: ${device.address} status=$status")
            isNotifying[device.address] = false
            processNextNotification(device)
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
            Log.d("GATT_SERVER", "onCharacteristicWriteRequest: ${device.address} char=${characteristic.uuid} size=${value.size} offset=$offset")
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
            Log.d("GATT_SERVER", "onDescriptorWriteRequest: ${device.address} desc=${descriptor.uuid} size=${value.size}")
            if (descriptor.uuid == CLIENT_CONFIG_DESCRIPTOR) {
                subscribedDevices += device
                Log.i("GATT_SERVER", "Device ${device.address} SUBSCRIBED to notifications")
            }
            if (responseNeeded && hasConnectPermission()) {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }
    }

    private fun handleControlWrite(device: BluetoothDevice, value: ByteArray) {
        Log.d("GATT_SERVER", "Decoding control message from ${device.address}")
        ProtocolCodec.decodeControl(value)
            .onSuccess { message ->
                if (message.type == ControlType.CONTROL_TYPE_HELLO) {
                    val peerNodeId = message.nodeId.toByteArray()
                    peerNodeIdsByAddress[device.address] = peerNodeId
                    Log.i("GATT_SERVER", "HELLO RECEIVED from ${device.address}: ${peerNodeId.toHexKey()} (${message.role})")
                    _events.tryEmit(
                        BleTransportEvent.HelloReceived(
                            device = device, 
                            peerNodeId = peerNodeId,
                            role = message.role
                        )
                    )
                    
                    Log.d("GATT_SERVER", "Sending HELLO ACK to ${device.address}")
                    notifyControl(
                        device,
                        ProtocolCodec.ack(localNodeId, identityProvider.nodeRole),
                    )
                }
            }
            .onFailure { error ->
                Log.e("GATT_SERVER", "Failed to decode control message from ${device.address}: ${error.message}")
                _events.tryEmit(BleTransportEvent.Error(device, "Malformed control message: ${error.message}"))
            }
    }

    private fun handleDataWrite(device: BluetoothDevice, value: ByteArray) {
        Log.d("GATT_SERVER", "Decoding DATA message from ${device.address}")
        ProtocolCodec.decodeMeshMessage(value)
            .onSuccess { message ->
                Log.i("GATT_SERVER", "DATA RECEIVED from ${device.address}: ID=${message.messageId.toByteArray().toHexKey()}")
                _events.tryEmit(
                    BleTransportEvent.MessageReceived(
                        device = device,
                        message = message,
                        sourcePeerNodeId = peerNodeIdsByAddress[device.address],
                    ),
                )
            }
            .onFailure { error ->
                Log.w("GATT_SERVER", "Malformed DATA frame from ${device.address}: ${error.message}")
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

private fun ByteArray.toHexKey(): String = joinToString(separator = "") { "%02x".format(it) }
