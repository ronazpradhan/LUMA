package com.stash.core.data.mix

import com.stash.core.data.blocklist.BlocklistGuard
import com.stash.core.data.db.dao.DiscoveryQueueDao
import com.stash.core.data.db.dao.ListeningEventDao
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.db.dao.TrackSkipEventDao
import com.stash.core.data.db.dao.TrackTagDao
import com.stash.core.data.db.entity.StashMixRecipeEntity
import com.stash.core.data.db.entity.TrackEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for [MixGenerator.generate]'s v0.9.20 `excludeIds` parameter — the
 * cross-mix dedup primitive. excludeIds filters the library pool BEFORE
 * scoring + slot allocation, so excluded tracks can never appear in the
 * result.
 */
class MixGeneratorExcludeIdsTest {

    private val trackDao: TrackDao = mockk()
    private val trackTagDao: TrackTagDao = mockk(relaxed = true)
    private val listeningEventDao: ListeningEventDao = mockk(relaxed = true)
    private val discoveryQueueDao: DiscoveryQueueDao = mockk(relaxed = true)
    private val blocklistGuard: BlocklistGuard = mockk(relaxed = true)
    private val trackSkipEventDao: TrackSkipEventDao = mockk(relaxed = true)
    private lateinit var generator: MixGenerator

    @Before fun setUp() {
        generator = MixGenerator(
            trackDao,
            trackTagDao,
            listeningEventDao,
            discoveryQueueDao,
            blocklistGuard,
            trackSkipEventDao,
        )
    }

    @Test fun `excludeIds removes matching tracks from the pool before scoring`() = runTest {
        val tracks = (1L..5L).map { stubTrack(it) }
        coEvery { trackDao.getAllDownloaded() } returns tracks
        coEvery { listeningEventDao.getPlayCountsSinceWithLatest(any()) } returns emptyList()
        coEvery { listeningEventDao.getTrackIdsPlayedSince(any()) } returns emptyList()
        coEvery { trackTagDao.getByTrack(any()) } returns emptyList()

        val result = generator.generate(
            recipe = stubRecipe(discoveryRatio = 0.0f, targetLength = 10),
            excludeIds = setOf(2L, 4L),
        )

        val ids = result.map { it.id }.toSet()
        assertEquals(setOf(1L, 3L, 5L), ids)
        assertTrue("expected 2L excluded", 2L !in ids)
        assertTrue("expected 4L excluded", 4L !in ids)
    }

    @Test fun `empty excludeIds preserves the full library pool`() = runTest {
        val tracks = (1L..5L).map { stubTrack(it) }
        coEvery { trackDao.getAllDownloaded() } returns tracks
        coEvery { listeningEventDao.getPlayCountsSinceWithLatest(any()) } returns emptyList()
        coEvery { listeningEventDao.getTrackIdsPlayedSince(any()) } returns emptyList()
        coEvery { trackTagDao.getByTrack(any()) } returns emptyList()

        val result = generator.generate(stubRecipe(discoveryRatio = 0.0f, targetLength = 10))

        assertEquals(5, result.size)
    }

    private fun stubRecipe(discoveryRatio: Float, targetLength: Int) = StashMixRecipeEntity(
        id = 1L,
        name = "Test",
        discoveryRatio = discoveryRatio,
        targetLength = targetLength,
        freshnessWindowDays = 0,
    )

    private fun stubTrack(id: Long) = TrackEntity(
        id = id,
        title = "Track $id",
        artist = "Artist $id",
        canonicalTitle = "track $id",
        canonicalArtist = "artist $id",
        isDownloaded = true,
    )
}
