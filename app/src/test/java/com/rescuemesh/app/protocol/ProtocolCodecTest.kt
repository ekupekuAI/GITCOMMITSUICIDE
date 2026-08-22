package com.rescuemesh.app.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolCodecTest {
    @Test
    fun controlRoundTripUsesWireFrame() {
        val nodeId = ByteArray(16) { it.toByte() }
        val encoded = ProtocolCodec.encodeControl(ProtocolCodec.hello(nodeId))
        val decoded = ProtocolCodec.decodeControl(encoded).getOrThrow()

        assertEquals(ControlType.CONTROL_TYPE_HELLO, decoded.type)
        assertArrayEquals(nodeId, decoded.nodeId.toByteArray())
    }

    @Test
    fun malformedControlFrameIsRejected() {
        val result = ProtocolCodec.decodeControl(byteArrayOf(1, 1, 3, 0, 1))

        assertTrue(result.isFailure)
    }
}
