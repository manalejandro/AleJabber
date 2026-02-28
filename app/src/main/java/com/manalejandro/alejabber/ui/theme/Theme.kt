package com.manalejandro.alejabber.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Primary80,
    onPrimary = Color(0xFF002082),
    primaryContainer = PrimaryContainer40,
    onPrimaryContainer = PrimaryContainer80,
    secondary = Secondary80,
    onSecondary = Color(0xFF003737),
    secondaryContainer = SecondaryContainer40,
    onSecondaryContainer = SecondaryContainer80,
    tertiary = Tertiary80,
    onTertiary = Color(0xFF3B0083),
    error = Error80,
    onError = Color(0xFF690005),
    background = BackgroundDark,
    onBackground = Color(0xFFE4E1E6),
    surface = SurfaceDark,
    onSurface = Color(0xFFE4E1E6),
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = Color(0xFF918F9A)
)

private val LightColorScheme = lightColorScheme(
    primary = Primary40,
    onPrimary = Color.White,
    primaryContainer = PrimaryContainer80,
    onPrimaryContainer = PrimaryContainer40,
    secondary = Secondary40,
    onSecondary = Color.White,
    secondaryContainer = SecondaryContainer80,
    onSecondaryContainer = SecondaryContainer40,
    tertiary = Tertiary40,
    onTertiary = Color.White,
    error = Error40,
    onError = Color.White,
    background = BackgroundLight,
    onBackground = Color(0xFF1B1B1F),
    surface = SurfaceLight,
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = Color(0xFF77767F)
)

enum class AppTheme { SYSTEM, LIGHT, DARK }

@Composable
fun AleJabberTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (appTheme) {
        AppTheme.DARK -> true
        AppTheme.LIGHT -> false
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}