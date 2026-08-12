package com.adaptiveui.animeapp.interpreter

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts a dominant/vibrant color from a remote image URL using the Android Palette API.
 *
 * Used by [UiSpecInterpreter] to resolve `BackgroundSpec.Extracted` — enabling dynamic theming
 * based on cover art (like Spotify's album-tinted UI).
 *
 * Images are downloaded as bitmaps (software, not hardware — Palette needs pixels) and the
 * palette is generated on a background thread. Results are cached in-memory per URL+variant.
 */
@Singleton
class ColorExtractor @Inject constructor(
    private val client: OkHttpClient
) {
    private val cache = mutableMapOf<String, Color>()

    suspend fun extract(imageUrl: String, variant: String, fallback: Color): Color {
        val key = "$imageUrl|$variant"
        cache[key]?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = downloadBitmap(imageUrl) ?: return@withContext fallback
                val palette = Palette.from(bitmap).generate()
                val color = when (variant) {
                    "vibrant" -> palette.vibrantSwatch
                    "dominant" -> palette.dominantSwatch
                    "muted" -> palette.mutedSwatch
                    "darkVibrant" -> palette.darkVibrantSwatch
                    "lightVibrant" -> palette.lightVibrantSwatch
                    "darkMuted" -> palette.darkMutedSwatch
                    "lightMuted" -> palette.lightMutedSwatch
                    else -> palette.dominantSwatch
                }?.let { Color(it.rgb) } ?: fallback
                cache[key] = color
                color
            }.getOrDefault(fallback)
        }
    }

    private fun downloadBitmap(url: String): Bitmap? = runCatching {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bytes = response.body?.bytes() ?: return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }.getOrNull()
}
