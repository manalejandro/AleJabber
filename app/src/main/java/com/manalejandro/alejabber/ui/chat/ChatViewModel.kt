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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
        // Initialise OMEMO if needed
        viewModelScope.launch {
            encryptionManager.initOmemo(accountId)
        }
        // Check PGP key availability
        val pgp = encryptionManager.pgpManager()
        _uiState.update {
            it.copy(
                pgpHasOwnKey     = pgp.hasOwnKey(),
                pgpHasContactKey = pgp.loadContactPublicKeyArmored(jid) != null,
                otrActive        = encryptionManager.isOtrSessionActive(accountId, jid)
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
            // Fast path — plain text via MessageRepository
            messageRepository.sendMessage(
                accountId      = currentAccountId,
                toJid          = currentJid,
                body           = body,
                encryptionType = EncryptionType.NONE
            )
            return
        }
        // Encrypted path — EncryptionManager handles sending the stanza;
        // we still persist the message locally.
        val (ok, errorMsg) = encryptionManager.sendMessage(
            accountId      = currentAccountId,
            toJid          = currentJid,
            body           = body,
            encryptionType = encType
        )
        if (ok) {
            // Persist as sent (EncryptionManager already sent the stanza)
            val msg = Message(
                accountId       = currentAccountId,
                conversationJid = currentJid,
                fromJid         = "",
                toJid           = currentJid,
                body            = body,
                direction       = MessageDirection.OUTGOING,
                status          = MessageStatus.SENT,
                encryptionType  = encType
            )
            messageRepository.saveOutgoingMessage(msg)
            errorMsg?.let { notice ->
                _uiState.update { it.copy(info = notice) }
            }
        } else {
            _uiState.update { it.copy(error = errorMsg ?: "Send failed") }
            // Revert input so user can retry
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

        val (finalType, notice) = when (type) {
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
                // Start OTR session
                encryptionManager.startOtrSession(currentAccountId, currentJid)
                _uiState.update { it.copy(otrActive = true) }
                type to "OTR session started. Messages are encrypted end-to-end."
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
