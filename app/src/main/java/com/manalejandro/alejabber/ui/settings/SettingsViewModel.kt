package com.manalejandro.alejabber.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val defaultEncryption: EncryptionType = EncryptionType.OMEMO
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>
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
                        appTheme = try { AppTheme.valueOf(prefs[KEY_THEME] ?: "SYSTEM") } catch (e: Exception) { AppTheme.SYSTEM },
                        notificationsEnabled = prefs[KEY_NOTIFICATIONS] ?: true,
                        vibrateEnabled = prefs[KEY_VIBRATE] ?: true,
                        soundEnabled = prefs[KEY_SOUND] ?: true,
                        defaultEncryption = try { EncryptionType.valueOf(prefs[KEY_DEFAULT_ENCRYPTION] ?: "OMEMO") } catch (e: Exception) { EncryptionType.OMEMO }
                    )
                }
            }
        }
    }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch {
            dataStore.edit { it[KEY_THEME] = theme.name }
        }
    }

    fun setNotifications(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { it[KEY_NOTIFICATIONS] = enabled }
        }
    }

    fun setVibrate(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { it[KEY_VIBRATE] = enabled }
        }
    }

    fun setSound(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { it[KEY_SOUND] = enabled }
        }
    }

    fun setDefaultEncryption(type: EncryptionType) {
        viewModelScope.launch {
            dataStore.edit { it[KEY_DEFAULT_ENCRYPTION] = type.name }
        }
    }
}

