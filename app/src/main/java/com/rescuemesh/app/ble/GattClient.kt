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
    
    // Per-device write queue to avoid GATT busy errors
    private val writeQueues = mutableMapOf<String, ArrayDeque<GattWriteOperation>>()
    private val isWriting = mutableMapOf<String, Boolean>()

    private val _events = MutableSharedFlow<BleTransportEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<BleTransportEvent> = _events

    private sealed class GattWriteOperation {
        data class Characteristic(val characteristic: BluetoothGattCharacteristic, val value: ByteArray) : GattWriteOperation()
        data class Descriptor(val descriptor: BluetoothGattDescriptor, val value: ByteArray) : GattWriteOperation()
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        val key = device.address ?: return
        Log.d("GATT_CLIENT", "Connect requested for $key. Active connections: ${connections.size}")
        
        if (connections.containsKey(key)) {
            Log.d("GATT_CLIENT", "Already connected/connecting to $key, skipping")
            return
        }
        if (connections.size >= MAX_CONNECTIONS) {
            Log.w("GATT_CLIENT", "Max connections ($MAX_CONNECTIONS) reached, skipping $key")
            return
        }
        val backoff = backoffUntilElapsedMs[key] ?: 0L
        if (backoff > SystemClock.elapsedRealtime()) {
            Log.d("GATT_CLIENT", "Backoff active for $key (wait ${backoff - SystemClock.elapsedRealtime()}ms), skipping")
            return
        }
        if (!hasConnectPermission()) {
            Log.e("GATT_CLIENT", "Permission missing for connect")
            _events.tryEmit(BleTransportEvent.Error(device, "BLUETOOTH_CONNECT permission missing"))
            return
        }

        Log.i("GATT_CLIENT", "INITIATING CONNECT: $key")
        connections[key] = device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun close() {
        Log.d("GATT_CLIENT", "Closing all connections")
        if (hasConnectPermission()) {
            connections.values.forEach { 
                Log.d("GATT_CLIENT", "Closing connection to ${it.device.address}")
                it.close() 
            }
        }
        connections.clear()
        sentMessageKeys.clear()
        peerNodeIdsByAddress.clear()
    }

    @SuppressLint("MissingPermission")
    private fun enqueueWrite(gatt: BluetoothGatt, op: GattWriteOperation) {
        val address = gatt.device.address
        val queue = writeQueues.getOrPut(address) { ArrayDeque() }
        queue.addLast(op)
        processNextWrite(gatt)
    }

    @SuppressLint("MissingPermission")
    private fun processNextWrite(gatt: BluetoothGatt) {
        val address = gatt.device.address
        if (isWriting[address] == true) return
        
        val queue = writeQueues[address] ?: return
        if (queue.isEmpty()) return
        
        val next = queue.removeFirst()
        isWriting[address] = true
        
        val success = when (next) {
            is GattWriteOperation.Characteristic -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(next.characteristic, next.value, next.characteristic.writeType) == BluetoothGatt.GATT_SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    next.characteristic.value = next.value
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(next.characteristic)
                }
            }
            is GattWriteOperation.Descriptor -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(next.descriptor, next.value) == BluetoothGatt.GATT_SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    next.descriptor.value = next.value
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(next.descriptor)
                }
            }
        }
        
        if (!success) {
            Log.e("GATT_CLIENT", "Write failed to start for $address")
            isWriting[address] = false
            processNextWrite(gatt)
        }
    }

    @SuppressLint("MissingPermission")
    fun sendMeshMessage(message: MeshMessage, excludePeerNodeId: ByteArray? = null): Int {
        if (!hasConnectPermission()) {
            Log.e("GATT_CLIENT", "No permission to send message")
            return 0
        }
        var started = 0
        val messageIdHex = message.messageId.toByteArray().toHexKey()
        Log.d("GATT_CLIENT", "SendMeshMessage: ID=$messageIdHex, targets=${connections.size}")
        
        connections.values.forEach { gatt ->
            val address = gatt.device.address
            val peerNodeId = peerNodeIdsByAddress[address]
            if (excludePeerNodeId != null && peerNodeId?.contentEquals(excludePeerNodeId) == true) {
                Log.d("GATT_CLIENT", "Skipping loopback/previous hop to $address")
                return@forEach
            }
            val key = "$address:$messageIdHex"
            if (!sentMessageKeys.add(key)) {
                Log.d("GATT_CLIENT", "Message already sent to $address, skipping")
                return@forEach
            }
            
            val service = gatt.getService(BleConstants.MeshServiceUuid)
            val dataRx = service?.getCharacteristic(BleConstants.DataRxCharacteristicUuid) ?: run {
                Log.e("GATT_CLIENT", "DataRx characteristic not found for $address")
                sentMessageKeys.remove(key)
                return@forEach
            }
            
            val payload = ProtocolCodec.encodeMeshMessage(message)
            dataRx.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            enqueueWrite(gatt, GattWriteOperation.Characteristic(dataRx, payload))
            
            started += 1
            Log.i("GATT_CLIENT", "DATA QUEUED: $address")
            _events.tryEmit(BleTransportEvent.MessageSent(gatt.device, message.messageId.toByteArray()))
        }
        return started
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        val address = gatt.device.address
        Log.d("GATT_CLIENT", "Enabling notifications for $address")
        
        gatt.setCharacteristicNotification(characteristic, true)
        
        val descriptor = characteristic.getDescriptor(CLIENT_CONFIG_DESCRIPTOR)
        if (descriptor == null) {
            Log.e("GATT_CLIENT", "Descriptor not found for $address")
            return
        }
        
        enqueueWrite(gatt, GattWriteOperation.Descriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE))
    }

    @SuppressLint("MissingPermission")
    private fun writeHello(gatt: BluetoothGatt) {
        val address = gatt.device.address
        Log.d("GATT_CLIENT", "Queuing HELLO for $address")
        
        val service = gatt.getService(BleConstants.MeshServiceUuid)
        val control = service?.getCharacteristic(BleConstants.ControlCharacteristicUuid) ?: return

        val payload = ProtocolCodec.encodeControl(ProtocolCodec.hello(localNodeId))
        control.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        enqueueWrite(gatt, GattWriteOperation.Characteristic(control, payload))
        _events.tryEmit(BleTransportEvent.HelloSent(gatt.device))
    }

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val address = gatt.device.address
            Log.i("GATT_CLIENT", "onConnectionStateChange: $address status=$status newState=$newState")
            
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e("GATT_CLIENT", "GATT Error status $status for $address")
                closeGatt(gatt, "GATT status $status")
                return
            }
            
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i("GATT_CLIENT", "CONNECTED: $address")
                    _events.tryEmit(BleTransportEvent.Connected(gatt.device))
                    if (hasConnectPermission()) {
                        Log.d("GATT_CLIENT", "Requesting MTU 512 for $address")
                        gatt.requestMtu(512)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i("GATT_CLIENT", "DISCONNECTED: $address")
                    closeGatt(gatt, "GATT client disconnected")
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.i("GATT_CLIENT", "onMtuChanged: ${gatt.device.address} mtu=$mtu status=$status")
            if (hasConnectPermission()) {
                Log.d("GATT_CLIENT", "Discovering services for ${gatt.device.address}")
                gatt.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val address = gatt.device.address
            Log.i("GATT_CLIENT", "onServicesDiscovered: $address status=$status")
            
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e("GATT_CLIENT", "Service discovery failed for $address")
                closeGatt(gatt, "Service discovery failed: $status")
                return
            }
            
            val service = gatt.getService(BleConstants.MeshServiceUuid)
            if (service == null) {
                Log.e("GATT_CLIENT", "Mesh service not found on $address. Available: ${gatt.services.map { it.uuid }}")
                closeGatt(gatt, "Mesh service missing")
                return
            }
            
            val tx = service.getCharacteristic(BleConstants.DataTxCharacteristicUuid)
            if (tx == null) {
                Log.e("GATT_CLIENT", "Mesh TX characteristic missing on $address")
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
            val address = gatt.device.address
            Log.d("GATT_CLIENT", "onDescriptorWrite: $address status=$status")
            isWriting[address] = false
            
            if (status == BluetoothGatt.GATT_SUCCESS && descriptor.uuid == CLIENT_CONFIG_DESCRIPTOR) {
                writeHello(gatt)
            }
            processNextWrite(gatt)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val address = gatt.device.address
            Log.d("GATT_CLIENT", "onCharacteristicWrite: $address status=$status")
            isWriting[address] = false
            processNextWrite(gatt)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            Log.d("GATT_CLIENT", "onCharacteristicChanged: ${gatt.device.address} characteristic=${characteristic.uuid} size=${value.size}")
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
        writeQueues.remove(address)
        isWriting.remove(address)
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
