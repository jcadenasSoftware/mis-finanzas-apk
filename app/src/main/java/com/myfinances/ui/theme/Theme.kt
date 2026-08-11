package com.jcadenas.xpendz.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private fun xpendzDarkColorScheme(tokens: XpendzColorTokens) = darkColorScheme(
    primary = tokens.brand,
    onPrimary = tokens.onBrand,
    primaryContainer = tokens.brandStrong,
    secondary = tokens.secondary,
    onSecondary = tokens.onSecondary,
    secondaryContainer = tokens.secondaryStrong,
    background = tokens.background,
    onBackground = tokens.onBackground,
    surface = tokens.surface,
    onSurface = tokens.onSurface,
    surfaceVariant = tokens.surfaceVariant,
    onSurfaceVariant = tokens.onSurfaceVariant
)

private fun xpendzLightColorScheme(tokens: XpendzColorTokens) = lightColorScheme(
    primary = tokens.brand,
    onPrimary = tokens.onBrand,
    primaryContainer = tokens.brandSubtle,
    secondary = tokens.secondary,
    onSecondary = tokens.onSecondary,
    secondaryContainer = tokens.secondary,
    background = tokens.background,
    onBackground = tokens.onBackground,
    surface = tokens.surface,
    onSurface = tokens.onSurface,
    surfaceVariant = tokens.surfaceVariant,
    onSurfaceVariant = tokens.onSurfaceVariant
)

object XpendzThemeTokens {
    val colors: XpendzColorTokens
        @Composable get() = LocalXpendzColorTokens.current

    val spacing: XpendzSpacingTokens
        @Composable get() = LocalXpendzSpacingTokens.current

    val elevation: XpendzElevationTokens
        @Composable get() = LocalXpendzElevationTokens.current

    val shapes: XpendzShapeTokens
        @Composable get() = LocalXpendzShapeTokens.current

    val typography
        @Composable get() = LocalXpendzTypographyTokens.current
}

@Composable
fun XpendzTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val foundationColors = if (darkTheme) DarkColorTokens else LightColorTokens
    val materialColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> xpendzDarkColorScheme(DarkColorTokens)
        else -> xpendzLightColorScheme(LightColorTokens)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = materialColorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalXpendzColorTokens provides foundationColors,
        LocalXpendzSpacingTokens provides DefaultXpendzSpacingTokens,
        LocalXpendzElevationTokens provides DefaultXpendzElevationTokens,
        LocalXpendzShapeTokens provides DefaultXpendzShapeTokens,
        LocalXpendzTypographyTokens provides DefaultXpendzTypographyTokens
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            shapes = Shapes,
            typography = Typography,
            content = content
        )
    }
}
