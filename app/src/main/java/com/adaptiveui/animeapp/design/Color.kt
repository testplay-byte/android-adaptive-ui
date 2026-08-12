package com.adaptiveui.animeapp.design

import androidx.compose.ui.graphics.Color

/**
 * Minimal monochrome-leaning palette with a single accent.
 * Designed to be clean and modern — dark surfaces, restrained color, one warm accent.
 */
data class AppColors(
    val bg: Color,
    val surface: Color,
    val surfaceHi: Color,
    val outline: Color,
    val text: Color,
    val textMuted: Color,
    val accent: Color,
    val accentText: Color,
    val danger: Color,
    val success: Color,
    val scrim: Color
) {
    companion object {
        val Dark = AppColors(
            bg = Color(0xFF0A0A0B),
            surface = Color(0xFF131316),
            surfaceHi = Color(0xFF1C1C20),
            outline = Color(0xFF2A2A30),
            text = Color(0xFFF5F5F7),
            textMuted = Color(0xFF8E8E93),
            accent = Color(0xFFFF6B35),
            accentText = Color(0xFF0A0A0B),
            danger = Color(0xFFFF453A),
            success = Color(0xFF30D158),
            scrim = Color(0x99000000)
        )

        val Light = AppColors(
            bg = Color(0xFFF7F7F8),
            surface = Color(0xFFFFFFFF),
            surfaceHi = Color(0xFFF0F0F2),
            outline = Color(0xFFD8D8DC),
            text = Color(0xFF0A0A0B),
            textMuted = Color(0xFF6E6E73),
            accent = Color(0xFFFF6B35),
            accentText = Color(0xFFFFFFFF),
            danger = Color(0xFFFF3B30),
            success = Color(0xFF24A857),
            scrim = Color(0x66000000)
        )
    }
}
