package com.stash.core.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stash.core.data.db.StashDatabase
import com.stash.core.data.db.entity.DiscoveryQueueEntity
import com.stash.core.data.db.entity.StashMixRecipeEntity
import com.stash.core.data.db.entity.TrackEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [PlaylistDao.getStreamableOrDoneTrackIdsForRecipe] — the
 * v0.9.37 Stash-Mixes stream-only addition. Mirrors
 * [DiscoveryQueueDao.getDoneTrackIdsForRecipe]'s query shape but relaxes
 * the predicate so stream-only stubs (`is_streamable = 1` without
 * `is_downloaded = 1`) also surface; `StashMixRefreshWorker.materializeMix`
 * uses this method so the Mix playlist includes both downloaded and
 * stream-only discovered tracks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class PlaylistDaoStreamableOrDoneTest {

    private lateinit var db: StashDatabase
    private lateinit var dao: PlaylistDao
    private var recipeId: Long = 0L

    @Before fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            StashDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        dao = db.playlistDao()
        // FK: discovery_queue.recipe_id -> stash_mix_recipes.id (CASCADE).
        recipeId = db.stashMixRecipeDao().insert(
            StashMixRecipeEntity(name = "Test Recipe", isBuiltin = false)
        )
    }

    @After fun tearDown() { db.close() }

    @Test fun `returns downloaded and streamable but not neither`() = runTest {
        // Three tracks, all linked to the same recipe via DONE discovery rows.
        // Downloaded only — must appear.
        db.trackDao().insert(track(id = 1L, isDownloaded = true, isStreamable = false))
        // Stream-only stub (the new v0.9.37 path) — must appear.
        db.trackDao().insert(track(id = 2L, isDownloaded = false, isStreamable = true))
        // Neither — must NOT appear (legacy stub or pending availability check).
        db.trackDao().insert(track(id = 3L, isDownloaded = false, isStreamable = false))

        db.discoveryQueueDao().insertIfNew(doneRow(recipeId, trackId = 1L, completedAt = 1000L))
        db.discoveryQueueDao().insertIfNew(doneRow(recipeId, trackId = 2L, completedAt = 2000L))
        db.discoveryQueueDao().insertIfNew(doneRow(recipeId, trackId = 3L, completedAt = 3000L))

        val result = dao.getStreamableOrDoneTrackIdsForRecipe(recipeId)

        assertEquals(setOf(1L, 2L), result.toSet())
        assertEquals("expected exactly two ids", 2, result.size)
    }

    private fun track(
        id: Long,
        isDownloaded: Boolean,
        isStreamable: Boolean,
    ) = TrackEntity(
        id = id,
        title = "Track $id",
        artist = "Artist $id",
        canonicalTitle = "track $id",
        canonicalArtist = "artist $id",
        isDownloaded = isDownloaded,
        isStreamable = isStreamable,
    )

    private fun doneRow(recipeId: Long, trackId: Long?, completedAt: Long?) = DiscoveryQueueEntity(
        recipeId = recipeId,
        artist = "Artist",
        title = "Title $trackId",
        seedArtist = "Seed",
        status = DiscoveryQueueEntity.STATUS_DONE,
        trackId = trackId,
        queuedAt = 0L,
        completedAt = completedAt,
        errorMessage = null,
    )
}
