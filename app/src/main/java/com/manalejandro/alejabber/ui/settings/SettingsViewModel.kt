package com.manalejandro.alejabber.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manalejandro.alejabber.data.remote.EncryptionManager
import com.manalejandro.alejabber.data.remote.PgpManager
import com.manalejandro.alejabber.domain.model.EncryptionType
import com.manalejandro.alejabber.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val appTheme: AppTheme = AppTheme.SYSTEM,
    val notificationsEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val defaultEncryption: EncryptionType = EncryptionType.OMEMO,
    // OMEMO
    val omemoFingerprint: String? = null,
    // OTR (informational only — sessions are per-chat)
    // OpenPGP
    val pgpHasOwnKey: Boolean = false,
    val pgpOwnKeyFingerprint: String? = null,
    val pgpContactKeys: List<String> = emptyList(),    // JIDs with stored pub key
    val pgpError: String? = null,
    val pgpInfo: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val encryptionManager: EncryptionManager
) : ViewModel() {

    companion object {
        val KEY_THEME = stringPreferencesKey("app_theme")
        val KEY_NOTIFICATIONS = booleanPreferencesKey("notifications")
        val KEY_VIBRATE = booleanPreferencesKey("vibrate")
        val KEY_SOUND = booleanPreferencesKey("sound")
        val KEY_DEFAULT_ENCRYPTION = stringPreferencesKey("default_encryption")
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            dataStore.data.collect { prefs ->
                _uiState.update { state ->
                    state.copy(
                        appTheme = try { AppTheme.valueOf(prefs[KEY_THEME] ?: "SYSTEM") } catch (_: Exception) { AppTheme.SYSTEM },
                        notificationsEnabled = prefs[KEY_NOTIFICATIONS] ?: true,
                        vibrateEnabled = prefs[KEY_VIBRATE] ?: true,
                        soundEnabled = prefs[KEY_SOUND] ?: true,
                        defaultEncryption = try { EncryptionType.valueOf(prefs[KEY_DEFAULT_ENCRYPTION] ?: "OMEMO") } catch (_: Exception) { EncryptionType.OMEMO }
                    )
                }
            }
        }
        refreshPgpState()
    }

    private fun refreshPgpState() {
        val pgp = encryptionManager.pgpManager()
        _uiState.update {
            it.copy(
                pgpHasOwnKey        = pgp.hasOwnKey(),
                pgpOwnKeyFingerprint = pgp.getOwnKeyFingerprint(),
                pgpContactKeys      = pgp.listContactsWithKeys()
            )
        }
    }

    // ── Preferences ───────────────────────────────────────────────────────

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch { dataStore.edit { it[KEY_THEME] = theme.name } }
    }
    fun setNotifications(enabled: Boolean) {
        viewModelScope.launch { dataStore.edit { it[KEY_NOTIFICATIONS] = enabled } }
    }
    fun setVibrate(enabled: Boolean) {
        viewModelScope.launch { dataStore.edit { it[KEY_VIBRATE] = enabled } }
    }
    fun setSound(enabled: Boolean) {
        viewModelScope.launch { dataStore.edit { it[KEY_SOUND] = enabled } }
    }
    fun setDefaultEncryption(type: EncryptionType) {
        viewModelScope.launch { dataStore.edit { it[KEY_DEFAULT_ENCRYPTION] = type.name } }
    }

    // ── OMEMO ─────────────────────────────────────────────────────────────

    fun refreshOmemoFingerprint(accountId: Long) {
        val fp = encryptionManager.getOwnOmemoFingerprint(accountId)
        _uiState.update { it.copy(omemoFingerprint = fp) }
    }

    // ── OpenPGP ───────────────────────────────────────────────────────────

    /** Import the user's own armored secret key (from file or paste). */
    fun importOwnPgpKey(armoredKey: String) {
        viewModelScope.launch {
            try {
                encryptionManager.pgpManager().saveOwnSecretKeyArmored(armoredKey)
                refreshPgpState()
                _uiState.update { it.copy(pgpInfo = "Own PGP key imported successfully.") }
            } catch (e: Exception) {
                _uiState.update { it.copy(pgpError = "Key import failed: ${e.message}") }
            }
        }
    }

    /** Delete the user's own key pair. */
    fun deleteOwnPgpKey() {
        viewModelScope.launch {
            encryptionManager.pgpManager().saveOwnSecretKeyArmored("") // overwrite with empty
            refreshPgpState()
        }
    }

    /** Import a contact's public key. */
    fun importContactPgpKey(jid: String, armoredKey: String) {
        viewModelScope.launch {
            try {
                encryptionManager.pgpManager().saveContactPublicKey(jid, armoredKey)
                refreshPgpState()
                _uiState.update { it.copy(pgpInfo = "Public key for $jid saved.") }
            } catch (e: Exception) {
                _uiState.update { it.copy(pgpError = "Key import failed: ${e.message}") }
            }
        }
    }

    /** Remove a contact's public key. */
    fun deleteContactPgpKey(jid: String) {
        encryptionManager.pgpManager().deleteContactPublicKey(jid)
        refreshPgpState()
    }

    fun clearPgpMessages() = _uiState.update { it.copy(pgpError = null, pgpInfo = null) }
}
