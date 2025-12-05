package com.example.isekiparcom.ui.theme

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
//    primary = PinkPrimary,
//    onPrimary = PinkOnPrimary,
//    primaryContainer = PinkContainer,
//    secondaryContainer = PinkSurface,
//    secondary = PinkSecondary,
//    background = Color(0xFF121212),
//    surface = Color(0xFF1E1E1E),
//    error = PinkError,
    primary = PinkPrimary,
    onPrimary = PinkOnPrimary,
    primaryContainer = PinkContainer,
    secondaryContainer = PinkSurface,
    secondary = PinkSecondary,
    background = PinkBackground,
    surface = PinkSurface,
    error = PinkError,
)

private val LightColorScheme = lightColorScheme(
    primary = PinkPrimary,
    onPrimary = PinkOnPrimary,
    primaryContainer = PinkContainer,
    secondaryContainer = PinkSurface,
    secondary = PinkSecondary,
    background = PinkBackground,
    surface = PinkSurface,
    error = PinkError,
)

@Composable
fun IsekiParcomTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
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