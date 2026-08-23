package com.rescuemesh.app.protocol

import com.google.protobuf.ByteString

object ProtocolCodec {
    fun encodeControl(message: ControlMessage): ByteArray {
        return WireFrameCodec.encode(WireFrameType.Control, message.toByteArray())
    }

    fun decodeControl(frameBytes: ByteArray): Result<ControlMessage> {
        return WireFrameCodec.decode(frameBytes).mapCatching { frame ->
            require(frame.type == WireFrameType.Control) { "Expected CONTROL frame" }
            ControlMessage.parseFrom(frame.payload)
        }
    }

    fun encodeMeshMessage(message: MeshMessage): ByteArray {
        return WireFrameCodec.encode(WireFrameType.Data, message.toByteArray())
    }

    fun decodeMeshMessage(frameBytes: ByteArray): Result<MeshMessage> {
        return WireFrameCodec.decode(frameBytes).mapCatching { frame ->
            require(frame.type == WireFrameType.Data) { "Expected DATA frame" }
            MeshMessage.parseFrom(frame.payload)
        }
    }

    fun hello(
        nodeId: ByteArray, 
        role: NodeRole = NodeRole.NODE_ROLE_PUBLIC_RELAY,
        batteryPercentage: Int = 0,
        latitude: Double? = null,
        longitude: Double? = null
    ): ControlMessage {
        val builder = ControlMessage.newBuilder()
            .setType(ControlType.CONTROL_TYPE_HELLO)
            .setProtocolVersion(WireFrameCodec.PROTOCOL_VERSION.toInt())
            .setNodeId(ByteString.copyFrom(nodeId))
            .setRole(role)
            .setBatteryPercentage(batteryPercentage)
            .addCapabilities("phase4-framed-protobuf")

        if (latitude != null && longitude != null) {
            builder.location = Location.newBuilder()
                .setLatitude(latitude)
                .setLongitude(longitude)
                .setTimestampMs(System.currentTimeMillis())
                .build()
        }

        return builder.build()
    }

    fun ack(
        nodeId: ByteArray, 
        role: NodeRole = NodeRole.NODE_ROLE_PUBLIC_RELAY,
        batteryPercentage: Int = 0,
        latitude: Double? = null,
        longitude: Double? = null
    ): ControlMessage {
        val builder = ControlMessage.newBuilder()
            .setType(ControlType.CONTROL_TYPE_ACK)
            .setProtocolVersion(WireFrameCodec.PROTOCOL_VERSION.toInt())
            .setNodeId(ByteString.copyFrom(nodeId))
            .setRole(role)
            .setBatteryPercentage(batteryPercentage)

        if (latitude != null && longitude != null) {
            builder.location = Location.newBuilder()
                .setLatitude(latitude)
                .setLongitude(longitude)
                .setTimestampMs(System.currentTimeMillis())
                .build()
        }

        return builder.build()
    }

    fun ackMessage(nodeId: ByteArray, messageId: ByteArray): ControlMessage {
        return ControlMessage.newBuilder()
            .setType(ControlType.CONTROL_TYPE_ACK)
            .setProtocolVersion(WireFrameCodec.PROTOCOL_VERSION.toInt())
            .setNodeId(ByteString.copyFrom(nodeId))
            .setAckMessageId(ByteString.copyFrom(messageId))
            .build()
    }
}
