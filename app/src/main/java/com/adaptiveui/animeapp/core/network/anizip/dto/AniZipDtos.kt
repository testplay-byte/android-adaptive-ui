package com.adaptiveui.animeapp.core.network.anizip.dto

import kotlinx.serialization.Serializable

/**
 * Top-level response from `GET https://api.ani.zip/mappings?anilist_id={id}`.
 *
 * - `titles` — multilingual title map (e.g. `{"en":"...", "ja":"..."}`).
 * - `episodes` — keyed by episode number (as a STRING — ani.zip's API quirk).
 * - `episodeCount` — total episodes ani.zip knows about (may be null).
 * - `mappings` — cross-reference IDs to other databases (MAL, Kitsu, TVDB, etc.).
 *
 * All fields are nullable / defaulted so an unexpected shape (e.g. 404 with a JSON error body)
 * doesn't crash the deserializer — repositories treat null `episodes` as "no metadata available".
 */
@Serializable
data class AniZipResponse(
    val titles: Map<String, String> = emptyMap(),
    val episodes: Map<String, AniZipEpisode> = emptyMap(),
    val episodeCount: Int? = null,
    val mappings: AniZipMappings? = null
)

@Serializable
data class AniZipEpisode(
    val episode: String? = null,
    val title: Map<String, String>? = null,
    val airDate: String? = null,
    val image: String? = null,
    val overview: String? = null,
    val runtime: Int? = null,
    val rating: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null
)

@Serializable
data class AniZipMappings(
    val kitsu_id: Int? = null,
    val mal_id: Int? = null,
    val anilist_id: Int? = null,
    val thetvdb_id: Int? = null,
    val anidb_id: Int? = null,
    val imdb_id: String? = null,
    val themoviedb_id: Int? = null,
    val livechart_id: Int? = null,
    val animeplanet_id: String? = null,
    val anisearch_id: Int? = null
)
