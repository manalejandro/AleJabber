package com.manalejandro.alejabber.data.remote

import android.util.Log
import com.manalejandro.alejabber.domain.model.Account
import com.manalejandro.alejabber.domain.model.ConnectionStatus
import com.manalejandro.alejabber.domain.model.PresenceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jivesoftware.smack.AbstractXMPPConnection
import org.jivesoftware.smack.ConnectionConfiguration
import org.jivesoftware.smack.ReconnectionManager
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smack.chat2.ChatManager
import org.jivesoftware.smack.packet.Presence
import org.jivesoftware.smack.roster.Roster
import org.jivesoftware.smack.roster.RosterEntry
import org.jivesoftware.smack.roster.RosterListener
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration
import org.jxmpp.jid.Jid
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.jid.parts.Resourcepart
import javax.inject.Inject
import javax.inject.Singleton

data class IncomingMessage(
    val accountId: Long,
    val from: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class PresenceUpdate(
    val accountId: Long,
    val jid: String,
    val status: PresenceStatus,
    val statusMessage: String
)

/** A contact has requested subscription (wants to see our presence). */
data class SubscriptionRequest(
    val accountId: Long,
    val fromJid: String
)

@Singleton
class XmppConnectionManager @Inject constructor() {

    private val TAG = "XmppConnectionManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val connections = mutableMapOf<Long, AbstractXMPPConnection>()

    // ── Account connection status ─────────────────────────────────────────
    private val _connectionStatus = MutableStateFlow<Map<Long, ConnectionStatus>>(emptyMap())
    val connectionStatus: StateFlow<Map<Long, ConnectionStatus>> = _connectionStatus.asStateFlow()

    // ── Incoming chat messages ────────────────────────────────────────────
    private val _incomingMessages = MutableSharedFlow<IncomingMessage>()
    val incomingMessages: SharedFlow<IncomingMessage> = _incomingMessages.asSharedFlow()

    // ── Live presence map: accountId → (bareJid → PresenceStatus) ────────
    // Updated on every presence stanza. Consumers (ContactRepository) combine
    // this with Room data so contacts show the correct online/away/offline state
    // without having to write every presence change to the database.
    private val _rosterPresence =
        MutableStateFlow<Map<Long, Map<String, PresenceStatus>>>(emptyMap())
    val rosterPresence: StateFlow<Map<Long, Map<String, PresenceStatus>>> =
        _rosterPresence.asStateFlow()

    // ── Presence updates (kept for backward compatibility) ────────────────
    private val _presenceUpdates = MutableSharedFlow<PresenceUpdate>(extraBufferCapacity = 64)
    val presenceUpdates: SharedFlow<PresenceUpdate> = _presenceUpdates.asSharedFlow()

    // ── Incoming subscription requests ───────────────────────────────────
    private val _subscriptionRequests = MutableSharedFlow<SubscriptionRequest>(extraBufferCapacity = 32)
    val subscriptionRequests: SharedFlow<SubscriptionRequest> = _subscriptionRequests.asSharedFlow()

    // ─────────────────────────────────────────────────────────────────────

    fun connect(account: Account) {
        scope.launch {
            try {
                updateStatus(account.id, ConnectionStatus.CONNECTING)
                val config = buildConfig(account)
                val connection = XMPPTCPConnection(config)
                connections[account.id] = connection

                connection.connect()
                connection.login()

                ReconnectionManager.getInstanceFor(connection).apply {
                    enableAutomaticReconnection()
                    setReconnectionPolicy(ReconnectionManager.ReconnectionPolicy.RANDOM_INCREASING_DELAY)
                }

                setupRoster(account.id, connection)
                setupMessageListener(account.id, connection)
                updateStatus(account.id, ConnectionStatus.ONLINE)
                Log.i(TAG, "Connected: ${account.jid}")
            } catch (e: XMPPException) {
                Log.e(TAG, "XMPP error for ${account.jid}", e)
                updateStatus(account.id, ConnectionStatus.ERROR)
            } catch (e: SmackException) {
                Log.e(TAG, "Smack error for ${account.jid}", e)
                updateStatus(account.id, ConnectionStatus.ERROR)
            } catch (e: Exception) {
                Log.e(TAG, "Connection error for ${account.jid}", e)
                updateStatus(account.id, ConnectionStatus.ERROR)
            }
        }
    }

    fun disconnect(accountId: Long) {
        scope.launch {
            try {
                connections[accountId]?.disconnect()
                connections.remove(accountId)
                // Clear presence data for this account
                _rosterPresence.update { it - accountId }
                updateStatus(accountId, ConnectionStatus.OFFLINE)
            } catch (e: Exception) {
                Log.e(TAG, "Disconnect error", e)
            }
        }
    }

    fun disconnectAll() {
        connections.keys.toList().forEach { disconnect(it) }
    }

    fun sendMessage(accountId: Long, toJid: String, body: String): Boolean {
        return try {
            val connection = connections[accountId] ?: return false
            if (!connection.isConnected) return false
            val chatManager = ChatManager.getInstanceFor(connection)
            val jid = JidCreate.entityBareFrom(toJid)
            val chat = chatManager.chatWith(jid)
            chat.send(body)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Send message error", e)
            false
        }
    }

    fun getRosterEntries(accountId: Long): List<RosterEntry> {
        return try {
            val connection = connections[accountId] ?: return emptyList()
            val roster = Roster.getInstanceFor(connection)
            roster.entries.toList()
        } catch (e: Exception) {
            Log.e(TAG, "Get roster error", e)
            emptyList()
        }
    }

    fun isConnected(accountId: Long): Boolean =
        connections[accountId]?.isConnected == true &&
                connections[accountId]?.isAuthenticated == true

    fun getConnection(accountId: Long): AbstractXMPPConnection? = connections[accountId]

    /**
     * Called by [EncryptionManager] when an incoming OMEMO message has been
     * decrypted. Re-emits it through the same [incomingMessages] flow so
     * [XmppForegroundService] and the chat UI can handle it uniformly.
     */
    suspend fun dispatchDecryptedOmemoMessage(accountId: Long, from: String, body: String) {
        _incomingMessages.emit(
            IncomingMessage(accountId = accountId, from = from, body = body)
        )
    }

    /** Accept a subscription request — send subscribed + subscribe back. */
    fun acceptSubscription(accountId: Long, fromJid: String) {
        scope.launch {
            try {
                val connection = connections[accountId] ?: return@launch
                val jid = JidCreate.entityBareFrom(fromJid)
                // Confirm subscription
                val subscribed = Presence(Presence.Type.subscribed)
                subscribed.to = jid
                connection.sendStanza(subscribed)
                // Subscribe back (mutual)
                val subscribe = Presence(Presence.Type.subscribe)
                subscribe.to = jid
                connection.sendStanza(subscribe)
                Log.i(TAG, "Accepted subscription from $fromJid")
            } catch (e: Exception) {
                Log.e(TAG, "Accept subscription error", e)
            }
        }
    }

    /** Deny/cancel a subscription request. */
    fun denySubscription(accountId: Long, fromJid: String) {
        scope.launch {
            try {
                val connection = connections[accountId] ?: return@launch
                val jid = JidCreate.entityBareFrom(fromJid)
                val unsubscribed = Presence(Presence.Type.unsubscribed)
                unsubscribed.to = jid
                connection.sendStanza(unsubscribed)
                Log.i(TAG, "Denied subscription from $fromJid")
            } catch (e: Exception) {
                Log.e(TAG, "Deny subscription error", e)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────

    private fun buildConfig(account: Account): XMPPTCPConnectionConfiguration {
        val jid = JidCreate.entityBareFrom(account.jid)
        val builder = XMPPTCPConnectionConfiguration.builder()
            .setUsernameAndPassword(jid.localpart.toString(), account.password)
            .setXmppDomain(jid.asDomainBareJid())
            .setResource(Resourcepart.from(account.resource.ifBlank { "AleJabber" }))
            .setConnectTimeout(30_000)
            .setSendPresence(true)

        if (account.server.isNotBlank()) builder.setHost(account.server)
        if (account.port != 5222)        builder.setPort(account.port)

        builder.setSecurityMode(
            if (account.useTls) ConnectionConfiguration.SecurityMode.required
            else ConnectionConfiguration.SecurityMode.disabled
        )
        return builder.build()
    }

    private fun setupRoster(accountId: Long, connection: AbstractXMPPConnection) {
        val roster = Roster.getInstanceFor(connection)
        roster.isRosterLoadedAtLogin = true
        // Manual subscription mode: we handle subscribe/unsubscribe stanzas ourselves
        roster.subscriptionMode = Roster.SubscriptionMode.manual

        // ── Subscription request listener ─────────────────────────────────
        connection.addAsyncStanzaListener({ stanza ->
            val presence = stanza as Presence
            scope.launch {
                when (presence.type) {
                    Presence.Type.subscribe -> {
                        val fromJid = presence.from?.asBareJid()?.toString() ?: return@launch
                        Log.i(TAG, "Subscription request from $fromJid")
                        _subscriptionRequests.emit(SubscriptionRequest(accountId, fromJid))
                    }
                    else -> { /* handled by roster listener */ }
                }
            }
        }, org.jivesoftware.smack.filter.StanzaTypeFilter(Presence::class.java))

        // ── Snapshot all current presences once the roster is loaded ──────
        scope.launch {
            try {
                // Wait until Smack has fetched the roster from the server
                roster.reloadAndWait()
                val snapshot = mutableMapOf<String, PresenceStatus>()
                roster.entries.forEach { entry ->
                    val bareJid = entry.jid.asBareJid().toString()
                    val p = roster.getPresence(entry.jid.asBareJid())
                    snapshot[bareJid] = p.toPresenceStatus()
                }
                _rosterPresence.update { current ->
                    current.toMutableMap().also { it[accountId] = snapshot }
                }
                Log.i(TAG, "Roster snapshot loaded for account $accountId: ${snapshot.size} contacts")
            } catch (e: Exception) {
                Log.w(TAG, "Roster snapshot failed", e)
            }
        }

        // ── Listen for live presence changes ──────────────────────────────
        roster.addRosterListener(object : RosterListener {
            override fun entriesAdded(addresses: MutableCollection<Jid>?) {
                // Refresh snapshot when new contacts are added
                addresses?.forEach { jid ->
                    scope.launch {
                        try {
                            val bareJid = jid.asBareJid().toString()
                            val p = roster.getPresence(jid.asBareJid())
                            updatePresenceInMap(accountId, bareJid, p.toPresenceStatus(), p.status ?: "")
                        } catch (_: Exception) {}
                    }
                }
            }
            override fun entriesUpdated(addresses: MutableCollection<Jid>?) {}
            override fun entriesDeleted(addresses: MutableCollection<Jid>?) {}

            override fun presenceChanged(presence: Presence) {
                scope.launch {
                    val bareJid = presence.from?.asBareJid()?.toString() ?: return@launch
                    val presenceStatus = presence.toPresenceStatus()
                    val statusMsg = presence.status ?: ""

                    // Update the in-memory map
                    updatePresenceInMap(accountId, bareJid, presenceStatus, statusMsg)

                    Log.d(TAG, "Presence changed: $bareJid → $presenceStatus")
                }
            }
        })
    }

    /** Updates the [_rosterPresence] map and emits a [PresenceUpdate]. */
    private suspend fun updatePresenceInMap(
        accountId: Long,
        bareJid: String,
        status: PresenceStatus,
        statusMsg: String
    ) {
        _rosterPresence.update { current ->
            val accountMap = current[accountId]?.toMutableMap() ?: mutableMapOf()
            accountMap[bareJid] = status
            current.toMutableMap().also { it[accountId] = accountMap }
        }
        _presenceUpdates.emit(
            PresenceUpdate(accountId, bareJid, status, statusMsg)
        )
    }

    private fun setupMessageListener(accountId: Long, connection: AbstractXMPPConnection) {
        val chatManager = ChatManager.getInstanceFor(connection)
        chatManager.addIncomingListener { from, message, _ ->
            val body = message.body ?: return@addIncomingListener
            scope.launch {
                _incomingMessages.emit(
                    IncomingMessage(
                        accountId = accountId,
                        from = from.asBareJid().toString(),
                        body = body
                    )
                )
            }
        }
    }

    private fun updateStatus(accountId: Long, status: ConnectionStatus) {
        _connectionStatus.update { current ->
            current.toMutableMap().also { it[accountId] = status }
        }
    }
}

// ── Extension helpers ─────────────────────────────────────────────────────

private fun Presence.toPresenceStatus(): PresenceStatus = when (type) {
    Presence.Type.available -> when (mode) {
        Presence.Mode.away, Presence.Mode.xa -> PresenceStatus.AWAY
        Presence.Mode.dnd                    -> PresenceStatus.DND
        else                                  -> PresenceStatus.ONLINE
    }
    else -> PresenceStatus.OFFLINE
}
