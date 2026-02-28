package com.manalejandro.alejabber.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = AccountEntity::class,
        parentColumns = ["id"],
        childColumns = ["accountId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("accountId"), Index("conversationJid"), Index("timestamp")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stanzaId: String = "",
    val accountId: Long,
    val conversationJid: String,
    val fromJid: String,
    val toJid: String,
    val body: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val direction: String, // INCOMING / OUTGOING
    val status: String = "PENDING",
    val encryptionType: String = "NONE",
    val mediaType: String = "TEXT",
    val mediaUrl: String? = null,
    val mediaLocalPath: String? = null,
    val mediaMimeType: String? = null,
    val mediaSize: Long = 0,
    val mediaName: String? = null,
    val audioDurationMs: Long = 0,
    val isRead: Boolean = false,
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val replyToId: Long? = null
)

