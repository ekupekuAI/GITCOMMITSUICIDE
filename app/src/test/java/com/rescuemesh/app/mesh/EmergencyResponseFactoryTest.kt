package com.rescuemesh.app.mesh

import com.google.protobuf.ByteString
import com.rescuemesh.app.protocol.MessageType
import com.rescuemesh.app.protocol.MeshMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class EmergencyResponseFactoryTest {
    @Test
    fun parsesCorrelatedResponderResponse() {
        val message = MeshMessage.newBuilder()
            .setMessageType(MessageType.MESSAGE_TYPE_TEXT)
            .setPayload(ByteString.copyFromUtf8("RESPONSE|RESPONDING|sos-123|Unit en route"))
            .build()

        val response = EmergencyResponseFactory.parse(message)

        assertNotNull(response)
        assertEquals("sos-123", response?.sosMessageIdHex)
        assertEquals(ResponderStatus.RESPONDING, response?.status)
        assertEquals("Unit en route", response?.message)
    }
}
