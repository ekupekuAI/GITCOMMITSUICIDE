package com.rescuemesh.app.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WireFrameCodecTest {
    @Test
    fun roundTripControlFrame() {
        val payload = byteArrayOf(1, 2, 3)
        val decoded = WireFrameCodec.decode(
            WireFrameCodec.encode(WireFrameType.Control, payload),
        ).getOrThrow()

        assertEquals(WireFrameType.Control, decoded.type)
        assertArrayEquals(payload, decoded.payload)
    }

    @Test
    fun rejectsTruncatedFrame() {
        val result = WireFrameCodec.decode(byteArrayOf(1, 1, 10, 0, 1))

        assertTrue(result.isFailure)
    }

    @Test
    fun rejectsOversizePayload() {
        val payload = ByteArray(WireFrameCodec.MAX_PAYLOAD_BYTES + 1)

        val result = runCatching { WireFrameCodec.encode(WireFrameType.Data, payload) }

        assertTrue(result.isFailure)
    }
}
