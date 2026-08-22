package com.rescuemesh.app.ble

import android.bluetooth.BluetoothDevice
import com.rescuemesh.app.protocol.MeshMessage
import com.rescuemesh.app.protocol.NodeRole

sealed interface BleTransportEvent {
    data class Connected(val device: BluetoothDevice) : BleTransportEvent
    data class Disconnected(val device: BluetoothDevice, val reason: String) : BleTransportEvent
    data class HelloReceived(
        val device: BluetoothDevice, 
        val peerNodeId: ByteArray,
        val role: NodeRole
    ) : BleTransportEvent
    data class HelloSent(val device: BluetoothDevice) : BleTransportEvent
    data class AckReceived(
        val device: BluetoothDevice, 
        val peerNodeId: ByteArray,
        val role: NodeRole
    ) : BleTransportEvent
    data class MessageReceived(
        val device: BluetoothDevice,
        val message: MeshMessage,
        val sourcePeerNodeId: ByteArray?,
    ) : BleTransportEvent
    data class MessageSent(val device: BluetoothDevice, val messageId: ByteArray) : BleTransportEvent
    data class MessageAckReceived(val device: BluetoothDevice, val messageId: ByteArray) : BleTransportEvent
    data class Error(val device: BluetoothDevice?, val reason: String) : BleTransportEvent
}
