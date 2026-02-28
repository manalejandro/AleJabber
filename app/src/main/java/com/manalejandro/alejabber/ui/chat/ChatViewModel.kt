package com.manalejandro.alejabber.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manalejandro.alejabber.data.remote.EncryptionManager
import com.manalejandro.alejabber.data.repository.ContactRepository
import com.manalejandro.alejabber.data.repository.MessageRepository
import com.manalejandro.alejabber.domain.model.*
import com.manalejandro.alejabber.media.AudioRecorder
import com.manalejandro.alejabber.media.HttpUploadManager
import com.manalejandro.alejabber.media.RecordingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val contactName: String = "",
    val contactPresence: PresenceStatus = PresenceStatus.OFFLINE,
    val contactAvatarUrl: String? = null,
    val inputText: String = "",
    val encryptionType: EncryptionType = EncryptionType.NONE,
    val isTyping: Boolean = false,
    val isUploading: Boolean = false,
    val recordingState: RecordingState = RecordingState.IDLE,
    val recordingDurationMs: Long = 0,
    val showEncryptionPicker: Boolean = false,
    val omemoState: EncryptionManager.OmemoState = EncryptionManager.OmemoState.IDLE,
    val otrActive: Boolean = false,
    val otrHandshakeState: EncryptionManager.OtrHandshakeState? = null,
    val pgpHasOwnKey: Boolean = false,
    val pgpHasContactKey: Boolean = false,
    val error: String? = null,
    val info: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val contactRepository: ContactRepository,
    private val httpUploadManager: HttpUploadManager,
    private val audioRecorder: AudioRecorder,
    private val encryptionManager: EncryptionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentAccountId: Long = 0
    private var currentJid: String = ""

    fun init(accountId: Long, jid: String) {
        currentAccountId = accountId
        currentJid = jid

        viewModelScope.launch {
            messageRepository.getMessages(accountId, jid).collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
        viewModelScope.launch {
            // Keep contact info (name, presence, avatar) live
            contactRepository.getContacts(accountId).collect { contacts ->
                val contact = contacts.find { it.jid == jid }
                _uiState.update {
                    it.copy(
                        contactName       = contact?.nickname?.ifBlank { jid } ?: jid,
                        contactPresence   = contact?.presence ?: PresenceStatus.OFFLINE,
                        contactAvatarUrl  = contact?.avatarUrl
                    )
                }
            }
        }
        viewModelScope.launch {
            messageRepository.markAllAsRead(accountId, jid)
        }
        viewModelScope.launch {
            audioRecorder.state.collect { state ->
                _uiState.update { it.copy(recordingState = state) }
            }
        }
        viewModelScope.launch {
            audioRecorder.durationMs.collect { ms ->
                _uiState.update { it.copy(recordingDurationMs = ms) }
            }
        }
        // Observe OMEMO state — updates UI badge in real time
        viewModelScope.launch {
            encryptionManager.omemoState.collect { stateMap ->
                val s = stateMap[accountId] ?: EncryptionManager.OmemoState.IDLE
                _uiState.update { it.copy(omemoState = s) }
            }
        }
        // Observe OTR handshake state changes
        viewModelScope.launch {
            encryptionManager.otrStateChanges.collect { event: EncryptionManager.OtrStateEvent ->
                val evAccountId = event.accountId
                val evJid       = event.jid
                val evState     = event.state
                if (evAccountId == accountId && evJid == currentJid) {
                    val established = evState == EncryptionManager.OtrHandshakeState.ESTABLISHED
                    _uiState.update { s ->
                        s.copy(
                            otrHandshakeState = evState,
                            info = if (established)
                                "OTR session established — messages are now encrypted end-to-end." else null
                        )
                    }
                }
            }
        }
        // Initialise OMEMO if needed
        viewModelScope.launch {
            encryptionManager.initOmemo(accountId)
        }
        // Check PGP key availability
        val pgp = encryptionManager.pgpManager()
        _uiState.update {
            it.copy(
                pgpHasOwnKey      = pgp.hasOwnKey(),
                pgpHasContactKey  = pgp.loadContactPublicKeyArmored(jid) != null,
                otrActive         = encryptionManager.isOtrSessionActive(accountId, jid),
                otrHandshakeState = encryptionManager.getOtrHandshakeState(accountId, jid)
            )
        }
    }

    fun onInputChange(text: String) = _uiState.update { it.copy(inputText = text) }

    fun sendTextMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank()) return
        _uiState.update { it.copy(inputText = "") }
        viewModelScope.launch {
            sendWithEncryption(text)
        }
    }

    private suspend fun sendWithEncryption(body: String) {
        val encType = _uiState.value.encryptionType

        if (encType == EncryptionType.NONE) {
            // Plain text — delegate entirely to MessageRepository (handles PENDING→SENT/FAILED)
            withContext(Dispatchers.IO) {
                messageRepository.sendMessage(
                    accountId      = currentAccountId,
                    toJid          = currentJid,
                    body           = body,
                    encryptionType = EncryptionType.NONE
                )
            }
            return
        }

        // ── Encrypted path ────────────────────────────────────────────────
        // 1. Insert PENDING immediately so the message appears in the UI right away.
        val localId = withContext(Dispatchers.IO) {
            messageRepository.saveOutgoingMessage(
                Message(
                    accountId       = currentAccountId,
                    conversationJid = currentJid,
                    fromJid         = "",
                    toJid           = currentJid,
                    body            = body,
                    direction       = MessageDirection.OUTGOING,
                    status          = MessageStatus.PENDING,
                    encryptionType  = encType
                )
            )
        }

        // 2. Send the encrypted stanza on IO thread (network I/O).
        val (ok, notice) = withContext(Dispatchers.IO) {
            encryptionManager.sendMessage(
                accountId      = currentAccountId,
                toJid          = currentJid,
                body           = body,
                encryptionType = encType
            )
        }

        // 3. Update the persisted message status.
        withContext(Dispatchers.IO) {
            messageRepository.updateMessageStatus(
                id     = localId,
                status = if (ok) MessageStatus.SENT else MessageStatus.FAILED
            )
            Unit
        }

        // 4. Notify the user.
        if (ok) {
            notice?.let { _uiState.update { s -> s.copy(info = it) } }
        } else {
            _uiState.update { it.copy(error = notice ?: "Send failed") }
            // Put the text back so the user can retry
            _uiState.update { it.copy(inputText = body) }
        }
    }

    fun sendFile(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true) }
            try {
                val url = httpUploadManager.uploadFile(currentAccountId, uri)
                if (url != null) {
                    messageRepository.sendMessage(
                        accountId      = currentAccountId,
                        toJid          = currentJid,
                        body           = url,
                        encryptionType = _uiState.value.encryptionType
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Upload failed: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isUploading = false) }
            }
        }
    }

    fun startRecording(): Boolean = audioRecorder.startRecording()

    fun stopAndSendRecording() {
        viewModelScope.launch {
            val file = audioRecorder.stopRecording() ?: return@launch
            _uiState.update { it.copy(isUploading = true) }
            try {
                val url = httpUploadManager.uploadFile(currentAccountId, file, "audio/mp4")
                if (url != null) {
                    messageRepository.sendMessage(
                        accountId      = currentAccountId,
                        toJid          = currentJid,
                        body           = url,
                        encryptionType = _uiState.value.encryptionType
                    )
                }
                audioRecorder.reset()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Audio upload failed: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isUploading = false) }
            }
        }
    }

    fun cancelRecording() = audioRecorder.cancelRecording()

    fun setEncryption(type: EncryptionType) {
        val omemoState = _uiState.value.omemoState
        val pgpHasOwn  = _uiState.value.pgpHasOwnKey
        val pgpHasCont = _uiState.value.pgpHasContactKey

        val result: Pair<EncryptionType, String?> = when (type) {
            EncryptionType.OMEMO -> when (omemoState) {
                EncryptionManager.OmemoState.READY -> type to null
                EncryptionManager.OmemoState.INITIALISING ->
                    type to "OMEMO is initialising — messages will be encrypted once ready."
                EncryptionManager.OmemoState.FAILED ->
                    EncryptionType.NONE to "OMEMO failed to initialise. Check server PubSub support."
                else ->
                    type to "OMEMO is starting up…"
            }
            EncryptionType.OTR -> {
                // startOtrSession sends the INIT message and returns a status string.
                // The session transitions to ESTABLISHED only after the remote ACK arrives.
                val notice = encryptionManager.startOtrSession(currentAccountId, currentJid)
                _uiState.update { it.copy(otrActive = true) }
                type to notice
            }
            EncryptionType.OPENPGP -> when {
                !pgpHasOwn  -> EncryptionType.NONE to "OpenPGP: You don't have a key pair. Generate one in Settings → Encryption."
                !pgpHasCont -> type to "OpenPGP: No public key for this contact. Ask them to share their key in Settings → Encryption."
                else        -> type to null
            }
            EncryptionType.NONE -> {
                if (_uiState.value.encryptionType == EncryptionType.OTR) {
                    encryptionManager.endOtrSession(currentAccountId, currentJid)
                    _uiState.update { it.copy(otrActive = false) }
                }
                type to null
            }
        }
        val finalType = result.first
        val notice    = result.second
        _uiState.update {
            it.copy(
                encryptionType       = finalType,
                showEncryptionPicker = false,
                info                 = notice
            )
        }
    }

    fun toggleEncryptionPicker() = _uiState.update {
        it.copy(showEncryptionPicker = !it.showEncryptionPicker)
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch { messageRepository.deleteMessage(messageId) }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
    fun clearInfo()  = _uiState.update { it.copy(info  = null) }
}
