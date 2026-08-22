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
    fun doesNotForwardBackToPreviousHop() {
        val peer = byteArrayOf(1, 2, 3)

        assertFalse(ForwardingPolicy.shouldForwardToPeer(peer, peer.copyOf()))
        assertTrue(ForwardingPolicy.shouldForwardToPeer(peer, byteArrayOf(4, 5, 6)))
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
        )
    }
}
