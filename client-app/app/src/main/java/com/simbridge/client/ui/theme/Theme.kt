package com.simbridge.client.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF0D47A1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBBDEFB),
    secondary = Color(0xFF00897B),
    onSecondary = Color.White,
    error = Color(0xFFD32F2F),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF82B1FF),
    onPrimary = Color(0xFF002171),
    primaryContainer = Color(0xFF1565C0),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF00332E),
    error = Color(0xFFEF9A9A),
)

@Composable
fun SimBridgeClientTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
