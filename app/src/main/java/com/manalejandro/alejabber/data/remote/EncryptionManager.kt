package com.manalejandro.alejabber.data.remote

import android.content.Context
import android.util.Log
import com.manalejandro.alejabber.domain.model.EncryptionType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smackx.carbons.packet.CarbonExtension
import org.jivesoftware.smackx.omemo.OmemoManager
import org.jivesoftware.smackx.omemo.OmemoMessage
import org.jivesoftware.smackx.omemo.exceptions.CryptoFailedException
import org.jivesoftware.smackx.omemo.exceptions.UndecidedOmemoIdentityException
import org.jivesoftware.smackx.omemo.listener.OmemoMessageListener
import org.jivesoftware.smackx.omemo.signal.SignalOmemoService
import org.jivesoftware.smackx.omemo.trust.OmemoFingerprint
import org.jivesoftware.smackx.omemo.trust.OmemoTrustCallback
import org.jivesoftware.smackx.omemo.trust.TrustState
import org.jxmpp.jid.impl.JidCreate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages OMEMO, OTR and OpenPGP encryption for outgoing messages and
 * decryption of incoming OMEMO-encrypted messages.
 *
 * ## OTR handshake protocol
 * OTR requires an ephemeral ECDH key exchange before any message can be encrypted.
 * The handshake uses two special XMPP message bodies:
 *
 *   Initiator → Responder:  `?OTR-INIT:<base64(pubkey)>`
 *   Responder → Initiator:  `?OTR-ACK:<base64(pubkey)>`
 *
 * On receiving ACK/INIT the session keys are derived and subsequent messages
 * are encrypted as `?OTR:<base64(nonce|ciphertext|hmac)>`.
 *
 * Incoming OTR control messages are intercepted in [handleIncomingMessage]
 * (called from XmppForegroundService / XmppConnectionManager) before they
 * reach the UI, so the user never sees the raw key exchange strings.
 */
