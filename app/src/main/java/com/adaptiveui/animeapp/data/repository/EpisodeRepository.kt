package com.adaptiveui.animeapp.data.repository

import android.util.Log
import com.adaptiveui.animeapp.core.database.dao.CacheDao
import com.adaptiveui.animeapp.core.database.entity.EpisodeCacheEntity
import com.adaptiveui.animeapp.core.network.anizip.AniZipApi
import com.adaptiveui.animeapp.core.network.jikan.JikanApi
import com.adaptiveui.animeapp.core.network.kitsu.KitsuApi
import com.adaptiveui.animeapp.data.mappers.mergeEpisodes
import com.adaptiveui.animeapp.data.mappers.toDomain
import com.adaptiveui.animeapp.domain.model.Episode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches and merges per-episode metadata for a single anime from THREE sources.
 *
 * Strategy:
 *   1. ani.zip (AniList ID direct) — PRIMARY: thumbnails + descriptions + multilingual titles.
 *      Also returns `mappings.kitsu_id` for Kitsu lookup.
 *   2. Jikan (AniList idMal) — SECONDARY: titles + airdate + filler/recap flags + score.
 *   3. Kitsu (via ani.zip mappings.kitsu_id, or title-search fallback) — TERTIARY: thumbnails +
 *      descriptions as fallback when ani.zip has gaps.
 *
 * All three requests run in parallel where possible. Results are merged by episode number with
 * ani.zip winning on overlap, Kitsu as fallback for thumbnails/descriptions, and Jikan enriching
 * (filler/recap flags, score). When the total episode count is known (AniList `Media.episodes`),
 * missing episode numbers are filled with placeholders.
 *
 * CRITICAL FIX: Empty/error results are NOT cached — only results with actual metadata are
 * persisted. This prevents a transient network failure from permanently hiding episodes.
 */
@Singleton
class EpisodeRepository @Inject constructor(
    private val aniZipApi: AniZipApi,
    private val jikanApi: JikanApi,
    private val kitsuApi: KitsuApi,
    private val cacheDao: CacheDao,
    private val json: Json
) {

    /**
     * @param animeId       AniList Media id (for ani.zip + cache key)
     * @param idMal         MyAnimeList id (AniList's `Media.idMal`) — null skips Jikan
     * @param episodeCount  Total episodes from AniList (`Media.episodes`) — fills placeholders
     * @param titleForKitsuSearch  Romaji/English title for Kitsu ID resolution fallback
     * @param forceRefresh  if true, ignores the cache and always hits the network
     */
    suspend fun getEpisodes(
        animeId: Int,
        idMal: Int?,
        episodeCount: Int?,
        forceRefresh: Boolean = false,
        titleForKitsuSearch: String? = null
    ): List<Episode> = withContext(Dispatchers.IO) {
        // Return cache if present and not forcing refresh.
        if (!forceRefresh) {
            cacheDao.getEpisodeCache(animeId)?.let { cached ->
                runCatching { decodeEpisodes(cached.json) }.getOrNull()?.let { return@withContext it }
            }
        }

        // Fan out all requests. Each swallows its own exceptions.
        var anizipEps: List<Episode> = emptyList()
        var jikanEps: List<Episode> = emptyList()
        var kitsuEps: List<Episode> = emptyList()
        try {
            coroutineScope {
                val anizipDeferred = async {
                    runCatching {
                        val resp = aniZipApi.getMappings(animeId)
                        val eps = resp.episodes.entries
                            .mapNotNull { (key, ep) ->
                                val n = key.toIntOrNull() ?: ep.episode?.toIntOrNull() ?: return@mapNotNull null
                                ep.toDomain(n)
                            }
                            .sortedBy { it.number }
                        eps to resp.mappings
                    }.getOrElse {
                        Log.w("EpisodeRepo", "ani.zip fetch failed for $animeId", it)
                        emptyList<Episode>() to null
                    }
                }
                val jikanDeferred = async {
                    runCatching {
                        if (idMal == null || idMal <= 0) emptyList()
                        else jikanApi.getEpisodes(idMal, page = 1).data.map { it.toDomain() }
                    }.getOrElse {
                        Log.w("EpisodeRepo", "Jikan fetch failed for mal=$idMal", it)
                        emptyList()
                    }
                }
                // Kitsu needs a Kitsu ID. Try ani.zip's mappings first, then title-search fallback.
                val anizipResult = anizipDeferred.await()
                anizipEps = anizipResult.first
                val mappings = anizipResult.second
                jikanEps = jikanDeferred.await()

                kitsuEps = runCatching {
                    val kitsuId = mappings?.kitsu_id
                    if (kitsuId != null && kitsuId > 0) {
                        fetchAllKitsuEpisodes(kitsuId)
                    } else if (!titleForKitsuSearch.isNullOrBlank()) {
                        runCatching {
                            val searchResp = kitsuApi.searchAnime(titleForKitsuSearch)
                            val foundId = searchResp.data.firstOrNull()?.id?.toIntOrNull()
                            if (foundId != null) fetchAllKitsuEpisodes(foundId) else emptyList()
                        }.getOrElse {
                            Log.w("EpisodeRepo", "Kitsu title-search failed for '$titleForKitsuSearch'", it)
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }
                }.getOrElse {
                    Log.w("EpisodeRepo", "Kitsu fetch failed for anime=$animeId", it)
                    emptyList()
                }
            }
        } catch (e: Throwable) {
            Log.e("EpisodeRepo", "Episode fetch failed for anime=$animeId", e)
        }

        val merged = mergeEpisodes(anizipEps, jikanEps, kitsuEps, episodeCount)

        // CRITICAL: only cache if we got real metadata from at least one source.
        // Placeholder-only lists (all source == PLACEHOLDER) are NOT cached so a retry can
        // actually fetch the data instead of returning a permanently-empty cache.
        val hasRealMetadata = merged.any { it.source != com.adaptiveui.animeapp.domain.model.EpisodeSource.PLACEHOLDER }
        if (hasRealMetadata) {
            runCatching {
                val raw = json.encodeToString(ListSerializer(Episode.serializer()), merged)
                cacheDao.saveEpisodeCache(
                    EpisodeCacheEntity(
                        animeId = animeId,
                        json = raw,
                        cachedAt = System.currentTimeMillis()
                    )
                )
            }
        } else {
            Log.i("EpisodeRepo", "No real metadata for anime=$animeId — not caching placeholders (will retry next time)")
        }

        merged
    }

    /**
     * Fetch ALL Kitsu episodes by paginating through the offset-based API.
     * Kitsu returns max 20 per page.
     */
    private suspend fun fetchAllKitsuEpisodes(kitsuId: Int): List<Episode> {
        val all = mutableListOf<Episode>()
        var offset = 0
        val limit = 20
        var hasMore = true
        // Safety cap to avoid infinite loops on broken pagination.
        var pages = 0
        while (hasMore && pages < 50) {
            val resp = kitsuApi.getEpisodes(kitsuId, limit = limit, offset = offset)
            val page = resp.data.map { it.toDomain() }
            all.addAll(page)
            hasMore = !resp.links?.next.isNullOrBlank() && page.isNotEmpty()
            offset += page.size
            pages++
            if (page.size < limit) hasMore = false
        }
        return all
    }

    private fun decodeEpisodes(rawJson: String): List<Episode> =
        json.decodeFromString(ListSerializer(Episode.serializer()), rawJson)
}
