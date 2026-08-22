package com.rescuemesh.app.data

import com.google.protobuf.ByteString
import com.rescuemesh.app.protocol.MeshMessage
import com.rescuemesh.app.protocol.MessageType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageRepositoryTest {
    @Test
    fun duplicateMessageIsProcessedOnlyOnce() = runTest {
        val dao = FakeMessageDao()
        val repository = MessageRepository(dao)
        val message = message(ttl = 2, expiresAtMs = 2_000)

        val first = repository.persistReceivedMessage(message, sourceNodeId = byteArrayOf(7), nowMs = 1_000)
        val second = repository.persistReceivedMessage(message, sourceNodeId = byteArrayOf(7), nowMs = 1_000)

        assertTrue(first is ReceiveResult.Accepted)
        assertEquals(ReceiveResult.Duplicate, second)
        assertEquals(1, dao.messages.size)
        assertEquals(1, dao.receipts.size)
    }

    @Test
    fun expiredMessageIsRejectedBeforeReceiptInsert() = runTest {
        val dao = FakeMessageDao()
        val repository = MessageRepository(dao)

        val result = repository.persistReceivedMessage(
            message = message(ttl = 1, expiresAtMs = 1_000),
            sourceNodeId = byteArrayOf(7),
            nowMs = 1_000,
        )

        assertTrue(result is ReceiveResult.Rejected)
        assertEquals(0, dao.messages.size)
        assertEquals(0, dao.receipts.size)
    }

    private fun message(ttl: Int, expiresAtMs: Long): MeshMessage {
        return MeshMessage.newBuilder()
            .setMessageId(ByteString.copyFrom(ByteArray(16) { 1 }))
            .setOriginNodeId(ByteString.copyFrom(ByteArray(16) { 2 }))
            .setMessageType(MessageType.MESSAGE_TYPE_SOS)
            .setPriority(0)
            .setTtl(ttl)
            .setHopCount(0)
            .setCreatedAtMs(500)
            .setExpiresAtMs(expiresAtMs)
            .setPayloadVersion(1)
            .setPayload(ByteString.copyFromUtf8("help"))
            .build()
    }
}

private class FakeMessageDao : MessageDao {
    val messages = linkedMapOf<String, MessageEntity>()
    val receipts = linkedMapOf<String, MessageReceiptEntity>()

    override suspend fun insertMessage(message: MessageEntity): Long {
        val key = message.messageId.contentToString()
        if (messages.containsKey(key)) return -1
        messages[key] = message
        return messages.size.toLong()
    }

    override suspend fun insertReceipt(receipt: MessageReceiptEntity): Long {
        val key = receipt.messageId.contentToString()
        if (receipts.containsKey(key)) return -1
        receipts[key] = receipt
        return receipts.size.toLong()
    }

    override suspend fun upsertNeighbor(neighbor: NeighborEntity) = Unit
    override suspend fun upsertIdentity(identity: NodeIdentityEntity) = Unit
    override suspend fun insertForwardAttempt(attempt: ForwardAttemptEntity) = Unit
    override fun observeMessages(): Flow<List<MessageEntity>> = emptyFlow()
    override fun observeNeighbors(): Flow<List<NeighborEntity>> = emptyFlow()
    override suspend fun pendingMessages(nowMs: Long, limit: Int): List<MessageEntity> = emptyList()
    override suspend fun updateMessageState(messageId: ByteArray, state: String) = Unit
    override suspend fun markForwarded(messageId: ByteArray, state: String, forwardedAtMs: Long) = Unit
}
