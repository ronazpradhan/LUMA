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
 * Tests for [MixGenerator.generate]'s v0.9.20 shortfall-fill threshold.
 * The old gate was `discoveryRatio < 1.0f`; the new gate is
 * `discoveryRatio < 0.5f` — high-discovery recipes return a sparser
 * library slice rather than backfilling from the rest of the library.
 */
class MixGeneratorShortfallFillTest {

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
        coEvery { listeningEventDao.getPlayCountsSinceWithLatest(any()) } returns emptyList()
        coEvery { listeningEventDao.getTrackIdsPlayedSince(any()) } returns emptyList()
        coEvery { trackTagDao.getByTrack(any()) } returns emptyList()
    }

    @Test fun `shortfall fill triggers at discoveryRatio = 0_4`() = runTest {
        coEvery { trackDao.getAllDownloaded() } returns (1L..50L).map { stubTrack(it) }

        val result = generator.generate(
            stubRecipe(discoveryRatio = 0.4f, targetLength = 50),
        )

        // librarySlots = 50 * (1 - 0.4) = 30; pool = 50; shortfall fills to 50.
        assertEquals(50, result.size)
    }

    @Test fun `shortfall fill does NOT trigger at discoveryRatio = 0_5`() = runTest {
        coEvery { trackDao.getAllDownloaded() } returns (1L..50L).map { stubTrack(it) }

        val result = generator.generate(
            stubRecipe(discoveryRatio = 0.5f, targetLength = 50),
        )

        // librarySlots = 50 * (1 - 0.5) = 25; no shortfall fill.
        assertEquals(25, result.size)
    }

    @Test fun `shortfall fill does NOT trigger at discoveryRatio = 0_85`() = runTest {
        coEvery { trackDao.getAllDownloaded() } returns (1L..50L).map { stubTrack(it) }

        val result = generator.generate(
            stubRecipe(discoveryRatio = 0.85f, targetLength = 40),
        )

        // librarySlots = (40 * (1f - 0.85f)).toInt() = 5 (float precision:
        // 1f - 0.85f = 0.14999998, * 40 = 5.9999..., truncated). The key
        // assertion is that this is far below targetLength = 40 — i.e. no
        // shortfall fill triggered.
        assertEquals(5, result.size)
    }

    @Test fun `pure discovery recipe at ratio = 1_0 returns empty library slice`() = runTest {
        coEvery { trackDao.getAllDownloaded() } returns (1L..50L).map { stubTrack(it) }

        val result = generator.generate(
            stubRecipe(discoveryRatio = 1.0f, targetLength = 50),
        )

        // librarySlots = 0; no shortfall fill (already gated by < 0.5).
        assertTrue("expected empty, got ${result.size}", result.isEmpty())
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
