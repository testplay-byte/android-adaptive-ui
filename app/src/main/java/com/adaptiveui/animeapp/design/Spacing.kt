package com.adaptiveui.animeapp.design

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 4pt spacing scale. Every gap in the app is a multiple of 4.
 */
object Spacing {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val xxxl: Dp = 48.dp
}

/** Corner radii. */
object Radius {
    val sm = 6.dp
    val md = 10.dp
    val lg = 16.dp
    val xl = 24.dp
    val pill = 9999.dp
}

/** Poster aspect ratios. */
object Aspect {
    const val PORTRAIT = 2f / 3f
    const val LANDSCAPE = 16f / 9f
    const val SQUARE = 1f
}
