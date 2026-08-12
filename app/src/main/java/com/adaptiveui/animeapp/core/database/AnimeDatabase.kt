package com.adaptiveui.animeapp.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.adaptiveui.animeapp.core.database.dao.CacheDao
import com.adaptiveui.animeapp.core.database.dao.LibraryDao
import com.adaptiveui.animeapp.core.database.entity.AnimeCacheEntity
import com.adaptiveui.animeapp.core.database.entity.CategoryEntity
import com.adaptiveui.animeapp.core.database.entity.EpisodeCacheEntity
import com.adaptiveui.animeapp.core.database.entity.HomeCacheEntity
import com.adaptiveui.animeapp.core.database.entity.LibraryCategoryCrossRef
import com.adaptiveui.animeapp.core.database.entity.LibraryEntryEntity

/**
 * The single Room database for the app. Holds:
 *  - the library (entries + categories + many-to-many cross ref)
 *  - the AniList cache (home page + per-anime detail JSON)
 *  - the merged episode cache (per-anime JSON)
 *
 * Schema version is pinned to 1 and `exportSchema = false` because we don't ship migrations
 * for this demo app — wiping the DB on schema change is fine.
 *
 * On first creation the [prepopulate] callback inserts the Default category (id=1) synchronously
 * inside `onCreate` (which runs in a transaction on the DB thread).
 */
@Database(
    entities = [
        CategoryEntity::class,
        LibraryEntryEntity::class,
        LibraryCategoryCrossRef::class,
        AnimeCacheEntity::class,
        EpisodeCacheEntity::class,
        HomeCacheEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AnimeDatabase : RoomDatabase() {

    abstract fun libraryDao(): LibraryDao
    abstract fun cacheDao(): CacheDao

    companion object {
        const val DB_NAME = "anime_app.db"

        /**
         * Inserts the Default category (id=1, isDefault=true, order=0) on first DB creation.
         * Runs synchronously inside `onCreate` (which is wrapped in a transaction by Room) —
         * using a background executor here would race against the transaction's teardown.
         * Idempotent: subsequent app starts skip this because the DB already exists.
         */
        val prepopulate: androidx.room.RoomDatabase.Callback =
            object : androidx.room.RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // Use raw SQL because the Dao isn't available yet on the pre-build database —
                    // `db` is the low-level SupportSQLiteDatabase.
                    val now = System.currentTimeMillis()
                    db.execSQL(
                        "INSERT INTO categories (id, name, isDefault, createdAt, `order`) " +
                            "VALUES (1, 'Default', 1, $now, 0)"
                    )
                }
            }
    }
}
