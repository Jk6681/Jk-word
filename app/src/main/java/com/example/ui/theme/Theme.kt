package com.example.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.jkword.data.local.AppThemeMode

val LocalThemeMode = compositionLocalOf { AppThemeMode.SYSTEM }
val LocalToggleTheme = compositionLocalOf<() -> Unit> { {} }
val LocalSetTheme = compositionLocalOf<(AppThemeMode) -> Unit> { {} }

private val DarkColorScheme = darkColorScheme(
    primary = WordDarkPrimary,
    onPrimary = Color(0xFF002B75),
    primaryContainer = OfficeBlueDark,
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBBC7DB),
    onSecondary = Color(0xFF253140),
    secondaryContainer = Color(0xFF3B4858),
    onSecondaryContainer = Color(0xFFD7E3F7),
    background = WordDarkBackground,
    onBackground = Color(0xFFE2E2E6),
    surface = WordDarkSurface,
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = WordDarkCard,
    onSurfaceVariant = Color(0xFFC3C7D0),
    outline = Color(0xFF8D9199)
)

private val LightColorScheme = lightColorScheme(
    primary = OfficeBluePrimary,
    onPrimary = Color.White,
    primaryContainer = OfficeBlueLight,
    onPrimaryContainer = OfficeBlueDark,
    secondary = OfficeSlate,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E7EE),
    onSecondaryContainer = OfficeNavy,
    background = OfficeSurfaceLight,
    onBackground = Color(0xFF191C1E),
    surface = Color.White,
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFEEF1F5),
    onSurfaceVariant = Color(0xFF44474E),
    outline = Color(0xFFC4C7D0)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val targetScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val animatedPrimary by animateColorAsState(targetScheme.primary, label = "theme_primary")
    val animatedBackground by animateColorAsState(targetScheme.background, label = "theme_bg")
    val animatedSurface by animateColorAsState(targetScheme.surface, label = "theme_surface")
    val animatedOnSurface by animateColorAsState(targetScheme.onSurface, label = "theme_on_surface")
    val animatedSurfaceVariant by animateColorAsState(targetScheme.surfaceVariant, label = "theme_card")

    val colorScheme = targetScheme.copy(
        primary = animatedPrimary,
        background = animatedBackground,
        surface = animatedSurface,
        onSurface = animatedOnSurface,
        surfaceVariant = animatedSurfaceVariant
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