@Singleton
class EncryptionManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val xmppManager: XmppConnectionManager
) {
    private val TAG = "EncryptionManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        // Register OTR message interceptor so handshake messages are processed
        // transparently and never reach the chat UI as raw text.
        xmppManager.setMessageInterceptor { accountId, from, body ->
            handleIncomingMessage(accountId, from, body)
        }
    }

    // ── OMEMO ────────────────────────────────────────────────────────────
    enum class OmemoState { IDLE, INITIALISING, READY, FAILED }

    private val _omemoState = MutableStateFlow<Map<Long, OmemoState>>(emptyMap())
    val omemoState: StateFlow<Map<Long, OmemoState>> = _omemoState.asStateFlow()

    private var omemoServiceSetup = false

    // ── OTR ──────────────────────────────────────────────────────────────
    /**
     * Lifecycle of an OTR session:
     *  IDLE           → no session
     *  AWAITING_ACK   → we sent INIT, waiting for the remote ACK
     *  AWAITING_INIT  → we received INIT, sent ACK, waiting to confirm
     *  ESTABLISHED    → ECDH complete, can encrypt/decrypt
     */
    enum class OtrHandshakeState { AWAITING_ACK, AWAITING_INIT, ESTABLISHED }

    private data class OtrEntry(
        val session: OtrSession,
        var state: OtrHandshakeState
    )

    private val otrEntries = mutableMapOf<Pair<Long, String>, OtrEntry>()

    /** Emitted whenever an OTR session reaches ESTABLISHED. */
    data class OtrStateEvent(val accountId: Long, val jid: String, val state: OtrHandshakeState)

    private val _otrStateChanges = MutableSharedFlow<OtrStateEvent>(extraBufferCapacity = 16)
    val otrStateChanges: SharedFlow<OtrStateEvent> = _otrStateChanges.asSharedFlow()

    // ── OpenPGP ───────────────────────────────────────────────────────────
    private val pgpManager = PgpManager(context)

    // ─────────────────────────────────────────────────────────────────────
    // OMEMO
    // ─────────────────────────────────────────────────────────────────────

    fun initOmemo(accountId: Long) {
        val state = _omemoState.value[accountId]
        if (state == OmemoState.READY || state == OmemoState.INITIALISING) return
        val connection = xmppManager.getConnection(accountId) ?: return
        if (!connection.isAuthenticated) return

        _omemoState.update { it + (accountId to OmemoState.INITIALISING) }
        with(scope) {
            launch {
            try {
                if (!omemoServiceSetup) {
                    SignalOmemoService.acknowledgeLicense()
                    SignalOmemoService.setup()
                    omemoServiceSetup = true
                }
                val omemoManager = OmemoManager.getInstanceFor(connection)
                omemoManager.setTrustCallback(object : OmemoTrustCallback {
                    override fun getTrust(
                        device: org.jivesoftware.smackx.omemo.internal.OmemoDevice,
                        fingerprint: OmemoFingerprint
                    ): TrustState = TrustState.trusted
                    override fun setTrust(
                        device: org.jivesoftware.smackx.omemo.internal.OmemoDevice,
                        fingerprint: OmemoFingerprint,
                        state: TrustState
                    ) { /* TOFU */ }
                })
                omemoManager.addOmemoMessageListener(object : OmemoMessageListener {
                    override fun onOmemoMessageReceived(
                        stanza: org.jivesoftware.smack.packet.Stanza,
                        decryptedMessage: OmemoMessage.Received
                    ) {
                        val from = stanza.from?.asBareJid()?.toString() ?: return
                        val body = decryptedMessage.body ?: return
                        val encScope = scope
                        encScope.launch { xmppManager.dispatchDecryptedOmemoMessage(accountId, from, body) }
                    }
                    override fun onOmemoCarbonCopyReceived(
                        direction: CarbonExtension.Direction,
                        carbonCopy: Message,
                        wrappingMessage: Message,
                        decryptedCarbonCopy: OmemoMessage.Received
                    ) {
                        val from = carbonCopy.from?.asBareJid()?.toString() ?: return
                        val body = decryptedCarbonCopy.body ?: return
                        val encScope = scope
                        encScope.launch { xmppManager.dispatchDecryptedOmemoMessage(accountId, from, body) }
                    }
                })
                omemoManager.initializeAsync(object : OmemoManager.InitializationFinishedCallback {
                    override fun initializationFinished(manager: OmemoManager) {
                        _omemoState.update { it + (accountId to OmemoState.READY) }
                        Log.i(TAG, "OMEMO ready for account $accountId")
                    }
                    override fun initializationFailed(cause: Exception) {
                        _omemoState.update { it + (accountId to OmemoState.FAILED) }
                        Log.e(TAG, "OMEMO init failed for account $accountId", cause)
                    }
                })
            } catch (e: Exception) {
                _omemoState.update { it + (accountId to OmemoState.FAILED) }
                Log.e(TAG, "OMEMO setup error for account $accountId", e)
            }
        } // launch
        } // with(scope)
    }

    fun isOmemoReady(accountId: Long) = _omemoState.value[accountId] == OmemoState.READY

    fun getOmemoStateLabel(accountId: Long): String = when (_omemoState.value[accountId]) {
        OmemoState.READY        -> "✓ Ready"
        OmemoState.INITIALISING -> "⏳ Initialising…"
        OmemoState.FAILED       -> "✗ Init failed"
        else                    -> "⏳ Not started"
    }

    fun getOwnOmemoFingerprint(accountId: Long): String? {
        if (!isOmemoReady(accountId)) return null
        return try {
            val connection = xmppManager.getConnection(accountId) ?: return null
            OmemoManager.getInstanceFor(connection).ownFingerprint.toString()
        } catch (_: Exception) { null }
    }

    // ─────────────────────────────────────────────────────────────────────
    // OTR — public API
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Initiates an OTR key exchange with [toJid].
     * Sends `?OTR-INIT:<base64pubkey>` and waits for the ACK.
     * Returns a user-visible status string.
     */
    fun startOtrSession(accountId: Long, toJid: String): String {
        val key = accountId to toJid
        // If already established, no-op
        val existing = otrEntries[key]
        if (existing?.state == OtrHandshakeState.ESTABLISHED) return "OTR session already active."

        val session = OtrSession()
        otrEntries[key] = OtrEntry(session, OtrHandshakeState.AWAITING_ACK)

        val pubKeyB64 = session.getPublicKeyBase64()
        val initMsg = "?OTR-INIT:$pubKeyB64"
        val sent = xmppManager.sendMessage(accountId, toJid, initMsg)
        return if (sent) "OTR key exchange started — waiting for the other side…"
        else "OTR: could not send key exchange message."
    }

    fun endOtrSession(accountId: Long, toJid: String) {
        otrEntries.remove(accountId to toJid)
        Log.i(TAG, "OTR session ended with $toJid")
    }

    /** True only when the ECDH handshake is complete and messages can be encrypted. */
    fun isOtrSessionEstablished(accountId: Long, toJid: String): Boolean =
        otrEntries[accountId to toJid]?.state == OtrHandshakeState.ESTABLISHED

    fun isOtrSessionActive(accountId: Long, toJid: String): Boolean =
        otrEntries.containsKey(accountId to toJid)

    fun getOtrHandshakeState(accountId: Long, toJid: String): OtrHandshakeState? =
        otrEntries[accountId to toJid]?.state

    /**
     * Inspects an incoming message body for OTR control strings.
     *
     * @return `true` if the message was an OTR control message (should NOT be shown in UI),
     *         `false` if it is a normal/encrypted chat message to display.
     */
    fun handleIncomingMessage(accountId: Long, fromJid: String, body: String): Boolean {
        return when {
            body.startsWith("?OTR-INIT:") -> {
                // Remote started a session — respond with our key and establish session
                val remoteKeyB64 = body.removePrefix("?OTR-INIT:")
                val key = accountId to fromJid
                val session = OtrSession()
                otrEntries[key] = OtrEntry(session, OtrHandshakeState.AWAITING_INIT)

                try {
                    val remoteKeyBytes = android.util.Base64.decode(remoteKeyB64, android.util.Base64.NO_WRAP)
                    session.setRemotePublicKey(remoteKeyBytes)
                    otrEntries[key]!!.state = OtrHandshakeState.ESTABLISHED
                    scope.launch { _otrStateChanges.emit(OtrStateEvent(accountId, fromJid, OtrHandshakeState.ESTABLISHED)) }
                    Log.i(TAG, "OTR session established with $fromJid (we responded)")
                } catch (e: Exception) {
                    Log.e(TAG, "OTR INIT key error from $fromJid", e)
                    otrEntries.remove(key)
                }

                // Always send our public key back as ACK
                val ackMsg = "?OTR-ACK:${session.getPublicKeyBase64()}"
                xmppManager.sendMessage(accountId, fromJid, ackMsg)
                true
            }

            body.startsWith("?OTR-ACK:") -> {
                // Remote acknowledged our INIT — complete our side of the handshake
                val remoteKeyB64 = body.removePrefix("?OTR-ACK:")
                val key = accountId to fromJid
                val entry = otrEntries[key]
                if (entry == null) {
                    Log.w(TAG, "Received OTR-ACK from $fromJid but no pending session")
                    return true
                }
                try {
                    val remoteKeyBytes = android.util.Base64.decode(remoteKeyB64, android.util.Base64.NO_WRAP)
                    entry.session.setRemotePublicKey(remoteKeyBytes)
                    entry.state = OtrHandshakeState.ESTABLISHED
                    scope.launch { _otrStateChanges.emit(OtrStateEvent(accountId, fromJid, OtrHandshakeState.ESTABLISHED)) }
                    Log.i(TAG, "OTR session established with $fromJid (we initiated)")
                } catch (e: Exception) {
                    Log.e(TAG, "OTR ACK key error from $fromJid", e)
                    otrEntries.remove(key)
                }
                true
            }

            body.startsWith("?OTR:") -> {
                // Encrypted OTR message — decrypt and let the caller save it
                false // caller handles it
            }

            else -> false // plain text or other protocol
        }
    }

    /**
     * Decrypts an incoming OTR-encrypted message body.
     * Returns the plaintext, or null if decryption fails.
     */
    fun decryptOtrMessage(accountId: Long, fromJid: String, body: String): String? {
        val entry = otrEntries[accountId to fromJid] ?: return null
        if (entry.state != OtrHandshakeState.ESTABLISHED) return null
        return entry.session.decrypt(body)
    }

    // ─────────────────────────────────────────────────────────────────────
    // OpenPGP
    // ─────────────────────────────────────────────────────────────────────

    fun pgpManager() = pgpManager

    // ─────────────────────────────────────────────────────────────────────
    // Unified send
    // ─────────────────────────────────────────────────────────────────────

    fun sendMessage(
        accountId: Long,
        toJid: String,
        body: String,
        encryptionType: EncryptionType
    ): Pair<Boolean, String?> {
        val connection = xmppManager.getConnection(accountId)
            ?: return false to "Not connected"
        if (!connection.isConnected || !connection.isAuthenticated)
            return false to "Not authenticated"

        return when (encryptionType) {
            EncryptionType.NONE    -> xmppManager.sendMessage(accountId, toJid, body) to null
            EncryptionType.OMEMO   -> sendOmemo(accountId, toJid, body)
            EncryptionType.OTR     -> sendOtr(accountId, toJid, body)
            EncryptionType.OPENPGP -> sendPgp(accountId, toJid, body)
        }
    }

    // ── OMEMO send ────────────────────────────────────────────────────────

    private fun sendOmemo(accountId: Long, toJid: String, body: String): Pair<Boolean, String?> {
        if (!isOmemoReady(accountId)) {
            val notice = when (_omemoState.value[accountId]) {
                OmemoState.INITIALISING -> "OMEMO is still initialising — sent as plain text."
                OmemoState.FAILED       -> "OMEMO failed to initialise — sent as plain text."
                else                    -> "OMEMO not ready — sent as plain text."
            }
            return if (xmppManager.sendMessage(accountId, toJid, body)) true to notice
            else false to "Send failed"
        }
        return try {
            val connection   = xmppManager.getConnection(accountId)!!
            val omemoManager = OmemoManager.getInstanceFor(connection)
            val recipientJid = JidCreate.entityBareFrom(toJid)
            val encrypted    = omemoManager.encrypt(recipientJid, body)
            val stanza = omemoSentToMessage(encrypted, toJid)
            connection.sendStanza(stanza)
            true to null
        } catch (e: UndecidedOmemoIdentityException) {
            Log.w(TAG, "OMEMO undecided identities — degrading to plain text: ${e.message}")
            val sent = xmppManager.sendMessage(accountId, toJid, body)
            if (sent) true to "OMEMO: undecided devices — sent as plain text."
            else false to "Send failed"
        } catch (e: CryptoFailedException) {
            Log.e(TAG, "OMEMO crypto failed", e)
            false to "OMEMO encryption failed: ${e.message}"
        } catch (e: Exception) {
            Log.e(TAG, "OMEMO send error", e)
            false to "OMEMO error: ${e.message}"
        }
    }

    private fun omemoSentToMessage(sent: OmemoMessage.Sent, toJid: String): Message {
        val bareJid = JidCreate.entityBareFrom(toJid)
        for (methodName in listOf("asMessage", "buildMessage", "toMessage", "getMessage")) {
            try {
                val m = sent.javaClass.getMethod(methodName, org.jxmpp.jid.BareJid::class.java)
                val result = m.invoke(sent, bareJid)
                if (result is Message) return result
            } catch (_: NoSuchMethodException) {}
            try {
                val m = sent.javaClass.getMethod(methodName)
                val result = m.invoke(sent)
                if (result is Message) return result
            } catch (_: NoSuchMethodException) {}
        }
        for (m in sent.javaClass.methods) {
            if (Message::class.java.isAssignableFrom(m.returnType) && m.parameterCount <= 1) {
                try {
                    val result = if (m.parameterCount == 0) m.invoke(sent)
                    else m.invoke(sent, bareJid)
                    if (result is Message) return result
                } catch (_: Exception) {}
            }
        }
        throw IllegalStateException("Cannot extract Message from OmemoMessage.Sent")
    }

    // ── OTR send ──────────────────────────────────────────────────────────

    private fun sendOtr(accountId: Long, toJid: String, body: String): Pair<Boolean, String?> {
        val key = accountId to toJid
        val entry = otrEntries[key]

        // If no session at all, start the handshake and inform the user
        if (entry == null) {
            val notice = startOtrSession(accountId, toJid)
            return false to "OTR handshake initiated. $notice Please resend your message once the session is ready."
        }

        // Handshake in progress — can't encrypt yet
        if (entry.state != OtrHandshakeState.ESTABLISHED) {
            return false to "OTR key exchange in progress — please wait a moment and try again."
        }

        return try {
            val ciphertext = entry.session.encrypt(body)
            val sent = xmppManager.sendMessage(accountId, toJid, ciphertext)
            if (sent) true to null
            else false to "Send failed"
        } catch (e: Exception) {
            Log.e(TAG, "OTR encrypt error", e)
            false to "OTR error: ${e.message}"
        }
    }

    // ── OpenPGP send ──────────────────────────────────────────────────────

    private fun sendPgp(accountId: Long, toJid: String, body: String): Pair<Boolean, String?> {
        return try {
            val encrypted = pgpManager.encryptFor(toJid, body)
            if (encrypted != null) {
                val sent = xmppManager.sendMessage(accountId, toJid, encrypted)
                if (sent) true to null
                else false to "Send failed"
            } else {
                val sent = xmppManager.sendMessage(accountId, toJid, body)
                if (sent) true to "OpenPGP: no key for $toJid — sent as plain text."
                else false to "Send failed"
            }
        } catch (e: Exception) {
            Log.e(TAG, "PGP encrypt error", e)
            false to "PGP error: ${e.message}"
        }
    }
}
