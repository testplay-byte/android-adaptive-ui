package com.adaptiveui.animeapp.core.network.anilist

import com.adaptiveui.animeapp.core.network.anilist.dto.AniListResponse
import com.adaptiveui.animeapp.core.network.anilist.dto.GraphQLRequest
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit interface for the AniList GraphQL endpoint (https://graphql.anilist.co).
 *
 * AniList exposes a single GraphQL endpoint accepting a POST body with `query` and `variables`.
 * No authentication is required for public queries; rate limit = 90 req/min tracked via the
 * `X-RateLimit-Remaining` / `X-RateLimit-Reset` response headers (handled in [NetworkModule]).
 */
interface AniListApi {

    @POST("graphql")
    suspend fun query(@Body body: GraphQLRequest): AniListResponse
}
