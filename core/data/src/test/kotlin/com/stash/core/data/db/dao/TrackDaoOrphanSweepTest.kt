package com.stash.core.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stash.core.data.db.StashDatabase
import com.stash.core.data.db.entity.DiscoveryQueueEntity
import com.stash.core.data.db.entity.PlaylistEntity
import com.stash.core.data.db.entity.PlaylistTrackCrossRef
import com.stash.core.data.db.entity.TrackEntity
import com.stash.core.model.MusicSource
import com.stash.core.model.PlaylistType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the orphan-sweep predicate + atomic delete (data-loss fix). Only a
 * downloaded track with no active membership and no active discovery row may
 * be swept; everything else must survive.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class TrackDaoOrphanSweepTest {

    private lateinit var db: StashDatabase
    private lateinit var trackDao: TrackDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            StashDatabase::class.java,
        ).allowMainThreadQueries().build()
        trackDao = db.trackDao()
    }

    @After fun tearDown() { db.close() }

    private fun track(
        id: Long,
        downloaded: Boolean = true,
        source: MusicSource = MusicSource.SPOTIFY,
    ) = TrackEntity(
        id = id, title = "T$id", artist = "a",
        canonicalTitle = "t$id", canonicalArtist = "a",
        isDownloaded = downloaded, filePath = if (downloaded) "/music/$id.flac" else null,
        source = source,
    )

    @Test fun `sweeps only the unprotected downloaded orphan`() = runTest {
        // 1: true orphan — downloaded, no membership, no discovery row → SWEEP.
        trackDao.insert(track(1))
        // 2: downloaded but has an active playlist membership → survives.
        trackDao.insert(track(2))
        val pl = db.playlistDao().insert(
            PlaylistEntity(
                name = "P", source = MusicSource.SPOTIFY,
                sourceId = "p", type = PlaylistType.CUSTOM,
            ),
        )
        db.playlistDao().insertCrossRef(PlaylistTrackCrossRef(playlistId = pl, trackId = 2, position = 0))
        // 3: downloaded orphan owned by an active (PENDING) discovery row → protected.
        trackDao.insert(track(3))
        val recipeId = db.stashMixRecipeDao().insert(
            com.stash.core.data.db.entity.StashMixRecipeEntity(name = "r"),
        )
        db.discoveryQueueDao().insertIfNew(
            DiscoveryQueueEntity(
                recipeId = recipeId, artist = "a", title = "T3", seedArtist = "s",
                status = DiscoveryQueueEntity.STATUS_PENDING, trackId = 3,
            ),
        )
        // 4: local import (source BOTH), no membership → never swept.
        trackDao.insert(track(4, source = MusicSource.BOTH))
        // 5: not downloaded → never swept.
        trackDao.insert(track(5, downloaded = false))

        val deleted = trackDao.deleteOrphanedDownloadedTracks()

        assertEquals(listOf(1L), deleted.map { it.id })
        // The row is actually gone; the survivors remain.
        assertEquals(
            setOf(2L, 3L, 4L, 5L),
            trackDao.getByIds(listOf(1L, 2L, 3L, 4L, 5L)).map { it.id }.toSet(),
        )
    }

    @Test fun `a removed (tombstoned) membership does not protect an orphan`() = runTest {
        trackDao.insert(track(1))
        val pl = db.playlistDao().insert(
            PlaylistEntity(
                name = "P", source = MusicSource.SPOTIFY,
                sourceId = "p", type = PlaylistType.CUSTOM,
            ),
        )
        // removed_at set = soft-deleted membership; the track is still an orphan.
        db.playlistDao().insertCrossRef(
            PlaylistTrackCrossRef(
                playlistId = pl, trackId = 1, position = 0,
                removedAt = java.time.Instant.parse("2025-01-01T00:00:00Z"),
            ),
        )

        val deleted = trackDao.deleteOrphanedDownloadedTracks()

        assertEquals(listOf(1L), deleted.map { it.id })
    }
}
