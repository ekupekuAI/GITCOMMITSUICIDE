package com.rescuemesh.app.data

import android.util.Log
import com.rescuemesh.app.protocol.MeshMessage
import com.rescuemesh.app.mesh.RelayPolicy
import kotlinx.coroutines.flow.Flow

class MessageRepository(
    private val dao: MessageDao,
) {
    fun observeMessages(): Flow<List<MessageEntity>> = dao.observeMessages()

    fun observeNeighbors(): Flow<List<NeighborEntity>> = dao.observeNeighbors()

    fun observeIncidents(): Flow<List<IncidentEntity>> = dao.observeIncidents()

    suspend fun ensureNodeIdentity(nodeId: ByteArray, nowMs: Long = System.currentTimeMillis()) {
        dao.upsertIdentity(
            NodeIdentityEntity(
                nodeId = nodeId,
                createdAtMs = nowMs,
                keyVersion = 1,
            ),
        )
    }

    suspend fun clearLocalMeshData() {
        dao.clearForwardAttempts()
        dao.clearMessages()
        dao.clearReceipts()
        dao.clearNeighbors()
        dao.clearIncidents()
    }

    suspend fun upsertKnownNeighbor(
        nodeId: ByteArray,
        rssi: Int,
        state: String,
        protocolVersion: Int,
        batteryPercentage: Int? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        dao.upsertNeighbor(
            NeighborEntity(
                nodeId = nodeId,
                lastSeenAtMs = nowMs,
                lastRssiDbm = rssi,
                state = state,
                protocolVersion = protocolVersion,
                lastConnectedAtMs = nowMs,
                connectSuccesses = if (state == "CONNECTED") 1 else 0,
                connectFailures = if (state == "DISCONNECTED" || state == "ERROR") 1 else 0,
                batteryPercentage = batteryPercentage,
                latitude = latitude,
                longitude = longitude
            ),
        )
    }

    suspend fun persistCreatedMessage(message: MeshMessage, nowMs: Long = System.currentTimeMillis()): Boolean {
        val entity = message.toEntity(
            state = "PERSISTED",
            receivedAtMs = nowMs,
            previousHopNodeId = null
        )
        val receipt = MessageReceiptEntity(
            messageId = message.messageId.toByteArray(),
            firstSeenAtMs = nowMs,
            sourceNodeId = message.originNodeId.toByteArray(),
            processed = true,
        )
        val inserted = dao.acceptNewMessage(receipt, entity)
        if (inserted) {
            Log.i("ROOM", "Created message persisted")
        }
        return inserted
    }

    suspend fun persistReceivedMessage(
        message: MeshMessage,
        sourceNodeId: ByteArray,
        nowMs: Long = System.currentTimeMillis(),
    ): ReceiveResult {
        val relayCopy = RelayPolicy.acceptedRelayCopy(message, nowMs)
            ?: return ReceiveResult.Rejected("expired_or_ttl_exhausted")
        val receipt = MessageReceiptEntity(
            messageId = message.messageId.toByteArray(),
            firstSeenAtMs = nowMs,
            sourceNodeId = sourceNodeId,
            processed = true,
        )
        val entity = relayCopy.toEntity(
            state = "QUEUED",
            receivedAtMs = nowMs,
            previousHopNodeId = sourceNodeId
        )
        val inserted = dao.acceptNewMessage(
            receipt = receipt,
            message = entity,
        )
        if (inserted) {
            Log.i("ROOM", "Received message persisted")
            aggregateMessageIntoIncident(entity)
            return ReceiveResult.Accepted(relayCopy)
        } else {
            Log.i("ROOM", "Duplicate receipt ignored")
            return ReceiveResult.Duplicate
        }
    }

    private suspend fun aggregateMessageIntoIncident(message: MessageEntity) {
        if (message.latitude == null || message.longitude == null) return
        
        val threshold = 0.001 // Approx 100m
        val existing = dao.findMatchingIncident(
            category = message.category,
            latMin = message.latitude - threshold,
            latMax = message.latitude + threshold,
            lonMin = message.longitude - threshold,
            lonMax = message.longitude + threshold
        )

        if (existing != null) {
            dao.upsertIncident(existing.copy(
                corroboratingCount = existing.corroboratingCount + 1,
                lastUpdatedAtMs = message.receivedAtMs,
                summary = "${existing.summary.take(100)}... (+${existing.corroboratingCount} more reports)"
            ))
        } else {
            dao.upsertIncident(IncidentEntity(
                category = message.category,
                latitude = message.latitude,
                longitude = message.longitude,
                corroboratingCount = 1,
                firstSeenAtMs = message.receivedAtMs,
                lastUpdatedAtMs = message.receivedAtMs,
                summary = message.payload.decodeToString()
            ))
        }
    }

    suspend fun pendingMessages(nowMs: Long = System.currentTimeMillis()): List<MessageEntity> {
        return dao.pendingMessages(nowMs)
    }

    suspend fun markRelayed(messageId: ByteArray, nowMs: Long = System.currentTimeMillis()) {
        dao.markForwarded(messageId, "RELAYED", nowMs)
    }

    suspend fun runMaintenance(nowMs: Long = System.currentTimeMillis()) {
        // Delete messages older than 24h or expired
        dao.deleteExpiredMessages(nowMs)
        
        // Delete neighbors not seen for more than 2 hours
        val staleThreshold = nowMs - (2 * 60 * 60 * 1000L)
        dao.deleteStaleNeighbors(staleThreshold)
        
        // Delete old receipts to keep database small
        dao.deleteOldReceipts(staleThreshold)
        
        // Delete old incidents
        dao.deleteOldIncidents(staleThreshold)
    }
}

