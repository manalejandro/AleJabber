package com.manalejandro.alejabber.ui.chat

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.manalejandro.alejabber.R
import com.manalejandro.alejabber.domain.model.*
import com.manalejandro.alejabber.media.RecordingState
import com.manalejandro.alejabber.ui.components.AvatarWithStatus
import com.manalejandro.alejabber.ui.components.EncryptionBadge
import com.manalejandro.alejabber.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ChatScreen(
    accountId: Long,
    conversationJid: String,
    isRoom: Boolean = false,
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val clipboard = LocalClipboard.current

    // Message selected via long-press → shows the action bottom sheet
    var selectedMessage by remember { mutableStateOf<Message?>(null) }
    // Confirm-delete dialog
    var messageToDelete by remember { mutableStateOf<Message?>(null) }

    // File picker
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.sendFile(it) } }

    LaunchedEffect(accountId, conversationJid) {
        viewModel.init(accountId, conversationJid)
    }

    // Scroll to bottom on new message
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarWithStatus(
                            name = uiState.contactName,
                            avatarUrl = null,
                            presence = uiState.contactPresence,
                            size = 36.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                uiState.contactName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            AnimatedVisibility(visible = uiState.isTyping) {
                                Text(
                                    stringResource(R.string.chat_typing, uiState.contactName),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                },
                actions = {
                    // Encryption badge — shown as a pill (lock icon + label).
                    // Must NOT be inside an IconButton because IconButton clips
                    // its content to 48×48 dp, hiding the text label.
                    EncryptionBadge(
                        encryptionType = uiState.encryptionType,
                        modifier = Modifier
                            .clickable { viewModel.toggleEncryptionPicker() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
        // No bottomBar — input is placed inside the content column so imePadding works
    ) { padding ->
        // imePadding() here at the column level makes the whole content
        // (messages + input bar) shift up when the soft keyboard appears.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            // ── Message list (takes all remaining space) ─────────────────
            Box(modifier = Modifier.weight(1f)) {
                if (uiState.messages.isEmpty()) {
                    Text(
                        text = stringResource(R.string.chat_empty),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val grouped = uiState.messages.groupBy { msg ->
                            SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                                .format(Date(msg.timestamp))
                        }
                        grouped.forEach { (date, msgs) ->
                            item { DateDivider(date) }
                            items(msgs, key = { it.id }) { message ->
                                MessageBubble(
                                    message = message,
                                    onLongPress = { selectedMessage = message }
                                )
                            }
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }

                // Upload progress banner
                if (uiState.isUploading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                    )
                }
            }

            // ── Input bar (always at bottom, above keyboard) ─────────────
            ChatInput(
                text = uiState.inputText,
                onTextChange = viewModel::onInputChange,
                onSend = viewModel::sendTextMessage,
                onAttach = { filePicker.launch("*/*") },
                onStartRecording = {
                    if (micPermission.status.isGranted) viewModel.startRecording()
                    else micPermission.launchPermissionRequest()
                },
                onStopRecording = viewModel::stopAndSendRecording,
                onCancelRecording = viewModel::cancelRecording,
                recordingState = uiState.recordingState,
                recordingDuration = uiState.recordingDurationMs,
                isUploading = uiState.isUploading
            )
        }
    }

    // ── Message action bottom sheet ───────────────────────────────────────
    selectedMessage?.let { msg ->
        MessageActionsSheet(
            message   = msg,
            clipboard = clipboard,
            onDelete       = {
                selectedMessage = null
                messageToDelete = msg
            },
            onDismiss      = { selectedMessage = null }
        )
    }

    // ── Confirm delete dialog ─────────────────────────────────────────────
    messageToDelete?.let { msg ->
        AlertDialog(
            onDismissRequest = { messageToDelete = null },
            icon  = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete message?") },
            text  = {
                Text(
                    "This will remove the message from this device only. " +
                            "The recipient may still have a copy.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMessage(msg.id)
                    messageToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { messageToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // ── Encryption picker ─────────────────────────────────────────────────
    if (uiState.showEncryptionPicker) {
        EncryptionPickerDialog(
            current  = uiState.encryptionType,
            onSelect = viewModel::setEncryption,
            onDismiss = viewModel::toggleEncryptionPicker
        )
    }

    // ── Error snackbar ────────────────────────────────────────────────────
    uiState.error?.let { LaunchedEffect(it) { viewModel.clearError() } }
}

// ── URL regex ─────────────────────────────────────────────────────────────
private val URL_PATTERN: Pattern = Pattern.compile(
    "(https?://|www\\.)[\\w\\-]+(\\.[\\w\\-]+)+([\\w.,@?^=%&:/~+#\\-_]*[\\w@?^=%&/~+#\\-_])?"
)

/** Converts a plain string into an [AnnotatedString] with clickable URL spans (modern API). */
fun buildMessageText(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    val matcher = URL_PATTERN.matcher(text)
    var last = 0
    while (matcher.find()) {
        append(text.substring(last, matcher.start()))
        val url = matcher.group()
        val fullUrl = if (url.startsWith("http")) url else "https://$url"
        // Use LinkAnnotation.Url — opens the browser automatically on click.
        withLink(
            LinkAnnotation.Url(
                url    = fullUrl,
                styles = TextLinkStyles(
                    style = SpanStyle(
                        color          = linkColor,
                        textDecoration = TextDecoration.Underline
                    )
                )
            )
        ) { append(url) }
        last = matcher.end()
    }
    append(text.substring(last))
}

@Composable
fun MessageBubble(message: Message, onLongPress: () -> Unit) {
    val isOutgoing = message.direction == MessageDirection.OUTGOING
    val darkTheme   = isSystemInDarkTheme()

    val bubbleColor = when {
        isOutgoing && darkTheme -> BubbleSentDark
        isOutgoing              -> BubbleSent
        darkTheme               -> BubbleReceivedDark
        else                    -> BubbleReceived
    }
    val textColor = if (isOutgoing) Color.White else MaterialTheme.colorScheme.onSurface
    // URL links are always white on sent bubbles, primary on received
    val linkColor = if (isOutgoing) Color.White else MaterialTheme.colorScheme.primary

    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isOutgoing) 48.dp else 0.dp,
                end   = if (isOutgoing) 0.dp else 48.dp
            ),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart    = 18.dp, topEnd      = 18.dp,
                        bottomStart = if (isOutgoing) 18.dp else 4.dp,
                        bottomEnd   = if (isOutgoing) 4.dp else 18.dp
                    )
                )
                .background(bubbleColor)
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { onLongPress() })
                }
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            when (message.mediaType) {
                MediaType.TEXT, MediaType.LINK -> {
                    // Build annotated text with clickable URLs via LinkAnnotation.
                    // Text handles link clicks automatically — no ClickableText needed.
                    val annotated = remember(message.body) {
                        buildMessageText(message.body, linkColor)
                    }
                    Text(
                        text  = annotated,
                        style = MaterialTheme.typography.bodyMedium.copy(color = textColor)
                    )
                }
                MediaType.IMAGE -> {
                    AsyncImage(
                        model               = message.mediaUrl ?: message.body,
                        contentDescription  = stringResource(R.string.chat_media_image),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val url = message.mediaUrl ?: message.body
                                if (url.startsWith("http")) uriHandler.openUri(url)
                            }
                    )
                }
                MediaType.AUDIO -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Headset, null, tint = textColor)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.chat_media_audio),
                            color = textColor, style = MaterialTheme.typography.bodySmall
                        )
                        if (message.audioDurationMs > 0) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                formatDuration(message.audioDurationMs),
                                color = textColor.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
                MediaType.FILE -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            val url = message.mediaUrl ?: message.body
                            if (url.startsWith("http")) uriHandler.openUri(url)
                        }
                    ) {
                        Icon(Icons.Default.Attachment, null, tint = textColor)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            message.mediaName ?: stringResource(R.string.chat_media_file),
                            color = textColor, style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                else -> Text(message.body, color = textColor)
            }
        }
        // Timestamp + delivery status
        Row(
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            if (message.encryptionType != EncryptionType.NONE) {
                Icon(
                    Icons.Default.Lock, contentDescription = null,
                    tint = message.encryptionType.toColor(),
                    modifier = Modifier.size(10.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text  = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            if (isOutgoing) {
                Spacer(Modifier.width(4.dp))
                StatusIcon(message.status)
            }
        }
    }
}

// ── Message context action sheet ──────────────────────────────────────────

/**
 * Bottom sheet shown on long-press of a message.
 * Provides: Copy text · Open URL (if present) · Delete (with confirmation)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionsSheet(
    message: Message,
    clipboard: androidx.compose.ui.platform.Clipboard,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val uriHandler  = LocalUriHandler.current
    val sheetState  = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hasUrl      = URL_PATTERN.matcher(message.body).find()
    val scope       = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = message.body.take(120) + if (message.body.length > 120) "…" else "",
                style  = MaterialTheme.typography.bodySmall,
                color  = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Copy text") },
                leadingContent  = { Icon(Icons.Default.ContentCopy, null) },
                modifier = Modifier.clickable {
                    scope.launch {
                        clipboard.setClipEntry(
                            androidx.compose.ui.platform.ClipEntry(
                                android.content.ClipData.newPlainText("message", message.body)
                            )
                        )
                    }
                    onDismiss()
                }
            )

            if (hasUrl) {
                val matcher = URL_PATTERN.matcher(message.body)
                if (matcher.find()) {
                    val url = matcher.group()
                    val fullUrl = if (url.startsWith("http")) url else "https://$url"
                    ListItem(
                        headlineContent  = { Text("Open link") },
                        supportingContent = {
                            Text(fullUrl, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary, maxLines = 1)
                        },
                        leadingContent = { Icon(Icons.Default.OpenInBrowser, null) },
                        modifier = Modifier.clickable { uriHandler.openUri(fullUrl); onDismiss() }
                    )
                    ListItem(
                        headlineContent = { Text("Copy link") },
                        leadingContent  = { Icon(Icons.Default.Link, null) },
                        modifier = Modifier.clickable {
                            scope.launch {
                                clipboard.setClipEntry(
                                    androidx.compose.ui.platform.ClipEntry(
                                        android.content.ClipData.newPlainText("link", fullUrl)
                                    )
                                )
                            }
                            onDismiss()
                        }
                    )
                }
            }

            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Delete message", color = MaterialTheme.colorScheme.error) },
                leadingContent  = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable { onDelete() }
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}


@Composable
fun StatusIcon(status: MessageStatus) {
    val (icon, tint, cd) = when (status) {
        MessageStatus.PENDING -> Triple(Icons.Default.AccessTime, MaterialTheme.colorScheme.outline, stringResource(R.string.chat_message_sending))
        MessageStatus.SENT -> Triple(Icons.Default.Check, MaterialTheme.colorScheme.outline, "Sent")
        MessageStatus.DELIVERED -> Triple(Icons.Default.CheckCircleOutline, MaterialTheme.colorScheme.outline, stringResource(R.string.chat_message_delivered))
        MessageStatus.READ -> Triple(Icons.Default.CheckCircle, MaterialTheme.colorScheme.primary, stringResource(R.string.chat_message_read))
        MessageStatus.FAILED -> Triple(Icons.Default.ErrorOutline, MaterialTheme.colorScheme.error, stringResource(R.string.chat_message_failed))
    }
    Icon(icon, contentDescription = cd, tint = tint, modifier = Modifier.size(14.dp).semantics { contentDescription = cd })
}

@Composable
fun DateDivider(date: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = date,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

// ── Full emoji catalog organized by category ──────────────────────────────

private data class EmojiCategory(val label: String, val icon: String, val emojis: List<String>)

private val EMOJI_CATEGORIES = listOf(
    EmojiCategory("Recent", "🕐", listOf(
        "😀","😂","🥰","😍","👍","❤️","🎉","🔥","😊","🤔","😅","🙏","💯","✅","🚀"
    )),
    EmojiCategory("Faces", "😀", listOf(
        "😀","😁","😂","🤣","😃","😄","😅","😆","😉","😊","😋","😎","😍","🥰","😘",
        "🥲","😗","😙","😚","🙂","🤗","🤩","🤔","🫡","🤨","😐","😑","😶","🫥","😏",
        "😒","🙄","😬","🤥","😌","😔","😪","🤤","😴","😷","🤒","🤕","🤢","🤮","🤧",
        "🥵","🥶","🥴","😵","🤯","🤠","🥸","🥳","😎","🤓","🧐","😟","😕","🫤","😣",
        "😖","😫","😩","🥺","😢","😭","😤","😠","😡","🤬","😈","👿","💀","☠️","💩",
        "🤡","👹","👺","👻","👽","👾","🤖","😺","😸","😹","😻","😼","😽","🙀","😿","😾"
    )),
    EmojiCategory("Gestures", "👋", listOf(
        "👋","🤚","🖐","✋","🖖","🫱","🫲","🫳","🫴","👌","🤌","🤏","✌️","🤞","🫰",
        "🤟","🤘","🤙","👈","👉","👆","🖕","👇","☝️","🫵","👍","👎","✊","👊","🤛",
        "🤜","👏","🙌","🫶","👐","🤲","🤝","🙏","✍️","💅","🤳","💪","🦾","🦿","🦵",
        "🦶","👂","🦻","👃","🫀","🫁","🧠","🦷","🦴","👀","👁","👅","👄","🫦","💋"
    )),
    EmojiCategory("People", "👩", listOf(
        "👶","🧒","👦","👧","🧑","👱","👨","🧔","👩","🧓","👴","👵","🙍","🙎","🙅",
        "🙆","💁","🙋","🧏","🙇","🤦","🤷","💆","💇","🚶","🧍","🧎","🏃","💃","🕺",
        "🧖","🧗","🤸","⛹","🤺","🏇","🏊","🤽","🚣","🧘","🛀","🛌","👫","👬","👭",
        "💑","👨‍👩‍👦","👨‍👩‍👧","👨‍👩‍👧‍👦","👨‍👩‍👦‍👦","👨‍👩‍👧‍👧","🪢","👣"
    )),
    EmojiCategory("Animals", "🐶", listOf(
        "🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐨","🐯","🦁","🐮","🐷","🐸","🐵",
        "🙈","🙉","🙊","🐔","🐧","🐦","🐤","🦆","🦅","🦉","🦇","🐺","🐗","🐴","🦄",
        "🐝","🪱","🐛","🦋","🐌","🐞","🐜","🪲","🦟","🦗","🕷","🦂","🐢","🐍","🦎",
        "🦖","🦕","🐙","🦑","🦐","🦞","🦀","🐡","🐠","🐟","🐬","🐳","🐋","🦈","🐊",
        "🐅","🐆","🦓","🦍","🦧","🦣","🐘","🦛","🦏","🐪","🐫","🦒","🦘","🦬","🐃",
        "🐂","🐄","🐎","🐖","🐏","🐑","🦙","🐐","🦌","🐕","🐩","🦮","🐈","🐓","🦃",
        "🦤","🦚","🦜","🦢","🦩","🕊","🐇","🦝","🦨","🦡","🦫","🦦","🦥","🐁","🐀","🦔"
    )),
    EmojiCategory("Food", "🍕", listOf(
        "🍏","🍎","🍐","🍊","🍋","🍌","🍉","🍇","🍓","🫐","🍈","🍑","🥭","🍍","🥥",
        "🥝","🍅","🍆","🥑","🥦","🥬","🥒","🌶","🫑","🧄","🧅","🥔","🍠","🫘","🥐",
        "🥖","🍞","🥨","🧀","🥚","🍳","🧈","🥞","🧇","🥓","🥩","🍗","🍖","🌭","🍔",
        "🍟","🍕","🫓","🥪","🥙","🧆","🌮","🌯","🫔","🥗","🥘","🫕","🥫","🍝","🍜",
        "🍲","🍛","🍣","🍱","🥟","🦪","🍤","🍙","🍚","🍘","🍥","🥮","🍢","🧁","🍰",
        "🎂","🍮","🍭","🍬","🍫","🍿","🍩","🍪","🌰","🥜","🍯","🧃","🥤","🧋","☕",
        "🍵","🫖","🍺","🍻","🥂","🍷","🫗","🥃","🍸","🍹","🧉","🍾","🧊","🥄","🍴"
    )),
    EmojiCategory("Nature", "🌿", listOf(
        "🌸","🌺","🌻","🌹","🌷","🌼","💐","🍄","🌾","🍀","🌿","☘️","🍃","🍂","🍁",
        "🌵","🌴","🌳","🌲","🎋","🎍","⛄","🌊","🌬","🌀","🌈","⚡","🔥","💧","🌍",
        "🌎","🌏","🌑","🌒","🌓","🌔","🌕","🌖","🌗","🌘","🌙","🌚","🌛","🌜","🌝",
        "⭐","🌟","💫","✨","☀️","🌤","⛅","🌥","🌦","🌧","⛈","🌩","🌨","❄️","🌫"
    )),
    EmojiCategory("Travel", "✈️", listOf(
        "🚗","🚕","🚙","🚌","🚎","🏎","🚓","🚑","🚒","🚐","🛻","🚚","🚛","🚜","🏍",
        "🛵","🚲","🛴","🛺","🚁","🛸","✈️","🛩","🚀","🛶","⛵","🚤","🛥","🛳","⛴",
        "🚂","🚆","🚇","🚈","🚉","🚊","🚞","🚋","🚌","🚍","🚎","🚐","🚑","🚒","🚓",
        "🗺","🧭","🏔","⛰","🌋","🏕","🏖","🏜","🏝","🏞","🏟","🏛","🏗","🏘","🏚",
        "🏠","🏡","🏢","🏣","🏤","🏥","🏦","🏨","🏩","🏪","🏫","🏬","🏭","🏯","🏰"
    )),
    EmojiCategory("Objects", "💡", listOf(
        "⌚","📱","💻","⌨️","🖥","🖨","🖱","🖲","🕹","💾","💿","📀","📷","📸","📹",
        "🎥","📽","🎞","📞","☎️","📟","📠","📺","📻","🧭","⏱","⏲","⏰","🕰","⌛",
        "📡","🔋","🔌","💡","🔦","🕯","💰","💴","💵","💶","💷","💸","💳","🪙","💹",
        "📧","📨","📩","📪","📫","📬","📭","📮","🗳","✏️","✒️","🖊","🖋","📝","📁",
        "📂","🗂","📅","📆","🗒","🗓","📇","📈","📉","📊","📋","📌","📍","📎","🖇",
        "📏","📐","✂️","🗃","🗄","🗑","🔒","🔓","🔏","🔐","🔑","🗝","🔨","🪓","⛏",
        "🔧","🪛","🔩","🪤","🧲","🪜","🧰","🪝","🧲","💊","💉","🩹","🩺","🔭","🔬"
    )),
    EmojiCategory("Symbols", "❤️", listOf(
        "❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔","❤️‍🔥","❤️‍🩹","💕","💞",
        "💓","💗","💖","💘","💝","💟","☮️","✝️","☯️","🕉","✡️","🔯","🕎","☦️","🛐",
        "⛎","♈","♉","♊","♋","♌","♍","♎","♏","♐","♑","♒","♓","🆔","⚜️",
        "🔀","🔁","🔂","▶️","⏩","⏪","⏫","⏬","⏭","⏮","🔼","🔽","⏸","⏹","⏺",
        "🎦","🔅","🔆","📶","📳","📴","📵","📱","📲","☎️","📞","📟","📠","🔋","🔌",
        "✅","❎","🔴","🟠","🟡","🟢","🔵","🟣","⚫","⚪","🟤","🔺","🔻","🔷","🔶",
        "🔹","🔸","🔲","🔳","▪️","▫️","◾","◽","◼️","◻️","🟥","🟧","🟨","🟩","🟦",
        "💯","🔞","🔅","🆗","🆙","🆒","🆕","🆓","🔟","🆖","🅰️","🅱️","🆎","🆑","🅾️","🆘"
    )),
    EmojiCategory("Flags", "🏳️", listOf(
        "🏳️","🏴","🚩","🏁","🏳️‍🌈","🏳️‍⚧️","🏴‍☠️",
        "🇺🇸","🇬🇧","🇪🇸","🇫🇷","🇩🇪","🇮🇹","🇯🇵","🇨🇳","🇷🇺","🇧🇷",
        "🇦🇷","🇦🇺","🇨🇦","🇲🇽","🇰🇷","🇮🇳","🇿🇦","🇳🇬","🇪🇬","🇸🇦",
        "🇹🇷","🇮🇩","🇵🇰","🇧🇩","🇵🇭","🇵🇱","🇳🇱","🇧🇪","🇸🇪","🇨🇭"
    )),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EmojiPicker(onEmojiClick: (String) -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val category = EMOJI_CATEGORIES[selectedTab]

    Column {
        // ── Category tab row ──────────────────────────────────────────────
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding      = 0.dp,
            divider          = {}
        ) {
            EMOJI_CATEGORIES.forEachIndexed { index, cat ->
                Tab(
                    selected = selectedTab == index,
                    onClick  = { selectedTab = index },
                    modifier = Modifier.height(44.dp)
                ) {
                    Text(
                        text     = cat.icon,
                        fontSize = 20.sp
                    )
                }
            }
        }
        HorizontalDivider()
        // ── Emoji grid for the selected category ──────────────────────────
        LazyVerticalGrid(
            columns        = GridCells.Adaptive(minSize = 44.dp),
            modifier       = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentPadding = PaddingValues(4.dp)
        ) {
            items(category.emojis) { emoji ->
                Text(
                    text     = emoji,
                    fontSize = 26.sp,
                    modifier = Modifier
                        .clickable { onEmojiClick(emoji) }
                        .padding(6.dp)
                )
            }
        }
    }
}


@Composable
fun ChatInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    recordingState: RecordingState,
    recordingDuration: Long,
    isUploading: Boolean
) {
    var showEmojiPicker by remember { mutableStateOf(false) }

    Column {
        // ── Emoji panel ───────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showEmojiPicker && recordingState != RecordingState.RECORDING,
            enter   = expandVertically() + fadeIn(),
            exit    = shrinkVertically() + fadeOut()
        ) {
            Surface(tonalElevation = 6.dp) {
                EmojiPicker(onEmojiClick = { onTextChange(text + it) })
            }
        }

        // ── Input bar ─────────────────────────────────────────────────────
        Surface(tonalElevation = 3.dp, shadowElevation = 4.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                when (recordingState) {
                    RecordingState.RECORDING -> {
                        // ── Recording UI ──────────────────────────────────
                        Row(
                            modifier             = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            verticalAlignment    = Alignment.CenterVertically
                        ) {
                            // Pulsing dot
                            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                            val alpha by infiniteTransition.animateFloat(
                                initialValue = 0.3f, targetValue = 1f,
                                animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                                label = "pulse_alpha"
                            )
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = alpha))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                formatDuration(recordingDuration),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                stringResource(R.string.chat_record_audio),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Cancel
                        IconButton(onClick = onCancelRecording) {
                            Icon(
                                Icons.Default.Close,
                                stringResource(R.string.chat_cancel_audio),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        // Send recording
                        IconButton(onClick = onStopRecording) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                stringResource(R.string.chat_send_audio),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    else -> {
                        // ── Normal input row ──────────────────────────────
                        // Attach file
                        IconButton(
                            onClick  = onAttach,
                            modifier = Modifier.semantics { contentDescription = "Attach file" }
                        ) {
                            Icon(
                                Icons.Default.Attachment,
                                stringResource(R.string.chat_attach),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Emoji toggle
                        IconButton(
                            onClick  = { showEmojiPicker = !showEmojiPicker },
                            modifier = Modifier.semantics { contentDescription = "Emoji picker" }
                        ) {
                            Icon(
                                if (showEmojiPicker) Icons.Default.KeyboardAlt
                                else Icons.Default.EmojiEmotions,
                                contentDescription = null,
                                tint = if (showEmojiPicker)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Text field
                        OutlinedTextField(
                            value         = text,
                            onValueChange = onTextChange,
                            placeholder   = { Text(stringResource(R.string.chat_hint)) },
                            shape         = RoundedCornerShape(24.dp),
                            modifier      = Modifier.weight(1f),
                            maxLines      = 5
                        )
                        // Send OR record
                        if (text.isNotBlank()) {
                            IconButton(
                                onClick  = { onSend(); showEmojiPicker = false },
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .semantics { contentDescription = "Send message" }
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    stringResource(R.string.chat_send),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            IconButton(
                                onClick  = onStartRecording,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .semantics { contentDescription = "Record audio message" }
                            ) {
                                Icon(
                                    Icons.Default.KeyboardVoice,
                                    stringResource(R.string.chat_record_audio),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EncryptionPickerDialog(
    current: EncryptionType,
    onSelect: (EncryptionType) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_encryption_select)) },
        text = {
            Column {
                EncryptionType.entries.forEach { type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .pointerInput(type) {
                                detectTapGestures { onSelect(type) }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = current == type, onClick = { onSelect(type) })
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(type.toDisplayName(), fontWeight = FontWeight.Medium)
                            Text(type.toDescription(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}

fun EncryptionType.toColor(): Color = when (this) {
    EncryptionType.OTR -> EncryptionOtr
    EncryptionType.OMEMO -> EncryptionOmemo
    EncryptionType.OPENPGP -> EncryptionPgp
    EncryptionType.NONE -> EncryptionNone
}

fun EncryptionType.toDisplayName(): String = when (this) {
    EncryptionType.NONE -> "None (Plain text)"
    EncryptionType.OTR -> "OTR"
    EncryptionType.OMEMO -> "OMEMO"
    EncryptionType.OPENPGP -> "OpenPGP"
}

fun EncryptionType.toDescription(): String = when (this) {
    EncryptionType.NONE -> "Messages are sent unencrypted"
    EncryptionType.OTR -> "Off-the-Record: perfect forward secrecy"
    EncryptionType.OMEMO -> "Multi-device end-to-end encryption (recommended)"
    EncryptionType.OPENPGP -> "OpenPGP asymmetric encryption"
}

fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}









