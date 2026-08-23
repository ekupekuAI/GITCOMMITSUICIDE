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
import com.rescuemesh.app.mesh.MeshConfig
import com.rescuemesh.app.protocol.ControlType
import com.rescuemesh.app.protocol.MeshMessage
import com.rescuemesh.app.protocol.ProtocolCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.util.UUID

class GattClient(
    context: Context,
    private val localNodeId: ByteArray,
    private val identityProvider: com.rescuemesh.app.identity.NodeIdentityProvider,
) {
    private val appContext = context.applicationContext
    private val connections = mutableMapOf<String, BluetoothGatt>()
    private val backoffUntilElapsedMs = mutableMapOf<String, Long>()
    private val sentMessageKeys = mutableSetOf<String>()
    private val peerNodeIdsByAddress = mutableMapOf<String, ByteArray>()
    private val peerRolesByAddress = mutableMapOf<String, com.rescuemesh.app.protocol.NodeRole>()
    
    // Per-device write queue to avoid GATT busy errors
    private val writeQueues = mutableMapOf<String, ArrayDeque<GattWriteOperation>>()
    private val isWriting = mutableMapOf<String, Boolean>()
    private val writingMessageKeys = mutableMapOf<String, String>()
    
    // Connection watchdog tracking
    private val watchdogJobs = mutableMapOf<String, kotlinx.coroutines.Job>()
    private val serviceDiscoveryStarted = mutableSetOf<String>()

    private val _events = MutableSharedFlow<BleTransportEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<BleTransportEvent> = _events

    private val scope = CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    private sealed class GattWriteOperation {
        data class Characteristic(
            val characteristic: BluetoothGattCharacteristic,
            val value: ByteArray,
            val messageKey: String? = null,
        ) : GattWriteOperation()
        data class Descriptor(val descriptor: BluetoothGattDescriptor, val value: ByteArray) : GattWriteOperation()
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        val key = device.address ?: return
        
        if (connections.containsKey(key)) return
        if (connections.size >= MeshConfig.MAX_CLIENT_CONNECTIONS) {
            Log.w("GATT_CLIENT", "Max slots reached, prioritizing existing links")
            return
        }
        
        val backoff = backoffUntilElapsedMs[key] ?: 0L
        if (backoff > SystemClock.elapsedRealtime()) return

        Log.i("GATT_CLIENT", "FAST CONNECT: $key")
        val gatt = device.connectGatt(
            appContext, 
            false, 
            callback, 
            BluetoothDevice.TRANSPORT_LE,
            BluetoothDevice.PHY_LE_1M_MASK
        )
        connections[key] = gatt
        startWatchdog(key, gatt)
    }

    private fun startWatchdog(address: String, gatt: BluetoothGatt) {
        watchdogJobs[address]?.cancel()
        watchdogJobs[address] = scope.launch {
            kotlinx.coroutines.delay(30_000)
            if (connections[address] == gatt && !peerNodeIdsByAddress.containsKey(address)) {
                Log.w("GATT_CLIENT", "Watchdog: Handshake timeout for $address. Resetting.")
                closeGatt(gatt, "Handshake timeout")
            }
        }
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
        peerRolesByAddress.clear()
        writeQueues.clear()
        isWriting.clear()
        writingMessageKeys.clear()
        serviceDiscoveryStarted.clear()
        watchdogJobs.values.forEach { it.cancel() }
        watchdogJobs.clear()
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
        if (next is GattWriteOperation.Characteristic) {
            next.messageKey?.let { writingMessageKeys[address] = it }
        }
        
        val success = when (next) {
            is GattWriteOperation.Characteristic -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(next.characteristic, next.value, next.characteristic.writeType) == android.bluetooth.BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    next.characteristic.value = next.value
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(next.characteristic)
                }
            }
            is GattWriteOperation.Descriptor -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(next.descriptor, next.value) == android.bluetooth.BluetoothStatusCodes.SUCCESS
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
            writingMessageKeys.remove(address)?.let(sentMessageKeys::remove)
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
        
        val messageIdHex = message.messageId.toByteArray().toHexKey()
        Log.d("GATT_CLIENT", "SendMeshMessage: ID=$messageIdHex, targets=${connections.size}")

        // Categorize targets: Responders vs Relays
        val targets = connections.values.filter { peerNodeIdsByAddress.containsKey(it.device.address) }
        val responders = targets.filter { isResponderFor(peerRolesByAddress[it.device.address], message.category) }
        val relays = targets.filter { !responders.contains(it) }

        // Try responders first, then relays
        val sortedTargets = responders + relays

        var started = 0
        sortedTargets.forEach { gatt ->
            val address = gatt.device.address
            val peerNodeId = peerNodeIdsByAddress[address] ?: return@forEach
            if (
                (excludePeerNodeId != null && peerNodeId?.contentEquals(excludePeerNodeId) == true) ||
                peerNodeId?.contentEquals(message.originNodeId.toByteArray()) == true
            ) {
                Log.d("GATT_CLIENT", "Skipping previous hop or origin at $address")
                return@forEach
            }
            
            // Limit fan-out per transmission attempt
            if (started >= MeshConfig.PREFERRED_RELAY_FANOUT) {
                Log.d("GATT_CLIENT", "Fan-out limit reached for this transmission")
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
            enqueueWrite(gatt, GattWriteOperation.Characteristic(dataRx, payload, key))
            
            started += 1
            Log.i("RELAY", "messageId=$messageIdHex from=${localNodeId.toHexKey()} to=${peerNodeId.toHexKey()} hopCount=${message.hopCount}")
            Log.i("GATT_CLIENT", "DATA QUEUED: $address (Role: ${peerRolesByAddress[address]})")
            _events.tryEmit(BleTransportEvent.MessageSent(gatt.device, message.messageId.toByteArray()))
        }
        return started
    }

    private fun isResponderFor(role: com.rescuemesh.app.protocol.NodeRole?, category: com.rescuemesh.app.protocol.EmergencyCategory): Boolean {
        if (role == null) return false
        if (role == com.rescuemesh.app.protocol.NodeRole.NODE_ROLE_COORDINATOR) return true
        if (role == com.rescuemesh.app.protocol.NodeRole.NODE_ROLE_NETWORK_GATEWAY) return true
        
        return when (category) {
            com.rescuemesh.app.protocol.EmergencyCategory.EMERGENCY_CATEGORY_MEDICAL -> role == com.rescuemesh.app.protocol.NodeRole.NODE_ROLE_MEDICAL_RESPONDER
            com.rescuemesh.app.protocol.EmergencyCategory.EMERGENCY_CATEGORY_FIRE -> role == com.rescuemesh.app.protocol.NodeRole.NODE_ROLE_FIRE_RESPONDER
            com.rescuemesh.app.protocol.EmergencyCategory.EMERGENCY_CATEGORY_SECURITY -> role == com.rescuemesh.app.protocol.NodeRole.NODE_ROLE_SEARCH_RESCUE
            com.rescuemesh.app.protocol.EmergencyCategory.EMERGENCY_CATEGORY_TRAPPED -> role == com.rescuemesh.app.protocol.NodeRole.NODE_ROLE_SEARCH_RESCUE
            com.rescuemesh.app.protocol.EmergencyCategory.EMERGENCY_CATEGORY_FLOOD -> role == com.rescuemesh.app.protocol.NodeRole.NODE_ROLE_SEARCH_RESCUE
            else -> false
        }
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

        val localLoc = identityProvider.getLastLocation() // I'll add this to identityProvider
        val battery = identityProvider.getBattery() // I'll add this too

        val payload = ProtocolCodec.encodeControl(
            ProtocolCodec.hello(
                nodeId = localNodeId, 
                role = identityProvider.nodeRole,
                batteryPercentage = battery,
                latitude = localLoc?.latitude,
                longitude = localLoc?.longitude
            )
        )
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
                        // Settle time for Samsung/Xiaomi stability
                        scope.launch {
                            delay(600)
                            Log.d("GATT_CLIENT", "Requesting MTU 512 for $address")
                            if (!gatt.requestMtu(512)) {
                                discoverServices(gatt)
                            }
                        }
                        scope.launch {
                            delay(2_000)
                            if (connections[address] == gatt) {
                                discoverServices(gatt)
                            }
                        }
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
            discoverServices(gatt)
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
            if (status != BluetoothGatt.GATT_SUCCESS) {
                writingMessageKeys.remove(address)?.let(sentMessageKeys::remove)
            } else {
                writingMessageKeys.remove(address)
            }
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
                        peerRolesByAddress[device.address] = message.role
                        Log.i("MESH", "ACK received from ${device.address} with role ${message.role}")
                        _events.tryEmit(
                            BleTransportEvent.AckReceived(
                                device = device,
                                peerNodeId = message.nodeId.toByteArray(),
                                role = message.role,
                                batteryPercentage = message.batteryPercentage,
                                latitude = if (message.hasLocation()) message.location.latitude else null,
                                longitude = if (message.hasLocation()) message.location.longitude else null
                            )
                        )
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
            .onFailure {
                ProtocolCodec.decodeMeshMessage(value)
                    .onSuccess { message ->
                        _events.tryEmit(
                            BleTransportEvent.MessageReceived(
                                device = device,
                                message = message,
                                sourcePeerNodeId = peerNodeIdsByAddress[device.address],
                            ),
                        )
                    }
                    .onFailure { error ->
                        _events.tryEmit(BleTransportEvent.Error(device, "Malformed notification: ${error.message}"))
                    }
            }
    }

    @SuppressLint("MissingPermission")
    private fun discoverServices(gatt: BluetoothGatt) {
        val address = gatt.device.address
        if (!hasConnectPermission() || connections[address] != gatt || !serviceDiscoveryStarted.add(address)) return
        Log.d("GATT_CLIENT", "Discovering services for $address")
        if (!gatt.discoverServices()) {
            serviceDiscoveryStarted.remove(address)
            closeGatt(gatt, "Service discovery could not start")
        }
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt(gatt: BluetoothGatt, reason: String) {
        val address = gatt.device.address
        connections.remove(address)
        peerNodeIdsByAddress.remove(address)
        peerRolesByAddress.remove(address)
        writeQueues.remove(address)
        isWriting.remove(address)
        writingMessageKeys.remove(address)
        serviceDiscoveryStarted.remove(address)
        sentMessageKeys.removeAll { it.startsWith("$address:") }
        watchdogJobs[address]?.cancel()
        watchdogJobs.remove(address)
        backoffUntilElapsedMs[address] = SystemClock.elapsedRealtime() + MeshConfig.RECONNECT_BACKOFF_MS
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
        val CLIENT_CONFIG_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}

private fun ByteArray.toHexKey(): String = joinToString(separator = "") { "%02x".format(it) }
