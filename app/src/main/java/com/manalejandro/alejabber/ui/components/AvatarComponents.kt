package com.manalejandro.alejabber.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.manalejandro.alejabber.domain.model.PresenceStatus
import com.manalejandro.alejabber.ui.theme.StatusAway
import com.manalejandro.alejabber.ui.theme.StatusDnd
import com.manalejandro.alejabber.ui.theme.StatusOffline
import com.manalejandro.alejabber.ui.theme.StatusOnline

@Composable
fun AvatarWithStatus(
    name: String,
    avatarUrl: String?,
    presence: PresenceStatus,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    contentDescription: String = ""
) {
    Box(modifier = modifier) {
        when {
            // ── data URI (vCard Base64 avatar) ────────────────────────────
            avatarUrl != null && avatarUrl.startsWith("data:") -> {
                DataUriAvatar(
                    dataUri            = avatarUrl,
                    size               = size,
                    contentDescription = contentDescription
                )
            }
            // ── Regular http/https URL ────────────────────────────────────
            !avatarUrl.isNullOrBlank() -> {
                AsyncImage(
                    model              = avatarUrl,
                    contentDescription = contentDescription,
                    contentScale       = ContentScale.Crop,
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape)
                )
            }
            // ── No avatar — show initials ─────────────────────────────────
            else -> {
                InitialsAvatar(
                    name               = name,
                    size               = size,
                    contentDescription = contentDescription
                )
            }
        }
        // Presence dot (bottom-right)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(size * 0.27f)
                .clip(CircleShape)
                .background(presence.toColor())
        )
    }
}

/**
 * Decodes a `data:image/...;base64,...` URI and renders it as a circular avatar.
 * Uses [remember] to avoid re-decoding on every recomposition.
 */
@Composable
private fun DataUriAvatar(
    dataUri: String,
    size: Dp,
    contentDescription: String
) {
    val bitmap = remember(dataUri) {
        runCatching {
            // Strip the "data:image/...;base64," prefix
            val commaIndex = dataUri.indexOf(',')
            if (commaIndex < 0) return@runCatching null
            val base64 = dataUri.substring(commaIndex + 1)
            val bytes  = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    if (bitmap != null) {
        Image(
            bitmap             = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale       = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
        )
    } else {
        // Fallback to initials if decoding failed
        InitialsAvatar(
            name               = contentDescription,
            size               = size,
            contentDescription = contentDescription
        )
    }
}

@Composable
fun InitialsAvatar(
    name: String,
    size: Dp = 48.dp,
    contentDescription: String = "",
    backgroundColor: Color = Color(0xFF3A5BCC)
) {
    val initials = name.split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifBlank { "?" }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
            .semantics { this.contentDescription = contentDescription }
    ) {
        Text(
            text       = initials,
            color      = Color.White,
            fontSize   = (size.value * 0.38f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

fun PresenceStatus.toColor(): Color = when (this) {
    PresenceStatus.ONLINE              -> StatusOnline
    PresenceStatus.AWAY, PresenceStatus.XA -> StatusAway
    PresenceStatus.DND                 -> StatusDnd
    PresenceStatus.OFFLINE             -> StatusOffline
}

fun PresenceStatus.toLabel(): String = when (this) {
    PresenceStatus.ONLINE  -> "Online"
    PresenceStatus.AWAY    -> "Away"
    PresenceStatus.XA      -> "Extended Away"
    PresenceStatus.DND     -> "Do Not Disturb"
    PresenceStatus.OFFLINE -> "Offline"
}

