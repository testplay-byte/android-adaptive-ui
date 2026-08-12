package com.adaptiveui.animeapp.domain.model

import kotlinx.serialization.Serializable

/**
 * A single episode with merged metadata from multiple sources (ani.zip primary, Jikan fallback,
 * Kitsu last resort). Episodes without metadata fall back to "Episode N".
 */
@Serializable
data class Episode(
    val number: Int,
    val title: String?,
    val description: String?,
    val thumbnail: String?,
    val airDate: String?,
    val runtime: Int?,
    val filler: Boolean,
    val recap: Boolean,
    val score: Double?,
    val source: EpisodeSource
) {
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: "Episode $number"
    val hasMetadata: Boolean get() = title != null || thumbnail != null || description != null
}

@Serializable
enum class EpisodeSource { ANIZIP, JIKAN, KITSU, PLACEHOLDER }
