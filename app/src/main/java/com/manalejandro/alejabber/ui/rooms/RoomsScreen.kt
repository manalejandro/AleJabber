package com.manalejandro.alejabber.ui.rooms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manalejandro.alejabber.R
import com.manalejandro.alejabber.domain.model.Account
import com.manalejandro.alejabber.domain.model.Room
import com.manalejandro.alejabber.ui.components.InitialsAvatar
import com.manalejandro.alejabber.ui.contacts.EmptyState

/**
 * Displays joined MUC rooms for all connected accounts.
 *
 * If no account is connected the screen shows an instructional empty-state instead
 * of the room list, and the FAB is hidden (there's no server to join a room on).
 * Once at least one account is online the FAB appears and lets the user join a room.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomsScreen(
    onNavigateToRoom: (Long, String) -> Unit,
    viewModel: RoomsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var roomToLeave by remember { mutableStateOf<Room?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.rooms_title)) })
        },
        floatingActionButton = {
            // Only show FAB when there is at least one connected account
            if (uiState.hasConnectedAccount) {
                FloatingActionButton(
                    onClick = viewModel::showJoinDialog,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor  = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, stringResource(R.string.join_room))
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                // ── Loading ────────────────────────────────────────────────
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                // ── No connected account ───────────────────────────────────
                !uiState.hasConnectedAccount -> {
                    EmptyState(
                        icon    = Icons.Default.CloudOff,
                        message = "Connect to an XMPP account first.\n" +
                                "Go to Accounts, then tap the cloud icon to connect.",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // ── Connected but no rooms yet ─────────────────────────────
                uiState.rooms.isEmpty() -> {
                    EmptyState(
                        icon        = Icons.Default.Forum,
                        message     = stringResource(R.string.rooms_empty),
                        actionLabel = stringResource(R.string.join_room),
                        onAction    = viewModel::showJoinDialog,
                        modifier    = Modifier.align(Alignment.Center)
                    )
                }

                // ── Room list ──────────────────────────────────────────────
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        modifier       = Modifier.fillMaxSize()
                    ) {
                        items(uiState.rooms, key = { "${it.accountId}_${it.jid}" }) { room ->
                            RoomItem(
                                room    = room,
                                onClick = { onNavigateToRoom(room.accountId, room.jid) },
                                onLeave = { roomToLeave = room }
                            )
                        }
                        item { Spacer(Modifier.height(88.dp)) }
                    }
                }
            }
        }
    }

    // ── Confirm leave room dialog ──────────────────────────────────────────
    roomToLeave?.let { room ->
        val displayName = room.name.ifBlank { room.jid }
        AlertDialog(
            onDismissRequest = { roomToLeave = null },
            icon  = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Leave room?") },
            text  = {
                Text(
                    "Leave \"$displayName\"?\n\nYou will no longer receive messages from this room.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.leaveRoom(room.accountId, room.jid)
                    roomToLeave = null
                }) { Text("Leave", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { roomToLeave = null }) { Text("Cancel") }
            }
        )
    }

    // ── Join-room dialog ───────────────────────────────────────────────────
    if (uiState.showJoinDialog) {
        JoinRoomDialog(
            connectedAccounts = uiState.connectedAccounts,
            onDismiss         = viewModel::hideJoinDialog,
            onJoin            = { accountId, jid, nickname, password ->
                viewModel.joinRoom(accountId, jid, nickname, password)
            }
        )
    }

    // ── Error snackbar ─────────────────────────────────────────────────────
    uiState.error?.let { msg ->
        LaunchedEffect(msg) { viewModel.clearError() }
    }
}

// ─── RoomItem ──────────────────────────────────────────────────────────────

@Composable
fun RoomItem(room: Room, onClick: () -> Unit, onLeave: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    val displayName = room.name.ifBlank { room.jid }

    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(displayName, fontWeight = FontWeight.Medium)
                if (room.unreadCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Badge { Text(room.unreadCount.toString()) }
                }
            }
        },
        supportingContent = {
            Text(
                room.topic.ifBlank { room.lastMessage.ifBlank { room.jid } },
                style   = MaterialTheme.typography.bodySmall,
                color   = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        },
        leadingContent  = { InitialsAvatar(name = displayName, size = 48.dp) },
        trailingContent = {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, stringResource(R.string.more_options))
                }
                DropdownMenu(
                    expanded         = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text        = {
                            Text(
                                stringResource(R.string.leave_room),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.ExitToApp, null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = { menuExpanded = false; onLeave() }
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

// ─── JoinRoomDialog ────────────────────────────────────────────────────────

/**
 * Dialog for joining a MUC room.
 * [connectedAccounts] are the only accounts eligible — disconnected ones are excluded.
 */
@Composable
fun JoinRoomDialog(
    connectedAccounts: List<Account>,
    onDismiss: () -> Unit,
    onJoin: (Long, String, String, String) -> Unit
) {
    var selectedAccountId by remember {
        mutableLongStateOf(connectedAccounts.firstOrNull()?.id ?: 0L)
    }
    var roomJid  by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var accountMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.join_room)) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // Account selector (always shown so user knows which account is used)
                Box {
                    OutlinedTextField(
                        value       = connectedAccounts.find { it.id == selectedAccountId }?.jid ?: "",
                        onValueChange = {},
                        readOnly    = true,
                        label       = { Text("Account") },
                        trailingIcon = {
                            IconButton(onClick = { accountMenuExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded         = accountMenuExpanded,
                        onDismissRequest = { accountMenuExpanded = false }
                    ) {
                        connectedAccounts.forEach { acc ->
                            DropdownMenuItem(
                                text    = { Text(acc.jid) },
                                onClick = { selectedAccountId = acc.id; accountMenuExpanded = false }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value         = roomJid,
                    onValueChange = { roomJid = it },
                    label         = { Text(stringResource(R.string.room_jid)) },
                    placeholder   = { Text("room@conference.example.com") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value         = nickname,
                    onValueChange = { nickname = it },
                    label         = { Text(stringResource(R.string.room_nickname)) },
                    placeholder   = { Text("Your display name in the room") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value         = password,
                    onValueChange = { password = it },
                    label         = { Text(stringResource(R.string.room_password) + " (optional)") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (roomJid.isNotBlank() && nickname.isNotBlank())
                        onJoin(selectedAccountId, roomJid.trim(), nickname.trim(), password)
                },
                enabled = roomJid.contains("@") && nickname.isNotBlank()
            ) { Text(stringResource(R.string.join_room)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
