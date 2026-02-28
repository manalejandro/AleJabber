package com.manalejandro.alejabber.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manalejandro.alejabber.data.repository.AccountRepository
import com.manalejandro.alejabber.data.repository.ContactRepository
import com.manalejandro.alejabber.domain.model.Contact
import com.manalejandro.alejabber.domain.model.PresenceStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for [ContactsScreen].
 *
 * @property accountJid      JID of the account being displayed, shown in the toolbar subtitle.
 * @property allContacts     Full roster list from Room for this account.
 * @property filteredContacts Roster filtered by [searchQuery].
 * @property isLoading       True while contacts are being fetched.
 * @property searchQuery     Current text in the search bar.
 * @property showAddDialog   Whether the add-contact dialog is visible.
 * @property error           Non-null when an operation failed.
 */
data class ContactsUiState(
    val accountJid: String? = null,
    val allContacts: List<Contact> = emptyList(),
    val filteredContacts: List<Contact> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val showAddDialog: Boolean = false,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val contactRepository: ContactRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    private val searchQuery = MutableStateFlow("")

    /**
     * Load contacts for the given [accountId].
     * Safe to call multiple times; cancels the previous collection.
     */
    fun loadForAccount(accountId: Long) {
        viewModelScope.launch {
            // Resolve the JID for the toolbar subtitle
            val account = accountRepository.getAccountById(accountId)
            _uiState.update { it.copy(accountJid = account?.jid, isLoading = true) }

            // Observe contacts + search query together
            contactRepository.getContacts(accountId)
                .combine(searchQuery) { contacts, query ->
                    contacts to query
                }
                .debounce(80)
                .collect { (contacts, query) ->
                    // Deduplicate by JID — the roster sync can insert the same JID
                    // multiple times (e.g. once as OFFLINE seed + once from the server).
                    // Keep the entry whose presence is most available.
                    val presenceRank = mapOf(
                        PresenceStatus.ONLINE to 0, PresenceStatus.AWAY to 1,
                        PresenceStatus.DND to 2, PresenceStatus.XA to 3,
                        PresenceStatus.OFFLINE to 4
                    )
                    val deduped = contacts
                        .groupBy { it.jid }
                        .map { (_, dupes) ->
                            dupes.minByOrNull { presenceRank[it.presence] ?: 5 }!!
                        }

                    val filtered = if (query.isBlank()) deduped
                    else deduped.filter {
                        it.jid.contains(query, ignoreCase = true) ||
                                it.nickname.contains(query, ignoreCase = true)
                    }
                    _uiState.update {
                        it.copy(
                            allContacts      = deduped,
                            filteredContacts = filtered,
                            isLoading        = false
                        )
                    }
                }
        }
    }

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun showAddDialog()  = _uiState.update { it.copy(showAddDialog = true) }
    fun hideAddDialog()  = _uiState.update { it.copy(showAddDialog = false) }

    fun addContact(accountId: Long, jid: String, nickname: String) {
        viewModelScope.launch {
            try {
                contactRepository.addContact(
                    Contact(
                        accountId     = accountId,
                        jid           = jid,
                        nickname      = nickname,
                        presence      = PresenceStatus.OFFLINE,
                        statusMessage = "",
                        avatarUrl     = null,
                        groups        = emptyList()
                    )
                )
                hideAddDialog()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to add contact: ${e.message}") }
            }
        }
    }

    fun removeContact(accountId: Long, jid: String) {
        viewModelScope.launch {
            try {
                contactRepository.removeContact(accountId, jid)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to remove contact: ${e.message}") }
            }
        }
    }

    fun syncRoster(accountId: Long) {
        viewModelScope.launch {
            try {
                contactRepository.syncRoster(accountId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Sync failed: ${e.message}") }
            }
        }
    }
}

