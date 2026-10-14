package com.rescuemesh.app.mesh

import com.google.protobuf.ByteString
import com.rescuemesh.app.identity.SecurityProvider
import com.rescuemesh.app.protocol.EmergencyCategory
import com.rescuemesh.app.protocol.MessageType
import com.rescuemesh.app.protocol.MeshMessage
import com.rescuemesh.app.protocol.NodeRole
import java.security.SecureRandom

enum class ResponderStatus {
    ACKNOWLEDGED,
    RESPONDING,
}

data class ParsedResponderResponse(
    val sosMessageIdHex: String,
    val status: ResponderStatus,
    val message: String,
)

object EmergencyResponseFactory {
    fun create(
        responderNodeId: ByteArray,
        victimNodeId: ByteArray,
        sosMessageId: ByteArray,
        status: ResponderStatus,
        responseText: String,
        role: NodeRole,
        securityProvider: SecurityProvider,
        nowMs: Long = System.currentTimeMillis(),
    ): MeshMessage {
        // Keep the whole payload (header + details) within the same budget as SOS text;
        // a 220-char response previously overflowed the 500-byte wire frame and crashed.
        val header = "RESPONSE|${status.name}|${sosMessageId.toHexKey()}|"
        val details = responseText.trim()
            .truncateUtf8(MAX_PAYLOAD_TEXT_BYTES - header.encodeToByteArray().size)
        val encodedPayload = header + details
        val builder = MeshMessage.newBuilder()
            .setMessageId(ByteString.copyFrom(randomId()))
            .setOriginNodeId(ByteString.copyFrom(responderNodeId))
            .setDestinationId(ByteString.copyFrom(victimNodeId))
            .setMessageType(MessageType.MESSAGE_TYPE_TEXT)
            .setPriority(3)
            .setTtl(8)
            .setHopCount(0)
            .setCreatedAtMs(nowMs)
            .setExpiresAtMs(nowMs + MeshConfig.SOS_LIFETIME_MS)
            .setPayloadVersion(1)
            .setPayload(ByteString.copyFromUtf8(encodedPayload))
            .setPublicKey(ByteString.copyFrom(securityProvider.getPublicKey()))
            .setCategory(EmergencyCategory.EMERGENCY_CATEGORY_OTHER)
            .setOriginRole(role)
        val unsigned = builder.build()
        return unsigned.toBuilder()
            .setSignature(ByteString.copyFrom(securityProvider.sign(unsigned.toByteArray())))
            .build()
    }

    fun parse(message: MeshMessage): ParsedResponderResponse? {
        if (message.messageType != MessageType.MESSAGE_TYPE_TEXT) return null
        val value = message.payload.toStringUtf8()
        val parts = value.split("|", limit = 4)
        if (parts.size < 4 || parts[0] != "RESPONSE") return null
        val status = runCatching { ResponderStatus.valueOf(parts[1]) }.getOrNull() ?: return null
        return ParsedResponderResponse(parts[2], status, parts[3])
    }

    private fun randomId(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }
}

private fun ByteArray.toHexKey(): String = joinToString(separator = "") { "%02x".format(it) }