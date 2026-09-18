package com.pat.app.holidayplanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Teal40,
    onPrimary = Color.White,
    primaryContainer = Teal90,
    onPrimaryContainer = Teal10,
    secondary = Sand40,
    onSecondary = Color.White,
    secondaryContainer = Sand90,
    onSecondaryContainer = Sand10,
    tertiary = Coral40,
    onTertiary = Color.White,
    tertiaryContainer = Coral90,
    onTertiaryContainer = Coral10,
    background = Sand99,
    onBackground = Sand10,
    surface = Sand99,
    onSurface = Sand10,
    surfaceVariant = Sand90,
    onSurfaceVariant = Sand30,
    surfaceContainer = Sand95,
    surfaceContainerHigh = Sand90,
    outline = DayOutline,
    outlineVariant = Sand80,
)

private val DarkColorScheme = darkColorScheme(
    primary = Teal80,
    onPrimary = Teal20,
    primaryContainer = Teal30,
    onPrimaryContainer = Teal90,
    secondary = Sand80,
    onSecondary = Sand20,
    secondaryContainer = Sand30,
    onSecondaryContainer = Sand90,
    tertiary = Coral80,
    onTertiary = Coral20,
    tertiaryContainer = Coral20,
    onTertiaryContainer = Coral90,
    background = Night,
    onBackground = Sand90,
    surface = Night,
    onSurface = Sand90,
    surfaceVariant = Sand30,
    onSurfaceVariant = Sand80,
    surfaceContainer = NightSurface,
    surfaceContainerHigh = NightSurfaceHigh,
    outline = NightOutline,
    outlineVariant = Sand30,
)

/**
 * One palette rather than the phone's wallpaper colours, so the app looks the same on both phones
 * and the tag colours were chosen against a background that is known.
 */
@Composable
fun HolidayPlannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
