package com.manalejandro.alejabber.ui.rooms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manalejandro.alejabber.data.repository.AccountRepository
import com.manalejandro.alejabber.data.repository.RoomRepository
import com.manalejandro.alejabber.domain.model.Account
import com.manalejandro.alejabber.domain.model.ConnectionStatus
import com.manalejandro.alejabber.domain.model.Room
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RoomsUiState(
    /** All accounts, used to detect connected ones. */
    val accounts: List<Account> = emptyList(),
    val rooms: List<Room> = emptyList(),
    val isLoading: Boolean = true,
    val showJoinDialog: Boolean = false,
    val error: String? = null
) {
    /** True when at least one account has an active XMPP connection. */
    val hasConnectedAccount: Boolean
        get() = accounts.any {
            it.status == ConnectionStatus.ONLINE ||
                    it.status == ConnectionStatus.AWAY ||
                    it.status == ConnectionStatus.DND
        }

    /** Only accounts that are currently connected — used to populate the join-room dialog. */
    val connectedAccounts: List<Account>
        get() = accounts.filter {
            it.status == ConnectionStatus.ONLINE ||
                    it.status == ConnectionStatus.AWAY ||
                    it.status == ConnectionStatus.DND
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RoomsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val roomRepository: RoomRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoomsUiState(isLoading = true))
    val uiState: StateFlow<RoomsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            accountRepository.getAllAccounts().collect { accounts ->
                _uiState.update { it.copy(accounts = accounts) }
                if (accounts.isNotEmpty()) loadRooms(accounts.map { a -> a.id })
                else _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun loadRooms(accountIds: List<Long>) {
        viewModelScope.launch {
            val flows = accountIds.map { id -> roomRepository.getRooms(id) }
            combine(flows) { arrays -> arrays.flatMap { it } }
                .collect { rooms ->
                    _uiState.update { it.copy(rooms = rooms, isLoading = false) }
                }
        }
    }

    fun showJoinDialog() = _uiState.update { it.copy(showJoinDialog = true) }
    fun hideJoinDialog() = _uiState.update { it.copy(showJoinDialog = false) }

    fun joinRoom(accountId: Long, roomJid: String, nickname: String, password: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val success = roomRepository.joinRoom(accountId, roomJid, nickname, password)
                if (success) {
                    roomRepository.saveRoom(
                        Room(accountId = accountId, jid = roomJid, nickname = nickname, isJoined = true)
                    )
                }
                _uiState.update { it.copy(isLoading = false, showJoinDialog = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun leaveRoom(accountId: Long, roomJid: String) {
        viewModelScope.launch { roomRepository.leaveRoom(accountId, roomJid) }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}
