package com.manalejandro.alejabber.data.remote

import android.content.Context
import android.util.Log
import com.manalejandro.alejabber.domain.model.EncryptionType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
 * - OMEMO  : implemented via smack-omemo-signal (Signal Protocol / XEP-0384).
 *            Uses TOFU trust model — all new identities are trusted on first use.
 *            `initializeAsync` is used so the UI thread is never blocked.
 * - OTR    : implemented from scratch with BouncyCastle ECDH + AES-256-CTR.
 *            Session state is kept in memory. Keys are ephemeral per session.
 * - OpenPGP: encrypt with the recipient's public key stored via the Settings screen.
 *            Signing is done with the user's own private key (also in Settings).
 */
@Singleton
class EncryptionManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val xmppManager: XmppConnectionManager
) {
    private val TAG = "EncryptionManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ── OMEMO state per account ───────────────────────────────────────────
    enum class OmemoState { IDLE, INITIALISING, READY, FAILED }

    private val _omemoState = MutableStateFlow<Map<Long, OmemoState>>(emptyMap())
    val omemoState: StateFlow<Map<Long, OmemoState>> = _omemoState.asStateFlow()

    private var omemoServiceSetup = false

    // ── OTR sessions: (accountId, bareJid) → OtrSession ──────────────────
    private val otrSessions = mutableMapOf<Pair<Long, String>, OtrSession>()

    // ── OpenPGP manager ───────────────────────────────────────────────────
    private val pgpManager = PgpManager(context)

    // ─────────────────────────────────────────────────────────────────────
    // OMEMO
    // ─────────────────────────────────────────────────────────────────────

    /** Initialise OMEMO asynchronously for [accountId]. */
    fun initOmemo(accountId: Long) {
        val state = _omemoState.value[accountId]
        if (state == OmemoState.READY || state == OmemoState.INITIALISING) return
        val connection = xmppManager.getConnection(accountId) ?: return
        if (!connection.isAuthenticated) return

        _omemoState.update { it + (accountId to OmemoState.INITIALISING) }
        scope.launch {
            try {
                if (!omemoServiceSetup) {
                    SignalOmemoService.acknowledgeLicense()
                    SignalOmemoService.setup()
                    omemoServiceSetup = true
                }
                val omemoManager = OmemoManager.getInstanceFor(connection)
                // TOFU trust callback — trust every new identity on first encounter
                omemoManager.setTrustCallback(object : OmemoTrustCallback {
                    override fun getTrust(
                        device: org.jivesoftware.smackx.omemo.internal.OmemoDevice,
                        fingerprint: OmemoFingerprint
                    ): TrustState = TrustState.trusted

                    override fun setTrust(
                        device: org.jivesoftware.smackx.omemo.internal.OmemoDevice,
                        fingerprint: OmemoFingerprint,
                        state: TrustState
                    ) { /* TOFU: ignore */ }
                })
                // Register incoming OMEMO message listener
                omemoManager.addOmemoMessageListener(object : OmemoMessageListener {
                    override fun onOmemoMessageReceived(
                        stanza: org.jivesoftware.smack.packet.Stanza,
                        decryptedMessage: OmemoMessage.Received
                    ) {
                        val from = stanza.from?.asBareJid()?.toString() ?: return
                        val body = decryptedMessage.body ?: return
                        scope.launch {
                            xmppManager.dispatchDecryptedOmemoMessage(accountId, from, body)
                        }
                    }

                    override fun onOmemoCarbonCopyReceived(
                        direction: CarbonExtension.Direction,
                        carbonCopy: Message,
                        wrappingMessage: Message,
                        decryptedCarbonCopy: OmemoMessage.Received
                    ) {
                        val from = carbonCopy.from?.asBareJid()?.toString() ?: return
                        val body = decryptedCarbonCopy.body ?: return
                        scope.launch {
                            xmppManager.dispatchDecryptedOmemoMessage(accountId, from, body)
                        }
                    }
                })
                // Use async initialisation so we never block the IO thread during PubSub
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
        }
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
        } catch (e: Exception) { null }
    }

    // ─────────────────────────────────────────────────────────────────────
    // OTR
    // ─────────────────────────────────────────────────────────────────────

    /** Start or resume an OTR session with [toJid]. */
    fun startOtrSession(accountId: Long, toJid: String) {
        val key = accountId to toJid
        if (otrSessions.containsKey(key)) return
        otrSessions[key] = OtrSession()
        Log.i(TAG, "OTR session started with $toJid")
    }

    fun endOtrSession(accountId: Long, toJid: String) {
        otrSessions.remove(accountId to toJid)
        Log.i(TAG, "OTR session ended with $toJid")
    }

    fun isOtrSessionActive(accountId: Long, toJid: String) =
        otrSessions.containsKey(accountId to toJid)

    // ─────────────────────────────────────────────────────────────────────
    // OpenPGP
    // ─────────────────────────────────────────────────────────────────────

    fun pgpManager() = pgpManager

    // ─────────────────────────────────────────────────────────────────────
    // Unified send
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Sends [body] to [toJid] with the specified [encryptionType].
     * Returns (success: Boolean, notice: String?) where notice is a
     * non-null informational message when the call partially succeeded
     * (e.g. degraded to plain text).
     */
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
            // OmemoMessage.Sent — obtain the smack Message via reflection to avoid
            // depending on the exact method name which differs between smack versions.
            val stanza = omemoSentToMessage(encrypted, toJid)
            connection.sendStanza(stanza)
            true to null
        } catch (e: UndecidedOmemoIdentityException) {
            // TOFU: trust all undecided identities then retry — the trust callback
            // already trusts everything on get(), but the exception might still be
            // thrown if devices were added mid-flight. Just degrade gracefully.
            Log.w(TAG, "OMEMO undecided identities — degrading to plain text: ${e.message}")
            val sent = xmppManager.sendMessage(accountId, toJid, body)
            if (sent) true to "OMEMO: undecided devices — sent as plain text. Open Settings to manage trust."
            else false to "Send failed"
        } catch (e: CryptoFailedException) {
            Log.e(TAG, "OMEMO crypto failed", e)
            false to "OMEMO encryption failed: ${e.message}"
        } catch (e: Exception) {
            Log.e(TAG, "OMEMO send error", e)
            false to "OMEMO error: ${e.message}"
        }
    }

    /**
     * Converts an [OmemoMessage.Sent] to a [Message] stanza ready to send.
     *
     * Smack 4.4.x exposes the wrapped Message via one of several method names
     * depending on the exact patch version. We try known names via reflection
     * so the code compiles and runs regardless of minor API changes.
     */
    private fun omemoSentToMessage(sent: OmemoMessage.Sent, toJid: String): Message {
        val bareJid = JidCreate.entityBareFrom(toJid)
        // Try method names used in different smack 4.4.x versions
        for (methodName in listOf("asMessage", "buildMessage", "toMessage", "getMessage")) {
            try {
                val m = sent.javaClass.getMethod(methodName,
                    org.jxmpp.jid.BareJid::class.java)
                val result = m.invoke(sent, bareJid)
                if (result is Message) return result
            } catch (_: NoSuchMethodException) {}
            try {
                val m = sent.javaClass.getMethod(methodName)
                val result = m.invoke(sent)
                if (result is Message) return result
            } catch (_: NoSuchMethodException) {}
        }
        // Last resort: look for any method returning Message
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
        val session = otrSessions.getOrPut(key) { OtrSession() }
        return try {
            val ciphertext = session.encrypt(body)
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
                // No recipient key — fall back to plain
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
