package com.manalejandro.alejabber.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manalejandro.alejabber.R
import com.manalejandro.alejabber.domain.model.EncryptionType
import com.manalejandro.alejabber.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showThemeDialog     by remember { mutableStateOf(false) }
    var showEncryptionDialog by remember { mutableStateOf(false) }
    // PGP dialogs
    var showPgpImportOwnDialog     by remember { mutableStateOf(false) }
    var showPgpImportContactDialog by remember { mutableStateOf(false) }
    var pgpContactJid              by remember { mutableStateOf("") }
    var pgpKeyText                 by remember { mutableStateOf("") }
    var showPgpContactDeleteDialog by remember { mutableStateOf<String?>(null) }

    // File picker for PGP key import
    val keyFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val text = context.contentResolver.openInputStream(it)?.bufferedReader()?.readText() ?: return@let
            pgpKeyText = text
        }
    }

    LaunchedEffect(uiState.pgpError) {
        uiState.pgpError?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
            viewModel.clearPgpMessages()
        }
    }
    LaunchedEffect(uiState.pgpInfo) {
        uiState.pgpInfo?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearPgpMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Appearance ────────────────────────────────────────────────
            SettingsSectionHeader(stringResource(R.string.settings_appearance))
            SettingsItem(
                icon     = Icons.Default.Palette,
                title    = stringResource(R.string.settings_theme),
                subtitle = uiState.appTheme.toDisplayName(),
                onClick  = { showThemeDialog = true }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ── Notifications ─────────────────────────────────────────────
            SettingsSectionHeader(stringResource(R.string.settings_notifications))
            SettingsSwitchItem(
                icon           = Icons.Default.Notifications,
                title          = stringResource(R.string.settings_notifications_messages),
                checked        = uiState.notificationsEnabled,
                onCheckedChange = viewModel::setNotifications
            )
            SettingsSwitchItem(
                icon           = Icons.Default.Vibration,
                title          = stringResource(R.string.settings_notifications_vibrate),
                checked        = uiState.vibrateEnabled,
                onCheckedChange = viewModel::setVibrate
            )
            SettingsSwitchItem(
                icon           = Icons.AutoMirrored.Filled.VolumeUp,
                title          = stringResource(R.string.settings_notifications_sound),
                checked        = uiState.soundEnabled,
                onCheckedChange = viewModel::setSound
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ── Encryption — default ──────────────────────────────────────
            SettingsSectionHeader(stringResource(R.string.settings_encryption))
            SettingsItem(
                icon     = Icons.Default.Lock,
                title    = stringResource(R.string.settings_default_encryption),
                subtitle = uiState.defaultEncryption.name,
                onClick  = { showEncryptionDialog = true }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ── OMEMO ─────────────────────────────────────────────────────
            SettingsSectionHeader("OMEMO (XEP-0384)")
            ListItem(
                headlineContent   = { Text("OMEMO Device Fingerprint") },
                supportingContent = {
                    Text(
                        uiState.omemoFingerprint ?: "Not available — open a chat to initialise",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingContent = { Icon(Icons.Default.Fingerprint, null) }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ── OTR ───────────────────────────────────────────────────────
            SettingsSectionHeader("OTR (Off-The-Record)")
            ListItem(
                headlineContent   = { Text("OTR Sessions") },
                supportingContent = {
                    Text(
                        "OTR sessions are established per conversation.\n" +
                        "Select OTR encryption in any chat to start a session.\n" +
                        "Sessions use ephemeral ECDH keys — perfect forward secrecy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingContent = { Icon(Icons.Default.SwapHoriz, null) }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ── OpenPGP ───────────────────────────────────────────────────
            SettingsSectionHeader("OpenPGP")

            // Own key status
            ListItem(
                headlineContent   = { Text("My PGP Key Pair") },
                supportingContent = {
                    if (uiState.pgpHasOwnKey) {
                        Column {
                            Text("Fingerprint:", style = MaterialTheme.typography.labelSmall)
                            Text(
                                uiState.pgpOwnKeyFingerprint ?: "—",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            "No key pair. Import an armored secret key (.asc).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                leadingContent  = { Icon(Icons.Default.Key, null) },
                trailingContent = {
                    if (uiState.pgpHasOwnKey) {
                        IconButton(onClick = { viewModel.deleteOwnPgpKey() }) {
                            Icon(Icons.Default.Delete, "Delete own key",
                                tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        TextButton(onClick = { showPgpImportOwnDialog = true }) {
                            Text("Import")
                        }
                    }
                }
            )

            // Import own key button (also visible via trailing if no key)
            if (!uiState.pgpHasOwnKey) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick   = { showPgpImportOwnDialog = true },
                        modifier  = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Paste key")
                    }
                    OutlinedButton(
                        onClick   = { keyFilePicker.launch("*/*") },
                        modifier  = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("From file")
                    }
                }
            }

            // Contact public keys
            Spacer(Modifier.height(8.dp))
            ListItem(
                headlineContent   = { Text("Contact Public Keys") },
                supportingContent = {
                    if (uiState.pgpContactKeys.isEmpty()) {
                        Text(
                            "No contact keys stored.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column {
                            uiState.pgpContactKeys.forEach { jid ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        jid,
                                        style    = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick  = { showPgpContactDeleteDialog = jid },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete, "Remove",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                leadingContent = { Icon(Icons.Default.People, null) }
            )
            OutlinedButton(
                onClick   = { showPgpImportContactDialog = true },
                modifier  = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.PersonAdd, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add contact public key")
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ── About ─────────────────────────────────────────────────────
            SettingsSectionHeader(stringResource(R.string.settings_about))
            SettingsItem(
                icon    = Icons.Default.Info,
                title   = stringResource(R.string.settings_version),
                subtitle = "1.0.0",
                onClick = {}
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    // ── Theme dialog ──────────────────────────────────────────────────────
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringResource(R.string.settings_theme)) },
            text = {
                Column {
                    AppTheme.entries.forEach { theme ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setTheme(theme); showThemeDialog = false }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.appTheme == theme,
                                onClick  = { viewModel.setTheme(theme); showThemeDialog = false }
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(theme.toDisplayName())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // ── Default encryption dialog ─────────────────────────────────────────
    if (showEncryptionDialog) {
        AlertDialog(
            onDismissRequest = { showEncryptionDialog = false },
            title = { Text(stringResource(R.string.settings_default_encryption)) },
            text = {
                Column {
                    EncryptionType.entries.forEach { type ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setDefaultEncryption(type); showEncryptionDialog = false }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.defaultEncryption == type,
                                onClick  = { viewModel.setDefaultEncryption(type); showEncryptionDialog = false }
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(type.name, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEncryptionDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // ── PGP import own key ────────────────────────────────────────────────
    if (showPgpImportOwnDialog) {
        AlertDialog(
            onDismissRequest = { showPgpImportOwnDialog = false; pgpKeyText = "" },
            title = { Text("Import My PGP Secret Key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Paste your armored PGP secret key below (-----BEGIN PGP PRIVATE KEY BLOCK-----…).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value         = pgpKeyText,
                        onValueChange = { pgpKeyText = it },
                        label         = { Text("Armored PGP key") },
                        modifier      = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        maxLines      = 8
                    )
                    TextButton(onClick = { keyFilePicker.launch("*/*") }) {
                        Icon(Icons.Default.FolderOpen, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Pick from file")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.importOwnPgpKey(pgpKeyText)
                    showPgpImportOwnDialog = false
                    pgpKeyText = ""
                }, enabled = pgpKeyText.isNotBlank()) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPgpImportOwnDialog = false; pgpKeyText = "" }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // ── PGP import contact key ────────────────────────────────────────────
    if (showPgpImportContactDialog) {
        AlertDialog(
            onDismissRequest = { showPgpImportContactDialog = false; pgpKeyText = ""; pgpContactJid = "" },
            title = { Text("Add Contact Public Key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value         = pgpContactJid,
                        onValueChange = { pgpContactJid = it },
                        label         = { Text("Contact JID") },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true
                    )
                    OutlinedTextField(
                        value         = pgpKeyText,
                        onValueChange = { pgpKeyText = it },
                        label         = { Text("Armored public key") },
                        modifier      = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        maxLines      = 8
                    )
                    TextButton(onClick = { keyFilePicker.launch("*/*") }) {
                        Icon(Icons.Default.FolderOpen, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Pick from file")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.importContactPgpKey(pgpContactJid.trim(), pgpKeyText)
                        showPgpImportContactDialog = false
                        pgpKeyText = ""; pgpContactJid = ""
                    },
                    enabled = pgpContactJid.isNotBlank() && pgpKeyText.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showPgpImportContactDialog = false; pgpKeyText = ""; pgpContactJid = "" }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // ── PGP delete contact key confirmation ───────────────────────────────
    showPgpContactDeleteDialog?.let { jid ->
        AlertDialog(
            onDismissRequest = { showPgpContactDeleteDialog = null },
            title = { Text("Remove key?") },
            text  = { Text("Remove the PGP public key for $jid?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteContactPgpKey(jid)
                    showPgpContactDeleteDialog = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showPgpContactDeleteDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text     = title,
        style    = MaterialTheme.typography.labelLarge,
        color    = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp)
    )
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String = "",
    onClick: () -> Unit
) {
    ListItem(
        headlineContent   = { Text(title) },
        supportingContent = if (subtitle.isNotBlank()) {
            { Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else null,
        leadingContent    = { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier          = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun SettingsSwitchItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent  = { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) }
    )
}

fun AppTheme.toDisplayName(): String = when (this) {
    AppTheme.SYSTEM -> "System Default"
    AppTheme.LIGHT  -> "Light"
    AppTheme.DARK   -> "Dark"
}
