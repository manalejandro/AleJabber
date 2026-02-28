package com.manalejandro.alejabber.data.local.dao

import androidx.room.*
import com.manalejandro.alejabber.data.local.entity.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts WHERE accountId = :accountId ORDER BY nickname ASC, jid ASC")
    fun getContactsByAccount(accountId: Long): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE accountId = :accountId AND jid = :jid LIMIT 1")
    suspend fun getContact(accountId: Long, jid: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE accountId = :accountId AND (jid LIKE '%' || :query || '%' OR nickname LIKE '%' || :query || '%')")
    fun searchContacts(accountId: Long, query: String): Flow<List<ContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<ContactEntity>)

    @Update
    suspend fun updateContact(contact: ContactEntity)

    @Delete
    suspend fun deleteContact(contact: ContactEntity)

    @Query("DELETE FROM contacts WHERE accountId = :accountId AND jid = :jid")
    suspend fun deleteContact(accountId: Long, jid: String)

    @Query("UPDATE contacts SET presence = :presence, statusMessage = :statusMessage WHERE accountId = :accountId AND jid = :jid")
    suspend fun updatePresence(accountId: Long, jid: String, presence: String, statusMessage: String)

    @Query("UPDATE contacts SET avatarUrl = :avatarUrl WHERE accountId = :accountId AND jid = :jid")
    suspend fun updateAvatarUrl(accountId: Long, jid: String, avatarUrl: String?)
}

