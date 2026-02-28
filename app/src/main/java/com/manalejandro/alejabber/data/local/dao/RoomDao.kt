package com.manalejandro.alejabber.data.local.dao

import androidx.room.*
import com.manalejandro.alejabber.data.local.entity.RoomEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomDao {
    @Query("SELECT * FROM rooms WHERE accountId = :accountId ORDER BY isFavorite DESC, lastMessageTime DESC")
    fun getRoomsByAccount(accountId: Long): Flow<List<RoomEntity>>

    @Query("SELECT * FROM rooms WHERE accountId = :accountId AND isJoined = 1")
    fun getJoinedRooms(accountId: Long): Flow<List<RoomEntity>>

    @Query("SELECT * FROM rooms WHERE accountId = :accountId AND jid = :jid LIMIT 1")
    suspend fun getRoom(accountId: Long, jid: String): RoomEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoom(room: RoomEntity): Long

    @Update
    suspend fun updateRoom(room: RoomEntity)

    @Delete
    suspend fun deleteRoom(room: RoomEntity)

    @Query("UPDATE rooms SET isJoined = :isJoined WHERE accountId = :accountId AND jid = :jid")
    suspend fun updateJoinStatus(accountId: Long, jid: String, isJoined: Boolean)

    @Query("UPDATE rooms SET unreadCount = :count WHERE accountId = :accountId AND jid = :jid")
    suspend fun updateUnreadCount(accountId: Long, jid: String, count: Int)

    @Query("UPDATE rooms SET lastMessage = :lastMessage, lastMessageTime = :time WHERE accountId = :accountId AND jid = :jid")
    suspend fun updateLastMessage(accountId: Long, jid: String, lastMessage: String, time: Long)
}

