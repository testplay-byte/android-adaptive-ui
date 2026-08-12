package com.adaptiveui.animeapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.adaptiveui.animeapp.core.database.entity.AnimeCacheEntity
import com.adaptiveui.animeapp.core.database.entity.EpisodeCacheEntity
import com.adaptiveui.animeapp.core.database.entity.HomeCacheEntity

/**
 * DAOs for the AniList + episode caches. Stored as raw JSON strings — the repositories
 * parse them back into domain models on read. Single-row home cache (id=0).
 */
@Dao
interface CacheDao {

    // ---------- home page cache ----------

    @Query("SELECT * FROM home_cache WHERE id = 0 LIMIT 1")
    suspend fun getHomeCache(): HomeCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveHomeCache(entity: HomeCacheEntity)

    // ---------- anime detail cache ----------

    @Query("SELECT * FROM anime_cache WHERE animeId = :id LIMIT 1")
    suspend fun getAnimeCache(id: Int): AnimeCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAnimeCache(entity: AnimeCacheEntity)

    @Query("DELETE FROM anime_cache WHERE animeId = :id")
    suspend fun deleteAnimeCache(id: Int)

    // ---------- episode cache ----------

    @Query("SELECT * FROM episode_cache WHERE animeId = :id LIMIT 1")
    suspend fun getEpisodeCache(id: Int): EpisodeCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveEpisodeCache(entity: EpisodeCacheEntity)

    @Query("DELETE FROM episode_cache WHERE animeId = :id")
    suspend fun deleteEpisodeCache(id: Int)
}
