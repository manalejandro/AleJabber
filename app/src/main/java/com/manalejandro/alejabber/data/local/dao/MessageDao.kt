package com.manalejandro.alejabber.data.local.dao

import androidx.room.*
import com.manalejandro.alejabber.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE accountId = :accountId AND conversationJid = :conversationJid AND isDeleted = 0 ORDER BY timestamp ASC")
    fun getMessages(accountId: Long, conversationJid: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE accountId = :accountId AND conversationJid = :conversationJid ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessage(accountId: Long, conversationJid: String): MessageEntity?

    @Query("SELECT COUNT(*) FROM messages WHERE accountId = :accountId AND conversationJid = :conversationJid AND isRead = 0 AND direction = 'INCOMING'")
    fun getUnreadCount(accountId: Long, conversationJid: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET isRead = 1 WHERE accountId = :accountId AND conversationJid = :conversationJid AND direction = 'INCOMING'")
    suspend fun markAllAsRead(accountId: Long, conversationJid: String)

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE messages SET isDeleted = 1 WHERE id = :id")
    suspend fun deleteMessage(id: Long)

    @Query("DELETE FROM messages WHERE accountId = :accountId AND conversationJid = :conversationJid")
    suspend fun clearConversation(accountId: Long, conversationJid: String)
}

