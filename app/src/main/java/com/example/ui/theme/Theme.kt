package com.example.ui.theme

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
import com.example.data.ThemeMode

private val StudioDarkColorScheme = darkColorScheme(
    primary = StudioGreen,
    onPrimary = Color.White,
    primaryContainer = StudioGreenDark,
    onPrimaryContainer = StudioGreenSoft,
    secondary = StudioCyan,
    onSecondary = Color(0xFF00211B),
    secondaryContainer = StudioCyanDark,
    onSecondaryContainer = StudioCyanSoft,
    tertiary = TallyAmber,
    onTertiary = Color(0xFF2B1B00),
    tertiaryContainer = TallyAmberDark,
    onTertiaryContainer = Color(0xFFFFDFA0),
    background = StudioObsidian,
    onBackground = StudioTextPrimaryDark,
    surface = StudioObsidian,
    onSurface = StudioTextPrimaryDark,
    surfaceVariant = StudioGraphite,
    onSurfaceVariant = StudioTextSecondaryDark,
    surfaceContainerLowest = Color(0xFF07090C),
    surfaceContainerLow = StudioGraphite,
    surfaceContainer = StudioSlate,
    surfaceContainerHigh = StudioSlateHigh,
    surfaceContainerHighest = Color(0xFF2D3748),
    outline = StudioBorderDark,
    outlineVariant = Color(0xFF222A38),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3E0005)
)

private val StudioLightColorScheme = lightColorScheme(
    primary = StudioGreenLight,
    onPrimary = Color.White,
    primaryContainer = StudioGreenContainerLight,
    onPrimaryContainer = Color(0xFF063B20),
    secondary = StudioCyanLight,
    onSecondary = Color.White,
    secondaryContainer = StudioCyanContainerLight,
    onSecondaryContainer = Color(0xFF00372E),
    tertiary = TallyAmberLight,
    onTertiary = Color.White,
    tertiaryContainer = TallyAmberContainerLight,
    onTertiaryContainer = Color(0xFF3D2400),
    background = StudioDaylight,
    onBackground = StudioTextPrimaryLight,
    surface = StudioDaylight,
    onSurface = StudioTextPrimaryLight,
    surfaceVariant = StudioCoolCard,
    onSurfaceVariant = StudioTextSecondaryLight,
    surfaceContainerLowest = StudioPaper,
    surfaceContainerLow = StudioPaper,
    surfaceContainer = StudioCoolCard,
    surfaceContainerHigh = StudioCoolCardHigh,
    surfaceContainerHighest = Color(0xFFD0DAE8),
    outline = StudioBorderLight,
    outlineVariant = Color(0xFFDCE3EE),
    error = Color(0xFFC62828),
    onError = Color.White
)

@Composable
fun EasyCaptureTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDarkTheme) StudioDarkColorScheme else StudioLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !useDarkTheme
                controller.isAppearanceLightNavigationBars = !useDarkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = EasyCaptureTypography,
        content = content
    )
}
