package com.rescuemesh.app.mesh

import com.rescuemesh.app.protocol.MeshMessage

object RelayPolicy {
    fun acceptedRelayCopy(message: MeshMessage, nowMs: Long): MeshMessage? {
        if (message.expiresAtMs <= nowMs) return null
        if (message.ttl == 0) return null
        return message.toBuilder()
            .setTtl(message.ttl - 1)
            .setHopCount(message.hopCount + 1)
            .build()
    }
}
