package com.rescuemesh.app.mesh

import com.google.protobuf.ByteString
import com.rescuemesh.app.protocol.MeshMessage
import com.rescuemesh.app.protocol.MessageType
import java.security.SecureRandom

object SosFactory {
    private const val DEFAULT_TTL = 8
    private const val DEFAULT_LIFETIME_MS = 30 * 60 * 1_000L

    fun create(
        originNodeId: ByteArray,
        text: String,
        nowMs: Long = System.currentTimeMillis(),
    ): MeshMessage {
        require(text.isNotBlank()) { "SOS text cannot be blank" }
        require(text.encodeToByteArray().size <= 220) { "SOS text is too large" }

        return MeshMessage.newBuilder()
            .setMessageId(ByteString.copyFrom(randomId()))
            .setOriginNodeId(ByteString.copyFrom(originNodeId))
            .setDestinationId(ByteString.EMPTY)
            .setMessageType(MessageType.MESSAGE_TYPE_SOS)
            .setPriority(0)
            .setTtl(DEFAULT_TTL)
            .setHopCount(0)
            .setCreatedAtMs(nowMs)
            .setExpiresAtMs(nowMs + DEFAULT_LIFETIME_MS)
            .setPayloadVersion(1)
            .setPayload(ByteString.copyFrom(text.encodeToByteArray()))
            .setSignature(ByteString.EMPTY)
            .build()
    }

    private fun randomId(): ByteArray {
        return ByteArray(16).also { SecureRandom().nextBytes(it) }
    }
}
