package com.manalejandro.alejabber.data.repository

import android.util.Base64
import android.util.Log
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
import org.jivesoftware.smackx.vcardtemp.VCardManager
import org.jxmpp.jid.impl.JidCreate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepository @Inject constructor(
    private val contactDao: ContactDao,
    private val xmppManager: XmppConnectionManager
) {
    private val TAG = "ContactRepository"

    /**
     * Returns a Flow of contacts for [accountId], with **live presence** merged in.
     *
     * Room provides the persisted roster (name, JID, groups).
     * [XmppConnectionManager.rosterPresence] provides the real-time online/away/offline state.
     */
    fun getContacts(accountId: Long): Flow<List<Contact>> =
        contactDao.getContactsByAccount(accountId)
            .combine(xmppManager.rosterPresence) { entities, presenceMap ->
                val accountPresence = presenceMap[accountId] ?: emptyMap()
                entities.map { entity ->
                    val livePresence = accountPresence[entity.jid]
                    if (livePresence != null) entity.toDomain().copy(presence = livePresence)
                    else entity.toDomain()
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
            } catch (e: Exception) { /* save locally anyway */ }
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

    /**
     * Syncs the server roster to the local DB and then fetches each contact's
     * vCard avatar in the background.  Avatar bytes are stored as a
     * `data:image/png;base64,…` URI so Coil can display them with no extra
     * network request.
     */
    suspend fun syncRoster(accountId: Long) {
        val connection = xmppManager.getConnection(accountId) ?: return
        val entries    = xmppManager.getRosterEntries(accountId)

        // 1. Upsert the basic roster entries
        val entities = entries.map { entry ->
            Contact(
                accountId = accountId,
                jid       = entry.jid.asBareJid().toString(),
                nickname  = entry.name ?: entry.jid.asBareJid().toString(),
                groups    = entry.groups.map { it.name }
            ).toEntity()
        }
        if (entities.isNotEmpty()) contactDao.insertContacts(entities)

        // 2. Fetch vCard avatars for each contact (best-effort, non-blocking errors)
        if (!connection.isConnected || !connection.isAuthenticated) return
        val vcardManager = VCardManager.getInstanceFor(connection)
        entries.forEach { entry ->
            val bareJid = entry.jid.asBareJid().toString()
            try {
                val vcard = vcardManager.loadVCard(
                    JidCreate.entityBareFrom(bareJid)
                )
                val avatarBytes = vcard?.avatar
                if (avatarBytes != null && avatarBytes.isNotEmpty()) {
                    val mime = vcard.avatarMimeType?.ifBlank { "image/png" } ?: "image/png"
                    val b64  = Base64.encodeToString(avatarBytes, Base64.NO_WRAP)
                    val dataUri = "data:$mime;base64,$b64"
                    contactDao.updateAvatarUrl(accountId, bareJid, dataUri)
                    Log.d(TAG, "Avatar loaded for $bareJid (${avatarBytes.size} bytes)")
                }
            } catch (e: Exception) {
                // vCard not found or server error — keep whatever was stored before
                Log.d(TAG, "No vCard avatar for $bareJid: ${e.message}")
            }
        }
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
