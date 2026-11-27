package com.code7.techupdates.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors =
    lightColorScheme(
        primary = Indigo600,
        onPrimary = Slate50,
        primaryContainer = Indigo100,
        onPrimaryContainer = Indigo900,
        background = Slate50,
        onBackground = Slate900,
        surface = Slate50,
        onSurface = Slate900,
    )

private val DarkColors =
    darkColorScheme(
        primary = IndigoLight,
        onPrimary = Slate950,
        primaryContainer = IndigoDeep,
        onPrimaryContainer = Indigo50,
        background = Slate950,
        onBackground = Slate100,
        surface = Slate950,
        onSurface = Slate100,
    )

@Composable
fun Code7Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> {
                DarkColors
            }

            else -> {
                LightColors
            }
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
