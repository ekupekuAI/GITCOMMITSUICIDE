package com.rescuemesh.app.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

object WireFrameCodec {
    const val PROTOCOL_VERSION: UByte = 0x01u
    const val MAX_PAYLOAD_BYTES = 500

    fun encode(type: WireFrameType, payload: ByteArray): ByteArray {
        require(payload.size <= MAX_PAYLOAD_BYTES) {
            "Payload too large: ${payload.size}"
        }
        val buffer = ByteBuffer
            .allocate(HEADER_BYTES + payload.size)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(PROTOCOL_VERSION.toByte())
        buffer.put(type.code.toByte())
        buffer.putShort(payload.size.toShort())
        buffer.put(payload)
        return buffer.array()
    }

    fun decode(bytes: ByteArray): Result<WireFrame> {
        if (bytes.size < HEADER_BYTES) {
            return Result.failure(WireFrameException("Frame too short"))
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val version = buffer.get().toUByte()
        if (version != PROTOCOL_VERSION) {
            return Result.failure(WireFrameException("Unsupported protocol version: $version"))
        }

        val type = WireFrameType.fromCode(buffer.get().toUByte())
            ?: return Result.failure(WireFrameException("Unknown frame type"))

        val payloadLength = buffer.short.toInt() and 0xFFFF
        if (payloadLength > MAX_PAYLOAD_BYTES) {
            return Result.failure(WireFrameException("Payload too large: $payloadLength"))
        }
        if (bytes.size - HEADER_BYTES != payloadLength) {
            return Result.failure(WireFrameException("Truncated frame"))
        }

        val payload = ByteArray(payloadLength)
        buffer.get(payload)
        return Result.success(WireFrame(type, payload))
    }

    private const val HEADER_BYTES = 4
}

data class WireFrame(
    val type: WireFrameType,
    val payload: ByteArray,
)

enum class WireFrameType(val code: UByte) {
    Control(0x01u),
    Data(0x02u),
    ;

    companion object {
        fun fromCode(code: UByte): WireFrameType? {
            return entries.firstOrNull { it.code == code }
        }
    }
}

class WireFrameException(message: String) : IllegalArgumentException(message)
