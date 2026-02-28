package com.manalejandro.alejabber.ui.accounts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manalejandro.alejabber.R

/**
 * Form screen to add a new XMPP account or edit an existing one.
 *
 * @param accountId       Null when creating; the database id when editing.
 * @param onNavigateBack  Called when the user presses Back or after a successful save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditAccountScreen(
    accountId: Long?,
    onNavigateBack: () -> Unit,
    viewModel: AddEditAccountViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(accountId) { accountId?.let { viewModel.loadAccount(it) } }
    LaunchedEffect(uiState.isSaved) { if (uiState.isSaved) onNavigateBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (accountId == null) stringResource(R.string.add_account)
                        else stringResource(R.string.edit_account)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // JID (user@domain)
            OutlinedTextField(
                value = uiState.jid,
                onValueChange = viewModel::updateJid,
                label = { Text(stringResource(R.string.account_username)) },
                placeholder = { Text(stringResource(R.string.account_jid_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                isError = uiState.jid.isNotBlank() && !uiState.jid.contains("@"),
                supportingText = {
                    if (uiState.jid.isNotBlank() && !uiState.jid.contains("@"))
                        Text("Must be in user@domain format")
                }
            )
            // Password
            OutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::updatePassword,
                label = { Text(stringResource(R.string.account_password)) },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            contentDescription = "Toggle password visibility"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            // Server override (optional)
            OutlinedTextField(
                value = uiState.server,
                onValueChange = viewModel::updateServer,
                label = { Text(stringResource(R.string.account_server) + " (optional)") },
                placeholder = { Text("xmpp.example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Leave blank to use DNS SRV lookup") }
            )
            // Port
            OutlinedTextField(
                value = uiState.port,
                onValueChange = viewModel::updatePort,
                label = { Text(stringResource(R.string.account_port)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            // Resource name
            OutlinedTextField(
                value = uiState.resource,
                onValueChange = viewModel::updateResource,
                label = { Text(stringResource(R.string.account_resource)) },
                placeholder = { Text(stringResource(R.string.account_resource_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            // TLS toggle
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            stringResource(R.string.account_use_tls),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Require an encrypted TLS connection",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.useTls,
                        onCheckedChange = viewModel::updateUseTls
                    )
                }
            }
            // Error banner
            uiState.error?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // Save button
            Button(
                onClick = { viewModel.saveAccount(accountId) },
                enabled = !uiState.isLoading && uiState.jid.contains("@") && uiState.password.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.save), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
