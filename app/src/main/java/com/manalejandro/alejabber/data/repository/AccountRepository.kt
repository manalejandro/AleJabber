package com.manalejandro.alejabber.data.repository

import com.manalejandro.alejabber.data.local.dao.AccountDao
import com.manalejandro.alejabber.data.local.entity.toDomain
import com.manalejandro.alejabber.data.local.entity.toEntity
import com.manalejandro.alejabber.data.remote.XmppConnectionManager
import com.manalejandro.alejabber.domain.model.Account
import com.manalejandro.alejabber.domain.model.ConnectionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val xmppManager: XmppConnectionManager
) {
    fun getAllAccounts(): Flow<List<Account>> {
        return accountDao.getAllAccounts()
            .combine(xmppManager.connectionStatus) { entities, statusMap ->
                entities.map { entity ->
                    entity.toDomain(statusMap[entity.id] ?: ConnectionStatus.OFFLINE)
                }
            }
    }

    suspend fun getAccountById(id: Long): Account? =
        accountDao.getAccountById(id)?.toDomain(
            xmppManager.connectionStatus.value[id] ?: ConnectionStatus.OFFLINE
        )

    suspend fun addAccount(account: Account): Long {
        val id = accountDao.insertAccount(account.toEntity())
        val savedAccount = account.copy(id = id)
        if (savedAccount.isEnabled) {
            xmppManager.connect(savedAccount)
        }
        return id
    }

    suspend fun updateAccount(account: Account) {
        accountDao.updateAccount(account.toEntity())
    }

    suspend fun deleteAccount(id: Long) {
        xmppManager.disconnect(id)
        accountDao.deleteAccountById(id)
    }

    fun connectAccount(account: Account) = xmppManager.connect(account)

    fun disconnectAccount(accountId: Long) = xmppManager.disconnect(accountId)

    fun isConnected(accountId: Long) = xmppManager.isConnected(accountId)
}

