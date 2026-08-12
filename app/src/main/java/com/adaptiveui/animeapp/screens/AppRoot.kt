package com.adaptiveui.animeapp.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adaptiveui.animeapp.ai.AiViewModel
import com.adaptiveui.animeapp.design.AppTheme
import com.adaptiveui.animeapp.design.AppThemeMode

/**
 * App root — reads the persisted theme mode + the singleton [com.adaptiveui.animeapp.interpreter.ColorExtractor]
 * (via [ColorExtractorProvider]), wraps the nav host in the [AppTheme], and renders [AppNavGraph].
 *
 * The [AiViewModel] is hoisted here so the [AiBubbleHost] overlay can read it without being
 * re-created on every navigation transition.
 */
@Composable
fun AppRoot() {
    val settingsVm: SettingsViewModel = hiltViewModel()
    val themeMode by settingsVm.themeMode.collectAsStateWithLifecycle()
    val aiVm: AiViewModel = hiltViewModel()
    val colorProvider: ColorExtractorProvider = hiltViewModel()
    val colorExtractor = colorProvider.colorExtractor

    val mode = when (themeMode) {
        "LIGHT" -> AppThemeMode.LIGHT
        "DARK" -> AppThemeMode.DARK
        else -> AppThemeMode.AUTO
    }

    AppTheme(mode = mode) {
        AppNavGraph(
            aiVm = aiVm,
            colorExtractor = colorExtractor
        )
    }
}
