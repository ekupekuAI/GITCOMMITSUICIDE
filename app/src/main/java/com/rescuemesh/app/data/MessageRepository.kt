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

    suspend fun ensureNodeIdentity(nodeId: ByteArray, nowMs: Long = System.currentTimeMillis()) {
        dao.upsertIdentity(
            NodeIdentityEntity(
                nodeId = nodeId,
                createdAtMs = nowMs,
                keyVersion = 1,
            ),
        )
    }

    suspend fun upsertKnownNeighbor(
        nodeId: ByteArray,
        rssi: Int,
        state: String,
        protocolVersion: Int,
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
            ),
        )
    }

    suspend fun persistCreatedMessage(message: MeshMessage, nowMs: Long = System.currentTimeMillis()): Boolean {
        val entity = message.toEntity(
            state = "PERSISTED",
            receivedAtMs = nowMs,
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
        val inserted = dao.acceptNewMessage(
            receipt = receipt,
            message = relayCopy.toEntity(
                state = "QUEUED",
                receivedAtMs = nowMs,
            ),
        )
        return if (inserted) {
            Log.i("ROOM", "Received message persisted")
            ReceiveResult.Accepted(relayCopy)
        } else {
            Log.i("ROOM", "Duplicate receipt ignored")
            ReceiveResult.Duplicate
        }
    }

    suspend fun pendingMessages(nowMs: Long = System.currentTimeMillis()): List<MessageEntity> {
        return dao.pendingMessages(nowMs)
    }

    suspend fun markRelayed(messageId: ByteArray, nowMs: Long = System.currentTimeMillis()) {
        dao.markForwarded(messageId, "RELAYED", nowMs)
    }
}

sealed interface ReceiveResult {
    data class Accepted(val relayCopy: MeshMessage) : ReceiveResult
    data object Duplicate : ReceiveResult
    data class Rejected(val reason: String) : ReceiveResult
}

fun MessageEntity.toProto(): MeshMessage {
    return MeshMessage.newBuilder()
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
        .build()
}

fun MeshMessage.toEntity(
    state: String,
    receivedAtMs: Long,
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
        signature = signature.takeIf { !it.isEmpty }?.toByteArray(),
        state = state,
        receivedAtMs = receivedAtMs,
        lastForwardedAtMs = null,
    )
}
