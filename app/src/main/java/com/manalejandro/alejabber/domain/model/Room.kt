package com.manalejandro.alejabber.domain.model

data class Room(
    val id: Long = 0,
    val accountId: Long,
    val jid: String,
    val nickname: String = "",
    val name: String = "",
    val description: String = "",
    val topic: String = "",
    val password: String = "",
    val isJoined: Boolean = false,
    val isFavorite: Boolean = false,
    val participantCount: Int = 0,
    val avatarUrl: String? = null,
    val unreadCount: Int = 0,
    val lastMessage: String = "",
    val lastMessageTime: Long = 0
)

data class RoomParticipant(
    val jid: String,
    val nickname: String,
    val role: ParticipantRole = ParticipantRole.PARTICIPANT,
    val affiliation: ParticipantAffiliation = ParticipantAffiliation.NONE,
    val presence: PresenceStatus = PresenceStatus.ONLINE
)

enum class ParticipantRole {
    MODERATOR, PARTICIPANT, VISITOR, NONE
}

enum class ParticipantAffiliation {
    OWNER, ADMIN, MEMBER, OUTCAST, NONE
}

