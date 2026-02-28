package com.manalejandro.alejabber.data.repository

import com.manalejandro.alejabber.data.local.dao.ContactDao
import com.manalejandro.alejabber.data.local.entity.toDomain
import com.manalejandro.alejabber.data.local.entity.toEntity
import com.manalejandro.alejabber.data.remote.XmppConnectionManager
import com.manalejandro.alejabber.domain.model.Contact
import com.manalejandro.alejabber.domain.model.PresenceStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.jivesoftware.smack.roster.Roster
import org.jivesoftware.smack.roster.RosterEntry
import org.jxmpp.jid.impl.JidCreate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepository @Inject constructor(
    private val contactDao: ContactDao,
    private val xmppManager: XmppConnectionManager
) {
    /**
     * Returns a Flow of contacts for [accountId], with **live presence** merged in.
     *
     * Room provides the persisted roster (name, JID, groups).
     * [XmppConnectionManager.rosterPresence] provides the real-time online/away/offline state.
     * The two are combined so the UI always shows the current presence without
     * writing every presence stanza to the database.
     */
    fun getContacts(accountId: Long): Flow<List<Contact>> =
        contactDao.getContactsByAccount(accountId)
            .combine(xmppManager.rosterPresence) { entities, presenceMap ->
                val accountPresence = presenceMap[accountId] ?: emptyMap()
                entities.map { entity ->
                    val livePresence = accountPresence[entity.jid]
                    if (livePresence != null) {
                        entity.toDomain().copy(presence = livePresence)
                    } else {
                        entity.toDomain()
                    }
                }
            }

    fun searchContacts(accountId: Long, query: String): Flow<List<Contact>> =
        contactDao.searchContacts(accountId, query).map { list -> list.map { it.toDomain() } }

    suspend fun addContact(contact: Contact): Long {
        val connection = xmppManager.getConnection(contact.accountId)
        if (connection != null && connection.isConnected) {
            try {
                val roster = Roster.getInstanceFor(connection)
                val jid = JidCreate.entityBareFrom(contact.jid)
                roster.createItemAndRequestSubscription(
                    jid, contact.nickname.ifBlank { contact.jid }, null
                )
            } catch (e: Exception) {
                // Proceed to save locally even if the server call fails
            }
        }
        return contactDao.insertContact(contact.toEntity())
    }

    suspend fun removeContact(accountId: Long, jid: String) {
        val connection = xmppManager.getConnection(accountId)
        if (connection != null && connection.isConnected) {
            try {
                val roster = Roster.getInstanceFor(connection)
                val entry: RosterEntry? = roster.getEntry(JidCreate.entityBareFrom(jid))
                entry?.let { roster.removeEntry(it) }
            } catch (e: Exception) { /* ignore */ }
        }
        contactDao.deleteContact(accountId, jid)
    }

    suspend fun syncRoster(accountId: Long) {
        val entries = xmppManager.getRosterEntries(accountId)
        val contacts = entries.map { entry ->
            Contact(
                accountId = accountId,
                jid       = entry.jid.asBareJid().toString(),
                nickname  = entry.name ?: entry.jid.asBareJid().toString(),
                groups    = entry.groups.map { it.name }
            ).toEntity()
        }
        if (contacts.isNotEmpty()) contactDao.insertContacts(contacts)
    }

    suspend fun updatePresence(
        accountId: Long,
        jid: String,
        presence: PresenceStatus,
        statusMessage: String
    ) {
        contactDao.updatePresence(accountId, jid, presence.name, statusMessage)
    }
}
