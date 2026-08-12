package com.adaptiveui.animeapp.domain.model

import kotlinx.serialization.Serializable

/**
 * Full anime detail. EVERY field AniList exposes is captured here and cached locally
 * verbatim (per the requirement: "the whole home page data will be properly stored and
 * will be properly saved locally with all the things included in it, whether we use them or not").
 */
@Serializable
data class AnimeDetail(
    val id: Int,
    val idMal: Int?,
    val titleRomaji: String?,
    val titleEnglish: String?,
    val titleNative: String?,
    val coverExtraLarge: String?,
    val coverLarge: String?,
    val coverMedium: String?,
    val coverColor: String?,
    val bannerImage: String?,
    val description: String?,
    val averageScore: Int?,
    val meanScore: Int?,
    val popularity: Int?,
    val favourites: Int?,
    val format: String?,
    val episodes: Int?,
    val duration: Int?,
    val status: String?,
    val season: String?,
    val seasonYear: Int?,
    val startDate: FuzzyDate?,
    val endDate: FuzzyDate?,
    val genres: List<String>,
    val synonyms: List<String>,
    val source: String?,
    val hashtag: String?,
    val siteUrl: String?,
    val trailer: Trailer?,
    val tags: List<AnimeTag>,
    val studios: List<Studio>,
    val relations: List<Relation>,
    val characters: List<Character>,
    val recommendations: List<Recommendation>,
    val nextAiringEpisode: NextAiringEpisode?,
    val externalLinks: List<ExternalLink>,
    val rankings: List<Ranking>
) {
    val displayTitle: String get() = titleEnglish ?: titleRomaji ?: titleNative ?: "Untitled"
    val coverUrl: String get() = coverExtraLarge ?: coverLarge ?: coverMedium ?: ""
    val scoreLabel: String? get() = averageScore?.let { "%.1f".format(it / 10.0) }
    val durationLabel: String? get() = duration?.let { "${it}m" }
    val seasonLabel: String? get() = listOfNotNull(season?.lowercase()?.replaceFirstChar { it.uppercase() }, seasonYear?.toString()).joinToString(" ")
}

@Serializable
data class Trailer(val id: String?, val site: String?, val thumbnail: String?) {
    val isYoutube: Boolean get() = site?.equals("youtube", ignoreCase = true) == true
    val youtubeThumbnail: String? get() = if (isYoutube && id != null) "https://img.youtube.com/vi/$id/hqdefault.jpg" else thumbnail
}

@Serializable
data class AnimeTag(val id: Int, val name: String, val rank: Int?, val isMediaSpoiler: Boolean, val isGeneralSpoiler: Boolean)

@Serializable
data class Studio(val id: Int, val name: String, val isAnimationStudio: Boolean, val siteUrl: String?) {
    val isMain: Boolean get() = !isAnimationStudio
}

@Serializable
data class Relation(val edgeId: Int, val relationType: String, val nodeId: Int, val nodeTitle: String?, val nodeCover: String?, val nodeFormat: String?, val nodeType: String?)

@Serializable
data class Character(
    val role: String,
    val characterId: Int,
    val characterName: String?,
    val characterImage: String?,
    val voiceActorId: Int?,
    val voiceActorName: String?,
    val voiceActorImage: String?
)

@Serializable
data class Recommendation(val rating: Int?, val mediaId: Int, val title: String?, val cover: String?, val score: Int?)

@Serializable
data class ExternalLink(val id: Int, val url: String, val site: String, val type: String?, val icon: String?, val color: String?)

@Serializable
data class Ranking(val id: Int, val rank: Int, val type: String, val format: String?, val season: String?, val year: Int?, val allTime: Boolean, val context: String)
