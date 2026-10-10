package com.stash.core.data.mix

import com.stash.core.data.blocklist.BlocklistGuard
import com.stash.core.data.db.dao.DiscoveryQueueDao
import com.stash.core.data.db.dao.ListeningEventDao
import com.stash.core.data.db.dao.ListeningEventDao.TrackPlayCountWithLatest
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.db.dao.TrackSkipEventDao
import com.stash.core.data.db.dao.TrackTagDao
import com.stash.core.data.db.dao.TrackTagDao.TagCount
import com.stash.core.data.db.entity.TrackTagEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [MixGenerator.computeUserTopTags] — specifically the v0.9.19
 * library-histogram fallback path that fires when the user's
 * listening-affinity vector is empty.
 */
class MixGeneratorComputeUserTopTagsTest {

    private val trackDao: TrackDao = mockk(relaxed = true)
    private val trackTagDao: TrackTagDao = mockk()
    private val listeningEventDao: ListeningEventDao = mockk()
    private val discoveryQueueDao: DiscoveryQueueDao = mockk(relaxed = true)
    private val blocklistGuard: BlocklistGuard = mockk(relaxed = true)
    private val trackSkipEventDao: TrackSkipEventDao = mockk(relaxed = true)

    private val subject = MixGenerator(
        trackDao,
        trackTagDao,
        listeningEventDao,
        discoveryQueueDao,
        blocklistGuard,
        trackSkipEventDao,
    )

    // Helper: set up affinity-vector inputs to produce a non-empty vector.
    // One play row with one real tag is enough for compute() to yield a
    // non-empty L2-normalized result.
    private fun stubAffinityVectorNonEmpty(now: Long = System.currentTimeMillis()) {
        coEvery { listeningEventDao.getPlayCountsSinceWithLatest(any()) } returns listOf(
            TrackPlayCountWithLatest(trackId = 1L, plays = 5, latestPlayedAt = now),
        )
        coEvery { trackTagDao.getByTrack(1L) } returns listOf(
            TrackTagEntity(trackId = 1L, tag = "indie", weight = 80f, source = "ARTIST", fetchedAt = now),
            TrackTagEntity(trackId = 1L, tag = "dream pop", weight = 50f, source = "ARTIST", fetchedAt = now),
        )
    }

    // Helper: set up affinity-vector inputs to produce an empty vector
    // (plays exist but every track is untagged or only __untaggable__).
    private fun stubAffinityVectorEmpty(now: Long = System.currentTimeMillis()) {
        coEvery { listeningEventDao.getPlayCountsSinceWithLatest(any()) } returns listOf(
            TrackPlayCountWithLatest(trackId = 1L, plays = 5, latestPlayedAt = now),
        )
        coEvery { trackTagDao.getByTrack(1L) } returns listOf(
            TrackTagEntity(trackId = 1L, tag = "__untaggable__", weight = 0f, source = "ARTIST", fetchedAt = now),
        )
    }

    @Test fun `returns affinity-vector tags when vector is non-empty (histogram NOT consulted)`() = runTest {
        stubAffinityVectorNonEmpty()

        val result = subject.computeUserTopTags(limit = 10)

        assertTrue("expected non-empty result, got $result", result.isNotEmpty())
        assertTrue("indie should be in top tags, got $result", result.contains("indie"))
        coVerify(exactly = 0) { trackTagDao.getTagHistogram() }
    }

    @Test fun `falls back to histogram when affinity vector is empty (no listening events)`() = runTest {
        coEvery { listeningEventDao.getPlayCountsSinceWithLatest(any()) } returns emptyList()
        coEvery { trackTagDao.getTagHistogram() } returns listOf(
            TagCount(tag = "rock", trackCount = 100),
            TagCount(tag = "indie", trackCount = 80),
            TagCount(tag = "pop", trackCount = 60),
        )

        val result = subject.computeUserTopTags(limit = 10)

        assertEquals(listOf("rock", "indie", "pop"), result)
    }

    @Test fun `falls back to histogram when plays exist but all tracks are untagged`() = runTest {
        stubAffinityVectorEmpty()
        coEvery { trackTagDao.getTagHistogram() } returns listOf(
            TagCount(tag = "rock", trackCount = 100),
            TagCount(tag = "indie", trackCount = 80),
        )

        val result = subject.computeUserTopTags(limit = 10)

        assertEquals(listOf("rock", "indie"), result)
    }

    @Test fun `fallback filters out __untaggable__ sentinel`() = runTest {
        coEvery { listeningEventDao.getPlayCountsSinceWithLatest(any()) } returns emptyList()
        coEvery { trackTagDao.getTagHistogram() } returns listOf(
            TagCount(tag = "__untaggable__", trackCount = 200),  // top of histogram
            TagCount(tag = "rock", trackCount = 100),
            TagCount(tag = "indie", trackCount = 80),
        )

        val result = subject.computeUserTopTags(limit = 10)

        assertTrue("__untaggable__ must not appear, got $result", "__untaggable__" !in result)
        assertEquals(listOf("rock", "indie"), result)
    }

    @Test fun `returns empty when both affinity vector and histogram are empty`() = runTest {
        coEvery { listeningEventDao.getPlayCountsSinceWithLatest(any()) } returns emptyList()
        coEvery { trackTagDao.getTagHistogram() } returns emptyList()

        val result = subject.computeUserTopTags(limit = 10)

        assertEquals(emptyList<String>(), result)
    }

    @Test fun `respects the limit on the fallback path`() = runTest {
        coEvery { listeningEventDao.getPlayCountsSinceWithLatest(any()) } returns emptyList()
        coEvery { trackTagDao.getTagHistogram() } returns (1..15).map {
            TagCount(tag = "tag$it", trackCount = 100 - it)
        }

        val result = subject.computeUserTopTags(limit = 10)

        assertEquals(10, result.size)
        assertEquals("tag1", result.first())
        assertEquals("tag10", result.last())
    }

    @Test fun `fallback yields limit real tags even when __untaggable__ tops the histogram`() = runTest {
        // Combined-case regression guard. If the implementation ever
        // accidentally swaps to take().filter() (instead of
        // filter().take()), the sentinel consumes a result slot and
        // we'd return 9 real tags + a hole. Pin the order.
        coEvery { listeningEventDao.getPlayCountsSinceWithLatest(any()) } returns emptyList()
        coEvery { trackTagDao.getTagHistogram() } returns listOf(
            TagCount(tag = "__untaggable__", trackCount = 999),
        ) + (1..11).map { TagCount(tag = "tag$it", trackCount = 100 - it) }

        val result = subject.computeUserTopTags(limit = 10)

        assertEquals(10, result.size)
        assertTrue("sentinel must not appear, got $result", "__untaggable__" !in result)
        assertEquals("tag1", result.first())
        assertEquals("tag10", result.last())
    }
}
