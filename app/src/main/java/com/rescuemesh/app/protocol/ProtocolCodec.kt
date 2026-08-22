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

    fun hello(nodeId: ByteArray): ControlMessage {
        return ControlMessage.newBuilder()
            .setType(ControlType.CONTROL_TYPE_HELLO)
            .setProtocolVersion(WireFrameCodec.PROTOCOL_VERSION.toInt())
            .setNodeId(ByteString.copyFrom(nodeId))
            .addCapabilities("phase4-framed-protobuf")
            .build()
    }

    fun ack(nodeId: ByteArray): ControlMessage {
        return ControlMessage.newBuilder()
            .setType(ControlType.CONTROL_TYPE_ACK)
            .setProtocolVersion(WireFrameCodec.PROTOCOL_VERSION.toInt())
            .setNodeId(ByteString.copyFrom(nodeId))
            .build()
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
