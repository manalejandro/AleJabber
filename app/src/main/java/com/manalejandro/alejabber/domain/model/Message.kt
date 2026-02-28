package com.manalejandro.alejabber.domain.model

data class Message(
    val id: Long = 0,
    val stanzaId: String = "",
    val accountId: Long,
    val conversationJid: String,
    val fromJid: String,
    val toJid: String,
    val body: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val direction: MessageDirection,
    val status: MessageStatus = MessageStatus.PENDING,
    val encryptionType: EncryptionType = EncryptionType.NONE,
    val mediaType: MediaType = MediaType.TEXT,
    val mediaUrl: String? = null,
    val mediaLocalPath: String? = null,
    val mediaMimeType: String? = null,
    val mediaSize: Long = 0,
    val mediaName: String? = null,
    val audioDurationMs: Long = 0,
    val isRead: Boolean = false,
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val replyToId: Long? = null
)

enum class MessageDirection { INCOMING, OUTGOING }

enum class MessageStatus {
    PENDING, SENT, DELIVERED, READ, FAILED
}

enum class EncryptionType {
    NONE, OTR, OMEMO, OPENPGP
}

enum class MediaType {
    TEXT, IMAGE, VIDEO, AUDIO, FILE, LINK
}

