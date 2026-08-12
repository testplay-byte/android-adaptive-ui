package com.adaptiveui.animeapp.domain.model

import kotlinx.serialization.Serializable

/**
 * Search filters applied to the AniList `Page` query. All nullable — `null` means "no filter"
 * and the variable is omitted from the GraphQL request.
 *
 * `sort` is a list so the user can stack sort criteria; AniList accepts a `[MediaSort]` list.
 * Strings are used (rather than the GraphQL enum types) so the value can be passed verbatim
 * to the request.
 */
@Serializable
data class SearchFilters(
    val genre: String? = null,
    val year: Int? = null,
    val season: String? = null,
    val format: String? = null,
    val status: String? = null,
    val sort: List<String> = listOf("POPULARITY_DESC")
)

/**
 * Result of a search query. `items` is the current page's media; `pageInfo` carries
 * pagination metadata. `hasNextPage` is also exposed at the top level for convenience.
 */
@Serializable
data class SearchResult(
    val items: List<AnimeCard>,
    val pageInfo: SearchResultPageInfo,
    val hasNextPage: Boolean
)

@Serializable
data class SearchResultPageInfo(
    val total: Int,
    val currentPage: Int,
    val lastPage: Int,
    val perPage: Int
)

/**
 * Home page data — the five AniList aliased sections returned in a single GraphQL request.
 * Each is a list of [AnimeCard]. Cached verbatim (the raw JSON) so the Home screen renders
 * offline; this domain object is what the UI consumes.
 */
@Serializable
data class HomeData(
    val trending: List<AnimeCard>,
    val seasonal: List<AnimeCard>,
    val upcoming: List<AnimeCard>,
    val topRated: List<AnimeCard>,
    val allTimePopular: List<AnimeCard>
)
