package com.rescuemesh.app.mesh

import com.rescuemesh.app.data.MessageEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForwardingPolicyTest {
    @Test
    fun forwardableRequiresTtlAndFutureExpiryAndQueueState() {
        val message = message(ttl = 1, expiresAtMs = 2_000, state = "QUEUED")

        assertTrue(ForwardingPolicy.isForwardable(message, nowMs = 1_000))
        assertFalse(ForwardingPolicy.isForwardable(message(ttl = 0, expiresAtMs = 2_000), nowMs = 1_000))
        assertFalse(ForwardingPolicy.isForwardable(message(ttl = 1, expiresAtMs = 1_000), nowMs = 1_000))
        assertFalse(ForwardingPolicy.isForwardable(message(ttl = 1, expiresAtMs = 2_000, state = "RELAYED"), nowMs = 1_000))
    }

    @Test
    fun doesNotForwardBackToPreviousHopOrOrigin() {
        val origin = byteArrayOf(1, 1, 1)
        val prevHop = byteArrayOf(2, 2, 2)
        val other = byteArrayOf(3, 3, 3)
        
        val message = message(ttl = 1, expiresAtMs = 2000).copy(originNodeId = origin)

        // Don't send back to origin
        assertFalse(ForwardingPolicy.shouldForwardToPeer(message, origin, null))
        
        // Don't send back to previous hop
        assertFalse(ForwardingPolicy.shouldForwardToPeer(message, prevHop, prevHop))
        
        // Allowed to send to others
        assertTrue(ForwardingPolicy.shouldForwardToPeer(message, other, prevHop))
    }

    private fun message(
        ttl: Int,
        expiresAtMs: Long,
        state: String = "QUEUED",
    ): MessageEntity {
        return MessageEntity(
            messageId = byteArrayOf(1),
            originNodeId = byteArrayOf(2),
            destinationId = null,
            type = 1,
            priority = 0,
            ttl = ttl,
            hopCount = 0,
            createdAtMs = 1,
            expiresAtMs = expiresAtMs,
            payloadVersion = 1,
            payload = byteArrayOf(3),
            signature = null,
            state = state,
            receivedAtMs = 1,
            lastForwardedAtMs = null,
            previousHopNodeId = null,
            latitude = null,
            longitude = null,
            locationAccuracy = null,
            publicKey = null,
            category = 0,
            originRole = 0
        )
    }
}
