package com.adaptiveui.animeapp.core.network.kitsu

import com.adaptiveui.animeapp.core.network.kitsu.dto.KitsuEpisodesResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for the Kitsu API (https://kitsu.io/api/edge).
 *
 * Kitsu provides per-episode metadata — thumbnail, canonical title, synopsis, airdate, length —
 * that AniList lacks. Used as a fallback/third source when ani.zip and Jikan have gaps.
 *
 * Kitsu IDs do NOT match AniList IDs. Resolution path:
 *   1. ani.zip `mappings.kitsu_id` (preferred — already in hand from the ani.zip response).
 *   2. Text search fallback: `GET /anime?filter[text]={title}&page[limit]=1` → take `data[0].id`.
 *
 * No auth required for reads. JSON:API spec.
 */
interface KitsuApi {

    /** Fetch episodes for a Kitsu anime ID. Paginated via offset. */
    @GET("anime/{id}/episodes")
    suspend fun getEpisodes(
        @Path("id") kitsuId: Int,
        @Query("page[limit]") limit: Int = 20,
        @Query("page[offset]") offset: Int = 0
    ): KitsuEpisodesResponse

    /** Search Kitsu anime by text (title) — returns the first match. Used for ID resolution. */
    @GET("anime")
    suspend fun searchAnime(
        @Query("filter[text]") query: String,
        @Query("page[limit]") limit: Int = 1
    ): KitsuEpisodesResponse
}
