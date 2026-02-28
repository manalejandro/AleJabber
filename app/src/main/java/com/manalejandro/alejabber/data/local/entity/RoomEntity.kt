package com.manalejandro.alejabber.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rooms",
    foreignKeys = [ForeignKey(
        entity = AccountEntity::class,
        parentColumns = ["id"],
        childColumns = ["accountId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("accountId")]
)
data class RoomEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val jid: String,
    val nickname: String = "",
    val name: String = "",
    val description: String = "",
    val topic: String = "",
    val password: String = "",
    val isJoined: Boolean = false,
    val isFavorite: Boolean = false,
    val participantCount: Int = 0,
    val avatarUrl: String? = null,
    val unreadCount: Int = 0,
    val lastMessage: String = "",
    val lastMessageTime: Long = 0
)

