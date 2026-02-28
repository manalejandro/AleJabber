package com.manalejandro.alejabber.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manalejandro.alejabber.R
import com.manalejandro.alejabber.domain.model.EncryptionType
import com.manalejandro.alejabber.ui.theme.EncryptionNone
import com.manalejandro.alejabber.ui.theme.EncryptionOmemo
import com.manalejandro.alejabber.ui.theme.EncryptionOtr
import com.manalejandro.alejabber.ui.theme.EncryptionPgp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen

@Composable
fun EncryptionBadge(
    encryptionType: EncryptionType,
    modifier: Modifier = Modifier
) {
    val (color, label) = encryptionType.toBadgeInfo()
    val cdLabel = stringResource(R.string.cd_encryption_badge, label)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .semantics { contentDescription = cdLabel }
    ) {
        Icon(
            imageVector = if (encryptionType == EncryptionType.NONE) Icons.Default.LockOpen else Icons.Default.Lock,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

fun EncryptionType.toBadgeInfo(): Pair<Color, String> = when (this) {
    EncryptionType.OTR -> EncryptionOtr to "OTR"
    EncryptionType.OMEMO -> EncryptionOmemo to "OMEMO"
    EncryptionType.OPENPGP -> EncryptionPgp to "PGP"
    EncryptionType.NONE -> EncryptionNone to "Plain"
}

