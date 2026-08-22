package com.rescuemesh.app.mesh

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class PerformanceTracker {
    private val _stats = MutableStateFlow(PerformanceStats())
    val stats: StateFlow<PerformanceStats> = _stats

    private val deliveryStartTimes = mutableMapOf<String, Long>()

    fun recordDiscovery() {
        _stats.update { it.copy(totalDiscoveries = it.totalDiscoveries + 1) }
    }

    fun recordConnection(success: Boolean) {
        _stats.update { 
            if (success) it.copy(successfulConnections = it.totalConnections + 1, totalConnections = it.totalConnections + 1)
            else it.copy(totalConnections = it.totalConnections + 1)
        }
    }

    fun trackMessageStart(messageId: String) {
        deliveryStartTimes[messageId] = System.currentTimeMillis()
    }

    fun recordMessageDelivered(messageId: String) {
        val startTime = deliveryStartTimes.remove(messageId) ?: return
        val latency = System.currentTimeMillis() - startTime
        
        _stats.update { 
            val newTotal = it.messagesDelivered + 1
            val newAvgLatency = ((it.avgLatencyMs * it.messagesDelivered) + latency) / newTotal
            it.copy(
                messagesDelivered = newTotal,
                avgLatencyMs = newAvgLatency,
                lastLatencyMs = latency
            )
        }
    }

    fun recordDuplicateRejected() {
        _stats.update { it.copy(duplicatesRejected = it.duplicateRejectionRate + 1) }
    }
}

data class PerformanceStats(
    val totalDiscoveries: Int = 0,
    val totalConnections: Int = 0,
    val successfulConnections: Int = 0,
    val messagesDelivered: Int = 0,
    val avgLatencyMs: Long = 0,
    val lastLatencyMs: Long = 0,
    val duplicatesRejected: Int = 0
) {
    val connectionSuccessRate: Float 
        get() = if (totalConnections > 0) (successfulConnections.toFloat() / totalConnections) * 100f else 0f
    
    val duplicateRejectionRate: Int get() = duplicatesRejected
}
