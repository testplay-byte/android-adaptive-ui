package com.adaptiveui.animeapp.core.network.anizip

import com.adaptiveui.animeapp.core.network.anizip.dto.AniZipResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for the ani.zip API (https://api.ani.zip).
 *
 * ani.zip accepts an AniList ID directly (no mapping step required) and returns per-episode
 * metadata — thumbnail, multilingual titles, overview (description), airdate, runtime, rating —
 * plus a `mappings` object cross-referencing MAL/Kitsu/TVDB/etc IDs. Cloudflare-cached (~15 min).
 * No authentication required.
 */
interface AniZipApi {

    @GET("mappings")
    suspend fun getMappings(@Query("anilist_id") id: Int): AniZipResponse
}
