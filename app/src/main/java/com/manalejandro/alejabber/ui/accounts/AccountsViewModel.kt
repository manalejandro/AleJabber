package com.manalejandro.alejabber.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manalejandro.alejabber.data.repository.AccountRepository
import com.manalejandro.alejabber.domain.model.Account
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountsUiState(
    val accounts: List<Account> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountsUiState())
    val uiState: StateFlow<AccountsUiState> = _uiState.asStateFlow()

    init {
        loadAccounts()
    }

    private fun loadAccounts() {
        viewModelScope.launch {
            accountRepository.getAllAccounts().collect { accounts ->
                _uiState.update { it.copy(accounts = accounts, isLoading = false) }
            }
        }
    }

    fun connectAccount(account: Account) {
        accountRepository.connectAccount(account)
    }

    fun disconnectAccount(accountId: Long) {
        accountRepository.disconnectAccount(accountId)
    }

    fun deleteAccount(accountId: Long) {
        viewModelScope.launch {
            accountRepository.deleteAccount(accountId)
        }
    }
}

// ViewModel for Add/Edit Account
data class AddEditAccountUiState(
    val jid: String = "",
    val password: String = "",
    val server: String = "",
    val port: String = "5222",
    val useTls: Boolean = true,
    val resource: String = "AleJabber",
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AddEditAccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditAccountUiState())
    val uiState: StateFlow<AddEditAccountUiState> = _uiState.asStateFlow()

    fun loadAccount(id: Long) {
        viewModelScope.launch {
            val account = accountRepository.getAccountById(id) ?: return@launch
            _uiState.update {
                it.copy(
                    jid = account.jid,
                    password = account.password,
                    server = account.server,
                    port = account.port.toString(),
                    useTls = account.useTls,
                    resource = account.resource
                )
            }
        }
    }

    fun updateJid(v: String) = _uiState.update { it.copy(jid = v) }
    fun updatePassword(v: String) = _uiState.update { it.copy(password = v) }
    fun updateServer(v: String) = _uiState.update { it.copy(server = v) }
    fun updatePort(v: String) = _uiState.update { it.copy(port = v) }
    fun updateUseTls(v: Boolean) = _uiState.update { it.copy(useTls = v) }
    fun updateResource(v: String) = _uiState.update { it.copy(resource = v) }

    fun saveAccount(existingId: Long? = null) {
        val state = _uiState.value
        if (state.jid.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(error = "JID and password are required") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val account = Account(
                    id = existingId ?: 0,
                    jid = state.jid.trim(),
                    password = state.password,
                    server = state.server.trim(),
                    port = state.port.toIntOrNull() ?: 5222,
                    useTls = state.useTls,
                    resource = state.resource.ifBlank { "AleJabber" }
                )
                if (existingId != null) {
                    accountRepository.updateAccount(account)
                } else {
                    accountRepository.addAccount(account)
                }
                _uiState.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}

