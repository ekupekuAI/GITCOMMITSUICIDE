package com.rescuemesh.app.mesh

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.SystemClock
import android.util.Log
import com.rescuemesh.app.ble.AdvertiserState
import com.rescuemesh.app.ble.BleAdvertiser
import com.rescuemesh.app.ble.BleScanner
import com.rescuemesh.app.ble.BleTransportEvent
import com.rescuemesh.app.ble.GattClient
import com.rescuemesh.app.ble.GattServer
import com.rescuemesh.app.ble.ScannerState
import com.rescuemesh.app.data.MessageRepository
import com.rescuemesh.app.data.ReceiveResult
import com.rescuemesh.app.data.toProto
import com.rescuemesh.app.identity.NodeIdentityProvider
import com.rescuemesh.app.identity.toDisplayNodeId
import com.rescuemesh.app.identity.toHex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MeshCoordinator(
    private val identityProvider: NodeIdentityProvider,
    private val advertiser: BleAdvertiser,
    private val scanner: BleScanner,
    private val gattServer: GattServer,
    private val gattClient: GattClient,
    private val repository: MessageRepository,
    private val scope: CoroutineScope,
) {
    private val neighbors = MutableNeighborTable()

    val uiState: StateFlow<MeshDiscoveryUiState> = combine(
        advertiser.state,
        scanner.state,
        neighbors.state,
        repository.observeMessages(),
    ) { advertiserState, scannerState, neighborRows, messages ->
        MeshDiscoveryUiState(
            nodeId = identityProvider.displayId,
            advertiserState = advertiserState,
            scannerState = scannerState,
            neighbors = neighborRows.sortedByDescending { it.lastSeenElapsedMs },
            sosMessages = messages.map { message ->
                SosUiModel(
                    id = message.messageId.toDisplayNodeId(),
                    text = message.payload.decodeToString(),
                    state = message.state,
                    ttl = message.ttl,
                    hopCount = message.hopCount,
                )
            },
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MeshDiscoveryUiState(
            nodeId = identityProvider.displayId,
            advertiserState = AdvertiserState.Idle,
            scannerState = ScannerState.Idle,
            neighbors = emptyList(),
            sosMessages = emptyList(),
        ),
    )

    init {
        scope.launch {
            repository.ensureNodeIdentity(identityProvider.nodeId)
        }
        scope.launch {
            scanner.observations.collect { observation ->
                neighbors.record(observation.device, observation.rssi, observation.observedAtElapsedMs)
                
                // Arbitration: Only connect if my ID is higher than the peer's Bluetooth address
                // (Using address because we don't know their Node ID yet)
                val myPseudoId = identityProvider.nodeId.toHex()
                val peerPseudoId = observation.device.address.replace(":", "").lowercase()
                
                if (myPseudoId > peerPseudoId) {
                    if (neighbors.shouldAttemptConnection(observation.device.address)) {
                        Log.d("MESH", "Arbitration WON: Initiating connection to ${observation.device.address}")
                        gattClient.connect(observation.device)
                    }
                } else {
                    Log.v("MESH", "Arbitration LOST: Waiting for ${observation.device.address} to connect")
                }
            }
        }
        scope.launch {
            gattClient.events.collect { event ->
                neighbors.recordTransportEvent(event)
                handleTransportEvent(event)
            }
        }
        scope.launch {
            gattServer.events.collect { event ->
                neighbors.recordTransportEvent(event)
                handleTransportEvent(event)
            }
        }
        // Periodic cleanup and maintenance
        scope.launch {
            while (true) {
                delay(5_000)
                neighbors.refreshLiveness()
                cleanupStaleNeighbors()
                flushPendingMessages()
            }
        }
    }

    private suspend fun cleanupStaleNeighbors() {
        val lostNeighbors = neighbors.state.value.filter { it.liveness == NeighborLiveness.Lost }
        lostNeighbors.forEach { neighbor ->
            neighbor.peerNodeIdBytes?.let { nodeId ->
                repository.upsertKnownNeighbor(
                    nodeId = nodeId,
                    rssi = -127,
                    state = "LOST",
                    protocolVersion = 1
                )
            }
        }
    }

    fun startDiscovery() {
        gattServer.start()
        advertiser.start()
        scanner.start()
    }

    fun stopDiscovery() {
        scanner.stop()
        advertiser.stop()
        gattClient.close()
        gattServer.stop()
    }

    fun refreshNeighborLiveness() {
        neighbors.refreshLiveness()
    }

    fun createSos(text: String) {
        scope.launch {
            val message = SosFactory.create(
                originNodeId = identityProvider.nodeId,
                text = text,
            )
            val inserted = repository.persistCreatedMessage(message)
            if (inserted) {
                Log.i("SOS", "Message created and persisted: ${message.messageId.toByteArray().toDisplayNodeId()}")
                val forwards = gattClient.sendMeshMessage(message)
                Log.i("ROUTE", "Initial route selected for SOS: forwards_started=$forwards")
            }
        }
    }

    private suspend fun handleTransportEvent(event: BleTransportEvent) {
        when (event) {
            is BleTransportEvent.Connected -> {
                Log.d("MESH", "Connected to ${event.device.address}, flushing messages")
                flushPendingMessages()
            }
            is BleTransportEvent.Disconnected -> {
                val peerNodeId = neighbors.peerNodeIdBytesFor(event.device.address)
                if (peerNodeId != null) {
                    repository.upsertKnownNeighbor(
                        nodeId = peerNodeId,
                        rssi = neighbors.rssiFor(event.device.address),
                        state = "DISCONNECTED",
                        protocolVersion = 1,
                    )
                    Log.i("MESH", "Known peer unavailable: ${peerNodeId.toDisplayNodeId()}")
                }
            }
            is BleTransportEvent.AckReceived -> {
                repository.upsertKnownNeighbor(
                    nodeId = event.peerNodeId,
                    rssi = neighbors.rssiFor(event.device.address),
                    state = "CONNECTED",
                    protocolVersion = 1,
                )
                flushPendingMessages()
            }
            is BleTransportEvent.HelloReceived -> {
                repository.upsertKnownNeighbor(
                    nodeId = event.peerNodeId,
                    rssi = neighbors.rssiFor(event.device.address),
                    state = "CONNECTED",
                    protocolVersion = 1,
                )
                flushPendingMessages()
            }
            is BleTransportEvent.MessageReceived -> {
                val result = repository.persistReceivedMessage(
                    message = event.message,
                    sourceNodeId = event.sourcePeerNodeId ?: event.message.originNodeId.toByteArray(),
                )
                when (result) {
                    is ReceiveResult.Accepted -> {
                        Log.i("ROOM", "Message persisted: ${event.message.messageId.toByteArray().toDisplayNodeId()}")
                        gattServer.acknowledgeMessagePersisted(event.device, event.message.messageId.toByteArray())
                        if (result.relayCopy.ttl > 0) {
                            val forwards = gattClient.sendMeshMessage(
                                message = result.relayCopy,
                                excludePeerNodeId = event.sourcePeerNodeId,
                            )
                            if (forwards == 0) {
                                Log.i("ROUTE", "No suitable next hop; message remains queued")
                            }
                        }
                    }
                    ReceiveResult.Duplicate -> {
                        Log.i("MESH", "Duplicate rejected: ${event.message.messageId.toByteArray().toDisplayNodeId()}")
                        gattServer.acknowledgeMessagePersisted(event.device, event.message.messageId.toByteArray())
                    }
                    is ReceiveResult.Rejected -> {
                        Log.i("MESH", "Message rejected: ${result.reason}")
                    }
                }
            }
            is BleTransportEvent.MessageAckReceived -> {
                repository.markRelayed(event.messageId)
                Log.i("SOS", "Message delivered/relayed: ${event.messageId.toDisplayNodeId()}")
            }
            is BleTransportEvent.Error -> {
                Log.w("MESH", "Transport error with ${event.device?.address}: ${event.reason}")
            }
            else -> Unit
        }
    }

    private suspend fun flushPendingMessages() {
        val nowMs = System.currentTimeMillis()
        repository.pendingMessages()
            .filter { ForwardingPolicy.isForwardable(it, nowMs) }
            .map { it.toProto() }
            .forEach { message ->
                val forwards = gattClient.sendMeshMessage(message)
                Log.i("ROUTE", "Queued message flush: ${message.messageId.toByteArray().toDisplayNodeId()} forwards_started=$forwards")
            }
    }
}

data class MeshDiscoveryUiState(
    val nodeId: String,
    val advertiserState: AdvertiserState,
    val scannerState: ScannerState,
    val neighbors: List<NeighborUiModel>,
    val sosMessages: List<SosUiModel>,
) {
    val activeNeighborCount: Int
        get() = neighbors.count { it.liveness == NeighborLiveness.Active }
}

data class SosUiModel(
    val id: String,
    val text: String,
    val state: String,
    val ttl: Int,
    val hopCount: Int,
)

data class NeighborUiModel(
    val sessionId: String,
    val name: String?,
    val rssi: Int,
    val lastSeenElapsedMs: Long,
    val liveness: NeighborLiveness,
    val connectionState: String,
    val peerNodeId: String?,
    val peerNodeIdBytes: ByteArray?,
    val lastPacketState: String?,
)

enum class NeighborLiveness {
    Active,
    Stale,
    Lost,
}

private class MutableNeighborTable {
    private val rows = kotlinx.coroutines.flow.MutableStateFlow<List<NeighborUiModel>>(emptyList())
    val state: StateFlow<List<NeighborUiModel>> = rows

    @SuppressLint("MissingPermission")
    fun record(device: BluetoothDevice, rssi: Int, observedAtElapsedMs: Long) {
        val key = device.address ?: device.hashCode().toString()
        val current = rows.value.toMutableList()
        val index = current.indexOfFirst { it.sessionId == key }
        val previous = current.getOrNull(index)
        val next = NeighborUiModel(
            sessionId = key,
            name = device.name,
            rssi = rssi,
            lastSeenElapsedMs = observedAtElapsedMs,
            liveness = liveness(SystemClock.elapsedRealtime() - observedAtElapsedMs),
            connectionState = previous?.connectionState ?: "DISCOVERED",
            peerNodeId = previous?.peerNodeId,
            peerNodeIdBytes = previous?.peerNodeIdBytes,
            lastPacketState = previous?.lastPacketState,
        )
        if (index >= 0) {
            current[index] = next
        } else {
            current += next
        }
        rows.value = current
    }

    fun shouldAttemptConnection(address: String): Boolean {
        val neighbor = rows.value.find { it.sessionId == address } ?: return true
        return neighbor.connectionState !in setOf("CONNECTED", "HELLO_SENT", "HELLO_RECEIVED", "ACK_RECEIVED")
    }

    fun recordTransportEvent(event: BleTransportEvent) {
        val key = when (event) {
            is BleTransportEvent.AckReceived -> event.device.address
            is BleTransportEvent.Connected -> event.device.address
            is BleTransportEvent.Disconnected -> event.device.address
            is BleTransportEvent.Error -> event.device?.address
            is BleTransportEvent.HelloReceived -> event.device.address
            is BleTransportEvent.HelloSent -> event.device.address
            is BleTransportEvent.MessageAckReceived -> event.device.address
            is BleTransportEvent.MessageReceived -> event.device.address
            is BleTransportEvent.MessageSent -> event.device.address
        } ?: return

        val current = rows.value.toMutableList()
        val index = current.indexOfFirst { it.sessionId == key }
        val previous = current.getOrNull(index)
        val next = when (event) {
            is BleTransportEvent.Connected -> previous.copyOrPlaceholder(key).copy(
                connectionState = "CONNECTED",
                lastPacketState = "GATT connected",
            )
            is BleTransportEvent.Disconnected -> previous.copyOrPlaceholder(key).copy(
                connectionState = "DISCONNECTED",
                lastPacketState = event.reason,
            )
            is BleTransportEvent.HelloSent -> previous.copyOrPlaceholder(key).copy(
                connectionState = "HELLO_SENT",
                lastPacketState = "HELLO written",
            )
            is BleTransportEvent.HelloReceived -> previous.copyOrPlaceholder(key).copy(
                connectionState = "HELLO_RECEIVED",
                peerNodeId = event.peerNodeId.toDisplayNodeId(),
                peerNodeIdBytes = event.peerNodeId,
                lastPacketState = "HELLO received",
            )
            is BleTransportEvent.AckReceived -> previous.copyOrPlaceholder(key).copy(
                connectionState = "ACK_RECEIVED",
                peerNodeId = event.peerNodeId.toDisplayNodeId(),
                peerNodeIdBytes = event.peerNodeId,
                lastPacketState = "ACK notification received",
            )
            is BleTransportEvent.Error -> previous.copyOrPlaceholder(key).copy(
                connectionState = "ERROR",
                lastPacketState = event.reason,
            )
            is BleTransportEvent.MessageReceived -> previous.copyOrPlaceholder(key).copy(
                lastPacketState = "SOS DATA received",
            )
            is BleTransportEvent.MessageSent -> previous.copyOrPlaceholder(key).copy(
                lastPacketState = "SOS DATA sent",
            )
            is BleTransportEvent.MessageAckReceived -> previous.copyOrPlaceholder(key).copy(
                lastPacketState = "SOS ACK received",
            )
        }

        if (index >= 0) {
            current[index] = next
        } else {
            current += next
        }
        rows.value = current
    }

    fun refreshLiveness() {
        val now = SystemClock.elapsedRealtime()
        rows.value = rows.value.map { row ->
            row.copy(liveness = liveness(now - row.lastSeenElapsedMs))
        }
    }

    fun rssiFor(sessionId: String?): Int {
        return rows.value.firstOrNull { it.sessionId == sessionId }?.rssi ?: 0
    }

    fun peerNodeIdBytesFor(sessionId: String?): ByteArray? {
        return rows.value.firstOrNull { it.sessionId == sessionId }?.peerNodeIdBytes
    }

    private fun liveness(ageMs: Long): NeighborLiveness {
        return when {
            ageMs <= 5_000 -> NeighborLiveness.Active
            ageMs <= 15_000 -> NeighborLiveness.Stale
            else -> NeighborLiveness.Lost
        }
    }

    private fun NeighborUiModel?.copyOrPlaceholder(key: String): NeighborUiModel {
        return this ?: NeighborUiModel(
            sessionId = key,
            name = null,
            rssi = 0,
            lastSeenElapsedMs = SystemClock.elapsedRealtime(),
            liveness = NeighborLiveness.Active,
            connectionState = "DISCOVERED",
            peerNodeId = null,
            peerNodeIdBytes = null,
            lastPacketState = null,
        )
    }
}
