package com.x3squaredcircles.pixmap.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Colors from MAUI app
val Primary = Color(0xFFFF3D20)
val PrimaryDark = Color(0xFFE5351C)
val BackgroundGrey = Color(0xFF121212)
val ButtonOrange = Color(0xFFFF3D20)
val Gray100 = Color(0xFFE1E1E1)
val Gray200 = Color(0xFFC8C8C8)
val Gray600 = Color(0xFF404040)
val Gray900 = Color(0xFF212121)
val OffBlack = Color(0xFF1F1F1F)

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    secondary = Gray600,
    tertiary = Color(0xFF2B0B98),
    background = BackgroundGrey,
    surface = OffBlack,
    onPrimary = Color.White,
    onSecondary = Gray100,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    secondary = Gray200,
    tertiary = Color(0xFF2B0B98),
    background = Color.White,
    surface = Gray100,
    onPrimary = Color.White,
    onSecondary = Gray900,
    onTertiary = Color.White,
    onBackground = Gray900,
    onSurface = Gray900
)

@Composable
fun PixMapTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disabled to use custom colors
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

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}