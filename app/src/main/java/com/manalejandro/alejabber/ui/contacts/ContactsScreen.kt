package com.manalejandro.alejabber.ui.contacts

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manalejandro.alejabber.R
import com.manalejandro.alejabber.domain.model.Contact
import com.manalejandro.alejabber.domain.model.PresenceStatus
import com.manalejandro.alejabber.ui.components.AvatarWithStatus
import com.manalejandro.alejabber.ui.components.toColor
import com.manalejandro.alejabber.ui.components.toLabel

/**
 * Shows the roster (contact list) for a single XMPP account identified by [accountId].
 *
 * The user navigates here by tapping a connected account in [AccountsScreen].
 * From here, tapping a contact opens [ChatScreen] via [onNavigateToChat].
 *
 * @param accountId       The database id of the account whose contacts are shown.
 * @param onNavigateToChat Called with (accountId, contactJid) when a contact is tapped.
 * @param onNavigateBack  Called when the user presses the back arrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    accountId: Long,
    onNavigateToChat: (Long, String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ContactsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Contact whose info sheet is shown on long-press
    var detailContact by remember { mutableStateOf<Contact?>(null) }
    // Contact pending removal confirmation
    var removeTarget by remember { mutableStateOf<Contact?>(null) }

    LaunchedEffect(accountId) { viewModel.loadForAccount(accountId) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.contacts_title))
                            uiState.accountJid?.let { jid ->
                                Text(
                                    text = jid,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    actions = {
                        // Sync roster from server
                        IconButton(onClick = { viewModel.syncRoster(accountId) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync roster")
                        }
                    }
                )
                // Inline search bar
                SearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = viewModel::onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::showAddDialog,
                modifier = Modifier.semantics { contentDescription = "Add contact" }
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null)
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.filteredContacts.isEmpty() && uiState.searchQuery.isBlank() -> {
                    EmptyState(
                        icon = Icons.Default.People,
                        message = stringResource(R.string.contacts_empty),
                        actionLabel = "Sync now",
                        onAction = { viewModel.syncRoster(accountId) },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.filteredContacts.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Default.SearchOff,
                        message = "No contacts match \"${uiState.searchQuery}\"",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> ContactList(
                    contacts           = uiState.filteredContacts,
                    onContactClick     = { onNavigateToChat(accountId, it.jid) },
                    onContactLongPress = { detailContact = it }
                )
            }
        }
    }

    // Add contact dialog
    if (uiState.showAddDialog) {
        AddContactDialog(
            onDismiss = viewModel::hideAddDialog,
            onAdd = { jid, nickname -> viewModel.addContact(accountId, jid, nickname) }
        )
    }

    // Contact detail sheet (long-press)
    detailContact?.let { contact ->
        ContactDetailSheet(
            contact   = contact,
            onChat    = { onNavigateToChat(accountId, contact.jid); detailContact = null },
            onRemove  = { detailContact = null; removeTarget = contact },
            onDismiss = { detailContact = null }
        )
    }

    // Confirm remove dialog
    removeTarget?.let { contact ->
        val displayName = contact.nickname.ifBlank { contact.jid }
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            icon  = { Icon(Icons.Default.PersonRemove, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Remove contact?") },
            text  = {
                Text(
                    "Remove $displayName from your contact list?\n\n" +
                    "This will also remove them from your roster on the server.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeContact(accountId, contact.jid)
                    removeTarget = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // ── Subscription authorization dialog ─────────────────────────────────
    uiState.pendingSubscriptionJid?.let { fromJid ->
        AlertDialog(
            onDismissRequest = { viewModel.denySubscription() },
            icon  = { Icon(Icons.Default.PersonAdd, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Contact request") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "$fromJid wants to add you as a contact and see your presence status.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Do you want to accept and add them to your contacts?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.acceptSubscription() }) {
                    Icon(Icons.Default.Check, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Accept")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.denySubscription() }) {
                    Icon(Icons.Default.Close, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Deny")
                }
            }
        )
    }
}

// ── Contact Detail Bottom Sheet ───────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailSheet(
    contact: Contact,
    onChat: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState  = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val displayName = contact.nickname.ifBlank { contact.jid }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar
            AvatarWithStatus(
                name               = displayName,
                avatarUrl          = contact.avatarUrl,
                presence           = contact.presence,
                size               = 80.dp,
                contentDescription = displayName
            )
            Spacer(Modifier.height(12.dp))
            // Name
            Text(displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            // JID
            Text(
                contact.jid,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            // Presence badge
            Surface(
                shape = RoundedCornerShape(50),
                color = contact.presence.toColor().copy(alpha = 0.15f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(contact.presence.toColor())
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text  = contact.presence.toLabel(),
                        style = MaterialTheme.typography.labelMedium,
                        color = contact.presence.toColor()
                    )
                }
            }
            // Status message
            if (contact.statusMessage.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "\"${contact.statusMessage}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Groups
            if (contact.groups.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Groups: ${contact.groups.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            // Action: Chat
            ListItem(
                headlineContent = { Text("Start chat") },
                leadingContent  = { Icon(Icons.AutoMirrored.Filled.Chat, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable(onClick = onChat)
            )
            // Action: Remove
            ListItem(
                headlineContent = { Text("Remove contact", color = MaterialTheme.colorScheme.error) },
                leadingContent  = { Icon(Icons.Default.PersonRemove, null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable(onClick = onRemove)
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ─── Reusable composables ─────────────────────────────────────────────────

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.contacts_search)) },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Clear, null) }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
    )
}

@Composable
fun ContactList(
    contacts: List<Contact>,
    onContactClick: (Contact) -> Unit,
    onContactLongPress: (Contact) -> Unit
) {
    val presenceOrder = listOf(
        PresenceStatus.ONLINE, PresenceStatus.AWAY, PresenceStatus.DND,
        PresenceStatus.XA, PresenceStatus.OFFLINE
    )
    val deduplicated = contacts
        .groupBy { it.jid }
        .map { (_, dupes) ->
            dupes.minByOrNull { presenceOrder.indexOf(it.presence).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }!!
        }
    val grouped = deduplicated.groupBy { it.presence }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        presenceOrder.forEach { presence ->
            val group = grouped[presence] ?: return@forEach
            if (group.isEmpty()) return@forEach
            item(key = "header_${presence.name}") {
                Text(
                    text = "${presence.toLabel()} (${group.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = presence.toColor(),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            items(group, key = { "${presence.name}_${it.jid}" }) { contact ->
                ContactItem(
                    contact     = contact,
                    onClick     = { onContactClick(contact) },
                    onLongPress = { onContactLongPress(contact) }
                )
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactItem(
    contact: Contact,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val displayName = contact.nickname.ifBlank { contact.jid }

    ListItem(
        headlineContent  = { Text(displayName, fontWeight = FontWeight.Medium) },
        supportingContent = {
            Text(
                contact.statusMessage.ifBlank { contact.presence.toLabel() },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            AvatarWithStatus(
                name               = displayName,
                avatarUrl          = contact.avatarUrl,
                presence           = contact.presence,
                contentDescription = stringResource(R.string.cd_avatar, displayName)
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .animateContentSize()
            .semantics { contentDescription = "Chat with $displayName" }
    )
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun AddContactDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var jid by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_contact)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = jid, onValueChange = { jid = it },
                    label = { Text(stringResource(R.string.contact_jid)) },
                    placeholder = { Text("user@example.com") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = nickname, onValueChange = { nickname = it },
                    label = { Text(stringResource(R.string.contact_nickname)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (jid.isNotBlank()) onAdd(jid.trim(), nickname.trim()) }, enabled = jid.contains("@")) {
                Text(stringResource(R.string.add_contact))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.outline)
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null && onAction != null) {
            FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

