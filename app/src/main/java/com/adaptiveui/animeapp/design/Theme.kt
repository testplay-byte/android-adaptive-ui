package com.adaptiveui.animeapp.design

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalColors = staticCompositionLocalOf { AppColors.Dark }
val LocalTypography = staticCompositionLocalOf { AppTypography.Default }
val LocalIsDark = staticCompositionLocalOf { true }

enum class AppThemeMode { AUTO, LIGHT, DARK }

data class AppTheme(
    val colors: AppColors,
    val typography: AppTypography,
    val isDark: Boolean
)

@Composable
fun AppTheme(
    mode: AppThemeMode = AppThemeMode.AUTO,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (mode) {
        AppThemeMode.AUTO -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val colors = if (isDark) AppColors.Dark else AppColors.Light
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
        }
    }
    CompositionLocalProvider(
        LocalColors provides colors,
        LocalTypography provides AppTypography.Default,
        LocalIsDark provides isDark,
        content = content
    )
}
