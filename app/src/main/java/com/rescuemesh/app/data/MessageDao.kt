package com.rescuemesh.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReceipt(receipt: MessageReceiptEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNeighbor(neighbor: NeighborEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIdentity(identity: NodeIdentityEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIncident(incident: IncidentEntity): Long

    @Query("SELECT * FROM incidents ORDER BY last_updated_at_ms DESC")
    fun observeIncidents(): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents WHERE category = :category AND latitude BETWEEN :latMin AND :latMax AND longitude BETWEEN :lonMin AND :lonMax LIMIT 1")
    suspend fun findMatchingIncident(category: Int, latMin: Double, latMax: Double, lonMin: Double, lonMax: Double): IncidentEntity?

    @Query("SELECT * FROM messages ORDER BY created_at_ms DESC")
    fun observeMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM neighbors ORDER BY last_seen_at_ms DESC")
    fun observeNeighbors(): Flow<List<NeighborEntity>>

    @Query("SELECT * FROM messages WHERE state IN ('PERSISTED', 'QUEUED', 'WAITING_FOR_NEIGHBOR') AND expires_at_ms > :nowMs ORDER BY priority ASC, created_at_ms ASC LIMIT :limit")
    suspend fun pendingMessages(nowMs: Long, limit: Int = 50): List<MessageEntity>

    @Query("UPDATE messages SET state = :state WHERE message_id = :messageId")
    suspend fun updateMessageState(messageId: ByteArray, state: String)

    @Query("UPDATE messages SET state = :state, last_forwarded_at_ms = :forwardedAtMs WHERE message_id = :messageId")
    suspend fun markForwarded(messageId: ByteArray, state: String, forwardedAtMs: Long)

    @Query("DELETE FROM messages WHERE expires_at_ms < :nowMs OR ttl <= 0")
    suspend fun deleteExpiredMessages(nowMs: Long)

    @Query("DELETE FROM neighbors WHERE last_seen_at_ms < :thresholdMs")
    suspend fun deleteStaleNeighbors(thresholdMs: Long)

    @Query("DELETE FROM message_receipts WHERE first_seen_at_ms < :thresholdMs")
    suspend fun deleteOldReceipts(thresholdMs: Long)

    @Query("DELETE FROM incidents WHERE last_updated_at_ms < :thresholdMs")
    suspend fun deleteOldIncidents(thresholdMs: Long)

    @Transaction
    suspend fun acceptNewMessage(
        receipt: MessageReceiptEntity,
        message: MessageEntity,
    ): Boolean {
        val receiptInserted = insertReceipt(receipt)
        if (receiptInserted == -1L) return false
        insertMessage(message)
        return true
    }
}
