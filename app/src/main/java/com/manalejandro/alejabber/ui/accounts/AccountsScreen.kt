package com.manalejandro.alejabber.ui.accounts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manalejandro.alejabber.R
import com.manalejandro.alejabber.domain.model.Account
import com.manalejandro.alejabber.domain.model.ConnectionStatus
import com.manalejandro.alejabber.domain.model.PresenceStatus
import com.manalejandro.alejabber.ui.components.AvatarWithStatus
import com.manalejandro.alejabber.ui.theme.StatusAway
import com.manalejandro.alejabber.ui.theme.StatusDnd
import com.manalejandro.alejabber.ui.theme.StatusOffline
import com.manalejandro.alejabber.ui.theme.StatusOnline

/**
 * Displays all configured XMPP accounts and lets the user:
 *  - Add a new account (FAB → [onAddAccount])
 *  - Connect / disconnect each account
 *  - Tap a connected account to browse its contacts ([onOpenContacts])
 *  - Edit or delete an account via the overflow menu
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    onAddAccount: () -> Unit,
    onEditAccount: (Long) -> Unit,
    onOpenContacts: (Long) -> Unit,
    viewModel: AccountsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<Account?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.accounts_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            // Always visible FAB to add a new account
            FloatingActionButton(
                onClick = onAddAccount,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.semantics {
                    contentDescription = "Add new account"
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.accounts.isEmpty()) {
                // Empty-state prompt
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.account_no_accounts),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Tap + to add your first XMPP account",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onAddAccount) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.add_account))
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.accounts, key = { it.id }) { account ->
                        AccountCard(
                            account    = account,
                            onConnect  = { viewModel.connectAccount(account) },
                            onDisconnect = { viewModel.disconnectAccount(account.id) },
                            onEdit     = { onEditAccount(account.id) },
                            onDelete   = { deleteTarget = account },
                            onOpen     = { onOpenContacts(account.id) }
                        )
                    }
                    // Extra bottom padding so FAB doesn't overlap last item
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }

            // Loading overlay
            AnimatedVisibility(
                visible = uiState.isLoading,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                CircularProgressIndicator()
            }
        }
    }

    // ── Delete confirmation ───────────────────────────────────────────────────
    deleteTarget?.let { account ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_account)) },
            text  = { Text(stringResource(R.string.account_delete_confirm, account.jid)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAccount(account.id)
                    deleteTarget = null
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * A card representing one XMPP account.
 *
 * Tapping the card (when the account is connected) calls [onOpen].
 * The connect/disconnect button controls the XMPP connection.
 * The overflow menu exposes edit and delete actions.
 */
@Composable
fun AccountCard(
    account: Account,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isOnline = account.status == ConnectionStatus.ONLINE ||
            account.status == ConnectionStatus.AWAY ||
            account.status == ConnectionStatus.DND

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isOnline, onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOnline)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isOnline) 4.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with presence dot — uses AvatarWithStatus so it shows
            // a real photo when the account has a vcard avatar URL
            AvatarWithStatus(
                name               = account.jid,
                avatarUrl          = account.avatarUrl,
                presence           = account.status.toPresenceStatus(),
                size               = 48.dp,
                contentDescription = "Avatar for ${account.jid}"
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.jid,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = account.status.toLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isOnline) {
                    Text(
                        text = "Tap to view contacts →",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Connect / Disconnect / Connecting
            when (account.status) {
                ConnectionStatus.OFFLINE, ConnectionStatus.ERROR -> {
                    IconButton(
                        onClick = onConnect,
                        modifier = Modifier.semantics { contentDescription = "Connect" }
                    ) {
                        Icon(
                            Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                ConnectionStatus.CONNECTING -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .padding(4.dp),
                        strokeWidth = 2.dp
                    )
                }
                else -> {
                    IconButton(
                        onClick = onDisconnect,
                        modifier = Modifier.semantics { contentDescription = "Disconnect" }
                    ) {
                        Icon(
                            Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Overflow menu
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit_account)) },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { menuExpanded = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.delete_account),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete, null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}

/** Maps [ConnectionStatus] to its indicator colour. */
fun ConnectionStatus.toColor(): Color = when (this) {
    ConnectionStatus.ONLINE     -> StatusOnline
    ConnectionStatus.AWAY       -> StatusAway
    ConnectionStatus.DND        -> StatusDnd
    ConnectionStatus.OFFLINE    -> StatusOffline
    ConnectionStatus.CONNECTING -> StatusAway
    ConnectionStatus.ERROR      -> Color(0xFFF44336)
}

/** Maps [ConnectionStatus] to the equivalent [PresenceStatus] for [AvatarWithStatus]. */
fun ConnectionStatus.toPresenceStatus(): PresenceStatus = when (this) {
    ConnectionStatus.ONLINE     -> PresenceStatus.ONLINE
    ConnectionStatus.AWAY       -> PresenceStatus.AWAY
    ConnectionStatus.DND        -> PresenceStatus.DND
    ConnectionStatus.CONNECTING -> PresenceStatus.AWAY
    ConnectionStatus.OFFLINE,
    ConnectionStatus.ERROR      -> PresenceStatus.OFFLINE
}

/** Human-readable label for a [ConnectionStatus]. */
fun ConnectionStatus.toLabel(): String = when (this) {
    ConnectionStatus.ONLINE     -> "Online"
    ConnectionStatus.AWAY       -> "Away"
    ConnectionStatus.DND        -> "Do Not Disturb"
    ConnectionStatus.OFFLINE    -> "Offline"
    ConnectionStatus.CONNECTING -> "Connecting…"
    ConnectionStatus.ERROR      -> "Connection error – tap to retry"
}
