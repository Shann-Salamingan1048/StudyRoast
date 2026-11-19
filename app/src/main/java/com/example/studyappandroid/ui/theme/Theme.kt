package com.example.studyappandroid.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.studyappandroid.domain.model.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = RoastCoral,
    onPrimary = RoastOnCoral,
    primaryContainer = RoastCoralContainer,
    onPrimaryContainer = RoastCoralLight,
    secondary = RoastIndigo,
    onSecondary = Color.White,
    secondaryContainer = RoastIndigoContainer,
    onSecondaryContainer = RoastIndigoLight,
    tertiary = RoastMint,
    onTertiary = Color(0xFF002114),
    tertiaryContainer = RoastMintContainer,
    onTertiaryContainer = RoastMint,
    error = RoastCrimson,
    onError = Color.White,
    errorContainer = RoastCrimsonContainer,
    onErrorContainer = Color(0xFFFFB3C1),
    background = RoastBgDark,
    onBackground = RoastTextPrimaryDark,
    surface = RoastSurfaceDark,
    onSurface = RoastTextPrimaryDark,
    surfaceVariant = RoastSurfaceVariantDark,
    onSurfaceVariant = RoastTextSecondaryDark,
    outline = RoastOutlineDark,
    outlineVariant = Color(0xFF242C45)
)

private val LightColorScheme = lightColorScheme(
    primary = RoastCoralDark,
    onPrimary = Color.White,
    primaryContainer = RoastCoralContainerLight,
    onPrimaryContainer = Color(0xFF470D00),
    secondary = RoastIndigoDark,
    onSecondary = Color.White,
    secondaryContainer = RoastIndigoContainerLight,
    onSecondaryContainer = Color(0xFF1A0F66),
    tertiary = RoastMintDark,
    onTertiary = Color.White,
    tertiaryContainer = RoastMintContainerLight,
    onTertiaryContainer = Color(0xFF002919),
    error = RoastCrimsonDark,
    onError = Color.White,
    errorContainer = RoastCrimsonContainerLight,
    onErrorContainer = Color(0xFF4F0011),
    background = RoastBgLight,
    onBackground = RoastTextPrimaryLight,
    surface = RoastSurfaceLight,
    onSurface = RoastTextPrimaryLight,
    surfaceVariant = RoastSurfaceVariantLight,
    onSurfaceVariant = RoastTextSecondaryLight,
    outline = RoastOutlineLight,
    outlineVariant = Color(0xFFE3E8F5)
)

@Composable
fun StudyRoastTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !useDarkTheme
                    isAppearanceLightNavigationBars = !useDarkTheme
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = StudyRoastTypography,
        content = content
    )
}
