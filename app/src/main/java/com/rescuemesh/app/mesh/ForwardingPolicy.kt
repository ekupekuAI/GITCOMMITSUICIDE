package com.rescuemesh.app.mesh

import com.rescuemesh.app.data.MessageEntity

object ForwardingPolicy {
    fun isForwardable(message: MessageEntity, nowMs: Long): Boolean {
        return message.ttl > 0 &&
            message.expiresAtMs > nowMs &&
            message.state in setOf("PERSISTED", "QUEUED", "WAITING_FOR_NEIGHBOR")
    }

    fun shouldForwardToPeer(previousHopNodeId: ByteArray?, candidatePeerNodeId: ByteArray): Boolean {
        return previousHopNodeId == null || !previousHopNodeId.contentEquals(candidatePeerNodeId)
    }
}
