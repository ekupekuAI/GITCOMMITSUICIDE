package com.rescuemesh.app.mesh

import com.rescuemesh.app.protocol.MeshMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelayPolicyTest {
    @Test
    fun decrementsTtlAndIncrementsHopForAcceptedRelayCopy() {
        val message = MeshMessage.newBuilder()
            .setTtl(8)
            .setHopCount(2)
            .setExpiresAtMs(2_000)
            .build()

        val accepted = RelayPolicy.acceptedRelayCopy(message, nowMs = 1_000)!!

        assertEquals(7, accepted.ttl)
        assertEquals(3, accepted.hopCount)
    }

    @Test
    fun rejectsZeroTtl() {
        val message = MeshMessage.newBuilder()
            .setTtl(0)
            .setExpiresAtMs(2_000)
            .build()

        assertNull(RelayPolicy.acceptedRelayCopy(message, nowMs = 1_000))
    }

    @Test
    fun rejectsExpiredMessage() {
        val message = MeshMessage.newBuilder()
            .setTtl(1)
            .setExpiresAtMs(1_000)
            .build()

        assertNull(RelayPolicy.acceptedRelayCopy(message, nowMs = 1_000))
    }

    @Test
    fun rejectsMessageAtMaximumHopCount() {
        val message = MeshMessage.newBuilder()
            .setTtl(1)
            .setHopCount(MeshConfig.MAX_HOPS)
            .setExpiresAtMs(2_000)
            .build()

        assertNull(RelayPolicy.acceptedRelayCopy(message, nowMs = 1_000))
    }
}
