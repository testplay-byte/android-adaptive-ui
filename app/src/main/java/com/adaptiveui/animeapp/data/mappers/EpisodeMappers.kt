package com.adaptiveui.animeapp.data.mappers

import com.adaptiveui.animeapp.core.network.anizip.dto.AniZipEpisode
import com.adaptiveui.animeapp.core.network.jikan.dto.JikanEpisode
import com.adaptiveui.animeapp.core.network.kitsu.dto.KitsuResource
import com.adaptiveui.animeapp.domain.model.Episode
import com.adaptiveui.animeapp.domain.model.EpisodeSource

/**
 * Episode DTO -> domain mappers + the merge function that combines ani.zip and Jikan results
 * into a single ordered episode list with placeholders for any gaps.
 *
 * Merge priority per episode (per the research):
 *   - thumbnail  = ani.zip.image   (Jikan has no thumbnails)
 *   - title      = ani.zip.title.en || jikan.title || jikan.title_romanji || null
 *   - description= ani.zip.overview (Jikan has no descriptions)
 *   - airDate    = ani.zip.airDate || jikan.aired
 *   - runtime    = ani.zip.runtime
 *   - rating     = jikan.score (Double) — ani.zip's `rating` is a String and rarely populated
 *   - filler/recap = jikan.filler / jikan.recap
 *   - source     = ANIZIP if the ani.zip entry had any metadata, else JIKAN, else PLACEHOLDER
 */

fun AniZipEpisode.toDomain(number: Int): Episode {
    val titleEn = title?.entries?.firstOrNull { it.key.equals("en", ignoreCase = true) }?.value
        ?: title?.entries?.firstOrNull { it.key.equals("x-jat", ignoreCase = true) }?.value
        ?: title?.values?.firstOrNull()
    return Episode(
        number = number,
        title = titleEn,
        description = overview,
        thumbnail = image,
        airDate = airDate,
        runtime = runtime,
        filler = false,
        recap = false,
        score = rating?.toDoubleOrNull(),
        source = EpisodeSource.ANIZIP
    )
}

fun JikanEpisode.toDomain(): Episode = Episode(
    number = mal_id ?: 0,
    title = title ?: title_romanji ?: title_japanese,
    description = null,
    thumbnail = null,
    airDate = aired,
    runtime = null,
    filler = filler ?: false,
    recap = recap ?: false,
    score = score,
    source = EpisodeSource.JIKAN
)

fun KitsuResource.toDomain(): Episode {
    val attrs = attributes
    return Episode(
        number = attrs?.number ?: 0,
        title = attrs?.effectiveTitle,
        description = attrs?.effectiveDescription,
        thumbnail = attrs?.thumbnail?.original,
        airDate = attrs?.airdate,
        runtime = attrs?.length,
        filler = false,
        recap = false,
        score = null,
        source = EpisodeSource.KITSU
    )
}

/**
 * Merge ani.zip + Jikan + Kitsu episodes into a single 1..N list.
 *
 * @param anizip  episodes from ani.zip (primary — has thumbnails + descriptions)
 * @param jikan   episodes from Jikan (has filler flags + scores)
 * @param kitsu   episodes from Kitsu (has thumbnails + descriptions as fallback to ani.zip)
 * @param total   total episode count from AniList (`Media.episodes`)
 */
fun mergeEpisodes(
    anizip: List<Episode>,
    jikan: List<Episode>,
    total: Int?
): List<Episode> = mergeEpisodes(anizip, jikan, emptyList(), total)

fun mergeEpisodes(
    anizip: List<Episode>,
    jikan: List<Episode>,
    kitsu: List<Episode>,
    total: Int?
): List<Episode> {
    val jikanByNumber: Map<Int, Episode> = jikan.filter { it.number > 0 }.associateBy { it.number }
    val anizipByNumber: Map<Int, Episode> = anizip.filter { it.number > 0 }.associateBy { it.number }
    val kitsuByNumber: Map<Int, Episode> = kitsu.filter { it.number > 0 }.associateBy { it.number }

    val anizipNumbers = anizipByNumber.keys
    val jikanNumbers = jikanByNumber.keys
    val kitsuNumbers = kitsuByNumber.keys
    val knownMax = maxOf(
        anizipNumbers.maxOrNull() ?: 0,
        jikanNumbers.maxOrNull() ?: 0,
        kitsuNumbers.maxOrNull() ?: 0
    )
    val upperBound = total?.takeIf { it > knownMax } ?: knownMax

    if (upperBound <= 0 && total == null) return emptyList()

    val resolvedTotal = when {
        total != null && total > 0 -> maxOf(total, knownMax)
        upperBound > 0 -> upperBound
        else -> return emptyList()
    }

    val merged = (1..resolvedTotal).map { n ->
        val az = anizipByNumber[n]
        val jk = jikanByNumber[n]
        val kt = kitsuByNumber[n]
        when {
            az != null && jk != null -> az.copy(
                filler = jk.filler,
                recap = jk.recap,
                score = jk.score ?: az.score
            )
            az != null -> az
            kt != null && jk != null -> kt.copy(
                filler = jk.filler,
                recap = jk.recap,
                score = jk.score ?: kt.score
            )
            kt != null -> kt
            jk != null -> jk
            else -> Episode(
                number = n,
                title = null,
                description = null,
                thumbnail = null,
                airDate = null,
                runtime = null,
                filler = false,
                recap = false,
                score = null,
                source = EpisodeSource.PLACEHOLDER
            )
        }
    }
    return merged
}
