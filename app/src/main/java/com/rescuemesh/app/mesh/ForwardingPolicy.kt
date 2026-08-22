package com.rescuemesh.app.mesh

import com.rescuemesh.app.data.MessageEntity

object ForwardingPolicy {
    fun isForwardable(message: MessageEntity, nowMs: Long): Boolean {
        return message.ttl > 0 &&
            message.expiresAtMs > nowMs &&
            message.state in setOf("PERSISTED", "QUEUED", "WAITING_FOR_NEIGHBOR")
    }

    /**
     * Determines if a message should be sent to a specific peer.
     * Prevents immediate loopback to the previous hop.
     */
    fun shouldForwardToPeer(
        message: MessageEntity,
        candidatePeerNodeId: ByteArray,
        previousHopNodeId: ByteArray?
    ): Boolean {
        // Don't send back to where it just came from
        if (previousHopNodeId != null && previousHopNodeId.contentEquals(candidatePeerNodeId)) {
            return false
        }
        
        // Don't send back to the origin
        if (message.originNodeId.contentEquals(candidatePeerNodeId)) {
            return false
        }

        return true
    }
}
