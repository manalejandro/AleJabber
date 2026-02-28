package com.manalejandro.alejabber.data.repository

import com.manalejandro.alejabber.data.local.dao.MessageDao
import com.manalejandro.alejabber.data.local.entity.toDomain
import com.manalejandro.alejabber.data.local.entity.toEntity
import com.manalejandro.alejabber.data.remote.XmppConnectionManager
import com.manalejandro.alejabber.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(
    private val messageDao: MessageDao,
    private val xmppManager: XmppConnectionManager
) {
    fun getMessages(accountId: Long, conversationJid: String): Flow<List<Message>> =
        messageDao.getMessages(accountId, conversationJid).map { list -> list.map { it.toDomain() } }

    fun getUnreadCount(accountId: Long, conversationJid: String): Flow<Int> =
        messageDao.getUnreadCount(accountId, conversationJid)

    suspend fun sendMessage(
        accountId: Long,
        toJid: String,
        body: String,
        encryptionType: EncryptionType = EncryptionType.NONE
    ): Long {
        val msg = Message(
            accountId = accountId,
            conversationJid = toJid,
            fromJid = "",
            toJid = toJid,
            body = body,
            direction = MessageDirection.OUTGOING,
            status = MessageStatus.PENDING,
            encryptionType = encryptionType
        )
        val id = messageDao.insertMessage(msg.toEntity())
        val success = xmppManager.sendMessage(accountId, toJid, body)
        val status = if (success) MessageStatus.SENT else MessageStatus.FAILED
        messageDao.updateStatus(id, status.name)
        return id
    }

    suspend fun saveIncomingMessage(
        accountId: Long,
        from: String,
        body: String,
        encryptionType: EncryptionType = EncryptionType.NONE
    ): Long {
        val msg = Message(
            accountId = accountId,
            conversationJid = from,
            fromJid = from,
            toJid = "",
            body = body,
            direction = MessageDirection.INCOMING,
            status = MessageStatus.DELIVERED,
            encryptionType = encryptionType
        )
        return messageDao.insertMessage(msg.toEntity())
    }

    suspend fun markAllAsRead(accountId: Long, conversationJid: String) =
        messageDao.markAllAsRead(accountId, conversationJid)

    suspend fun deleteMessage(id: Long) = messageDao.deleteMessage(id)

    suspend fun clearConversation(accountId: Long, conversationJid: String) =
        messageDao.clearConversation(accountId, conversationJid)

    /** Persists an already-sent outgoing message (e.g. encrypted via EncryptionManager). */
    suspend fun saveOutgoingMessage(message: Message): Long =
        messageDao.insertMessage(message.toEntity())
}

