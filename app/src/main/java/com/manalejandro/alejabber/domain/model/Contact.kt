package com.manalejandro.alejabber.domain.model

data class Contact(
    val id: Long = 0,
    val accountId: Long,
    val jid: String,
    val nickname: String = "",
    val groups: List<String> = emptyList(),
    val presence: PresenceStatus = PresenceStatus.OFFLINE,
    val statusMessage: String = "",
    val avatarUrl: String? = null,
    val isBlocked: Boolean = false,
    val subscriptionState: SubscriptionState = SubscriptionState.NONE
)

enum class PresenceStatus {
    ONLINE, AWAY, DND, XA, OFFLINE
}

enum class SubscriptionState {
    NONE, PENDING_OUT, PENDING_IN, BOTH, REMOVE
}

