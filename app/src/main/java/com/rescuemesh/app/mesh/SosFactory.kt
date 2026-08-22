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
        val finalDetails = if (impactDetected) "[IMPACT DETECTED] $text" else text
        require(finalDetails.isNotBlank()) { "SOS text cannot be blank" }
        require(finalDetails.encodeToByteArray().size <= 220) { "SOS text is too large" }

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
                .setTimestampMs(location.time)
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
