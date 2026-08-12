package com.adaptiveui.animeapp.core.network.jikan

import com.adaptiveui.animeapp.core.network.jikan.dto.JikanEpisodesResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for the Jikan v4 API (https://api.jikan.moe/v4) — an unofficial MyAnimeList
 * REST wrapper. Used as a fallback episode source: AniList's `Media.idMal` is passed directly
 * to [getEpisodes].
 *
 * Rate limits: 3 req/sec, 60 req/min. The repository fetches at most a single page per anime
 * so we never approach the limit; an OkHttp interceptor is not required.
 */
interface JikanApi {

    @GET("anime/{id}/episodes")
    suspend fun getEpisodes(
        @Path("id") malId: Int,
        @Query("page") page: Int = 1
    ): JikanEpisodesResponse
}
