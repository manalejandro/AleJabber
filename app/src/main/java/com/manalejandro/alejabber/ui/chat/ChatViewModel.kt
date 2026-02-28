package com.manalejandro.alejabber.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val inputText: String = "",
    val encryptionType: EncryptionType = EncryptionType.NONE,
    val isTyping: Boolean = false,
    val isUploading: Boolean = false,
    val recordingState: RecordingState = RecordingState.IDLE,
    val recordingDurationMs: Long = 0,
    val showEncryptionPicker: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val contactRepository: ContactRepository,
    private val httpUploadManager: HttpUploadManager,
    private val audioRecorder: AudioRecorder
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentAccountId: Long = 0
    private var currentJid: String = ""

    fun init(accountId: Long, jid: String) {
        currentAccountId = accountId
        currentJid = jid

        viewModelScope.launch {
            // Load messages
            messageRepository.getMessages(accountId, jid).collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
        viewModelScope.launch {
            // Load contact info
            contactRepository.getContacts(accountId)
                .take(1)
                .collect { contacts ->
                    val contact = contacts.find { it.jid == jid }
                    _uiState.update {
                        it.copy(
                            contactName = contact?.nickname?.ifBlank { jid } ?: jid,
                            contactPresence = contact?.presence ?: PresenceStatus.OFFLINE
                        )
                    }
                }
        }
        viewModelScope.launch {
            // Mark as read
            messageRepository.markAllAsRead(accountId, jid)
        }
        // Observe recording state
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
    }

    fun onInputChange(text: String) = _uiState.update { it.copy(inputText = text) }

    fun sendTextMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank()) return
        _uiState.update { it.copy(inputText = "") }
        viewModelScope.launch {
            messageRepository.sendMessage(
                accountId = currentAccountId,
                toJid = currentJid,
                body = text,
                encryptionType = _uiState.value.encryptionType
            )
        }
    }

    fun sendFile(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true) }
            try {
                val url = httpUploadManager.uploadFile(currentAccountId, uri)
                if (url != null) {
                    messageRepository.sendMessage(
                        accountId = currentAccountId,
                        toJid = currentJid,
                        body = url,
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
                        accountId = currentAccountId,
                        toJid = currentJid,
                        body = url,
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

    fun setEncryption(type: EncryptionType) = _uiState.update {
        it.copy(encryptionType = type, showEncryptionPicker = false)
    }

    fun toggleEncryptionPicker() = _uiState.update {
        it.copy(showEncryptionPicker = !it.showEncryptionPicker)
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch { messageRepository.deleteMessage(messageId) }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}


