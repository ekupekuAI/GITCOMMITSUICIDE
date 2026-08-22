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
import com.rescuemesh.app.identity.LocationProvider
import com.rescuemesh.app.identity.NodeIdentityProvider
import com.rescuemesh.app.identity.SecurityProvider
import com.rescuemesh.app.identity.SensorProvider
import com.rescuemesh.app.identity.toDisplayNodeId
import com.rescuemesh.app.identity.toHex
import com.rescuemesh.app.protocol.MeshMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MeshCoordinator(
    private val identityProvider: NodeIdentityProvider,
    private val securityProvider: SecurityProvider,
    private val locationProvider: LocationProvider,
    private val sensorProvider: SensorProvider,
    private val advertiser: BleAdvertiser,
    private val scanner: BleScanner,
    private val gattServer: GattServer,
    private val gattClient: GattClient,
    private val repository: MessageRepository,
    private val scope: CoroutineScope,
) {
    private val neighbors = MutableNeighborTable()
    private val performanceTracker = PerformanceTracker()
    
    private val _powerMode = MutableStateFlow(MeshConfig.PowerMode.NORMAL)
    val powerMode: StateFlow<MeshConfig.PowerMode> = _powerMode

    val uiState: StateFlow<MeshDiscoveryUiState> = combine(
        advertiser.state,
        scanner.state,
        neighbors.state,
        repository.observeMessages(),
        performanceTracker.stats,
        _powerMode,
        sensorProvider.impactDetected,
        repository.observeIncidents()
    ) { args: Array<Any> ->
        val advertiserState = args[0] as AdvertiserState
        val scannerState = args[1] as ScannerState
        val neighborRows = args[2] as List<NeighborUiModel>
        val messages = args[3] as List<com.rescuemesh.app.data.MessageEntity>
        val stats = args[4] as PerformanceStats
        val mode = args[5] as MeshConfig.PowerMode
        val impact = args[6] as Boolean
        val incidents = args[7] as List<com.rescuemesh.app.data.IncidentEntity>

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
                    priority = message.priority,
                    latitude = message.latitude,
                    longitude = message.longitude
                )
            },
            performanceStats = stats,
            currentPowerMode = mode,
            impactDetected = impact,
            incidents = incidents
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
            performanceStats = PerformanceStats(),
            currentPowerMode = MeshConfig.PowerMode.NORMAL,
            impactDetected = false,
            incidents = emptyList()
        ),
    )

    init {
        scope.launch {
            repository.ensureNodeIdentity(identityProvider.nodeId)
        }
        scope.launch {
            scanner.observations.collect { observation ->
                neighbors.record(observation.device, observation.rssi, observation.observedAtElapsedMs)
                performanceTracker.recordDiscovery()
                
                val myPseudoId = identityProvider.nodeId.toHex()
                val peerPseudoId = observation.device.address.replace(":", "").lowercase()
                
                if (myPseudoId > peerPseudoId) {
                    if (neighbors.shouldAttemptConnection(observation.device.address)) {
                        Log.d("MESH", "Arbitration WON: Initiating connection to ${observation.device.address}")
                        gattClient.connect(observation.device)
                    }
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

    fun setPowerMode(mode: MeshConfig.PowerMode) {
        _powerMode.value = mode
        if (scanner.state.value == ScannerState.Scanning) {
            stopDiscovery()
            startDiscovery()
        }
    }

    fun startDiscovery() {
        sensorProvider.start()
        gattServer.start()
        advertiser.start(_powerMode.value)
        scanner.start(_powerMode.value)
    }

    fun stopDiscovery() {
        scanner.stop()
        advertiser.stop()
        gattClient.close()
        gattServer.stop()
        sensorProvider.stop()
    }

    fun refreshNeighborLiveness() {
        neighbors.refreshLiveness()
    }

    fun createSos(
        text: String, 
        priority: Int = 0, 
        category: com.rescuemesh.app.protocol.EmergencyCategory = com.rescuemesh.app.protocol.EmergencyCategory.EMERGENCY_CATEGORY_UNSPECIFIED
    ) {
        scope.launch {
            val location = locationProvider.getCurrentLocation()
            val impact = sensorProvider.impactDetected.value
            val message = SosFactory.create(
                originNodeId = identityProvider.nodeId,
                text = text,
                securityProvider = securityProvider,
                category = category,
                originRole = identityProvider.nodeRole,
                location = location,
                impactDetected = impact,
                priority = priority
            )
            performanceTracker.trackMessageStart(message.messageId.toByteArray().toHex())
            val inserted = repository.persistCreatedMessage(message)
            if (inserted) {
                val forwards = gattClient.sendMeshMessage(message)
                Log.i("ROUTE", "Initial route selected for SOS: forwards_started=$forwards")
            }
        }
    }

    private suspend fun handleTransportEvent(event: BleTransportEvent) {
        when (event) {
            is BleTransportEvent.Connected -> {
                performanceTracker.recordConnection(true)
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
                val message = event.message
                
                // Replay protection (Phase 8)
                val now = System.currentTimeMillis()
                if (message.createdAtMs < now - MeshConfig.SOS_LIFETIME_MS || message.createdAtMs > now + 60_000) {
                    Log.w("SECURITY", "Dropping stale/future message")
                    return
                }

                // Integrity Check: Verify signature
                val isValid = if (message.signature.size() > 0 && message.publicKey.size() > 0) {
                    val unsignedBytes = message.toBuilder().clearSignature().build().toByteArray()
                    securityProvider.verify(
                        message.publicKey.toByteArray(),
                        unsignedBytes,
                        message.signature.toByteArray()
                    )
                } else false

                if (!isValid) {
                    Log.e("SECURITY", "Invalid signature or unsigned message from ${event.device.address}")
                    return
                }

                val result = repository.persistReceivedMessage(
                    message = message,
                    sourceNodeId = event.sourcePeerNodeId ?: message.originNodeId.toByteArray(),
                )
                when (result) {
                    is ReceiveResult.Accepted -> {
                        gattServer.acknowledgeMessagePersisted(event.device, message.messageId.toByteArray())
                        if (result.relayCopy.ttl > 0) {
                            gattClient.sendMeshMessage(
                                message = result.relayCopy,
                                excludePeerNodeId = event.sourcePeerNodeId,
                            )
                        }
                    }
                    ReceiveResult.Duplicate -> {
                        performanceTracker.recordDuplicateRejected()
                        gattServer.acknowledgeMessagePersisted(event.device, message.messageId.toByteArray())
                    }
                    else -> Unit
                }
            }
            is BleTransportEvent.MessageAckReceived -> {
                repository.markRelayed(event.messageId)
                performanceTracker.recordMessageDelivered(event.messageId.toHex())
            }
            is BleTransportEvent.Error -> performanceTracker.recordConnection(false)
            else -> Unit
        }
    }

    private suspend fun flushPendingMessages() {
        val nowMs = System.currentTimeMillis()
        repository.pendingMessages()
            .forEach { entity ->
                if (ForwardingPolicy.isForwardable(entity, nowMs)) {
                    val message = entity.toProto()
                    gattClient.sendMeshMessage(
                        message = message,
                        excludePeerNodeId = entity.previousHopNodeId
                    )
                }
            }
    }
}

data class MeshDiscoveryUiState(
    val nodeId: String,
    val advertiserState: AdvertiserState,
    val scannerState: ScannerState,
    val neighbors: List<NeighborUiModel>,
    val sosMessages: List<SosUiModel>,
    val performanceStats: PerformanceStats,
    val currentPowerMode: MeshConfig.PowerMode,
    val impactDetected: Boolean,
    val incidents: List<com.rescuemesh.app.data.IncidentEntity>
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
    val priority: Int,
    val latitude: Double?,
    val longitude: Double?
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
    val role: com.rescuemesh.app.protocol.NodeRole? = null
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
                role = event.role
            )
            is BleTransportEvent.AckReceived -> previous.copyOrPlaceholder(key).copy(
                connectionState = "ACK_RECEIVED",
                peerNodeId = event.peerNodeId.toDisplayNodeId(),
                peerNodeIdBytes = event.peerNodeId,
                lastPacketState = "ACK received",
                role = event.role
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
            ageMs <= MeshConfig.NEIGHBOR_STALE_TIMEOUT_MS -> NeighborLiveness.Active
            ageMs <= MeshConfig.NEIGHBOR_LOST_TIMEOUT_MS -> NeighborLiveness.Stale
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
