package com.adaptiveui.animeapp.domain.model

import kotlinx.serialization.Serializable

/**
 * Compact anime representation used in lists (home, search, library).
 * Mirrors the fields returned by the AniList `mediaCard` fragment.
 */
@Serializable
data class AnimeCard(
    val id: Int,
    val idMal: Int?,
    val titleRomaji: String?,
    val titleEnglish: String?,
    val titleNative: String?,
    val coverExtraLarge: String?,
    val coverLarge: String?,
    val coverMedium: String?,
    val coverColor: String?,
    val bannerImage: String?,
    val averageScore: Int?,
    val popularity: Int?,
    val episodes: Int?,
    val format: String?,
    val status: String?,
    val season: String?,
    val seasonYear: Int?,
    val startDate: FuzzyDate?,
    val genres: List<String>,
    val nextAiringEpisode: NextAiringEpisode?
) {
    val displayTitle: String get() = titleEnglish ?: titleRomaji ?: titleNative ?: "Untitled"
    val coverUrl: String get() = coverExtraLarge ?: coverLarge ?: coverMedium ?: ""
    val yearLabel: String? get() = seasonYear?.toString()
    val scoreLabel: String? get() = averageScore?.let { "%.1f".format(it / 10.0) }
}

@Serializable
data class FuzzyDate(val year: Int? = null, val month: Int? = null, val day: Int? = null) {
    val isValid: Boolean get() = year != null || month != null || day != null
    override fun toString(): String = listOfNotNull(year, month, day).joinToString("-")
}

@Serializable
data class NextAiringEpisode(val episode: Int, val airingAt: Long, val timeUntilAiring: Long)
