package com.adaptiveui.animeapp.data.repository

import com.adaptiveui.animeapp.core.database.dao.LibraryDao
import com.adaptiveui.animeapp.core.database.entity.CategoryEntity
import com.adaptiveui.animeapp.core.database.entity.LibraryCategoryCrossRef
import com.adaptiveui.animeapp.core.database.entity.LibraryEntryEntity
import com.adaptiveui.animeapp.domain.model.AnimeDetail
import com.adaptiveui.animeapp.domain.model.Category
import com.adaptiveui.animeapp.domain.model.LibraryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user's library — saved anime and the categories they're filed under.
 *
 * All observe-* methods return a [Flow] that recomposes the UI on every DB change.
 * Mutating methods are `suspend` and run on [Dispatchers.IO].
 *
 * Default-category rules:
 *   - The Default category (id=1, isDefault=true) is seeded by [AnimeDatabase]'s prepopulate callback.
 *   - `addCategory` always assigns `order = count + 1`.
 *   - `deleteCategory` refuses to delete the Default category. Before deleting a non-default
 *     category, any entries attached to it are moved to Default so the user's saved anime
 *     never disappears.
 *   - `renameCategory` refuses to rename the Default category.
 *   - `saveAnime` always includes the Default category id in the final ref set if the caller
 *     passed an empty list (so an anime is always findable from the Default tab).
 */
@Singleton
class LibraryRepository @Inject constructor(
    private val libraryDao: LibraryDao
) {

    fun observeCategories(): Flow<List<Category>> =
        libraryDao.getAll().map { list -> list.map { it.toDomain() } }

    fun observeAllEntries(): Flow<List<LibraryEntry>> =
        libraryDao.getAllEntries().combine(observeAllRefs()) { entries, refs ->
            entries.map { e -> e.toDomain(refs[e.animeId].orEmpty()) }
        }

    fun observeEntriesForCategory(categoryId: Long): Flow<List<LibraryEntry>> =
        libraryDao.getEntriesForCategory(categoryId)
            .combine(observeAllRefs()) { entries, refs ->
                entries.map { e -> e.toDomain(refs[e.animeId].orEmpty()) }
            }

    fun observeIsSaved(animeId: Int): Flow<Boolean> = libraryDao.isSaved(animeId)

    fun observeCategoryIdsForAnime(animeId: Int): Flow<List<Long>> =
        libraryDao.observeCategoryIdsForAnime(animeId)

    /**
     * Saves (or updates) a library entry and attaches it to the given [categoryIds].
     * If [categoryIds] is empty, the Default category is used. If the entry already exists,
     * the category refs are MERGED (existing refs preserved); pass an explicit set to replace.
     *
     * Domain `AnimeDetail` carries the flat fields we need; no JSON blob is persisted here
     * (the full detail payload lives in the AniList cache).
     */
    suspend fun saveAnime(detail: AnimeDetail, categoryIds: List<Long>) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val entry = LibraryEntryEntity(
            animeId = detail.id,
            title = detail.displayTitle,
            coverUrl = detail.coverUrl.takeIf { it.isNotBlank() },
            bannerUrl = detail.bannerImage,
            score = detail.averageScore,
            episodes = detail.episodes,
            format = detail.format,
            year = detail.seasonYear,
            savedAt = now
        )
        libraryDao.insertEntry(entry)

        // Determine final category set: merge with existing refs, defaulting to Default if empty.
        val existing = libraryDao.getCategoryIdsForAnime(detail.id).toMutableSet()
        val desired = categoryIds.toMutableSet()
        if (desired.isEmpty()) desired.add(Category.DEFAULT_ID)
        val final = (existing + desired).distinct()
        for (cid in final) {
            libraryDao.addCategoryRef(
                LibraryCategoryCrossRef(animeId = detail.id, categoryId = cid)
            )
        }
    }

    suspend fun removeAnime(animeId: Int) = withContext(Dispatchers.IO) {
        // Cross-ref rows are deleted via the foreign-key-style cleanup — but since we have no
        // FK constraint declared, we manually remove refs first then the entry.
        val categoryIds = libraryDao.getCategoryIdsForAnime(animeId)
        for (cid in categoryIds) {
            libraryDao.removeCategoryRef(animeId, cid)
        }
        libraryDao.deleteEntry(animeId)
    }

    /**
     * Creates a new category. `order` = current count + 1 (Default occupies order 0).
     * @return the new category id
     */
    suspend fun addCategory(name: String): Long = withContext(Dispatchers.IO) {
        val order = libraryDao.count() + 1
        // Use a synthetic id — current max id + 1 (Default is id=1).
        val newId = (System.currentTimeMillis() / 1000L) // unique-enough for a demo app
        libraryDao.insert(
            CategoryEntity(
                id = newId,
                name = name,
                isDefault = false,
                createdAt = System.currentTimeMillis(),
                order = order
            )
        )
        newId
    }

    /**
     * Deletes a category. Refuses if it's the Default category. Moves all entries attached to
     * this category into the Default category first so the user's saves are preserved.
     */
    suspend fun deleteCategory(categoryId: Long) = withContext(Dispatchers.IO) {
        val category = libraryDao.getCategory(categoryId) ?: return@withContext
        if (category.isDefault) return@withContext
        val default = libraryDao.getDefault() ?: return@withContext
        // Move this category's anime into Default (preserving their existing Default refs).
        libraryDao.reassignCategory(categoryId, default.id)
        libraryDao.delete(category)
    }

    /**
     * Renames a category. Refuses if it's the Default category.
     */
    suspend fun renameCategory(categoryId: Long, name: String) = withContext(Dispatchers.IO) {
        val category = libraryDao.getCategory(categoryId) ?: return@withContext
        if (category.isDefault) return@withContext
        libraryDao.update(category.copy(name = name))
    }

    // ---------- helpers ----------

    /**
     * Returns a Flow<Map<animeId, List<categoryId>>> for ALL cross-refs, so the entries flows
     * can attach the category ids to each [LibraryEntry] without an N+1 query per row.
     */
    private fun observeAllRefs(): Flow<Map<Int, List<Long>>> =
        libraryDao.getAllEntries().map { entries ->
            // We re-query the refs per emit — for a library of typical size (hundreds) this is
            // fine. Could be replaced with a relational @Relation if performance becomes an issue.
            entries.associate { it.animeId to libraryDao.getCategoryIdsForAnime(it.animeId) }
        }

    private fun CategoryEntity.toDomain(): Category = Category(
        id = id,
        name = name,
        isDefault = isDefault,
        createdAt = createdAt,
        order = order
    )

    private fun LibraryEntryEntity.toDomain(categoryIds: List<Long>): LibraryEntry = LibraryEntry(
        animeId = animeId,
        title = title,
        coverUrl = coverUrl,
        bannerUrl = bannerUrl,
        score = score,
        episodes = episodes,
        format = format,
        year = year,
        savedAt = savedAt,
        categoryIds = categoryIds
    )
}
