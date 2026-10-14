package com.rescuemesh.app.mesh

import com.google.protobuf.ByteString
import com.rescuemesh.app.protocol.MeshMessage
import com.rescuemesh.app.protocol.MessageType
import com.rescuemesh.app.protocol.Location
import com.rescuemesh.app.protocol.EmergencyCategory
import com.rescuemesh.app.protocol.NodeRole
import com.rescuemesh.app.identity.SecurityProvider
import java.security.SecureRandom

object SosFactory {
    private const val DEFAULT_TTL = 8
    private const val DEFAULT_LIFETIME_MS = 30 * 60 * 1_000L

    fun create(
        originNodeId: ByteArray,
        text: String,
        securityProvider: SecurityProvider,
        category: EmergencyCategory = EmergencyCategory.EMERGENCY_CATEGORY_UNSPECIFIED,
        originRole: NodeRole = NodeRole.NODE_ROLE_PUBLIC_RELAY,
        location: android.location.Location? = null,
        impactDetected: Boolean = false,
        priority: Int = 0,
        nowMs: Long = System.currentTimeMillis(),
    ): MeshMessage {
        require(text.isNotBlank()) { "SOS text cannot be blank" }
        // Truncate instead of throwing: the UI caps characters, not UTF-8 bytes, and the
        // impact prefix adds more. The whole frame must stay under WireFrameCodec.MAX_PAYLOAD_BYTES.
        val finalDetails = (if (impactDetected) "[IMPACT DETECTED] $text" else text)
            .truncateUtf8(MAX_PAYLOAD_TEXT_BYTES)

        val builder = MeshMessage.newBuilder()
            .setMessageId(ByteString.copyFrom(randomId()))
            .setOriginNodeId(ByteString.copyFrom(originNodeId))
            .setDestinationId(ByteString.EMPTY)
            .setMessageType(MessageType.MESSAGE_TYPE_SOS)
            .setPriority(priority)
            .setTtl(DEFAULT_TTL)
            .setHopCount(0)
            .setCreatedAtMs(nowMs)
            .setExpiresAtMs(nowMs + DEFAULT_LIFETIME_MS)
            .setPayloadVersion(1)
            .setPayload(ByteString.copyFrom(finalDetails.encodeToByteArray()))
            .setPublicKey(ByteString.copyFrom(securityProvider.getPublicKey()))
            .setCategory(category)
            .setOriginRole(originRole)

        if (location != null) {
            builder.location = Location.newBuilder()
                .setLatitude(location.latitude)
                .setLongitude(location.longitude)
                // No timestamp: Room doesn't store it, so a queued/relayed copy rebuilt via
                // toProto() would no longer match the signature. createdAtMs covers it.
                .setAccuracy(location.accuracy)
                .build()
        }

        val unsignedMessage = builder.build()
        val signature = securityProvider.sign(unsignedMessage.toByteArray())
        
        return unsignedMessage.toBuilder()
            .setSignature(ByteString.copyFrom(signature))
            .build()
    }

    private fun randomId(): ByteArray {
        return ByteArray(16).also { SecureRandom().nextBytes(it) }
    }
}

internal const val MAX_PAYLOAD_TEXT_BYTES = 220

/** Cuts the string so its UTF-8 encoding fits [maxBytes] without splitting a character. */
internal fun String.truncateUtf8(maxBytes: Int): String {
    if (encodeToByteArray().size <= maxBytes) return this
    var end = length
    while (end > 0 && substring(0, end).encodeToByteArray().size > maxBytes) {
        end--
        if (end > 0 && this[end - 1].isHighSurrogate()) end--
    }
    return substring(0, end)
}
