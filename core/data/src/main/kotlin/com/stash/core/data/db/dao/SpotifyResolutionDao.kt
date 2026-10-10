package com.stash.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.stash.core.data.db.chunkedForBindWrite
import com.stash.core.data.db.entity.SpotifyResolutionEntity

/**
 * Read/write access to the [SpotifyResolutionEntity] cache. TTL/staleness is
 * the caller's concern (via `expiresAtMs`) — this DAO just stores and returns
 * resolution outcomes keyed by `trackId`.
 */
@Dao
interface SpotifyResolutionDao {

    @Query("SELECT * FROM spotify_resolution WHERE trackId = :trackId")
    suspend fun get(trackId: Long): SpotifyResolutionEntity?

    @Upsert
    suspend fun upsert(entity: SpotifyResolutionEntity)

    @Query("DELETE FROM spotify_resolution WHERE trackId IN (:trackIds)")
    suspend fun deleteByTrackIdsRaw(trackIds: List<Long>)

    /**
     * Chunked wrapper for [deleteByTrackIdsRaw]: a batch delete can pass a
     * library-sized id list, so it must chunk under the bind cap (#337).
     */
    suspend fun deleteByTrackIds(trackIds: List<Long>) =
        trackIds.chunkedForBindWrite { deleteByTrackIdsRaw(it) }
}
