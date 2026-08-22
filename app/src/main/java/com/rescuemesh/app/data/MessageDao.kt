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

    @Insert
    suspend fun insertForwardAttempt(attempt: ForwardAttemptEntity)

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
