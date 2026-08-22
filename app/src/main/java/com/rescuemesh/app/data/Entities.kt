package com.rescuemesh.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "node_identity")
data class NodeIdentityEntity(
    @PrimaryKey
    @ColumnInfo(name = "node_id", typeAffinity = ColumnInfo.BLOB)
    val nodeId: ByteArray,
    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long,
    @ColumnInfo(name = "key_version")
    val keyVersion: Int,
)

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["state", "expires_at_ms"]),
        Index(value = ["priority"]),
    ],
)
data class MessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "message_id", typeAffinity = ColumnInfo.BLOB)
    val messageId: ByteArray,
    @ColumnInfo(name = "origin_node_id", typeAffinity = ColumnInfo.BLOB)
    val originNodeId: ByteArray,
    @ColumnInfo(name = "destination_id", typeAffinity = ColumnInfo.BLOB)
    val destinationId: ByteArray?,
    @ColumnInfo(name = "type")
    val type: Int,
    @ColumnInfo(name = "priority")
    val priority: Int,
    @ColumnInfo(name = "ttl")
    val ttl: Int,
    @ColumnInfo(name = "hop_count")
    val hopCount: Int,
    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long,
    @ColumnInfo(name = "expires_at_ms")
    val expiresAtMs: Long,
    @ColumnInfo(name = "payload_version")
    val payloadVersion: Int,
    @ColumnInfo(name = "payload", typeAffinity = ColumnInfo.BLOB)
    val payload: ByteArray,
    @ColumnInfo(name = "signature", typeAffinity = ColumnInfo.BLOB)
    val signature: ByteArray?,
    @ColumnInfo(name = "state")
    val state: String,
    @ColumnInfo(name = "received_at_ms")
    val receivedAtMs: Long,
    @ColumnInfo(name = "last_forwarded_at_ms")
    val lastForwardedAtMs: Long?,
)

@Entity(tableName = "message_receipts")
data class MessageReceiptEntity(
    @PrimaryKey
    @ColumnInfo(name = "message_id", typeAffinity = ColumnInfo.BLOB)
    val messageId: ByteArray,
    @ColumnInfo(name = "first_seen_at_ms")
    val firstSeenAtMs: Long,
    @ColumnInfo(name = "source_node_id", typeAffinity = ColumnInfo.BLOB)
    val sourceNodeId: ByteArray,
    @ColumnInfo(name = "processed")
    val processed: Boolean,
)

@Entity(tableName = "neighbors")
data class NeighborEntity(
    @PrimaryKey
    @ColumnInfo(name = "node_id", typeAffinity = ColumnInfo.BLOB)
    val nodeId: ByteArray,
    @ColumnInfo(name = "last_seen_at_ms")
    val lastSeenAtMs: Long,
    @ColumnInfo(name = "last_rssi_dbm")
    val lastRssiDbm: Int,
    @ColumnInfo(name = "state")
    val state: String,
    @ColumnInfo(name = "protocol_version")
    val protocolVersion: Int,
    @ColumnInfo(name = "last_connected_at_ms")
    val lastConnectedAtMs: Long?,
    @ColumnInfo(name = "connect_successes")
    val connectSuccesses: Int,
    @ColumnInfo(name = "connect_failures")
    val connectFailures: Int,
)

@Entity(
    tableName = "forward_attempts",
    indices = [Index(value = ["message_id"])],
)
data class ForwardAttemptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "message_id", typeAffinity = ColumnInfo.BLOB)
    val messageId: ByteArray,
    @ColumnInfo(name = "peer_node_id", typeAffinity = ColumnInfo.BLOB)
    val peerNodeId: ByteArray,
    @ColumnInfo(name = "started_at_ms")
    val startedAtMs: Long,
    @ColumnInfo(name = "result")
    val result: String,
    @ColumnInfo(name = "reason")
    val reason: String,
)
