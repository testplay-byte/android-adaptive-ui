package com.adaptiveui.animeapp.data.mappers

import com.adaptiveui.animeapp.core.network.anilist.dto.MediaDto
import com.adaptiveui.animeapp.domain.model.AnimeCard
import com.adaptiveui.animeapp.domain.model.AnimeDetail
import com.adaptiveui.animeapp.domain.model.AnimeTag
import com.adaptiveui.animeapp.domain.model.Character
import com.adaptiveui.animeapp.domain.model.ExternalLink
import com.adaptiveui.animeapp.domain.model.FuzzyDate
import com.adaptiveui.animeapp.domain.model.NextAiringEpisode
import com.adaptiveui.animeapp.domain.model.Ranking
import com.adaptiveui.animeapp.domain.model.Recommendation
import com.adaptiveui.animeapp.domain.model.Relation
import com.adaptiveui.animeapp.domain.model.Studio
import com.adaptiveui.animeapp.domain.model.Trailer

/**
 * DTO -> domain mappers for AniList data. Both [toAnimeCard] and [toAnimeDetail] handle every
 * field; nulls are preserved (the domain models are fully nullable). Lists default to empty
 * when the JSON field is missing.
 */

fun MediaDto.toAnimeCard(): AnimeCard = AnimeCard(
    id = id ?: 0,
    idMal = idMal,
    titleRomaji = title?.romaji,
    titleEnglish = title?.english,
    titleNative = title?.native,
    coverExtraLarge = coverImage?.extraLarge,
    coverLarge = coverImage?.large,
    coverMedium = coverImage?.medium,
    coverColor = coverImage?.color,
    bannerImage = bannerImage,
    averageScore = averageScore,
    popularity = popularity,
    episodes = episodes,
    format = format,
    status = status,
    season = season,
    seasonYear = seasonYear,
    startDate = startDate.toFuzzyDate(),
    genres = genres,
    nextAiringEpisode = nextAiringEpisode.toDomain()
)

fun MediaDto.toAnimeDetail(): AnimeDetail = AnimeDetail(
    id = id ?: 0,
    idMal = idMal,
    titleRomaji = title?.romaji,
    titleEnglish = title?.english,
    titleNative = title?.native,
    coverExtraLarge = coverImage?.extraLarge,
    coverLarge = coverImage?.large,
    coverMedium = coverImage?.medium,
    coverColor = coverImage?.color,
    bannerImage = bannerImage,
    description = description,
    averageScore = averageScore,
    meanScore = meanScore,
    popularity = popularity,
    favourites = favourites,
    format = format,
    episodes = episodes,
    duration = duration,
    status = status,
    season = season,
    seasonYear = seasonYear,
    startDate = startDate.toFuzzyDate(),
    endDate = endDate.toFuzzyDate(),
    genres = genres,
    synonyms = synonyms,
    source = source,
    hashtag = hashtag,
    siteUrl = siteUrl,
    trailer = trailer?.let {
        Trailer(id = it.id, site = it.site, thumbnail = it.thumbnail)
    },
    tags = tags.map { tag ->
        AnimeTag(
            id = tag.id ?: 0,
            name = tag.name.orEmpty(),
            rank = tag.rank,
            isMediaSpoiler = tag.isMediaSpoiler ?: false,
            isGeneralSpoiler = tag.isGeneralSpoiler ?: false
        )
    },
    studios = studios?.nodes.orEmpty().map { s ->
        Studio(
            id = s.id ?: 0,
            name = s.name.orEmpty(),
            isAnimationStudio = s.isAnimationStudio ?: false,
            siteUrl = s.siteUrl
        )
    },
    relations = relations?.edges.orEmpty().mapNotNull { edge ->
        val node = edge.node ?: return@mapNotNull null
        Relation(
            edgeId = edge.id ?: 0,
            relationType = edge.relationType.orEmpty(),
            nodeId = node.id ?: 0,
            nodeTitle = node.title?.english ?: node.title?.romaji,
            nodeCover = node.coverImage?.large,
            nodeFormat = node.format,
            nodeType = node.type
        )
    },
    characters = characters?.edges.orEmpty().mapNotNull { edge ->
        val node = edge.node ?: return@mapNotNull null
        val va = edge.voiceActors.firstOrNull()
        Character(
            role = edge.role.orEmpty(),
            characterId = node.id ?: 0,
            characterName = node.name?.full,
            characterImage = node.image?.large,
            voiceActorId = va?.id,
            voiceActorName = va?.name?.full,
            voiceActorImage = va?.image?.large
        )
    },
    recommendations = recommendations?.nodes.orEmpty().mapNotNull { node ->
        val media = node.mediaRecommendation ?: return@mapNotNull null
        Recommendation(
            rating = node.rating,
            mediaId = media.id ?: 0,
            title = media.title?.english ?: media.title?.romaji,
            cover = media.coverImage?.large,
            score = media.averageScore
        )
    },
    nextAiringEpisode = nextAiringEpisode.toDomain(),
    externalLinks = externalLinks.map { link ->
        ExternalLink(
            id = link.id ?: 0,
            url = link.url.orEmpty(),
            site = link.site.orEmpty(),
            type = link.type,
            icon = link.icon,
            color = link.color
        )
    },
    rankings = rankings.map { r ->
        Ranking(
            id = r.id ?: 0,
            rank = r.rank ?: 0,
            type = r.type.orEmpty(),
            format = r.format,
            season = r.season,
            year = r.year,
            allTime = r.allTime ?: false,
            context = r.context.orEmpty()
        )
    }
)

// ---------- helpers ----------

private fun com.adaptiveui.animeapp.core.network.anilist.dto.FuzzyDateDto?.toFuzzyDate(): FuzzyDate? {
    this ?: return null
    return FuzzyDate(year = year, month = month, day = day)
}

private fun com.adaptiveui.animeapp.core.network.anilist.dto.NextAiringEpisodeDto?.toDomain(): NextAiringEpisode? {
    this ?: return null
    val ep = episode ?: return null
    val at = airingAt ?: return null
    val until = timeUntilAiring ?: 0L
    return NextAiringEpisode(episode = ep, airingAt = at, timeUntilAiring = until)
}