sealed interface ReceiveResult {
    data class Accepted(val relayCopy: MeshMessage) : ReceiveResult
    data object Duplicate : ReceiveResult
    data class Rejected(val reason: String) : ReceiveResult
}

fun MessageEntity.toProto(): MeshMessage {
    val builder = MeshMessage.newBuilder()
        .setMessageId(com.google.protobuf.ByteString.copyFrom(messageId))
        .setOriginNodeId(com.google.protobuf.ByteString.copyFrom(originNodeId))
        .setDestinationId(com.google.protobuf.ByteString.copyFrom(destinationId ?: ByteArray(0)))
        .setMessageType(com.rescuemesh.app.protocol.MessageType.forNumber(type))
        .setPriority(priority)
        .setTtl(ttl)
        .setHopCount(hopCount)
        .setCreatedAtMs(createdAtMs)
        .setExpiresAtMs(expiresAtMs)
        .setPayloadVersion(payloadVersion)
        .setPayload(com.google.protobuf.ByteString.copyFrom(payload))
        .setSignature(com.google.protobuf.ByteString.copyFrom(signature ?: ByteArray(0)))
        .setPublicKey(com.google.protobuf.ByteString.copyFrom(publicKey ?: ByteArray(0)))
        .setCategory(com.rescuemesh.app.protocol.EmergencyCategory.forNumber(category))
        .setOriginRole(com.rescuemesh.app.protocol.NodeRole.forNumber(originRole))

    if (latitude != null && longitude != null) {
        builder.setLocation(
            com.rescuemesh.app.protocol.Location.newBuilder()
                .setLatitude(latitude)
                .setLongitude(longitude)
                .setAccuracy(locationAccuracy ?: 0f)
                .build()
        )
    }
    
    return builder.build()
}

fun MeshMessage.toEntity(
    state: String,
    receivedAtMs: Long,
    previousHopNodeId: ByteArray? = null
): MessageEntity {
    return MessageEntity(
        messageId = messageId.toByteArray(),
        originNodeId = originNodeId.toByteArray(),
        destinationId = destinationId.takeIf { !it.isEmpty }?.toByteArray(),
        type = messageType.number,
        priority = priority,
        ttl = ttl,
        hopCount = hopCount,
        createdAtMs = createdAtMs,
        expiresAtMs = expiresAtMs,
        payloadVersion = payloadVersion,
        payload = payload.toByteArray(),
        signature = if (signature.size() > 0) signature.toByteArray() else null,
        state = state,
        receivedAtMs = receivedAtMs,
        lastForwardedAtMs = null,
        previousHopNodeId = previousHopNodeId,
        latitude = if (hasLocation()) location.latitude else null,
        longitude = if (hasLocation()) location.longitude else null,
        locationAccuracy = if (hasLocation()) location.accuracy else null,
        publicKey = if (publicKey.size() > 0) publicKey.toByteArray() else null,
        category = category.number,
        originRole = originRole.number
    )
}
