package com.adaptiveui.animeapp.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-created library category. The Default category is seeded at DB creation time
 * with id=1, isDefault=true, order=0 and can never be deleted or renamed.
 *
 * `order` is used to render the category bar in the user's chosen order.
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val isDefault: Boolean,
    val createdAt: Long,
    val order: Int
)

/**
 * A saved anime entry in the library. The full AniList detail JSON is NOT stored here — only
 * the flat fields needed to render the library row (cover, title, score, etc.). The full
 * detail payload lives in [AnimeCacheEntity] and is re-read on the Details screen.
 *
 * `animeId` is the AniList Media id (the canonical id used throughout the app).
 */
@Entity(tableName = "library_entries")
data class LibraryEntryEntity(
    @PrimaryKey val animeId: Int,
    val title: String,
    val coverUrl: String?,
    val bannerUrl: String?,
    val score: Int?,
    val episodes: Int?,
    val format: String?,
    val year: Int?,
    val savedAt: Long
)

/**
 * Many-to-many join between library entries and categories.
 * One anime can be in multiple categories (the multi-save feature on the Details screen).
 *
 * Uses an auto-generated `id` as the primary key plus a UNIQUE index on (animeId, categoryId)
 * so duplicate inserts either fail (in a transaction) or can be ignored via `OnConflictStrategy.IGNORE`.
 */
@Entity(
    tableName = "library_category_ref",
    indices = [Index(value = ["animeId", "categoryId"], unique = true)]
)
data class LibraryCategoryCrossRef(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val animeId: Int,
    val categoryId: Long
)

/**
 * Verbatim AniList `Media` JSON cached by [com.adaptiveui.animeapp.data.repository.AnimeRepository].
 * Storing raw JSON keeps the schema stable even when AniList adds new fields; the mapper parses
 * it back into a domain [com.adaptiveui.animeapp.domain.model.AnimeDetail] on read.
 */
@Entity(tableName = "anime_cache")
data class AnimeCacheEntity(
    @PrimaryKey val animeId: Int,
    val json: String,
    val cachedAt: Long
)

/**
 * Verbatim merged episode list JSON cached by [com.adaptiveui.animeapp.data.repository.EpisodeRepository].
 * Source-merged episodes (ani.zip + Jikan + placeholders) live here as a JSON array of
 * [com.adaptiveui.animeapp.domain.model.Episode].
 */
@Entity(tableName = "episode_cache")
data class EpisodeCacheEntity(
    @PrimaryKey val animeId: Int,
    val json: String,
    val cachedAt: Long
)

/**
 * Single-row table (id=0) holding the cached AniList home page JSON. The whole home response
 * (all 5 sections: trending, seasonPopular, upcoming, topRated, allTimePopular) is persisted
 * here verbatim so the Home screen can render offline.
 */
@Entity(tableName = "home_cache")
data class HomeCacheEntity(
    @PrimaryKey val id: Int = 0,
    val json: String,
    val cachedAt: Long
)
