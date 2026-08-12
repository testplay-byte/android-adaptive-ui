package com.adaptiveui.animeapp.interpreter

import com.adaptiveui.animeapp.domain.model.AnimeCard
import com.adaptiveui.animeapp.domain.model.AnimeDetail
import com.adaptiveui.animeapp.domain.model.Category
import com.adaptiveui.animeapp.domain.model.Episode
import com.adaptiveui.animeapp.domain.model.LibraryEntry

/**
 * Data available to a [ScreenSpec] during rendering.
 *
 * The interpreter resolves `data("key")` bindings against this map. Each value is a [SpecValue]
 * — either a list of items (for forEach), a single object (for detail), or a primitive.
 */
data class ScreenData(
    val lists: Map<String, List<DataItem>> = emptyMap(),
    val single: Map<String, DataItem> = emptyMap(),
    val callbacks: SpecCallbacks = SpecCallbacks()
) {
    companion object {
        fun fromHome(
            trending: List<AnimeCard>,
            seasonal: List<AnimeCard>,
            upcoming: List<AnimeCard>,
            topRated: List<AnimeCard>,
            allTimePopular: List<AnimeCard>,
            callbacks: SpecCallbacks
        ): ScreenData = ScreenData(
            lists = mapOf(
                "trending" to trending.map { it.toDataItem() },
                "seasonal" to seasonal.map { it.toDataItem() },
                "upcoming" to upcoming.map { it.toDataItem() },
                "topRated" to topRated.map { it.toDataItem() },
                "allTimePopular" to allTimePopular.map { it.toDataItem() }
            ),
            callbacks = callbacks
        )

        fun fromSearch(results: List<AnimeCard>, callbacks: SpecCallbacks): ScreenData = ScreenData(
            lists = mapOf("results" to results.map { it.toDataItem() }),
            callbacks = callbacks
        )

        fun fromLibrary(
            entries: List<LibraryEntry>,
            categories: List<Category>,
            callbacks: SpecCallbacks
        ): ScreenData = ScreenData(
            lists = mapOf(
                "entries" to entries.map { it.toDataItem() },
                "categories" to categories.map { it.toDataItem() }
            ),
            callbacks = callbacks
        )

        fun fromDetail(
            detail: AnimeDetail,
            episodes: List<Episode>,
            callbacks: SpecCallbacks
        ): ScreenData = ScreenData(
            single = mapOf("detail" to detail.toDataItem()),
            lists = mapOf("episodes" to episodes.map { it.toDataItem() }),
            callbacks = callbacks
        )
    }
}

/** Callbacks the spec can trigger (buttons, clicks). */
data class SpecCallbacks(
    val onAnimeClick: (Int) -> Unit = {},
    val onBack: () -> Unit = {},
    val onSearch: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onNavigate: (String) -> Unit = {},
    val onCategorySelect: (Long?) -> Unit = {}
)

/** A single data item with field access. */
data class DataItem(val fields: Map<String, Any?>) {
    operator fun get(key: String): Any? = fields[key]
}

fun AnimeCard.toDataItem(): DataItem = DataItem(
    mapOf(
        "id" to id,
        "title" to displayTitle,
        "coverUrl" to coverUrl,
        "bannerImage" to bannerImage,
        "score" to averageScore,
        "scoreLabel" to scoreLabel,
        "year" to seasonYear,
        "format" to format,
        "episodes" to episodes,
        "genres" to genres
    )
)

fun AnimeDetail.toDataItem(): DataItem = DataItem(
    mapOf(
        "id" to id,
        "title" to displayTitle,
        "coverUrl" to coverUrl,
        "bannerImage" to bannerImage,
        "description" to description,
        "score" to averageScore,
        "scoreLabel" to scoreLabel,
        "popularity" to popularity,
        "favourites" to favourites,
        "format" to format,
        "episodes" to episodes,
        "duration" to duration,
        "status" to status,
        "season" to season,
        "seasonYear" to seasonYear,
        "genres" to genres,
        "studios" to studios.map { it.name },
        "idMal" to idMal
    )
)

fun Episode.toDataItem(): DataItem = DataItem(
    mapOf(
        "number" to number,
        "title" to displayTitle,
        "description" to description,
        "thumbnail" to thumbnail,
        "airDate" to airDate,
        "runtime" to runtime,
        "filler" to filler,
        "score" to score,
        "hasMetadata" to hasMetadata
    )
)

fun LibraryEntry.toDataItem(): DataItem = DataItem(
    mapOf(
        "id" to animeId,
        "title" to title,
        "coverUrl" to coverUrl,
        "bannerImage" to bannerUrl,
        "score" to score,
        "episodes" to episodes,
        "format" to format,
        "year" to year
    )
)

fun Category.toDataItem(): DataItem = DataItem(
    mapOf(
        "id" to id,
        "name" to name,
        "isDefault" to isDefault
    )
)
