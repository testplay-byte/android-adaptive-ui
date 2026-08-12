package com.adaptiveui.animeapp.core.network.anilist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Body of a GraphQL POST request. `variables` is a Map<String, JsonElement> — Retrofit's
 * kotlinx.serialization converter serializes it via the injected [kotlinx.serialization.json.Json]
 * instance (configured with `encodeDefaults = true`). Callers build JsonElement values via
 * `buildJsonObject { put("key", value) }` (see [com.adaptiveui.animeapp.data.repository.AnimeRepository]).
 */
@Serializable
data class GraphQLRequest(
    val query: String,
    val variables: Map<String, JsonElement> = emptyMap()
)

/**
 * Raw AniList response envelope. AniList returns `{ "data": { ... } }` on success and
 * `{ "errors": [...] }` on failure. We only deserialize `data` here and let the repository
 * pull the relevant sub-keys via the [JsonElement] tree API.
 *
 * Error responses surface as HttpException (non-2xx) in Retrofit and are caught in the
 * repositories' try/catch fallback-to-cache paths.
 */
@Serializable
data class AniListResponse(
    val data: JsonElement? = null
)

// ---------- HOME PAGE ----------

@Serializable
data class HomePageDto(
    val trending: PageDto? = null,
    @SerialName("seasonPopular") val seasonPopular: PageDto? = null,
    val upcoming: PageDto? = null,
    @SerialName("topRated") val topRated: PageDto? = null,
    @SerialName("allTimePopular") val allTimePopular: PageDto? = null
)

@Serializable
data class PageDto(
    val media: List<MediaDto> = emptyList()
)

// ---------- MEDIA DETAILS ----------

@Serializable
data class MediaDetailsDto(
    val Media: MediaDto? = null
)

// ---------- SEARCH ----------

@Serializable
data class SearchPageDto(
    val Page: SearchPageInfoDto? = null
)

@Serializable
data class SearchPageInfoDto(
    val pageInfo: PageInfoDto? = null,
    val media: List<MediaDto> = emptyList()
)

@Serializable
data class PageInfoDto(
    val total: Int? = null,
    val currentPage: Int? = null,
    val lastPage: Int? = null,
    val hasNextPage: Boolean? = null,
    val perPage: Int? = null
)

// ---------- MEDIA (every field returned by MEDIA_DETAILS_QUERY) ----------

@Serializable
data class MediaDto(
    val id: Int? = null,
    val idMal: Int? = null,
    val title: TitleDto? = null,
    val coverImage: CoverImageDto? = null,
    val bannerImage: String? = null,
    val description: String? = null,
    val averageScore: Int? = null,
    val meanScore: Int? = null,
    val popularity: Int? = null,
    val favourites: Int? = null,
    val format: String? = null,
    val episodes: Int? = null,
    val duration: Int? = null,
    val status: String? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val startDate: FuzzyDateDto? = null,
    val endDate: FuzzyDateDto? = null,
    val genres: List<String> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val source: String? = null,
    val hashtag: String? = null,
    val siteUrl: String? = null,
    val trailer: TrailerDto? = null,
    val tags: List<TagDto> = emptyList(),
    val studios: StudiosDto? = null,
    val relations: RelationsDto? = null,
    val characters: CharactersDto? = null,
    val recommendations: RecommendationsDto? = null,
    val nextAiringEpisode: NextAiringEpisodeDto? = null,
    val externalLinks: List<ExternalLinkDto> = emptyList(),
    val rankings: List<RankingDto> = emptyList()
)

@Serializable
data class TitleDto(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null
)

@Serializable
data class CoverImageDto(
    val extraLarge: String? = null,
    val large: String? = null,
    val medium: String? = null,
    val color: String? = null
)

@Serializable
data class FuzzyDateDto(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null
)

@Serializable
data class TrailerDto(
    val id: String? = null,
    val site: String? = null,
    val thumbnail: String? = null
)

@Serializable
data class TagDto(
    val id: Int? = null,
    val name: String? = null,
    val rank: Int? = null,
    val isMediaSpoiler: Boolean? = null,
    val isGeneralSpoiler: Boolean? = null
)

@Serializable
data class StudiosDto(
    val nodes: List<StudioDto> = emptyList()
)

@Serializable
data class StudioDto(
    val id: Int? = null,
    val name: String? = null,
    val isAnimationStudio: Boolean? = null,
    val siteUrl: String? = null
)

@Serializable
data class RelationsDto(
    val edges: List<RelationEdgeDto> = emptyList()
)

@Serializable
data class RelationEdgeDto(
    val id: Int? = null,
    val relationType: String? = null,
    val node: RelationNodeDto? = null
)

@Serializable
data class RelationNodeDto(
    val id: Int? = null,
    val title: TitleDto? = null,
    val coverImage: CoverImageDto? = null,
    val format: String? = null,
    val type: String? = null
)

@Serializable
data class CharactersDto(
    val edges: List<CharacterEdgeDto> = emptyList()
)

@Serializable
data class CharacterEdgeDto(
    val role: String? = null,
    val node: CharacterNodeDto? = null,
    val voiceActors: List<VoiceActorDto> = emptyList()
)

@Serializable
data class CharacterNodeDto(
    val id: Int? = null,
    val name: NameDto? = null,
    val image: CharacterImageDto? = null
)

@Serializable
data class NameDto(
    val full: String? = null
)

@Serializable
data class CharacterImageDto(
    val large: String? = null
)

@Serializable
data class VoiceActorDto(
    val id: Int? = null,
    val name: NameDto? = null,
    val image: CharacterImageDto? = null
)

@Serializable
data class RecommendationsDto(
    val nodes: List<RecommendationNodeDto> = emptyList()
)

@Serializable
data class RecommendationNodeDto(
    val rating: Int? = null,
    val mediaRecommendation: RecommendationMediaDto? = null
)

@Serializable
data class RecommendationMediaDto(
    val id: Int? = null,
    val title: TitleDto? = null,
    val coverImage: CoverImageDto? = null,
    val averageScore: Int? = null
)

@Serializable
data class NextAiringEpisodeDto(
    val episode: Int? = null,
    val airingAt: Long? = null,
    val timeUntilAiring: Long? = null
)

@Serializable
data class ExternalLinkDto(
    val id: Int? = null,
    val url: String? = null,
    val site: String? = null,
    val type: String? = null,
    val icon: String? = null,
    val color: String? = null
)

@Serializable
data class RankingDto(
    val id: Int? = null,
    val rank: Int? = null,
    val type: String? = null,
    val format: String? = null,
    val season: String? = null,
    val year: Int? = null,
    val allTime: Boolean? = null,
    val context: String? = null
)
