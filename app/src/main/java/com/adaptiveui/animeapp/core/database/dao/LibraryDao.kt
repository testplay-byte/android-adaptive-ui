package com.adaptiveui.animeapp.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.adaptiveui.animeapp.core.database.entity.CategoryEntity
import com.adaptiveui.animeapp.core.database.entity.LibraryCategoryCrossRef
import com.adaptiveui.animeapp.core.database.entity.LibraryEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAOs for the library + categories.
 *
 * All observe-* methods return a [Flow] so the UI recomposes when the DB changes.
 * Mutating methods are `suspend`. Cross-ref inserts use `IGNORE` conflict strategy so
 * re-saving an anime with the same category is a no-op rather than a crash.
 */
@Dao
interface LibraryDao {

    // ---------- categories ----------

    @Query("SELECT * FROM categories ORDER BY `order` ASC, id ASC")
    fun getAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): CategoryEntity?

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getCategory(id: Long): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)

    // ---------- library entries ----------

    @Query("SELECT * FROM library_entries ORDER BY savedAt DESC")
    fun getAllEntries(): Flow<List<LibraryEntryEntity>>

    @Query("SELECT * FROM library_entries WHERE animeId = :animeId LIMIT 1")
    suspend fun getEntry(animeId: Int): LibraryEntryEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM library_entries WHERE animeId = :animeId)")
    fun isSaved(animeId: Int): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: LibraryEntryEntity)

    @Query("DELETE FROM library_entries WHERE animeId = :animeId")
    suspend fun deleteEntry(animeId: Int)

    /**
     * Returns all library entries attached to a given category, newest first.
     * Joined via the cross-ref table.
     */
    @Transaction
    @Query(
        """
        SELECT library_entries.*
        FROM library_entries
        INNER JOIN library_category_ref ON library_category_ref.animeId = library_entries.animeId
        WHERE library_category_ref.categoryId = :categoryId
        ORDER BY library_entries.savedAt DESC
        """
    )
    fun getEntriesForCategory(categoryId: Long): Flow<List<LibraryEntryEntity>>

    // ---------- cross refs ----------

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addCategoryRef(ref: LibraryCategoryCrossRef): Long

    @Query("DELETE FROM library_category_ref WHERE animeId = :animeId AND categoryId = :categoryId")
    suspend fun removeCategoryRef(animeId: Int, categoryId: Long)

    @Query("SELECT categoryId FROM library_category_ref WHERE animeId = :animeId")
    suspend fun getCategoryIdsForAnime(animeId: Int): List<Long>

    @Query("SELECT categoryId FROM library_category_ref WHERE animeId = :animeId")
    fun observeCategoryIdsForAnime(animeId: Int): Flow<List<Long>>

    /**
     * Moves all entries currently attached to `fromCategoryId` to `toCategoryId` before a
     * category is deleted. Runs in a single transaction.
     */
    @Transaction
    suspend fun reassignCategory(fromCategoryId: Long, toCategoryId: Long) {
        val animeIds = getAnimeIdsForCategory(fromCategoryId)
        for (animeId in animeIds) {
            removeCategoryRef(animeId, fromCategoryId)
            addCategoryRef(LibraryCategoryCrossRef(animeId = animeId, categoryId = toCategoryId))
        }
    }

    @Query("SELECT animeId FROM library_category_ref WHERE categoryId = :categoryId")
    suspend fun getAnimeIdsForCategory(categoryId: Long): List<Int>

    /**
     * Removes any category refs whose categoryId no longer exists in `categories`
     * (defensive cleanup — currently unused but available).
     */
    @Query(
        """
        DELETE FROM library_category_ref
        WHERE categoryId NOT IN (SELECT id FROM categories)
        """
    )
    suspend fun deleteOrphanRefs()
}
