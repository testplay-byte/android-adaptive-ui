package com.adaptiveui.animeapp.core.network.jikan.dto

import kotlinx.serialization.Serializable

/**
 * Response from `GET https://api.jikan.moe/v4/anime/{mal_id}/episodes`.
 * Jikan does NOT provide thumbnails or descriptions — only titles (English, Japanese, Romanji),
 * air date, score, and filler/recap flags. Used as the secondary source after ani.zip.
 */
@Serializable
data class JikanEpisodesResponse(
    val data: List<JikanEpisode> = emptyList(),
    val pagination: JikanPagination? = null
)

@Serializable
data class JikanEpisode(
    val mal_id: Int? = null,
    val title: String? = null,
    val title_japanese: String? = null,
    val title_romanji: String? = null,
    val aired: String? = null,
    val score: Double? = null,
    val filler: Boolean? = null,
    val recap: Boolean? = null
)

@Serializable
data class JikanPagination(
    val last_visible_page: Int? = null,
    val has_next_page: Boolean? = null,
    val items: JikanPaginationItems? = null,
    val current_page: Int? = null
)

@Serializable
data class JikanPaginationItems(
    val count: Int? = null,
    val total: Int? = null,
    val per_page: Int? = null
)
