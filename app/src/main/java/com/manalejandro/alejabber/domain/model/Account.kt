package com.manalejandro.alejabber.domain.model

data class Account(
    val id: Long = 0,
    val jid: String,
    val password: String,
    val server: String = "",
    val port: Int = 5222,
    val useTls: Boolean = true,
    val resource: String = "AleJabber",
    val isEnabled: Boolean = true,
    val status: ConnectionStatus = ConnectionStatus.OFFLINE,
    val statusMessage: String = "",
    val avatarUrl: String? = null
)

enum class ConnectionStatus {
    ONLINE, AWAY, DND, OFFLINE, CONNECTING, ERROR
}

