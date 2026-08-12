package com.adaptiveui.animeapp.data.repository

import com.adaptiveui.animeapp.core.database.dao.CacheDao
import com.adaptiveui.animeapp.core.database.entity.AnimeCacheEntity
import com.adaptiveui.animeapp.core.database.entity.HomeCacheEntity
import com.adaptiveui.animeapp.core.network.anilist.AniListApi
import com.adaptiveui.animeapp.core.network.anilist.AniListQueries
import com.adaptiveui.animeapp.core.network.anilist.dto.GraphQLRequest
import com.adaptiveui.animeapp.core.network.anilist.dto.HomePageDto
import com.adaptiveui.animeapp.core.network.anilist.dto.MediaDetailsDto
import com.adaptiveui.animeapp.core.network.anilist.dto.MediaDto
import com.adaptiveui.animeapp.core.network.anilist.dto.PageDto
import com.adaptiveui.animeapp.core.network.anilist.dto.PageInfoDto
import com.adaptiveui.animeapp.core.network.anilist.dto.SearchPageDto
import com.adaptiveui.animeapp.data.mappers.toAnimeCard
import com.adaptiveui.animeapp.data.mappers.toAnimeDetail
import com.adaptiveui.animeapp.domain.model.AnimeCard
import com.adaptiveui.animeapp.domain.model.AnimeDetail
import com.adaptiveui.animeapp.domain.model.HomeData
import com.adaptiveui.animeapp.domain.model.SearchFilters
import com.adaptiveui.animeapp.domain.model.SearchResult
import com.adaptiveui.animeapp.domain.model.SearchResultPageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single entry point for AniList data: home, details, search. All methods are `suspend`
 * and never throw on network failure — they fall back to the Room cache, and on cache miss
 * return empty/default values so the UI keeps rendering.
 *
 * Caching strategy: the raw GraphQL `data` JSON is persisted verbatim in Room (so future
 * field additions don't break the cache schema). On read we decode it back into the typed DTO
 * via the injected [Json] instance, then map to the domain model.
 *
 * All network calls run on [Dispatchers.IO].
 */
@Singleton
class AnimeRepository @Inject constructor(
    private val anilistApi: AniListApi,
    private val cacheDao: CacheDao,
    private val json: Json
) {

    /**
     * Fetches the home page. Computes the current season/year and next season/year from the
     * device clock for the GraphQL variables.
     *
     * @param forceRefresh if true, ignores the cache and always hits the network
     */
    suspend fun getHomePage(forceRefresh: Boolean = false): HomeData = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            cacheDao.getHomeCache()?.let { cached ->
                runCatching { decodeHomeData(cached.json) }.getOrNull()?.let { return@withContext it }
            }
        }
        val (season, year, nextSeason, nextYear) = currentSeasonInfo()
        val variables = buildJsonObject {
            put("season", season)
            put("seasonYear", year)
            put("nextSeason", nextSeason)
            put("nextYear", nextYear)
        }
        val request = GraphQLRequest(
            query = AniListQueries.HOME_PAGE_QUERY,
            variables = variables
        )
        val homeData: HomeData = try {
            val response = anilistApi.query(request)
            val dto = decodeDto<HomePageDto>(response.data, json)
            if (dto != null) {
                val home = HomeData(
                    trending = dto.trending.toCards(),
                    seasonal = dto.seasonPopular.toCards(),
                    upcoming = dto.upcoming.toCards(),
                    topRated = dto.topRated.toCards(),
                    allTimePopular = dto.allTimePopular.toCards()
                )
                // Cache the raw `data` JSON verbatim.
                cacheDao.saveHomeCache(
                    HomeCacheEntity(
                        id = 0,
                        json = json.encodeToString(JsonObject.serializer(), response.data?.jsonObject ?: buildJsonObject {}),
                        cachedAt = System.currentTimeMillis()
                    )
                )
                home
            } else {
                // Null data — treat as failure and fall through to cache.
                throw IllegalStateException("AniList returned null data")
            }
        } catch (t: Throwable) {
            // Network failure — fall back to cache if present, else empty.
            cacheDao.getHomeCache()?.let { cached ->
                runCatching { decodeHomeData(cached.json) }.getOrNull()
            } ?: HomeData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        }
        homeData
    }

    /**
     * Fetches full details for one anime. Caches the raw Media JSON.
     */
    suspend fun getAnimeDetail(id: Int, forceRefresh: Boolean = false): AnimeDetail? =
        withContext(Dispatchers.IO) {
            if (!forceRefresh) {
                cacheDao.getAnimeCache(id)?.let { cached ->
                    runCatching { decodeAnimeDetail(cached.json) }.getOrNull()?.let { return@withContext it }
                }
            }
            val variables = buildJsonObject { put("id", id) }
            val request = GraphQLRequest(
                query = AniListQueries.MEDIA_DETAILS_QUERY,
                variables = variables
            )
            try {
                val response = anilistApi.query(request)
                val dto = decodeDto<MediaDetailsDto>(response.data, json)
                val media = dto?.Media
                if (media != null) {
                    cacheDao.saveAnimeCache(
                        AnimeCacheEntity(
                            animeId = id,
                            // Cache the inner Media object only — `data.Media` — so decoding is direct.
                            json = json.encodeToString(MediaDto.serializer(), media),
                            cachedAt = System.currentTimeMillis()
                        )
                    )
                    return@withContext media.toAnimeDetail()
                }
                // Fall through to cache on null Media.
            } catch (_: Throwable) {
                // Fall through to cache.
            }
            cacheDao.getAnimeCache(id)?.let { cached ->
                runCatching { decodeAnimeDetail(cached.json) }.getOrNull()
            }
        }

    /**
     * Searches AniList with the given query string, page, and filters.
     * If `query` is blank/null, performs a "default" search (sorted by popularity).
     */
    suspend fun search(
        query: String?,
        page: Int = 1,
        filters: SearchFilters = SearchFilters()
    ): SearchResult = withContext(Dispatchers.IO) {
        val variables = buildJsonObject {
            put("page", page)
            put("perPage", 30)
            if (!query.isNullOrBlank()) put("search", query)
            // GraphQL expects `sort: [MediaSort]` — serialize the list as a JsonArray of string literals.
            put("sort", JsonArray(filters.sort.map { JsonPrimitive(it) }))
            filters.genre?.let { put("genre", it) }
            filters.season?.let { put("season", it) }
            filters.year?.let { put("seasonYear", it) }
            filters.format?.let { put("format", it) }
            filters.status?.let { put("status", it) }
        }
        val request = GraphQLRequest(
            query = AniListQueries.SEARCH_QUERY,
            variables = variables
        )
        try {
            val response = anilistApi.query(request)
            val dto = decodeDto<SearchPageDto>(response.data, json)
            val pageInfo = dto?.Page?.pageInfo
            val items = dto?.Page?.media.orEmpty().map { it.toAnimeCard() }
            SearchResult(
                items = items,
                pageInfo = pageInfo.toDomain(),
                hasNextPage = pageInfo?.hasNextPage ?: false
            )
        } catch (_: Throwable) {
            SearchResult(emptyList(), SearchResultPageInfo(0, page, 1, 30), false)
        }
    }

    /**
     * Default search — no text query, sort by POPULARITY_DESC, page 1.
     * Used by the Search screen's initial state.
     */
    suspend fun getDefaultSearch(): SearchResult = search(
        query = null,
        page = 1,
        filters = SearchFilters(sort = listOf("POPULARITY_DESC"))
    )

    // ---------- helpers ----------

    private fun decodeHomeData(rawJson: String): HomeData {
        // The cached JSON is the GraphQL `data` object (with the 5 aliased Page selections).
        val dto = json.decodeFromString(HomePageDto.serializer(), rawJson)
        return HomeData(
            trending = dto.trending.toCards(),
            seasonal = dto.seasonPopular.toCards(),
            upcoming = dto.upcoming.toCards(),
            topRated = dto.topRated.toCards(),
            allTimePopular = dto.allTimePopular.toCards()
        )
    }

    private fun decodeAnimeDetail(rawJson: String): AnimeDetail {
        // The cached JSON is the Media object itself (not wrapped in `data.Media`).
        val media = json.decodeFromString(MediaDto.serializer(), rawJson)
        return media.toAnimeDetail()
    }

    private fun PageDto?.toCards(): List<AnimeCard> = this?.media.orEmpty().map { it.toAnimeCard() }

    private fun PageInfoDto?.toDomain(): SearchResultPageInfo = SearchResultPageInfo(
        total = this?.total ?: 0,
        currentPage = this?.currentPage ?: 1,
        lastPage = this?.lastPage ?: 1,
        perPage = this?.perPage ?: 30
    )

    /**
     * Returns [currentSeason, currentYear, nextSeason, nextYear] computed from the device clock.
     * Jan-Mar=WINTER, Apr-Jun=SPRING, Jul-Sep=SUMMER, Oct-Dec=FALL.
     */
    private fun currentSeasonInfo(): SeasonInfo {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) // 0-11
        val (season, nextSeason, nextYear) = when (month) {
            in 0..2 -> Triple("WINTER", "SPRING", year)
            in 3..5 -> Triple("SPRING", "SUMMER", year)
            in 6..8 -> Triple("SUMMER", "FALL", year)
            in 9..11 -> Triple("FALL", "WINTER", year + 1)
            else -> Triple("WINTER", "SPRING", year)
        }
        return SeasonInfo(season, year, nextSeason, nextYear)
    }

    private data class SeasonInfo(
        val season: String,
        val year: Int,
        val nextSeason: String,
        val nextYear: Int
    )
}

/**
 * Decodes an arbitrary [JsonElement] subtree into a typed DTO by re-serializing it to a string
 * and decoding the string. The AniList endpoint returns loosely-typed `data` objects whose shape
 * depends on the query; this helper routes them through the typed DTOs safely.
 *
 * Returns null if [element] is null — callers should treat that as "no data" and fall back to cache.
 */
private inline fun <reified T> decodeDto(
    element: kotlinx.serialization.json.JsonElement?,
    json: Json
): T? {
    if (element == null) return null
    val raw = json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), element)
    return json.decodeFromString(raw)
}
