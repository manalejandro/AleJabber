package com.manalejandro.alejabber.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jid: String,
    val password: String,
    val server: String = "",
    val port: Int = 5222,
    val useTls: Boolean = true,
    val resource: String = "AleJabber",
    val isEnabled: Boolean = true,
    val statusMessage: String = "",
    val avatarUrl: String? = null
)

