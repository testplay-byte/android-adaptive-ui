package com.adaptiveui.animeapp.core.network.anilist

/**
 * GraphQL query strings for the AniList API.
 *
 * - [HOME_PAGE_QUERY] fetches the home page in a single request via 5 aliased `Page` selections
 *   (trending / seasonPopular / upcoming / topRated / allTimePopular). A shared `mediaCard`
 *   fragment is defined inline so every alias returns identical, compact media fields.
 * - [MEDIA_DETAILS_QUERY] fetches every relevant field on a single `Media` (id) including
 *   relations, characters + voice actors, recommendations, rankings, external links, etc.
 * - [SEARCH_QUERY] is a `Page` query with `pageInfo` + `media`, supporting the search filters
 *   used by the Search screen (genre, year, season, format, status, sort).
 *
 * Variables for [HOME_PAGE_QUERY]:
 *   - season: String (e.g. "WINTER")
 *   - seasonYear: Int
 *   - nextSeason: String
 *   - nextYear: Int
 *
 * Variables for [MEDIA_DETAILS_QUERY]:
 *   - id: Int
 *
 * Variables for [SEARCH_QUERY]:
 *   - page: Int
 *   - perPage: Int
 *   - search: String?
 *   - sort: [String]      (defaults to POPULARITY_DESC in code when null)
 *   - genre: String?
 *   - season: String?
 *   - seasonYear: Int?
 *   - format: String?
 *   - status: String?
 */
object AniListQueries {

    const val HOME_PAGE_QUERY = """
        query HomePage(${"$"}season: MediaSeason, ${"$"}seasonYear: Int, ${"$"}nextSeason: MediaSeason, ${"$"}nextYear: Int) {
          trending: Page(page: 1, perPage: 20) {
            media(sort: TRENDING_DESC, type: ANIME, isAdult: false) { ...mediaCard }
          }
          seasonPopular: Page(page: 1, perPage: 20) {
            media(sort: POPULARITY_DESC, season: ${"$"}season, seasonYear: ${"$"}seasonYear, type: ANIME, isAdult: false) { ...mediaCard }
          }
          upcoming: Page(page: 1, perPage: 20) {
            media(sort: POPULARITY_DESC, season: ${"$"}nextSeason, seasonYear: ${"$"}nextYear, type: ANIME, isAdult: false) { ...mediaCard }
          }
          topRated: Page(page: 1, perPage: 20) {
            media(sort: SCORE_DESC, type: ANIME, isAdult: false) { ...mediaCard }
          }
          allTimePopular: Page(page: 1, perPage: 20) {
            media(sort: POPULARITY_DESC, type: ANIME, isAdult: false) { ...mediaCard }
          }
        }

        fragment mediaCard on Media {
          id
          idMal
          title { romaji english native }
          coverImage { extraLarge large medium color }
          bannerImage
          averageScore
          popularity
          episodes
          format
          status
          season
          seasonYear
          startDate { year month day }
          genres
          nextAiringEpisode { episode airingAt timeUntilAiring }
        }
    """

    const val MEDIA_DETAILS_QUERY = """
        query MediaDetails(${"$"}id: Int) {
          Media(id: ${"$"}id, type: ANIME) {
            id
            idMal
            title { romaji english native }
            coverImage { extraLarge large medium color }
            bannerImage
            description(asHtml: false)
            averageScore
            meanScore
            popularity
            favourites
            format
            episodes
            duration
            status
            season
            seasonYear
            startDate { year month day }
            endDate { year month day }
            genres
            synonyms
            source
            hashtag
            siteUrl
            trailer { id site thumbnail }
            tags { id name rank isMediaSpoiler isGeneralSpoiler }
            studios {
              nodes { id name isAnimationStudio siteUrl }
            }
            relations {
              edges {
                id
                relationType(version: 2)
                node {
                  id
                  title { romaji english }
                  coverImage { large }
                  format
                  type
                }
              }
            }
            characters(perPage: 25, sort: ROLE) {
              edges {
                role
                node { id name { full } image { large } }
                voiceActors(language: JAPANESE) { id name { full } image { large } }
              }
            }
            recommendations(perPage: 18, sort: RATING_DESC) {
              nodes {
                rating
                mediaRecommendation {
                  id
                  title { romaji english }
                  coverImage { large }
                  averageScore
                }
              }
            }
            nextAiringEpisode { episode airingAt timeUntilAiring }
            externalLinks { id url site type icon color }
            rankings { id rank type format season year allTime context }
          }
        }
    """

    const val SEARCH_QUERY = """
        query Search(
          ${"$"}page: Int,
          ${"$"}perPage: Int,
          ${"$"}search: String,
          ${"$"}sort: [MediaSort],
          ${"$"}genre: String,
          ${"$"}season: MediaSeason,
          ${"$"}seasonYear: Int,
          ${"$"}format: MediaFormat,
          ${"$"}status: MediaStatus
        ) {
          Page(page: ${"$"}page, perPage: ${"$"}perPage) {
            pageInfo { total currentPage lastPage hasNextPage perPage }
            media(
              type: ANIME,
              search: ${"$"}search,
              sort: ${"$"}sort,
              genre: ${"$"}genre,
              season: ${"$"}season,
              seasonYear: ${"$"}seasonYear,
              format: ${"$"}format,
              status: ${"$"}status,
              isAdult: false
            ) { ...mediaCard }
          }
        }

        fragment mediaCard on Media {
          id
          idMal
          title { romaji english native }
          coverImage { extraLarge large medium color }
          bannerImage
          averageScore
          popularity
          episodes
          format
          status
          season
          seasonYear
          startDate { year month day }
          genres
          nextAiringEpisode { episode airingAt timeUntilAiring }
        }
    """
}
