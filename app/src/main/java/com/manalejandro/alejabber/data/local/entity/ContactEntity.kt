package com.manalejandro.alejabber.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing an XMPP roster contact.
 *
 * The composite unique index on (accountId, jid) ensures that calling
 * [ContactDao.insertContact] / [ContactDao.insertContacts] with
 * [OnConflictStrategy.REPLACE] updates an existing row rather than
 * inserting a duplicate — which would cause LazyColumn key-collision crashes.
 */
@Entity(
    tableName = "contacts",
    foreignKeys = [ForeignKey(
        entity = AccountEntity::class,
        parentColumns = ["id"],
        childColumns = ["accountId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index("accountId"),
        // Unique composite index — prevents duplicate (account, jid) pairs
        Index(value = ["accountId", "jid"], unique = true)
    ]
)
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val jid: String,
    val nickname: String = "",
    val groups: String = "",          // JSON array
    val presence: String = "OFFLINE",
    val statusMessage: String = "",
    val avatarUrl: String? = null,
    val isBlocked: Boolean = false,
    val subscriptionState: String = "NONE"
)
