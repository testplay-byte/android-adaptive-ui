package com.adaptiveui.animeapp.core.di

import android.content.Context
import androidx.room.Room
import com.adaptiveui.animeapp.core.database.AnimeDatabase
import com.adaptiveui.animeapp.core.database.dao.CacheDao
import com.adaptiveui.animeapp.core.database.dao.LibraryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing the Room [AnimeDatabase] and its DAOs as singletons.
 *
 * The DB is built with the [AnimeDatabase.prepopulate] callback so the Default category (id=1)
 * exists on first launch.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAnimeDatabase(@ApplicationContext context: Context): AnimeDatabase =
        Room.databaseBuilder(
            context = context,
            klass = AnimeDatabase::class.java,
            name = AnimeDatabase.DB_NAME
        )
            .addCallback(AnimeDatabase.prepopulate)
            // For this demo app, destructive migration is acceptable — schema bumps wipe data.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideLibraryDao(db: AnimeDatabase): LibraryDao = db.libraryDao()

    @Provides
    fun provideCacheDao(db: AnimeDatabase): CacheDao = db.cacheDao()
}
